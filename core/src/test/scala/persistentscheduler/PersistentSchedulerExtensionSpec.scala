package persistentscheduler

import java.util.UUID

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.miguno.akka.testing.VirtualTime
import org.joda.time.DateTime
import org.scalatest.{Matchers, OneInstancePerTest, WordSpecLike}
import persistentscheduler.persistence.InMemorySchedulerPersistence

import scala.compat.java8.OptionConverters._
import scala.concurrent.duration._

class PersistentSchedulerExtensionSpec extends TestKit(ActorSystem("test"))
  with WordSpecLike
  with OneInstancePerTest
  with Matchers
  with ImplicitSender {

  implicit val to = Timeout(1.second)
  val time = new VirtualTime

  "A PersistentScheduler" should {

    "find existing events" in {
      val event1 = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", Some("ref").asJava, None.asJava)
      val event2 = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", Some("ref").asJava, None.asJava)
      val eventOther = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", Some("ref-other").asJava, None.asJava)
      val persistence = InMemorySchedulerPersistence(Seq(event1, event2, eventOther))
      val scheduler = new PersistentSchedulerExtension(persistence, system) {}

      scheduler.findEvents("type", "ref") should equal(List(event1, event2))
    }

    "find missing events is empty list" in {
      val eventOther = TimedEvent(UUID.randomUUID(), DateTime.now().plusSeconds(5), "type", Some("ref-other").asJava, None.asJava)
      val persistence = InMemorySchedulerPersistence(Seq(eventOther))
      val scheduler = new PersistentSchedulerExtension(persistence, system) {}

      scheduler.findEvents("type", "ref") should equal(List())
    }
  }
}