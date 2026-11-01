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
 * The trap the whole class is shaped around (paid for in wasted paper by the driver's
 * author, and confirmed in its research notes): Bluetooth SPP transmits asynchronously
 * behind flush(). Closing the socket when flush() returns truncates the job — the
 * printer accepts every byte and prints *nothing*. So send() drains, then holds the
 * link open through a settle window that any chatter from the printer extends.
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
          settle(socket)
        }
        device.name ?: PRINTER_NAME
      }

  /**
   * Hold the link open while the printer works. Any byte back from the printer starts
   * a fresh settle window (it talks while printing); the whole hold is capped so a
   * chatty printer cannot pin the command open past its timeout.
   */
  private fun settle(socket: BluetoothSocket) {
    val input = socket.inputStream
    val buf = ByteArray(256)
    val cap = SystemClock.elapsedRealtime() + SETTLE_CAP_MS
    var deadline = SystemClock.elapsedRealtime() + SETTLE_MS
    while (SystemClock.elapsedRealtime() < deadline) {
      try {
        if (input.available() > 0) {
          input.read(buf)
          deadline = minOf(SystemClock.elapsedRealtime() + SETTLE_MS, cap)
        } else {
          Thread.sleep(SETTLE_POLL_MS)
        }
      } catch (e: IOException) {
        return // The printer went away mid-print; the bytes were already delivered.
      }
    }
  }

  /** Connect on the IO dispatcher, run [block] with the open socket, always close. */
  @SuppressLint("MissingPermission")
  private suspend fun <T> connect(device: BluetoothDevice, block: (BluetoothSocket) -> T): T =
      withContext(Dispatchers.IO) {
        val socket =
            try {
              device.createRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e: Exception) {
              throw CommandFailed("printer unreachable")
            }
        try {
          runCatching { adapter()?.cancelDiscovery() }
          try {
            socket.connect()
          } catch (e: IOException) {
            throw CommandFailed("printer unreachable")
          }
          block(socket)
        } finally {
          runCatching { socket.close() }
        }
      }

  private companion object {
    const val PRINTER_NAME = "PM230"
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    const val CHUNK_BYTES = 1024
    const val SETTLE_MS = 5_000L
    const val SETTLE_CAP_MS = 30_000L
    const val SETTLE_POLL_MS = 50L
  }
}
