package dev.wobsoriano.museportal.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayTimelineTest {
  @Test
  fun segmentsByMinute() {
    assertNull(DayTimeline.segmentAt(8 * 60 + 59))
    assertEquals("Morning", DayTimeline.segmentAt(9 * 60)?.label)
    assertEquals("Boss on shift", DayTimeline.segmentAt(13 * 60 + 59)?.label)
    assertEquals("Lunch — Kelsie home", DayTimeline.segmentAt(14 * 60)?.label)
    assertEquals("Afternoon shift", DayTimeline.segmentAt(15 * 60)?.label)
    assertEquals("Dinner prep", DayTimeline.segmentAt(17 * 60 + 30)?.label)
    assertEquals("Dinner", DayTimeline.segmentAt(19 * 60)?.label)
    assertEquals("Evening", DayTimeline.segmentAt(22 * 60 + 59)?.label)
  }

  @Test
  fun fractionsClamp() {
    assertEquals(0f, DayTimeline.fractionAt(9 * 60), 0.001f)
    assertEquals(1f, DayTimeline.fractionAt(23 * 60), 0.001f)
    assertEquals(0f, DayTimeline.fractionAt(6 * 60), 0.001f)
    assertEquals(1f, DayTimeline.fractionAt(23 * 60 + 30), 0.001f)
    // 2 PM lunch sits five fourteenths along the bar.
    assertEquals(5f / 14f, DayTimeline.fractionAt(14 * 60), 0.001f)
  }

  @Test
  fun segmentEnds() {
    val lunch = DayTimeline.SEGMENTS.first { it.label.startsWith("Lunch") }
    assertEquals(15 * 60, DayTimeline.endOf(lunch))
    assertEquals(DayTimeline.END_MIN, DayTimeline.endOf(DayTimeline.SEGMENTS.last()))
  }
}
