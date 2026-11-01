package dev.wobsoriano.museportal.printer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drain hold is the fix for the 2026-11-01 truncations: the link must stay open
 * long enough for the host Bluetooth stack to deliver the whole job. Small jobs get
 * the floor, the window scales with size, and a huge job is capped so the command
 * cannot be pinned open forever.
 */
class Pm230PrinterTest {

  @Test
  fun smallJobsGetTheFloor() {
    assertEquals(SETTLE_MS, drainHoldMs(0))
    assertEquals(SETTLE_MS, drainHoldMs(2_000))
  }

  @Test
  fun holdScalesWithJobSize() {
    // 18 KB at 1,800 B/s is 10 s of drain.
    assertEquals(10_000L, drainHoldMs(18_000))
    // The 61 KB ruler job that truncated at a fixed 5 s hold now gets ~34 s.
    assertEquals(34_000L, drainHoldMs(61_200))
  }

  @Test
  fun hugeJobsAreCapped() {
    assertEquals(DRAIN_HOLD_CAP_MS, drainHoldMs(1_000_000))
  }

  @Test
  fun holdIsMonotonicInSize() {
    var previous = 0L
    for (bytes in listOf(0, 5_000, 21_218, 43_000, 61_204, 115_200, 500_000)) {
      val hold = drainHoldMs(bytes)
      assertTrue("hold for $bytes bytes regressed", hold >= previous)
      previous = hold
    }
  }
}
