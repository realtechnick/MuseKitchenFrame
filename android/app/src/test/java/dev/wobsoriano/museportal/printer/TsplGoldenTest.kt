package dev.wobsoriano.museportal.printer

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The acceptance gate for PM230 printing, mirroring the reference driver's own golden
 * test (test_build_job_reproduces_captured_vendor_job_byte_for_byte): the fixtures in
 * src/test/resources/pm230 are extracted verbatim from an Android HCI snoop capture of
 * the official Nelko app (driver repo, MIT — see the LICENSE beside them). Our builder
 * must reproduce the captured job byte for byte before any build may print for real.
 */
class TsplGoldenTest {

  private fun fixture(name: String): ByteArray {
    val stream = javaClass.getResourceAsStream("/pm230/$name") ?: error("missing test fixture pm230/$name")
    val out = ByteArrayOutputStream()
    stream.use { it.copyTo(out) }
    return out.toByteArray()
  }

  @Test
  fun buildJobReproducesCapturedVendorJobByteForByte() {
    val bitmap = fixture("job_bitmap.bin")
    val expected = fixture("job_expected.bin")
    assertEquals(21_120, bitmap.size)
    assertEquals(21_218, expected.size)

    val job =
        Tspl.buildJob(
            bitmap = bitmap,
            widthBytes = 48,
            heightDots = 440,
            labelWidthMm = 54.0,
            labelHeightMm = 57.0,
            density = 10,
            x = 0,
            y = 8,
        )

    assertArrayEquals(expected, job)
  }

  @Test
  fun labelHeightMatchesTheDriversFormula() {
    // (height + 2*y) / 8 with y = 8: the fixture's 440 dots are its 57.0 mm label.
    assertEquals(57.0, PrintFormat.labelHeightMm(440), 0.001)
    assertEquals(36.0, PrintFormat.labelHeightMm(272), 0.001)
  }
}
