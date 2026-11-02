package dev.wobsoriano.museportal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardMarkupTest {
  @Test
  fun plainLineIsBody() {
    val lines = BoardMarkup.parse("Boss · on shift")
    assertEquals(1, lines.size)
    assertEquals(BoardKind.Body, lines[0].kind)
    assertEquals(BoardTone.Normal, lines[0].tone)
    assertEquals(BoardAlign.Center, lines[0].align)
    assertEquals("Boss · on shift", lines[0].text)
  }

  @Test
  fun blankLineIsSpacer() {
    val lines = BoardMarkup.parse("a\n\nb")
    assertEquals(3, lines.size)
    assertNull(lines[1].text)
  }

  @Test
  fun headlineAndHero() {
    val lines = BoardMarkup.parse("# Dinner\n## Big news")
    assertEquals(BoardKind.Headline, lines[0].kind)
    assertEquals(BoardTone.Gold, lines[0].tone)
    assertEquals("Dinner", lines[0].text)
    assertEquals(BoardKind.Hero, lines[1].kind)
    assertEquals("Big news", lines[1].text)
  }

  @Test
  fun divider() {
    val lines = BoardMarkup.parse("---")
    assertEquals(BoardKind.Divider, lines[0].kind)
    assertNull(lines[0].text)
  }

  @Test
  fun tonePrefixesAndCombos() {
    val lines = BoardMarkup.parse("[red] # Alert\n[dim] small note\n> a quote\n~ a footnote\n< • left item")
    assertEquals(BoardKind.Headline, lines[0].kind)
    assertEquals(BoardTone.Red, lines[0].tone)
    assertEquals("Alert", lines[0].text)
    assertEquals(BoardTone.Dim, lines[1].tone)
    assertEquals(BoardKind.Quote, lines[2].kind)
    assertEquals(BoardTone.Dim, lines[2].tone)
    assertEquals(BoardKind.Footnote, lines[3].kind)
    assertEquals(BoardAlign.Left, lines[4].align)
    assertEquals("• left item", lines[4].text)
  }

  @Test
  fun hashWithoutSpaceIsBody() {
    val lines = BoardMarkup.parse("#1 priority")
    assertEquals(BoardKind.Body, lines[0].kind)
    assertEquals("#1 priority", lines[0].text)
  }
}
