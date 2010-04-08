package com.shorrockin.cascal

import org.junit.{Assert, Test}
import com.shorrockin.cascal.utils.UUID
import org.scalatest.junit.JUnitSuite

/**
 * tests session removal
 */
class TestRemoval extends JUnitSuite with CassandraTestPool {
  import com.shorrockin.cascal.utils.Conversions._
  import Assert._

  @Test def testKeyRemoval = borrow { (s) =>
    val std = s.insert("Test" \ "Standard" \ UUID() \ ("Column", "Value"))
    val sup = s.insert("Test" \\ "Super" \ "SuperKey" \ UUID() \ ("Column", "Value"))

    s.remove(std.key)
    s.remove(sup.key)

    assertEquals(None, s.get(std))
    assertEquals(None, s.get(sup))
  }

  @Test def testColumnRemoval = borrow { (s) =>
    val std = s.insert("Test" \ "Standard" \ UUID() \ ("Column", "Value"))
    val sup = s.insert("Test" \\ "Super" \ "SuperKey" \ UUID() \ ("Column", "Value"))

    s.remove(std)
    s.remove(sup)

    assertEquals(None, s.get(std))
    assertEquals(None, s.get(sup))
  }
}