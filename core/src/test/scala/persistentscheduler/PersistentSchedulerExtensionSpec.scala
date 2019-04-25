package persistentscheduler

import java.util.UUID

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.joda.time.DateTime
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.{Matchers, OneInstancePerTest, WordSpecLike}
import persistentscheduler.scaladsl.PersistentSchedulerExtension

import scala.compat.java8.OptionConverters._
import scala.concurrent.duration._

class PersistentSchedulerExtensionSpec
  extends TestKit(ActorSystem("test"))
    with WordSpecLike
    with OneInstancePerTest
    with Matchers {

  private val settings    = SchedulerSettings(1.second, 5.seconds, 15.seconds)

  "A PersistentScheduler" should {

    "find existing events" in {
      val event1 = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", Some("ref").asJava, None.asJava)
      val event2 = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", Some("ref").asJava, None.asJava)
      val eventOther =
        TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", Some("ref-other").asJava, None.asJava)
      val persistence = InMemorySchedulerPersistence(Seq(event1, event2, eventOther))
      val scheduler   = new PersistentSchedulerExtension(persistence, settings)

      val result = scheduler.findEvents("type", "ref")

      whenReady(result) { s =>
        s should contain allOf (event1, event2)
      }
    }

    "find missing events is empty list" in {
      val eventOther =
        TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", Some("ref-other").asJava, None.asJava)
      val persistence = InMemorySchedulerPersistence(Seq(eventOther))
      val scheduler   = new PersistentSchedulerExtension(persistence, settings)

      val result = scheduler.findEvents("type", "ref")

      whenReady(result) { s =>
        s should equal(List())
      }
    }
  }
}
