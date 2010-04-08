package com.shorrockin.cascal

import org.junit.{Assert, Test}
import utils.Conversions
import org.scalatest.junit.JUnitSuite

class TestEmbeddedCassandra extends JUnitSuite with CassandraTestPool {
  import Assert._
  import Conversions._

  @Test def testCanUseSession = borrow { (s) =>
    import s._
    val key    = "Test" \ "Standard" \ "testCanUseSessions"
    val column = key \ "Hello" \ "World"

    insert(column)
    assertEquals(1, list(key).size)
  }
}