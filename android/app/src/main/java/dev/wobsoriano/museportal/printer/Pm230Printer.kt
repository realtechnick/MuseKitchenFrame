package dev.wobsoriano.museportal.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dev.wobsoriano.muse.CommandFailed
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * How long to keep the link open after the last byte is written, so the host stack
 * can actually deliver the job. Android's flush() returns when the bytes sit in the
 * *host* Bluetooth stack, not in the printer; closing the socket discards whatever
 * the stack has not yet sent, and the printer prints a clean prefix of the job and
 * stops (field-proven 2026-11-01: jobs past ~23 KB truncated, identical jobs at the
 * identical line). The reference driver avoids this on desktop by draining
 * out_waiting; Android exposes no equivalent, so the hold is sized by the job —
 * a deliberately pessimistic drain rate, floored for small jobs, capped so a huge
 * job cannot pin the command open forever.
 */
internal fun drainHoldMs(jobBytes: Int): Long {
  val sized = jobBytes.toLong() * 1_000L / DRAIN_BYTES_PER_SEC
  return sized.coerceIn(SETTLE_MS, DRAIN_HOLD_CAP_MS)
}

internal const val SETTLE_MS = 5_000L
internal const val DRAIN_BYTES_PER_SEC = 1_800L
internal const val DRAIN_HOLD_CAP_MS = 90_000L

/**
 * The one class that touches hardware: sends a built TSPL job to the Nelko PM230 over
 * Bluetooth Classic SPP, a port of the reference driver's port.py.
 *
 * The connection model is deliberate: find the *already bonded* printer, connect per
 * job, and close — the link is never held between jobs (the printer sleeps on a
 * battery, and the vendor phone app must stay able to connect; SPP is a single link,
 * so if that app is connected, our connect fails and reports "printer unreachable",
 * which is the truth). Jobs serialise on a mutex: two overlapping prints must queue,
 * never interleave bytes on the one link.
 *
 * Two traps shape the class, both paid for in wasted paper. The driver's: Bluetooth
 * SPP transmits asynchronously behind flush(), so closing on flush() prints nothing.
 * Ours (2026-11-01): a fixed short settle still closes too early on Android, because
 * the bytes are then only in the host stack — so the settle window scales with the
 * job size; see [drainHoldMs]. And a printer that has just finished a job refuses an
 * immediate reconnect while its stack tears the old link down, so connect retries
 * once after a short pause before reporting "printer unreachable".
 */
class Pm230Printer(private val context: Context) {
  private val mutex = Mutex()

  /** Whether BLUETOOTH_CONNECT is held (pre-Android-12 the legacy permission covers it). */
  private fun hasPermission(): Boolean =
      Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
          ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
              PackageManager.PERMISSION_GRANTED

