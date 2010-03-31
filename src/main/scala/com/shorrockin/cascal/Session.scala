package com.shorrockin.cascal


import org.apache.thrift.transport.TSocket
import org.apache.thrift.protocol.TBinaryProtocol
import collection.jcl.Conversions._
import java.util.Arrays
import java.util.{List => JList}
import com.shorrockin.cascal.Conversions._

import model._
import org.apache.cassandra.thrift.{Column => CasColumn}
import org.apache.cassandra.thrift.{Cassandra, ColumnPath, ColumnParent, ColumnOrSuperColumn, ConsistencyLevel}

/**
 * a cascal session is the entry point for interacting with the
 * cassandra system through various path elements.
 *
 * @author Chris Shorrock
 */
class Session(val host:String, val port:Int, val defaultConsistency:Consistency) {

  // TODO - replace with a better way to retrieve the connection
  private val sock    = new TSocket(host, port);
  private val tr      = new TBinaryProtocol(sock);
  private val client  = new Cassandra.Client(tr,tr);
  sock.open();

  def close() = sock.close();

  /**
   * return the current cluster name of the cassandra instance
   */
  lazy val clusterName = client.get_string_property("cluster name")


  /**
   * returns the configuration file of the connected cassandra instance
   */
  lazy val configFile = client.get_string_property("config file")


  /**
   * returns the version of the cassandra instance
   */
  lazy val version = client.get_string_property("version")


  /**
   * returns a map of tokens from the cassandra instance.
   */
  lazy val tokenMap = Map("1" -> "2", "3" -> "4") // in the form of {"token1":"host1","token2":"host2"}


  /**
   * returns all the keyspaces from the cassandra instance
   */
  lazy val keyspaces = client.get_string_list_property("keyspaces")


  /**
   *  returns the column value for the specified column
   */
  def get[ResultType](col:Gettable[ResultType], consistency:Consistency):ResultType = {
    toColumnNameType(col, client.get(col.keyspace.value, col.key.value, toColumnPath(col), consistency))
  }

  
  /**
   * returns the column value for the specified column, using the default consistency
   */
  def get[ResultType](col:Gettable[ResultType]):ResultType = get(col, defaultConsistency)


  /**
   * inserts the specified column value
   */
  def insert(col:Column[_], consistency:Consistency) {
    client.insert(col.keyspace.value, col.key.value, toColumnPath(col), col.value, col.time, consistency)
  }


  /**
   * inserts the specified column value using the default consistency
   */
  def insert(col:Column[_]):Unit = insert(col, defaultConsistency)


  /**
   * counts the number of columns in the specified column container
   */
  def count(container:ColumnContainer[_ ,_], consistency:Consistency):Int = {
    client.get_count(container.keyspace.value, container.key.value, toColumnParent(container), consistency)
  }


  /**
   * performs count on the specified column container
   */
  def count(container:ColumnContainer[_, _]):Int = count(container, defaultConsistency)



  /**
   * performs a list of the provided standard key. uses the list of columns as the predicate
   * to determine which columns to return.
   */
  def list[ResultType](container:ColumnContainer[_, ResultType], predicate:Predicate, consistency:Consistency):ResultType = {
    val results = client.get_slice(container.keyspace.value, container.key.value, toColumnParent(container), predicate.slicePredicate, consistency)
    columnResultListToContainerType(container, results)
  }


  /**
   * performs a list of the specified container using no predicate and the default consistency.
   */
  def list[ResultType](container:ColumnContainer[_, ResultType]):ResultType = list(container, EmptyPredicate, defaultConsistency)


  /**
   * performs a list of the specified container using the specified predicate value
   */
  def list[ResultType](container:ColumnContainer[_, ResultType], predicate:Predicate):ResultType = list(container, predicate, defaultConsistency)


