package dev.wobsoriano.museportal.ui

/** How one status-board line is set: its size and posture on the panel. */
enum class BoardKind {
  Body,
  Headline,
  Hero,
  Quote,
  Footnote,
  Divider,
}

/** A status-board line's color voice. */
enum class BoardTone {
  Normal,
  Gold,
  Dim,
  Green,
  Blue,
  Red,
}

enum class BoardAlign {
  Center,
  Left,
}

/** One parsed line of the hub status body. A blank source line parses to null text (a spacer). */
data class BoardLine(
    val text: String?,
    val kind: BoardKind = BoardKind.Body,
    val tone: BoardTone = BoardTone.Normal,
    val align: BoardAlign = BoardAlign.Center,
)

/**
 * The status board's light markup (Boss's dynamic panel, 2026-11-02): plain lines, with
 * prefixes that compose the layout. Line prefixes, applied in this order:
 *
 * - `[gold] `, `[dim] `, `[green] `, `[blue] `, `[red] ` — the line's tone
 * - `## ` — a hero line (largest, gold); `# ` — a headline (gold; the v0.9.2 markup)
 * - `> ` — a quote line (dimmer, smaller); `~ ` — a footnote line (smallest, dim)
 * - `---` alone — a gold divider rule
 * - `< ` — set the line against the left margin instead of centered
 *
 * A blank line is a spacer; consecutive blanks stack into bigger air. Everything else is
 * body text, exactly as written.
 */
object BoardMarkup {
  private val TONES =
      listOf(
          "[gold] " to BoardTone.Gold,
          "[dim] " to BoardTone.Dim,
          "[green] " to BoardTone.Green,
          "[blue] " to BoardTone.Blue,
          "[red] " to BoardTone.Red,
      )

  fun parse(body: String): List<BoardLine> = body.split("\n").map(::parseLine)

  private fun parseLine(raw: String): BoardLine {
    if (raw.isBlank()) return BoardLine(null)
    var line = raw
    var tone = BoardTone.Normal
    for ((prefix, t) in TONES) {
      if (line.startsWith(prefix)) {
        tone = t
        line = line.removePrefix(prefix)
        break
      }
    }
    if (line.trim() == "---") return BoardLine(null, BoardKind.Divider, tone)
    var align = BoardAlign.Center
    if (line.startsWith("< ")) {
      align = BoardAlign.Left
      line = line.removePrefix("< ")
    }
    val headlineTone = if (tone == BoardTone.Normal) BoardTone.Gold else tone
    return when {
      line.startsWith("## ") -> BoardLine(line.removePrefix("## "), BoardKind.Hero, headlineTone, align)
      line.startsWith("# ") -> BoardLine(line.removePrefix("# "), BoardKind.Headline, headlineTone, align)
      line.startsWith("> ") -> BoardLine(line.removePrefix("> "), BoardKind.Quote, if (tone == BoardTone.Normal) BoardTone.Dim else tone, align)
      line.startsWith("~ ") -> BoardLine(line.removePrefix("~ "), BoardKind.Footnote, if (tone == BoardTone.Normal) BoardTone.Dim else tone, align)
      else -> BoardLine(line, BoardKind.Body, tone, align)
    }
  }
}
