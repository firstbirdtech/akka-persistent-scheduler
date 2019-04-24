package persistentscheduler.scaladsl

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern._
import akka.util.Timeout
import persistentscheduler.PersistentScheduler.{FindEventsByReference, FoundEventsByReference, RemoveEventsByReference}
import persistentscheduler.persistence.SchedulerPersistence
import persistentscheduler.{PersistentScheduler, TimedEvent}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class PersistentSchedulerExtension(persistence: SchedulerPersistence)(implicit system: ActorSystem, timeout: Timeout) {

  private implicit val ec: ExecutionContext = system.dispatcher

  private lazy val scheduler: ActorRef = {
    val actor = system.actorSelection("/user/akka-persistent-scheduler")

    val actorRefResult = actor
      .ask(PersistentScheduler.IsAlive())
      .mapTo[PersistentScheduler.Info]
      .map(_.self)
      .recover { case _ => system.actorOf(PersistentScheduler.props(persistence), "akka-persistent-scheduler") }

    Await.result(actorRefResult, 30.seconds)
  }

  def schedule(event: TimedEvent): Future[Unit] = {
    val result = scheduler ? PersistentScheduler.Schedule(event)
    result.map(_ => ())
  }

  def subscribe(eventType: String, subscriber: ActorRef): Future[Unit] = {
    val result = scheduler ? PersistentScheduler.SubscribeActorRef(subscriber, eventType)
    result.map(_ => ())
  }

  def removeEvents(eventType: String, reference: String): Future[Unit] = {
    val result = scheduler ? RemoveEventsByReference(eventType, reference)
    result.map(_ => ())
  }

  def findEvents(eventType: String, reference: String): Future[List[TimedEvent]] = {
    val future: Future[Any] = scheduler ? FindEventsByReference(eventType, reference)

    future.map {
      case FoundEventsByReference(_, _, events) => events
      case _                                    => List()
    }
  }

}
