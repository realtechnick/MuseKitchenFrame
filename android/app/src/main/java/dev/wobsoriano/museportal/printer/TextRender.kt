package dev.wobsoriano.museportal.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import dev.wobsoriano.muse.CommandFailed

/** Where the wrapped text block sits against the label. */
enum class PrintAlign {
  Left,
  Center,
  Right,
}

/**
 * Lays text out as a 1-bit bitmap at the PM230's 384-dot width, following the reference
 * driver's render/compose semantics: word wrap where a long unbroken token (a SKU, a
 * URL) is split rather than clipped, and alignment that positions the whole wrapped
 * block against the label instead of merely aligning its lines to each other.
 *
 * The text itself is drawn supersampled — laid out and rasterised at 2x in greyscale,
 * area-downsampled to 384, then hard-thresholded. A bake-off against the driver's
 * pipeline (Floyd-Steinberg dither, as PIL applies it) showed the dither is what makes
 * text at this size look speckled; 2x + threshold gives solid, even glyphs, and 4x
 * bought nothing further. Dithering stays reserved for greyscale images, later.
 */
object TextRender {
  private const val SS = 2
  private const val W = PrintFormat.WIDTH_DOTS * SS
  private const val PAD_X = 10 * SS
  private const val TOP_PAD = 6 * SS
  private const val BOTTOM_PAD = 8 * SS
  private const val TITLE_GAP = 8 * SS
  private const val TITLE_SIZE = 34f * SS
  private const val BODY_SIZE = 26f * SS
  private const val TITLE_LINE = 44 * SS
  private const val BODY_LINE = 34 * SS
  private const val PARA_GAP = BODY_LINE / 2

  private class Line(val text: String?, val paint: Paint, val height: Int, val centered: Boolean)

  /** Render [title] (optional, bold, centred) over [text] into a 384-wide 1-bit bitmap. */
  fun render(title: String?, text: String, align: PrintAlign): Bitmap {
    val titlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          typeface = Typeface.DEFAULT_BOLD
          textSize = TITLE_SIZE
          color = Color.BLACK
        }
    val bodyPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          typeface = Typeface.DEFAULT
          textSize = BODY_SIZE
          color = Color.BLACK
        }
    val maxText = (W - 2 * PAD_X).toFloat()

    val lines = ArrayList<Line>()
    if (!title.isNullOrBlank()) {
      for (l in wrap(title.trim(), titlePaint, maxText)) lines += Line(l, titlePaint, TITLE_LINE, centered = true)
      lines += Line(null, bodyPaint, TITLE_GAP, centered = false)
    }
    // The body's block: wrapped lines keep their left alignment to each other, and the
    // block as a whole is positioned per `align`.
    val bodyLines = ArrayList<Line>()
    val paragraphs = text.split('\n')
    paragraphs.forEachIndexed { i, para ->
      if (i > 0) bodyLines += Line(null, bodyPaint, PARA_GAP, centered = false)
      val trimmed = para.trim()
      if (trimmed.isNotEmpty()) {
        for (l in wrap(trimmed, bodyPaint, maxText)) bodyLines += Line(l, bodyPaint, BODY_LINE, centered = false)
      }
    }
    val blockWidth = bodyLines.mapNotNull { it.text }.maxOfOrNull { bodyPaint.measureText(it) } ?: 0f
    val blockX =
        when (align) {
          PrintAlign.Left -> PAD_X.toFloat()
          PrintAlign.Center -> (W - blockWidth) / 2f
          PrintAlign.Right -> W - PAD_X - blockWidth
        }
    lines += bodyLines

    val height = TOP_PAD + lines.sumOf { it.height } + BOTTOM_PAD
    val big = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(big)
    canvas.drawColor(Color.WHITE)
    var y = TOP_PAD
    for (line in lines) {
      val t = line.text
      if (t != null) {
        val x =
            if (line.centered) (W - line.paint.measureText(t)) / 2f
            else if (line.paint === titlePaint) (W - line.paint.measureText(t)) / 2f
            else blockX
        // drawText's y is the baseline; sit it inside the line box.
        canvas.drawText(t, x, y + line.height * 0.78f, line.paint)
      }
      y += line.height
    }

    val small = Bitmap.createScaledBitmap(big, PrintFormat.WIDTH_DOTS, height / SS, true)
    // Threshold at 50%: the coverage the downsample computed becomes the dot decision.
    val px = IntArray(small.width * small.height)
    small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
    for (i in px.indices) {
      val c = px[i]
      val lum = (((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)) / 3
      px[i] = if (lum < 128) Color.BLACK else Color.WHITE
    }
    val out = Bitmap.createBitmap(small.width, small.height, Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, small.width, 0, 0, small.width, small.height)
    return out
  }

  /** Word wrap; a token wider than the line is split at the widest fitting prefix. */
  private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
    val lines = ArrayList<String>()
    var current = ""
    for (raw in text.split(Regex("\\s+"))) {
      var word = raw
      while (paint.measureText(word) > maxWidth) {
        var n = word.length
        while (n > 1 && paint.measureText(word.substring(0, n)) > maxWidth) n--
        lines += word.substring(0, n)
        word = word.substring(n)
      }
      val trial = if (current.isEmpty()) word else "$current $word"
      if (paint.measureText(trial) <= maxWidth) {
        current = trial
      } else {
        lines += current
        current = word
      }
    }
    if (current.isNotEmpty()) lines += current
    return lines
  }
}

/** Assembles a complete TSPL job from title + text: render, pack, wrap in the job bytes. */
object PrintJob {

  /** The job bytes and the label length they declare, in mm. */
  fun buildTextJob(title: String?, text: String, align: PrintAlign, density: Int): Pair<ByteArray, Double> {
    val bitmap = TextRender.render(title, text, align)
    if (bitmap.height > PrintFormat.MAX_HEIGHT_DOTS) {
      throw CommandFailed("print too long")
    }
    val raster = Raster.packBitmap(bitmap)
    val heightMm = PrintFormat.labelHeightMm(raster.heightDots)
    val job =
        Tspl.buildJob(
            bitmap = raster.bytes,
            widthBytes = raster.widthBytes,
            heightDots = raster.heightDots,
            labelWidthMm = PrintFormat.PAPER_WIDTH_MM,
            labelHeightMm = heightMm,
            density = density,
            x = PrintFormat.BITMAP_X,
            y = PrintFormat.BITMAP_Y,
        )
    return job to heightMm
  }
}
