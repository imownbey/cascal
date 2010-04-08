package com.shorrockin.cascal

import org.apache.cassandra.thrift.CassandraDaemon
import org.apache.cassandra.config.DatabaseDescriptor
import java.io.File
import java.net.ConnectException
import org.apache.thrift.transport.{TTransportException, TSocket}
import session._
import utils.{Utils, Logging}
/**
 * trait which mixes in the functionality necessary to embed
 * cassandra into a unit test
 */
trait CassandraTestPool extends Logging {
  def borrow(f:(Session) => Unit) = {
    EmbeddedTestCassandra.init
    EmbeddedTestCassandra.pool.get.borrow(f)
  }
}

/**
 * maintains the single instance of the cassandra server
 */
object EmbeddedTestCassandra extends Logging {
  import Utils._
  var initialized = false

  val hosts  = Host("localhost", 9160, 250) :: Nil
  val params = new PoolParams(10, ExhaustionPolicy.Fail, 500L, 6, 2)
  
  var daemon:Option[CassandraDaemonThread] = None
  var pool:Option[SessionPool] = None

  def close() = synchronized {
    pool.foreach { _.close }
    daemon.foreach { _.close }
    initialized = false
  }

  def borrow(f:(Session) => Unit) = {
    pool.foreach { _.borrow(f) }
  }

  def init() = synchronized {
    if (!initialized) {
      val homeDirectory = new File("target/cassandra.home.unit-tests")
      delete(homeDirectory)
      homeDirectory.mkdirs

      log.debug("creating cassandra instance at: " + homeDirectory.getCanonicalPath)
      log.debug("copying cassandra configuration files to root directory")
    
      val fileSep     = System.getProperty("file.separator")
      val storageFile = new File(homeDirectory, "storage-conf.xml")
      val logFile     = new File(homeDirectory, "log4j.properties")

      replace(copy(resource("/storage-conf.xml"), storageFile), ("%temp-dir%" -> (homeDirectory.getCanonicalPath + fileSep)))
      copy(resource("/log4j.properties"), logFile)

      System.setProperty("storage-config", homeDirectory.getCanonicalPath)

      log.debug("creating data file and log location directories")
      DatabaseDescriptor.getAllDataFileLocations.foreach { (file) => new File(file).mkdirs }
      new File(DatabaseDescriptor.getLogFileLocation).mkdirs

      val thread = new CassandraDaemonThread
      thread.start

      // try to make sockets until the server opens up - there has to be a better
      // way - just not sure what it is.
      val socket = new TSocket("localhost", 9160);
      var opened = false

      log.debug("waiting for confirmation of cassandra open for business")

      while (!opened) {  
        try {
          socket.open()
          opened = true
          log.debug("cassandra db confirmed open")
          socket.close()
        } catch {
          case e:TTransportException => /* ignore */
          case e:ConnectException => /* ignore */
        }
      }

      initialized = true
      daemon = Some(thread)
      pool = Some(new SessionPool(hosts, params, Consistency.One))
    }
  }

  private def resource(str:String) = classOf[CassandraTestPool].getResourceAsStream(str)
}

/**
 * daemon thread used to start and stop cassandra
 */
class CassandraDaemonThread extends Thread("CassandraDaemonThread") with Logging {
  private val daemon = new CassandraDaemon

  setDaemon(true)

  override def run:Unit = {
    log.debug("initializing cassandra daemon")
    daemon.init(new Array[String](0))
    log.debug("starting cassandra daemon")
    daemon.start
  }

  def close():Unit = {
    log.debug("instructing cassandra deamon to shut down")
    daemon.stop
    log.debug("blocking on cassandra shutdown")
    this.join
    log.debug("cassandra shut down")
  }
}

