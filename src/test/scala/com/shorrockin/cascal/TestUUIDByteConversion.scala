package com.shorrockin.cascal

import org.junit.{Assert, Test}
import utils.{UUID, Conversions}
import org.scalatest.junit.JUnitSuite

/**
 * tests the UUID ability to convert to and from bytes, strings, etc.
 */
class TestUUIDByteConversion extends JUnitSuite {
  import Assert._

  @Test def ensureUUIDConvertsToFromBytes = {
    val original = UUID()
    assertEquals(original, UUID(Conversions.bytes(original)))
  }

  @Test def ensureUUIDConvertsToStringAndBack = {
    val original = UUID()
    val string   = Conversions.string(original)
    assertEquals(original, Conversions.uuid(string))
  }
}