package dev.wobsoriano.museportal.device

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import java.util.Calendar

/**
 * The frame's night schedule (Boss, 2026-11-02): the screen goes off at night — 11:59 PM by
 * default — and comes back on its own in the morning, 8 AM by default. Going off is a true
 * display-off through the device admin's force-lock when that grant is in place; the wake is
 * an alarm that brings the hub back to the front with the screen lit.
 *
 * One interference to respect: the developer option "Stay awake" (stay on while plugged in)
 * outranks a scheduled off — the OS would relight the screen moments later. The Settings
 * screen warns about it while it is on; [stayAwakeOn] is the check.
 */
object NightSchedule {
  const val ACTION_OFF = "dev.wobsoriano.museportal.NIGHT_OFF"
  const val ACTION_ON = "dev.wobsoriano.museportal.NIGHT_ON"
  private const val REQ_OFF = 4101
  private const val REQ_ON = 4102

  fun adminComponent(context: Context): ComponentName = ComponentName(context, NightAdminReceiver::class.java)

  fun isAdminActive(context: Context): Boolean {
    val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
    return dpm.isAdminActive(adminComponent(context))
  }

  /** Locks the device now, which turns the display off; false when the admin grant is missing. */
  fun lockNow(context: Context): Boolean {
    if (!isAdminActive(context)) return false
    val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
    return runCatching { dpm.lockNow() }.isSuccess
  }

  /** True when the developer "Stay awake while charging" option is on — it defeats a scheduled off. */
  fun stayAwakeOn(context: Context): Boolean =
      runCatching { AndroidSettings.Global.getInt(context.contentResolver, AndroidSettings.Global.STAY_ON_WHILE_PLUGGED_IN, 0) != 0 }
          .getOrDefault(false)

  /** Whether [nowMin] falls in the night window; the window usually wraps past midnight. */
  fun inWindow(startMin: Int, endMin: Int, nowMin: Int): Boolean =
      if (startMin <= endMin) nowMin in startMin until endMin else nowMin >= startMin || nowMin < endMin

  /** Arms the next off and on alarms from the saved settings; no-op when the schedule is off. */
  fun armAll(context: Context) {
    val settings = Settings(context)
    if (!settings.nightEnabled) {
      cancelAll(context)
      return
    }
    schedule(context, ACTION_OFF, REQ_OFF, nextInstant(settings.nightStartMin))
    schedule(context, ACTION_ON, REQ_ON, nextInstant(settings.nightEndMin))
  }

  fun cancelAll(context: Context) {
    val am = context.getSystemService(AlarmManager::class.java) ?: return
    am.cancel(pending(context, ACTION_OFF, REQ_OFF))
    am.cancel(pending(context, ACTION_ON, REQ_ON))
  }

  /** The next epoch-millis at which [minuteOfDay] occurs — today if still ahead, else tomorrow. */
  fun nextInstant(minuteOfDay: Int, nowMs: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
    val target =
        (cal.clone() as Calendar).apply {
          set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
          set(Calendar.MINUTE, minuteOfDay % 60)
          set(Calendar.SECOND, 0)
          set(Calendar.MILLISECOND, 0)
        }
    if (target.timeInMillis <= nowMs) target.add(Calendar.DAY_OF_YEAR, 1)
    return target.timeInMillis
  }

  private fun pending(context: Context, action: String, requestCode: Int): PendingIntent {
    val intent = Intent(context, NightReceiver::class.java).setAction(action)
    return PendingIntent.getBroadcast(
        context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  }

  private fun schedule(context: Context, action: String, requestCode: Int, atMs: Long) {
    val am = context.getSystemService(AlarmManager::class.java) ?: return
    val pi = pending(context, action, requestCode)
    val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    runCatching {
      if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
      else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
    }
  }
}
