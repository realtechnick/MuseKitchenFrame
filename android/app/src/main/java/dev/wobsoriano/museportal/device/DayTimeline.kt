package dev.wobsoriano.museportal.device

/** One named stretch of the household day on the hub's timeline bar. */
data class DaySegment(val startMin: Int, val label: String)

/**
 * The hub clock's day timeline (Boss's design, 2026-11-02): a bar from 9 AM to 11 PM with a
 * marker for the current time and ticks where the day's named stretches begin. A stretch's
 * label is only shown while the clock is inside it — the bar otherwise speaks in ticks.
 */
object DayTimeline {
  const val START_MIN = 9 * 60
  const val END_MIN = 23 * 60

  val SEGMENTS: List<DaySegment> =
      listOf(
          DaySegment(9 * 60, "Morning"),
          DaySegment(10 * 60, "Boss on shift"),
          DaySegment(14 * 60, "Lunch — Kelsie home"),
          DaySegment(15 * 60, "Afternoon shift"),
          DaySegment(17 * 60 + 30, "Dinner prep"),
          DaySegment(19 * 60, "Dinner"),
          DaySegment(20 * 60, "Evening"),
      )

  /** The segment the given minute of day falls in, or null before the bar's day begins. */
  fun segmentAt(minuteOfDay: Int): DaySegment? = SEGMENTS.lastOrNull { it.startMin <= minuteOfDay }

  /** Where a minute of day sits along the bar, 0 at 9 AM and 1 at 11 PM, clamped. */
  fun fractionAt(minuteOfDay: Int): Float =
      ((minuteOfDay - START_MIN).toFloat() / (END_MIN - START_MIN)).coerceIn(0f, 1f)

  /** Where a segment start sits along the bar. */
  fun fractionAt(segment: DaySegment): Float = fractionAt(segment.startMin)

  /** The minute a segment ends: the next segment's start, or the bar's end for the last one. */
  fun endOf(segment: DaySegment): Int =
      SEGMENTS.firstOrNull { it.startMin > segment.startMin }?.startMin ?: END_MIN
}
