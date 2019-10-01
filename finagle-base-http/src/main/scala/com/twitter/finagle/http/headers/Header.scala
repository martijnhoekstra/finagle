package com.twitter.finagle.http.headers

import scala.collection.mutable
import scala.collection.AbstractIterator

private[http] final class Header(val name: String, val value: String, var next: Header = null)
  extends HeaderMap.NameValue {
    
  def iterator: Iterator[HeaderMap.NameValue] =
    if (next == null) Iterator.single(this)
    else {
      var cur = this
      new AbstractIterator[HeaderMap.NameValue] {
        def hasNext: Boolean = cur != null
        def next(): HeaderMap.NameValue = {
          var n = cur
          cur = n.next
          n
        }
      }
    }

  def values: Seq[String] =
    if (next == null) value :: Nil
    else {
      val result = new mutable.ListBuffer[String] += value

      var i = next
    do {
      result += i.value
      i = i.next
    } while (i != null)

    result.toList
  }

  def names: Seq[String] =
    if (next == null) name :: Nil
    else {
      val result = new mutable.ListBuffer[String] += name

      var i = next
      do {
        result += i.name
        i = i.next
      } while (i != null)

    result.toList
  }

  def add(h: Header): Unit = {
    var i = this
    while (i.next != null) {
      i = i.next
    }

    i.next = h
  }
}