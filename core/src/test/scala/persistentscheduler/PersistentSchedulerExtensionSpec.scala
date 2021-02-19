package persistentscheduler

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.OneInstancePerTest
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import persistentscheduler.scaladsl.PersistentSchedulerExtension

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration._

class PersistentSchedulerExtensionSpec
    extends TestKit(ActorSystem("test"))
    with AnyWordSpecLike
    with OneInstancePerTest
    with Matchers {

  private val settings = SchedulerSettings(1.second, 5.seconds, 15.seconds)

  "A PersistentScheduler" should {

    "find existing events" in {
      val event1 =
        TimedEvent(Id(UUID.randomUUID()), Instant.now().plusSeconds(5), EventType("type"), Some(Reference("ref")), None)
      val event2 =
        TimedEvent(Id(UUID.randomUUID()), Instant.now().plusSeconds(5), EventType("type"), Some(Reference("ref")), None)
      val eventOther = TimedEvent(
        Id(UUID.randomUUID()),
        Instant.now().plusSeconds(5),
        EventType("type"),
        Some(Reference("ref-other")),
        None)

      val persistence = InMemorySchedulerPersistence(event1, event2, eventOther)
      val scheduler   = new PersistentSchedulerExtension(persistence, settings)

      val result = scheduler.findEvents(EventType("type"), Reference("ref"))

      whenReady(result) { s =>
        s must contain.allOf(event1, event2)
      }
    }

    "find missing events is empty list" in {
      val eventOther = TimedEvent(
        Id(UUID.randomUUID()),
        Instant.now().plusSeconds(5),
        EventType("type"),
        Some(Reference("ref-other")),
        None)

      val persistence = InMemorySchedulerPersistence(eventOther)
      val scheduler   = new PersistentSchedulerExtension(persistence, settings)

      val result = scheduler.findEvents(EventType("type"), Reference("ref"))

      whenReady(result) { s =>
        s mustBe Nil
      }
    }
  }

}
