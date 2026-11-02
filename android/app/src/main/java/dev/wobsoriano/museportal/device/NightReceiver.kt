package dev.wobsoriano.museportal.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.wobsoriano.museportal.MainActivity
import dev.wobsoriano.museportal.muse

/** The night schedule's two alarms: off at night, and the morning wake that relights the hub. */
class NightReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      NightSchedule.ACTION_OFF -> {
        context.muse.onNightOff()
      }
      NightSchedule.ACTION_ON -> {
        context.muse.onNightOn()
        val launch =
            Intent(context, MainActivity::class.java).apply {
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        runCatching { context.startActivity(launch) }
      }
    }
    // Each alarm is one-shot; re-arm so the schedule repeats tomorrow.
    NightSchedule.armAll(context)
  }
}
