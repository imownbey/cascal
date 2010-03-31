package com.shorrockin.cascal.model

/**
 * implementation of a standard key, which is an object which can be thought
 * of as a container for a list of columns. The parent of this will either
 * be a StandardColumnFamily or a SuperKey
 *
 * @author Chris Shorrock
 */
case class StandardKey(val value:String, val family:StandardColumnFamily) extends Key[Column[StandardKey], Seq[Column[StandardKey]]]
                                                                             with StandardColumnContainer[Column[StandardKey], Seq[Column[StandardKey]]] {
  def \(name:Array[Byte]) = new Column(name, this)
  def \(name:Array[Byte], value:Array[Byte]) = new Column(name, value, this)
  def \(name:Array[Byte], value:Array[Byte], time:Long) = new Column(name, value, time, this)
}