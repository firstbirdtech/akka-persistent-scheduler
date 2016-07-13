package persistentscheduler

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, Props, Scheduler}
import akka.pattern._
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import com.miguno.akka.testing.VirtualTime
import org.joda.time.DateTime
import org.scalatest.{Matchers, OneInstancePerTest, WordSpecLike}
import persistentscheduler.PersistentScheduler._
import persistentscheduler.persistence.InMemorySchedulerPersistence

import scala.compat.java8.OptionConverters._
import scala.concurrent.duration._

class PersistentSchedulerSpec extends TestKit(ActorSystem("test"))
  with WordSpecLike
  with OneInstancePerTest
  with Matchers
  with ImplicitSender {

  implicit val to = Timeout(1.second)
  val time = new VirtualTime

  "A PersistentScheduler" should {

    "persist events when they are scheduled" in {

      val persistence = InMemorySchedulerPersistence()

      val testref = TestActorRef(PersistentScheduler(persistence))
      testref ? Schedule(TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", "reference", "reference-id", None.asJava))

      persistence.count should equal(1)

    }

    "send fired events to subscribed actors" in {
      val scheduler = persistentSchedulerWithVirtualTime()

      scheduler ! SubscribeActorRef(self, "type")
      expectMsg(SubscribedActorRef(self))

      val event = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", "company", "1", None.asJava)

      scheduler ! Schedule(event)
      expectMsg(Scheduled(event))

      time.advance(5.seconds)

      expectMsg(event)
    }

    "send no events if time is not come" in {
      val scheduler = persistentSchedulerWithVirtualTime()

      scheduler ! SubscribeActorRef(self, "type")
      expectMsg(SubscribedActorRef(self))

      val event = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", "company", "1", None.asJava)

      scheduler ! Schedule(event)
      expectMsg(Scheduled(event))

      time.advance(3.seconds)

      expectNoMsg()
    }

    "load an additional timed event after publishing one" in {
      val scheduler = persistentSchedulerWithVirtualTime()

      scheduler ! SubscribeActorRef(self, "type")
      expectMsg(SubscribedActorRef(self))

      val event = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", "company", "1", None.asJava)
      scheduler ! Schedule(event)
      expectMsg(Scheduled(event))

      val event2 = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(10), "type", "company", "1", None.asJava)
      scheduler ! Schedule(event2)
      expectMsg(Scheduled(event2))

      time.advance(5.seconds)
      expectMsg(event)

      time.advance(10.seconds) //this is necessary because even if scheduler advanced the real time hasn't
      expectMsg(event2)
    }


    "schedule existing events from persistence on startup" in {

      val existingEvent = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", "ref", "refId", None.asJava)
      val persistenceWithExistingEvents = InMemorySchedulerPersistence(Seq(existingEvent))

      val scheduler = persistentSchedulerWithVirtualTime(persistenceWithExistingEvents)

      scheduler ! SubscribeActorRef(self, "type")
      expectMsgClass(classOf[SubscribedActorRef])

      time.advance(5.seconds)

      expectMsg(existingEvent)
    }

    "delete events when they have been published" in {

      val persistence = InMemorySchedulerPersistence(Seq(TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", "ref", "refId", None.asJava)))
      val scheduler = persistentSchedulerWithVirtualTime(persistence)

      scheduler ! SubscribeActorRef(self, "type")
      expectMsgClass(classOf[SubscribedActorRef])

      time.advance(5.seconds)

      expectMsgClass(classOf[TimedEvent])

      persistence.count() shouldBe 0

    }

    "remove persisted events if they are removed from the schedule" in {
      val persistence = InMemorySchedulerPersistence(Seq(TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", "ref", "refId", None.asJava)))
      val scheduler = persistentSchedulerWithVirtualTime(persistence)

      scheduler ! RemoveEventsByReference("type", "ref", "refId")
      expectMsg(RemovedEventsByReference("type", "ref", "refId"))

      persistence.count() shouldBe 0
    }

    "remove already scheduled events if they are removed from the schedule" in {
      val persistence = InMemorySchedulerPersistence(Seq(TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", "ref", "refId", None.asJava)))
      val scheduler = persistentSchedulerWithVirtualTime(persistence)

      scheduler ! SubscribeActorRef(self, "type")
      expectMsg(SubscribedActorRef(self))

      scheduler ! RemoveEventsByReference("type", "ref", "refId")
      expectMsg(RemovedEventsByReference("type", "ref", "refId"))

      time.advance(5.seconds)

      expectNoMsg(1.second)
    }

    "schedules events that have been manually inserted into the persistence after start" in {
      val persistence = InMemorySchedulerPersistence()
      val scheduler = persistentSchedulerWithVirtualTime(persistence)

      scheduler ! SubscribeActorRef(self, "type")
      expectMsg(SubscribedActorRef(self))

      val expectedEvent = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", "ref", "refId", None.asJava)
      persistence.save(expectedEvent)

      time.advance(1.minutes)
      expectNoMsg(200.millis)

      time.advance(5.seconds)
      expectMsg(expectedEvent)
    }

    "keeps subscriptions even after a restart" in {
      val buggyPersistence = new InMemorySchedulerPersistence() {
        var failed: Boolean = false

        override def save(event: TimedEvent): TimedEvent = {
          if (!failed) {
            failed = true
            throw new Exception("throw this at the first time to force actor restart")
          }
          else {
            super.save(event)
          }
        }
      }

      val scheduler = persistentSchedulerWithVirtualTime(buggyPersistence)

      scheduler ! SubscribeActorRef(self, "type")
      expectMsg(SubscribedActorRef(self))

      val event = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", "ref", "refId", None.asJava)
      scheduler ! Schedule(event)

      val event2 = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", "ref", "refId", None.asJava)
      scheduler ! Schedule(event2)
      expectMsg(Scheduled(event2))

      time.advance(5.seconds)
      expectMsg(event2)

    }
  }

  def persistentSchedulerWithVirtualTime(persistenceWithExistingEvents: InMemorySchedulerPersistence = InMemorySchedulerPersistence()): ActorRef = {
    system.actorOf(Props(new PersistentScheduler(persistenceWithExistingEvents) {
      override def scheduler: Scheduler = time.scheduler
    }))
  }
}
