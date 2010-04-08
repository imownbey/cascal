package com.shorrockin.cascal

import session._
import utils.{UUID, Conversions}
import org.specs.{Specification}

object SessionPoolSpec extends Specification {
  import Conversions._

  doBeforeSpec { EmbeddedTestCassandra.init }
  doAfterSpec { EmbeddedTestCassandra.close }

  "cascal session pool" should {
//    setSequential() // ensures these are ran in order
//    shareVariables() // ensures that the pool is retained between runs
//
//    val hosts  = Host("localhost", 9160, 250) :: Nil
//    val params = new PoolParams(10, ExhaustionPolicy.Fail, 500L, 6, 2)
//    val pool = new SessionPool(hosts, params, Consistency.One)

    "borrow method should return usable connection" in {
      var count:Option[Int] = None
      EmbeddedTestCassandra.borrow { (s) => count = Some(s.count("Test" \ "Standard" \ UUID())) }
      count must beSome[Int]
    }
    
//  Can't be run unless we're run sequential, can't run sequential with
//  http://code.google.com/p/specs/issues/detail?id=134
//    "connection should sit idle in pool" in {
//      EmbeddedTestCassandra.borrow { _.count("Test" \ "Standard" \ UUID()) }
//      pool.idle must beEqual(1)
//    }

//  Can't be run unless we're run sequential, can't run sequential with
//  http://code.google.com/p/specs/issues/detail?id=134
//    "be able to close out all connections" in {
//      pool.close
//      pool.idle must beEqual(0)
//    }

  }


}