  private fun adapter(): BluetoothAdapter? =
      (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

  /** The bonded printer, or null. Throws CommandFailed when the permission is missing. */
  @SuppressLint("MissingPermission")
  private fun bondedPrinter(): BluetoothDevice? {
    if (!hasPermission()) throw CommandFailed("printer permission not granted")
    val bonded =
        try {
          adapter()?.bondedDevices.orEmpty()
        } catch (e: SecurityException) {
          throw CommandFailed("printer permission not granted")
        }
    return bonded.firstOrNull { it.name?.startsWith(PRINTER_NAME, ignoreCase = true) == true }
  }

  private fun deviceOrThrow(): BluetoothDevice = bondedPrinter() ?: throw CommandFailed("printer not paired")

  /** A one-line status for Settings and health; never throws. */
  fun statusText(): String =
      try {
        when {
          !hasPermission() -> "Permission needed — reopen the app and allow Nearby devices"
          else -> bondedPrinter()?.let { "${it.name} · paired" } ?: "Not paired — pair the PM230 in Bluetooth settings"
        }
      } catch (e: CommandFailed) {
        e.message ?: "Unknown"
      }

  fun isPaired(): Boolean = try { bondedPrinter() != null } catch (e: Exception) { false }

  /**
   * Open the printer's link and close it again, sending nothing — the only honest
   * reachability test, since discovery can list a switched-off printer. Returns the
   * printer's name. A cold RFCOMM connect to an absent printer can take ~20–30 s to
   * fail inside the OS; that bound is the honest answer, not a bug.
   */
  suspend fun probe(): String =
      mutex.withLock {
        val device = deviceOrThrow()
        connect(device) { /* deliberately send nothing */ }
        device.name ?: PRINTER_NAME
      }

  /** Send one built job and hold the link while the printer works. Returns its name. */
  suspend fun send(job: ByteArray): String =
      mutex.withLock {
        val device = deviceOrThrow()
        connect(device) { socket ->
          val out = socket.outputStream
          try {
            var start = 0
            while (start < job.size) {
              val n = minOf(CHUNK_BYTES, job.size - start)
              out.write(job, start, n)
              start += n
            }
            out.flush()
          } catch (e: IOException) {
            throw CommandFailed("print failed mid-send — may have printed partially")
          }
          settle(socket, job.size)
        }
        device.name ?: PRINTER_NAME
      }

  /**
   * Hold the link open until the job has had time to drain out of the host stack and
   * the printer has gone quiet. The window opens at [drainHoldMs] for the job size;
   * any byte back from the printer counts as still-working and restarts a short quiet
   * window, with a hard cap so a chatty printer cannot pin the command open.
   */
  private fun settle(socket: BluetoothSocket, jobBytes: Int) {
    val input = socket.inputStream
    val buf = ByteArray(256)
    val started = SystemClock.elapsedRealtime()
    val drainUntil = started + drainHoldMs(jobBytes)
    val cap = drainUntil + CHATTER_CAP_MS
    var lastHeard = started
    while (true) {
      val now = SystemClock.elapsedRealtime()
      val drained = now >= drainUntil
      val quiet = now - lastHeard >= QUIET_MS
      if ((drained && quiet) || now >= cap) return
      try {
        if (input.available() > 0) {
          input.read(buf)
          lastHeard = SystemClock.elapsedRealtime()
        } else {
          Thread.sleep(SETTLE_POLL_MS)
        }
      } catch (e: IOException) {
        return // The printer went away mid-print; the bytes were already delivered.
      }
    }
  }

  /** Connect on the IO dispatcher, run [block] with the open socket, always close. */
  private suspend fun <T> connect(device: BluetoothDevice, block: (BluetoothSocket) -> T): T =
      withContext(Dispatchers.IO) {
        val socket = openSocket(device)
        try {
          block(socket)
        } finally {
          runCatching { socket.close() }
        }
      }

  /**
   * Create and connect a socket, retrying once after a short pause: a printer that
   * has just finished a job (or whose previous link is still closing) refuses the
   * first attempt and accepts the second — field-proven 2026-11-01, when an immediate
   * back-to-back print reported "printer unreachable" and the retry succeeded.
   */
  @SuppressLint("MissingPermission")
  private fun openSocket(device: BluetoothDevice): BluetoothSocket {
    var lastError: IOException? = null
    for (attempt in 1..CONNECT_ATTEMPTS) {
      val socket =
          try {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
          } catch (e: Exception) {
            throw CommandFailed("printer unreachable")
          }
      try {
        runCatching { adapter()?.cancelDiscovery() }
        socket.connect()
        return socket
      } catch (e: IOException) {
        lastError = e
        runCatching { socket.close() }
        if (attempt < CONNECT_ATTEMPTS) Thread.sleep(CONNECT_RETRY_MS)
      }
    }
    throw CommandFailed("printer unreachable")
  }

  private companion object {
    const val PRINTER_NAME = "PM230"
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    const val CHUNK_BYTES = 1024
    const val QUIET_MS = 2_000L
    const val CHATTER_CAP_MS = 30_000L
    const val SETTLE_POLL_MS = 50L
    const val CONNECT_ATTEMPTS = 2
    const val CONNECT_RETRY_MS = 3_000L
  }
}
