package dev.wobsoriano.museportal.printer

import java.util.Locale

/**
 * The PM230's printable geometry, from job.py of the reference driver
 * (github.com/an21p/nelko-pm230-driver, MIT): 54 mm paper, a 384-dot (48 mm) head at
 * 8 dots/mm (203 DPI — the printer's own CONFIG? reply confirms it), the bitmap placed
 * at x=0, y=8, and the label length derived from the bitmap height plus the y offset
 * twice.
 */
object PrintFormat {
  const val WIDTH_DOTS = 384
  const val WIDTH_BYTES = 48
  const val PAPER_WIDTH_MM = 54.0
  const val DOTS_PER_MM = 8
  const val BITMAP_X = 0
  const val BITMAP_Y = 8
  const val DEFAULT_DENSITY = 10

  /** Tallest label a single job may render; refused rather than eating a roll. */
  const val MAX_HEIGHT_DOTS = 2400

  fun labelHeightMm(heightDots: Int): Double = (heightDots + 2 * BITMAP_Y) / DOTS_PER_MM.toDouble()
}

/**
 * Builds TSPL print jobs for the Nelko PM230: a line-for-line port of the reference
 * driver's tspl.py, whose output is verified byte-for-byte against a job captured from
 * the official Nelko app (see TsplGoldenTest). The vendor quirks are the format, not
 * bugs: the ESC !? lead, PRINT 1 *before* the BITMAP line, the raster bytes glued
 * directly to the BITMAP header's last comma, and ALLEND closing the job.
 */
object Tspl {
  private const val EOL = "\r\n"
  private const val BITMAP_MODE = 1

  /** The complete byte stream for one label. */
  fun buildJob(
      bitmap: ByteArray,
      widthBytes: Int,
      heightDots: Int,
      labelWidthMm: Double,
      labelHeightMm: Double,
      density: Int,
      x: Int,
      y: Int,
  ): ByteArray {
    val head =
        listOf(
                "\u001B!?",
                "SIZE ${mm(labelWidthMm)} mm,${mm(labelHeightMm)} mm",
                "DIRECTION 0,0",
                "DENSITY $density",
                "CLS",
                "PRINT 1",
                "BITMAP $x,$y,$widthBytes,$heightDots,$BITMAP_MODE,",
            )
            .joinToString(EOL)
    return head.toByteArray(Charsets.US_ASCII) + bitmap + ("ALLEND" + EOL + EOL).toByteArray(Charsets.US_ASCII)
  }

  /** TSPL writes sizes with one decimal and a dot; never let a locale turn it into "54,0". */
  private fun mm(v: Double): String = String.format(Locale.US, "%.1f", v)
}
