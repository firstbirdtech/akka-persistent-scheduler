package persistentscheduler

import java.util.UUID

import akka.actor.{ActorSystem, Props, Scheduler}
import akka.pattern._
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import com.miguno.akka.testing.VirtualTime
import org.joda.time.DateTime
import org.scalatest.{Matchers, OneInstancePerTest, WordSpecLike}
import persistentscheduler.PersistentScheduler._
import persistentscheduler.persistence.InMemorySchedulerPersistence

import scala.concurrent.duration._

class PersistentSchedulerSpec extends TestKit(ActorSystem("test"))
  with WordSpecLike
  with OneInstancePerTest
  with Matchers
  with ImplicitSender {

  implicit val to = Timeout(1.second)
  val persistence = InMemorySchedulerPersistence()
  val virtualTime = new VirtualTime

  "A PersistentScheduler" should {

    "persist events when they are scheduled" in {

      val testref = TestActorRef(PersistentScheduler(persistence))
      testref ? Schedule(TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", "reference", "reference-id"))

      persistence.count should equal(1)

    }

    "send fired events to subscribed actors" in {
      val scheduler = system.actorOf(Props(new PersistentScheduler(persistence) {
        override def scheduler: Scheduler = virtualTime.scheduler
      }))

      scheduler ! SubscribeActorRef(self, "type")
      expectMsg(SubscribedActorRef(self))

      val event = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", "company", "1")

      scheduler ! Schedule(event)
      expectMsg(Scheduled(event))

      virtualTime.advance(5.seconds)

      expectMsg(event)
    }

    "send no events if time is not come" in {
      val scheduler = system.actorOf(Props(new PersistentScheduler(persistence) {
        override def scheduler: Scheduler = virtualTime.scheduler
      }))

      scheduler ! SubscribeActorRef(self, "type")
      expectMsg(SubscribedActorRef(self))

      val event = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", "company", "1")

      scheduler ! Schedule(event)
      expectMsg(Scheduled(event))

      virtualTime.advance(3.seconds)

      expectNoMsg()
    }

    "load an additional timed event after publishing one" in {
      val scheduler = system.actorOf(Props(new PersistentScheduler(persistence) {
        override def scheduler: Scheduler = virtualTime.scheduler
      }))

      scheduler ! SubscribeActorRef(self, "type")
      expectMsg(SubscribedActorRef(self))

      val event = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", "company", "1")
      scheduler ! Schedule(event)
      expectMsg(Scheduled(event))

      val event2 = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(10), "type", "company", "1")
      scheduler ! Schedule(event2)
      expectMsg(Scheduled(event2))

      virtualTime.advance(5.seconds)
      expectMsg(event)

      virtualTime.advance(10.seconds) //this is necessary because even if scheduler advanced the real time hasn't
      expectMsg(event2)
    }


    "schedule the new event if there hasn't been another" in {

      val ref = TestActorRef(new PersistentScheduler(persistence) {
        override def scheduler: Scheduler = virtualTime.scheduler
      })

    }
  }
}
