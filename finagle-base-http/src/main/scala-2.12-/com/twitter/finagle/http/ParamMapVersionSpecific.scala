
package com.twitter.finagle.http

import scala.collection.mutable.MapLike

protected trait ParamMapVersionSpecific {
  def setParam[B >: String](kv: (String, B)): ParamMap
  def +[V1 >: String](kv: (String, V1)): ParamMap = setParam(kv._1, kv._2)
  //def +[V1 >: String](key: String, value: V1): ParamMap = setParam(key, value)

  def clearParam(name: String): ParamMap
  def -(name: String): ParamMap = clearParam(name)
}