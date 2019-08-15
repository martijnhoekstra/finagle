package com.twitter.finagle.server

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ServerInfoTest extends FunSuite {

  test("ServerInfo.Empty") {
    assert(ServerInfo.Empty.environment.isEmpty)
  }

}
