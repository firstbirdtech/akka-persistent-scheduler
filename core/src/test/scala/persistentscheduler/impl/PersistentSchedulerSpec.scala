package persistentscheduler.impl

import java.time.Instant
import java.util.UUID

import akka.actor.{ActorSystem, Props, Scheduler}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.miguno.akka.testing.VirtualTime
import org.scalatest.OneInstancePerTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import persistentscheduler._
import persistentscheduler.impl.PersistentScheduler.{Request, Result}
import persistentscheduler.scaladsl.SchedulerPersistence

import scala.concurrent.Future
import scala.concurrent.duration._

class PersistentSchedulerSpec
    extends TestKit(ActorSystem("test"))
    with AnyWordSpecLike
    with OneInstancePerTest
    with ScalaFutures
    with Matchers
    with ImplicitSender {

  private val time     = new VirtualTime
  private val settings = SchedulerSettings(3.seconds, 30.seconds, 15.seconds)

  "A PersistentScheduler" should {

    "persist events when they are scheduled" in {
      val persistence = InMemorySchedulerPersistence()

      val testRef = TestActorRef(PersistentScheduler(persistence, settings))
      val event   = TimedEvent(Id(UUID.randomUUID()), Instant.now().plusSeconds(5), EventType("type"), None, None)
      testRef ! Request.Schedule(event)

      expectMsg(())
      whenReady(persistence.count())(_ mustBe 1)
    }

    "send fired events to subscribed actors" in {
      val scheduler = persistentSchedulerWithVirtualTime()

      scheduler ! Request.SubscribeActorRef(self, EventType("type"))
      expectMsg(Result.SubscribedActorRef(self))

      val event = TimedEvent(Id(UUID.randomUUID()), Instant.now().plusSeconds(5), EventType("type"), None, None)

      scheduler ! Request.Schedule(event)
      expectMsg(())

      time.advance(3.seconds) // trigger scheduled check for new events

      // expect event published after schedule check
      delayed {
        time.advance(5.seconds)
        expectMsg(event)
      }
    }

    "send no events if time is not come" in {
      val scheduler = persistentSchedulerWithVirtualTime()

      scheduler ! Request.SubscribeActorRef(self, EventType("type"))
      expectMsg(Result.SubscribedActorRef(self))

      val event = TimedEvent(Id(UUID.randomUUID()), Instant.now().plusSeconds(5), EventType("type"), None, None)

      scheduler ! Request.Schedule(event)
      expectMsg(())

      time.advance(3.seconds)

      delayed {
        expectNoMessage()
      }
    }

    "load an additional timed event after publishing one" in {
      val scheduler = persistentSchedulerWithVirtualTime()

      scheduler ! Request.SubscribeActorRef(self, EventType("type"))
      expectMsg(Result.SubscribedActorRef(self))

      val event = TimedEvent(Id(UUID.randomUUID()), Instant.now().plusSeconds(5), EventType("type"), None, None)
      scheduler ! Request.Schedule(event)
      expectMsg(())

      val event2 = TimedEvent(Id(UUID.randomUUID()), Instant.now().plusSeconds(10), EventType("type"), None, None)
      scheduler ! Request.Schedule(event2)
      expectMsg(())

      time.advance(3.seconds) // trigger scheduled check for new events

      // expect event published after schedule check
      delayed {
        time.advance(5.seconds)
        expectMsg(event)
      }

      // trigger scheduled check for new events after interval
      time.advance(30.seconds)

      // expect event published after interval
      delayed {
        time.advance(40.seconds)
        expectMsg(event2)
      }
    }

    "schedule existing events from persistence on startup" in {
      val existingEvent                 = TimedEvent(Id(UUID.randomUUID()), Instant.now().plusSeconds(5), EventType("type"), None, None)
      val persistenceWithExistingEvents = InMemorySchedulerPersistence(existingEvent)

      val scheduler = persistentSchedulerWithVirtualTime(persistenceWithExistingEvents)

      scheduler ! Request.SubscribeActorRef(self, EventType("type"))
      expectMsg(Result.SubscribedActorRef(self))

      time.advance(3.seconds)

      delayed {
        time.advance(5.seconds)
        expectMsgClass(classOf[TimedEvent])
      }
    }

    "delete events when they have been published" in {
      val persistence = InMemorySchedulerPersistence(
        TimedEvent(Id(UUID.randomUUID()), Instant.now().plusSeconds(5), EventType("type"), None, None)
      )

      val scheduler = persistentSchedulerWithVirtualTime(persistence)

      scheduler ! Request.SubscribeActorRef(self, EventType("type"))
      expectMsg(Result.SubscribedActorRef(self))

      time.advance(3.seconds)

      delayed {
        time.advance(5.seconds)
        expectMsgClass(classOf[TimedEvent])
      }

      whenReady(persistence.count())(_ mustBe 0)
    }

    "remove persisted events if they are removed from the schedule" in {
      val persistence = InMemorySchedulerPersistence(
        TimedEvent(Id(UUID.randomUUID()), Instant.now().plusSeconds(5), EventType("type"), Some(Reference("ref")), None)
      )
      val scheduler = persistentSchedulerWithVirtualTime(persistence)

      scheduler ! Request.RemoveEventsByReference(EventType("type"), Reference("ref"))
      expectMsg(())

      whenReady(persistence.count())(_ mustBe 0)
    }

    "remove already scheduled events if they are removed from the schedule" in {
      val persistence = InMemorySchedulerPersistence(
        TimedEvent(Id(UUID.randomUUID()), Instant.now().plusSeconds(5), EventType("type"), Some(Reference("ref")), None)
      )
      val scheduler = persistentSchedulerWithVirtualTime(persistence)

      scheduler ! Request.SubscribeActorRef(self, EventType("type"))
      expectMsg(Result.SubscribedActorRef(self))

      scheduler ! Request.RemoveEventsByReference(EventType("type"), Reference("ref"))
      expectMsg(())

      time.advance(5.seconds)

      expectNoMessage(1.second)
    }

    "schedules events that have been manually inserted into the persistence after start" in {
      val persistence = InMemorySchedulerPersistence()
      val scheduler   = persistentSchedulerWithVirtualTime(persistence)

      scheduler ! Request.SubscribeActorRef(self, EventType("type"))
      expectMsg(Result.SubscribedActorRef(self))

      val expectedEvent = TimedEvent(Id(UUID.randomUUID()), Instant.now().plusSeconds(5), EventType("type"), None, None)
      persistence.save(expectedEvent)

      time.advance(1.minutes)
      expectNoMessage(200.millis)

      time.advance(5.seconds)
      expectMsg(expectedEvent)
    }

    "keeps subscriptions even after a restart" in {
      val buggyPersistence: InMemorySchedulerPersistence = new InMemorySchedulerPersistence() {
        var failed: Boolean = false

        override def save(event: TimedEvent): Future[TimedEvent] = {
          if (!failed) {
            failed = true
            throw new Exception("throw this at the first time to force actor restart")
          } else {
            super.save(event)
          }
        }
      }

      val scheduler = persistentSchedulerWithVirtualTime(buggyPersistence)

      scheduler ! Request.SubscribeActorRef(self, EventType("type"))
      expectMsg(Result.SubscribedActorRef(self))

      val event = TimedEvent(Id(UUID.randomUUID()), Instant.now().plusSeconds(5), EventType("type"), None, None)
      scheduler ! Request.Schedule(event)

      val event2 = TimedEvent(Id(UUID.randomUUID()), Instant.now().plusSeconds(5), EventType("type"), None, None)
      scheduler ! Request.Schedule(event2)
      expectMsg(())

      time.advance(3.seconds)

      delayed {
        time.advance(5.seconds)
        expectMsg(event2)
      }
    }

    "find persisted events" in {
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
      val scheduler   = persistentSchedulerWithVirtualTime(persistence)

      scheduler ! Request.SubscribeActorRef(self, EventType("type"))
      expectMsg(Result.SubscribedActorRef(self))

      scheduler ! Request.FindEventsByReference(EventType("type"), Reference("ref"))
      expectMsg(List(event1, event2))
    }
  }

  private def persistentSchedulerWithVirtualTime(persistence: SchedulerPersistence = InMemorySchedulerPersistence()) = {
    system.actorOf(Props(new PersistentScheduler(persistence, settings) {
      override def scheduler: Scheduler = time.scheduler
    }))
  }

  private def delayed[A](a: => A): A = {
    Thread.sleep(25)
    a
  }
}
