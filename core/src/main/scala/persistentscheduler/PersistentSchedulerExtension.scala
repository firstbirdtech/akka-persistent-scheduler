package persistentscheduler

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern._
import akka.util.Timeout
import persistentscheduler.PersistentScheduler.{FindEventsByReference, FoundEventsByReference, RemoveEventsByReference}
import persistentscheduler.persistence.SchedulerPersistence

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object PersistentSchedulerExtension {
  def apply(persistence: SchedulerPersistence)(implicit system: ActorSystem,
                                               timeout: Timeout): PersistentSchedulerExtension =
    new PersistentSchedulerExtension(persistence, system)

  def create(persistence: SchedulerPersistence, system: ActorSystem, timeout: Timeout): PersistentSchedulerExtension = {
    implicit val to: Timeout = timeout
    new PersistentSchedulerExtension(persistence, system)
  }
}

class PersistentSchedulerExtension(persistence: SchedulerPersistence, system: ActorSystem)(implicit timeout: Timeout) {

  implicit val ec = system.dispatcher

  lazy val scheduler: ActorRef = {
    val actor = system.actorSelection("/user/akka-persistent-scheduler")

    val actorRefResult = actor
      .ask(PersistentScheduler.IsAlive())
      .mapTo[PersistentScheduler.Info]
      .map(_.self)
      .recover { case _ => system.actorOf(PersistentScheduler.props(persistence), "akka-persistent-scheduler") }

    Await.result(actorRefResult, 30.seconds)
  }

  def schedule(event: TimedEvent): Future[Any] = {
    scheduler ? PersistentScheduler.Schedule(event)
  }

  def subscribe(eventType: String, subscriber: ActorRef): Future[Any] = {
    scheduler ? PersistentScheduler.SubscribeActorRef(subscriber, eventType)
  }

  def removeEvents(eventType: String, reference: String): Future[Any] = {
    scheduler ? RemoveEventsByReference(eventType, reference)
  }

  def findEvents(eventType: String, reference: String): Future[List[TimedEvent]] = {
    val future: Future[Any] = scheduler ? FindEventsByReference(eventType, reference)

    future.map {
      case FoundEventsByReference(_, _, events) => events
      case _                                    => List()
    }
  }

}
