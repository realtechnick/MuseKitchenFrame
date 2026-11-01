package dev.wobsoriano.museportal.printer

import android.graphics.Bitmap
import kotlin.math.roundToInt

/** A packed raster: the bytes, how wide each row is in bytes, and how many rows. */
class RasterData(val bytes: ByteArray, val widthBytes: Int, val heightDots: Int)

/**
 * Turns a finished 1-bit bitmap into the packed rows the TSPL BITMAP command expects:
 * a port of the reference driver's raster.py packing. Bit 0 = black (standard TSPL),
 * MSB first within each byte, rows initialised to 0xFF so unwritten area — including
 * the padding beside a narrow image — stays white instead of printing a black bar.
 * The head lays down a full-width row whatever the source width, so sources are
 * scaled down to the print width or padded out to it, left-aligned on white.
 */
object Raster {

  /** Pack a virtual 1-bit image; [black] answers per dot. Pure, so it unit-tests on the JVM. */
  fun pack(widthDots: Int, heightDots: Int, black: (Int, Int) -> Boolean): RasterData {
    val widthBytes = (widthDots + 7) / 8
    val out = ByteArray(widthBytes * heightDots) { 0xFF.toByte() }
    for (y in 0 until heightDots) {
      val row = y * widthBytes
      for (x in 0 until widthDots) {
        if (black(x, y)) {
          val i = row + (x shr 3)
          out[i] = (out[i].toInt() and ((0x80 shr (x and 7)) xor 0xFF)).toByte()
        }
      }
    }
    return RasterData(out, widthBytes, heightDots)
  }

  /** Pack an Android bitmap that is already 1-bit (pure black / pure white pixels). */
  fun packBitmap(src: Bitmap): RasterData {
    val scaled =
        if (src.width > PrintFormat.WIDTH_DOTS) {
          val h = maxOf(1, (src.height * PrintFormat.WIDTH_DOTS.toDouble() / src.width).roundToInt())
          Bitmap.createScaledBitmap(src, PrintFormat.WIDTH_DOTS, h, true)
        } else {
          src
        }
    val pixels = IntArray(scaled.width * scaled.height)
    scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
    return pack(PrintFormat.WIDTH_DOTS, scaled.height) { x, y ->
      x < scaled.width && isBlack(pixels[y * scaled.width + x])
    }
  }

  private fun isBlack(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (r + g + b) / 3 < 128
  }
}
