package com.twitter.finagle.memcached.unit

import com.twitter.finagle.{Service, ServiceException}
import com.twitter.finagle.memcached._
import com.twitter.finagle.memcached.protocol._
import com.twitter.hashing.KeyHasher
import com.twitter.concurrent.Broker
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffers
import org.specs.mock.Mockito
import org.specs.Specification
import scala.collection.mutable
import _root_.java.io.{BufferedReader, InputStreamReader}

object ClientSpec extends Specification with Mockito {
  "KetamaClient" should {
    // Test from Smile's KetamaNodeLocatorSpec.scala

    // Load known good results (key, hash(?), continuum ceiling(?), IP)
    val stream = getClass.getClassLoader.getResourceAsStream("ketama_results")
    val reader = new BufferedReader(new InputStreamReader(stream))
    val expected = new mutable.ListBuffer[Array[String]]
    var line: String = null
    do {
      line = reader.readLine
      if (line != null) {
        val segments = line.split(" ")
        segments.length mustEqual 4
        expected += segments
      }
    } while (line != null)
    expected.size mustEqual 99

    // Build Ketama client
    val clients = Map(
      ("10.0.1.1", 11211, 600)  -> mock[Service[Command, Response]],
      ("10.0.1.2", 11211, 300)  -> mock[Service[Command, Response]],
      ("10.0.1.3", 11211, 200)  -> mock[Service[Command, Response]],
      ("10.0.1.4", 11211, 350)  -> mock[Service[Command, Response]],
      ("10.0.1.5", 11211, 1000) -> mock[Service[Command, Response]],
      ("10.0.1.6", 11211, 800)  -> mock[Service[Command, Response]],
      ("10.0.1.7", 11211, 950)  -> mock[Service[Command, Response]],
      ("10.0.1.8", 11211, 100)  -> mock[Service[Command, Response]]
    ) map { case ((h,p,w), v) => KetamaClientKey(h,p,w) -> v }
    val broker = new Broker[NodeHealth]
    val ketamaClient = new KetamaClient(clients, broker.recv, KeyHasher.KETAMA, 160)

    "pick the correct node" in {
      val ipToService = clients map { case (key, service) => key.host -> service } toMap
      val rng = new scala.util.Random
      for (testcase <- expected) {
        val mockClient = ketamaClient.clientOf(testcase(0))
        val expectedService = ipToService(testcase(3))
        val randomResponse = Number(rng.nextLong)

        expectedService.apply(any[Incr]) returns Future.value(randomResponse)

        mockClient.incr("foo")().get mustEqual randomResponse.value
      }
    }

    "release" in {
      ketamaClient.release()
      clients.values foreach { client =>
        there was one(client).release()
      }
    }

    "ejects dead clients" in {
      val serviceA = smartMock[Service[Command,Response]]
      val serviceB = smartMock[Service[Command,Response]]
      val keyA = ("10.0.1.1", 11211, 100)
      val keyB = ("10.0.1.2", 11211, 100)
      val nodeKeyA = KetamaClientKey(keyA._1, keyA._2, keyA._3)
      val services = Map(
        keyA -> serviceA,
        keyB -> serviceB
      ) map { case ((h,p,w), v) => KetamaClientKey(h,p,w) -> v }

      val key = ChannelBuffers.wrappedBuffer("foo".getBytes)
      val value = smartMock[Value]
      value.key returns key
      serviceA(Get(Seq(key))) returns Future.value(Values(Seq(value)))

      val broker = new Broker[NodeHealth]
      val ketamaClient = new KetamaClient(services, broker.recv, KeyHasher.KETAMA, 160)

      ketamaClient.get("foo")()
      there was one(serviceA).apply(any)

      broker !! NodeMarkedDead(nodeKeyA)

      serviceB(Get(Seq(key))) returns Future.value(Values(Seq(value)))
      ketamaClient.get("foo")()
      there was one(serviceB).apply(any)
    }
  }

  "RubyMemCacheClient" should {
    val client1 = mock[Client]
    val client2 = mock[Client]
    val client3 = mock[Client]
    val rubyMemCacheClient = new RubyMemCacheClient(Seq(client1, client2, client3))

    "pick the correct node" in {
      rubyMemCacheClient.clientOf("apple")    must be_==(client1)
      rubyMemCacheClient.clientOf("banana")   must be_==(client2)
      rubyMemCacheClient.clientOf("cow")      must be_==(client1)
      rubyMemCacheClient.clientOf("dog")      must be_==(client1)
      rubyMemCacheClient.clientOf("elephant") must be_==(client3)
    }
    "release" in {
      rubyMemCacheClient.release()
      there was one(client1).release()
      there was one(client2).release()
      there was one(client3).release()
    }
  }

  "PHPMemCacheClient" should {
    val client1 = mock[Client]
    val client2 = mock[Client]
    val client3 = mock[Client]
    val phpMemCacheClient = new PHPMemCacheClient(Array(client1, client2, client3), KeyHasher.FNV1_32)

    "pick the correct node" in {
      phpMemCacheClient.clientOf("apple")    must be_==(client3)
      phpMemCacheClient.clientOf("banana")   must be_==(client1)
      phpMemCacheClient.clientOf("cow")      must be_==(client3)
      phpMemCacheClient.clientOf("dog")      must be_==(client2)
      phpMemCacheClient.clientOf("elephant") must be_==(client2)
    }
    "release" in {
      phpMemCacheClient.release()
      there was one(client1).release()
      there was one(client2).release()
      there was one(client3).release()
    }
  }

}
