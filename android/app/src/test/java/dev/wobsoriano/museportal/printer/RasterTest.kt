package dev.wobsoriano.museportal.printer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Raster packing, checked the way the reference driver's traps demand. */
class RasterTest {

  @Test
  fun whiteStaysWhite() {
    // Rows initialise to 0xFF: an all-white image is all 0xFF, never a black bar.
    val r = Raster.pack(384, 3) { _, _ -> false }
    assertEquals(48, r.widthBytes)
    assertEquals(3, r.heightDots)
    assertTrue(r.bytes.all { it == 0xFF.toByte() })
  }

  @Test
  fun firstDotClearsTheTopBitOfTheFirstByte() {
    val r = Raster.pack(384, 1) { x, y -> x == 0 && y == 0 }
    assertEquals(0x7F.toByte(), r.bytes[0])
    assertTrue(r.bytes.drop(1).all { it == 0xFF.toByte() })
  }

  @Test
  fun lastDotClearsTheLowBitOfTheLastByte() {
    val r = Raster.pack(384, 1) { x, y -> x == 383 && y == 0 }
    assertEquals(0xFE.toByte(), r.bytes[47])
    assertTrue(r.bytes.dropLast(1).all { it == 0xFF.toByte() })
  }

  @Test
  fun narrowRowsPackAtTheFullWidth() {
    // A 100-dot-wide virtual image still packs into 48-byte rows (the caller's lambda
    // simply never reports black past its own width — the padding stays white).
    val r = Raster.pack(384, 2) { x, y -> x < 100 && y == 0 }
    assertEquals(96, r.bytes.size)
  }
}
