package persistentscheduler.impl

import java.util.UUID

import akka.actor.{ActorSystem, Props, Scheduler}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.miguno.akka.testing.VirtualTime
import org.joda.time.DateTime
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, OneInstancePerTest, WordSpecLike}
import persistentscheduler.impl.PersistentScheduler._
import persistentscheduler.scaladsl.SchedulerPersistence
import persistentscheduler.{InMemorySchedulerPersistence, SchedulerSettings, TimedEvent}

import scala.compat.java8.OptionConverters._
import scala.concurrent.Future
import scala.concurrent.duration._

class PersistentSchedulerSpec
    extends TestKit(ActorSystem("test"))
    with WordSpecLike
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
      val event   = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", None.asJava, None.asJava)
      testRef ! Schedule(event)

      expectMsg(())
      whenReady(persistence.count())(_ should equal(1))
    }

    "send fired events to subscribed actors" in {
      val scheduler = persistentSchedulerWithVirtualTime()

      scheduler ! SubscribeActorRef(self, "type")
      expectMsg(SubscribedActorRef(self))

      val event = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", None.asJava, None.asJava)

      scheduler ! Schedule(event)
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

      scheduler ! SubscribeActorRef(self, "type")
      expectMsg(SubscribedActorRef(self))

      val event = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", None.asJava, None.asJava)

      scheduler ! Schedule(event)
      expectMsg(())

      time.advance(3.seconds)

      delayed {
        expectNoMessage()
      }
    }

    "load an additional timed event after publishing one" in {
      val scheduler = persistentSchedulerWithVirtualTime()

      scheduler ! SubscribeActorRef(self, "type")
      expectMsg(SubscribedActorRef(self))

      val event = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", None.asJava, None.asJava)
      scheduler ! Schedule(event)
      expectMsg(())

      val event2 = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(10), "type", None.asJava, None.asJava)
      scheduler ! Schedule(event2)
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

      val existingEvent                 = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", None.asJava, None.asJava)
      val persistenceWithExistingEvents = InMemorySchedulerPersistence(Seq(existingEvent))

      val scheduler = persistentSchedulerWithVirtualTime(persistenceWithExistingEvents)

      scheduler ! SubscribeActorRef(self, "type")
      expectMsgClass(classOf[SubscribedActorRef])

      time.advance(3.seconds)

      delayed {
        time.advance(5.seconds)
        expectMsgClass(classOf[TimedEvent])
      }
    }

    "delete events when they have been published" in {
      val persistence = InMemorySchedulerPersistence(
        Seq(TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", None.asJava, None.asJava)))

      val scheduler = persistentSchedulerWithVirtualTime(persistence)

      scheduler ! SubscribeActorRef(self, "type")
      expectMsgClass(classOf[SubscribedActorRef])

      time.advance(3.seconds)

      delayed {
        time.advance(5.seconds)
        expectMsgClass(classOf[TimedEvent])
      }

      whenReady(persistence.count())(_ shouldBe 0)
    }

    "remove persisted events if they are removed from the schedule" in {
      val persistence = InMemorySchedulerPersistence(
        Seq(TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", Some("ref").asJava, None.asJava)))
      val scheduler = persistentSchedulerWithVirtualTime(persistence)

      scheduler ! RemoveEventsByReference("type", "ref")
      expectMsg(())

      whenReady(persistence.count())(_ shouldBe 0)
    }

    "remove already scheduled events if they are removed from the schedule" in {
      val persistence = InMemorySchedulerPersistence(
        Seq(TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", Some("ref").asJava, None.asJava)))
      val scheduler = persistentSchedulerWithVirtualTime(persistence)

      scheduler ! SubscribeActorRef(self, "type")
      expectMsg(SubscribedActorRef(self))

      scheduler ! RemoveEventsByReference("type", "ref")
      expectMsg(())

      time.advance(5.seconds)

      expectNoMessage(1.second)
    }

    "schedules events that have been manually inserted into the persistence after start" in {
      val persistence = InMemorySchedulerPersistence()
      val scheduler   = persistentSchedulerWithVirtualTime(persistence)

      scheduler ! SubscribeActorRef(self, "type")
      expectMsg(SubscribedActorRef(self))

      val expectedEvent = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", None.asJava, None.asJava)
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

      scheduler ! SubscribeActorRef(self, "type")
      expectMsg(SubscribedActorRef(self))

      val event = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", None.asJava, None.asJava)
      scheduler ! Schedule(event)

      val event2 = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", None.asJava, None.asJava)
      scheduler ! Schedule(event2)
      expectMsg(())

      time.advance(3.seconds)

      delayed {
        time.advance(5.seconds)
        expectMsg(event2)
      }
    }

    "find persisted events" in {
      val event1 = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", Some("ref").asJava, None.asJava)
      val event2 = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", Some("ref").asJava, None.asJava)
      val eventOther =
        TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", Some("ref-other").asJava, None.asJava)
      val persistence = InMemorySchedulerPersistence(Seq(event1, event2, eventOther))
      val scheduler   = persistentSchedulerWithVirtualTime(persistence)

      scheduler ! SubscribeActorRef(self, "type")
      expectMsg(SubscribedActorRef(self))

      scheduler ! FindEventsByReference("type", "ref")
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