  /**
   * takes in a list of ColumnOrSuperColumn objects, and a container, and based on the
   * container will create the appropriate return type. the possible containers and their
   * return types which could be passed into this method include:
   *
   * StandardKey -> List[ColumnValue[StandardKey]]
   * SuperColumn -> List[ColumnValue[SuperColumn]]
   * SuperKey -> Map[SuperColumn, List[ColumnValue[SuperColumn]]]
   */
  private def columnResultListToContainerType[ResultType](container:ColumnContainer[_, ResultType],
                                                          javaResults:JList[ColumnOrSuperColumn]):ResultType = {
    

    def asColumnList[A, B](c:StandardColumnContainer[Column[A], B], results:Seq[CasColumn] ) = {
      results.map { (casColumn) =>
        c \ (casColumn.getName, casColumn.getValue, casColumn.getTimestamp)
      }
    }

    def asSuperColumnMap(c:SuperKey, results:Seq[ColumnOrSuperColumn]) = {
      var out = Map[SuperColumn, Seq[Column[SuperColumn]]]()
      results.foreach { (result) =>
        val casSuperCol = result.getSuper_column
        val sc = c \ casSuperCol.getName
        out = out + (sc -> asColumnList(sc, casSuperCol.getColumns))
      }
      out
    }

    container match {
      case sk:StandardKey => asColumnList(sk, javaResults.map { _.getColumn }).asInstanceOf[ResultType]
      case sp:SuperColumn => asColumnList(sp, javaResults.map { _.getColumn }).asInstanceOf[ResultType]
      case sk:SuperKey    => asSuperColumnMap(sk, javaResults).asInstanceOf[ResultType]
    }
  }


  /**
   * implicitly coverts a consistency value to an int
   */
  private implicit def toThriftConsistency(c:Consistency):ConsistencyLevel = c.thriftValue


  /**
   * converts a column container to a parent structure
   */
  private def toColumnParent(col:ColumnContainer[_, _]):ColumnParent = col match {
    case key:SuperColumn => val out = new ColumnParent(col.family.value) ; out.setSuper_column(key.value)
    case key:Key[_ , _]  => new ColumnParent(col.family.value)
  }


  /**
   * given the column name which was used to retrieve the result
   * create the instance of E required to interpret this result.
   */
  private def toColumnNameType[E](col:Gettable[E], result:ColumnOrSuperColumn):E = columnToDefinition(col) match {
    // returns a ColumnValue
    case StandardColDef(_, std) =>
      val resCol = result.getColumn
      (std.key.asInstanceOf[StandardKey] \ (resCol.getName, resCol.getValue, resCol.getTimestamp)).asInstanceOf[E]

    // returns a ColumnValue
    case SuperStandardColDef(SuperColDef(_, sup), _) =>
      val resCol = result.getColumn
      (sup \ (resCol.getName, resCol.getValue, resCol.getTimestamp)).asInstanceOf[E]

    // returns a Map[DepStandardColumn -> ColumnValue]
    case SuperColDef(_, sup) =>
      val resCol = result.getSuper_column
      var out = List[Column[SuperColumn]]()

      val scalaColumns = scala.collection.jcl.Conversions.convertList(resCol.getColumns)
      scalaColumns.foreach { (column) =>
        out = (sup \ (column.getName, column.getValue, column.getTimestamp)) :: out
      }
      out.asInstanceOf[E]
  }


  /**
   *  takes any column name and provides a column path to that column. column
   * names will be either a standard column which belongs to a standard key,
   * a super column, or a standard column which belongs to a super key.
   */
  private def toColumnPath(col:Gettable[_]):ColumnPath = columnToDefinition(col) match {
    case StandardColDef(name, std) =>
      val out = new ColumnPath(std.family.value)
      out.setColumn(name)
    case SuperColDef(name, sup) =>
      val out = new ColumnPath(sup.family.value)
      out.setSuper_column(name)
    case SuperStandardColDef(SuperColDef(superVal, _), StandardColDef(colName, std)) =>
      val out = new ColumnPath(std.family.value)
      out.setColumn(colName)
      out.setSuper_column(superVal)
  }


  /**
   * maps a column name to a definition which should allow for better pattern
   * matching.
   */
  private def columnToDefinition(col:Gettable[_]) = col.key match {
    case key:StandardKey => StandardColDef(col.asInstanceOf[Column[_]].name, col.asInstanceOf[Column[_]])
    case key:SuperKey    => col match {
      case sc:SuperColumn       => SuperColDef(col.value, sc)
      case sc:Column[_] =>
        val scOwner        = sc.owner.asInstanceOf[SuperColumn]
        val superColDef    = SuperColDef(scOwner.value, scOwner)
        val standardColDef = StandardColDef(sc.name, sc)
        SuperStandardColDef(superColDef, standardColDef)
    }
  }

  private abstract class ColumnDefinition
  private case class StandardColDef(val value:Array[Byte], val column:Column[_]) extends ColumnDefinition
  private case class SuperColDef(val value:Array[Byte], val column:SuperColumn) extends ColumnDefinition
  private case class SuperStandardColDef(val superCol:SuperColDef, val standardCol:StandardColDef) extends ColumnDefinition
}