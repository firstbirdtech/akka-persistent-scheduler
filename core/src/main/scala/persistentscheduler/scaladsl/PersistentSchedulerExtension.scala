package persistentscheduler.scaladsl

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern._
import akka.util.Timeout
import persistentscheduler.impl.PersistentScheduler
import persistentscheduler.impl.PersistentScheduler._
import persistentscheduler.{SchedulerSettings, TimedEvent}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object PersistentSchedulerExtension {
  def apply(persistence: SchedulerPersistence, settings: SchedulerSettings)(
      implicit system: ActorSystem): PersistentSchedulerExtension = {
    new PersistentSchedulerExtension(persistence, settings)
  }

  def apply(persistence: SchedulerPersistence)(implicit system: ActorSystem): PersistentSchedulerExtension = {
    new PersistentSchedulerExtension(persistence, SchedulerSettings.Defaults)
  }
}

class PersistentSchedulerExtension(persistence: SchedulerPersistence, settings: SchedulerSettings)(
    implicit system: ActorSystem) {

  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val askTimeout: Timeout  = settings.askTimeout

  private lazy val scheduler: ActorRef = {
    val actor = system.actorSelection("/user/akka-persistent-scheduler")

    val actorRefResult = actor
      .ask(PersistentScheduler.IsAlive)
      .mapTo[PersistentScheduler.Info]
      .map(_.self)
      .recover {
        case _ => system.actorOf(PersistentScheduler.props(persistence, settings), "akka-persistent-scheduler")
      }

    Await.result(actorRefResult, 30.seconds)
  }

  def schedule(event: TimedEvent): Future[Unit] = {
    val result = scheduler ? PersistentScheduler.Schedule(event)
    result.mapTo[Unit]
  }

  def subscribe(eventType: String, subscriber: ActorRef): Future[Unit] = {
    val result = scheduler ? PersistentScheduler.SubscribeActorRef(subscriber, eventType)
    result.mapTo[Unit]
  }

  def removeEvents(eventType: String, reference: String): Future[Unit] = {
    val result = scheduler ? RemoveEventsByReference(eventType, reference)
    result.mapTo[Unit]
  }

  def findEvents(eventType: String, reference: String): Future[List[TimedEvent]] = {
    (scheduler ? FindEventsByReference(eventType, reference)).mapTo[List[TimedEvent]]
  }

}
