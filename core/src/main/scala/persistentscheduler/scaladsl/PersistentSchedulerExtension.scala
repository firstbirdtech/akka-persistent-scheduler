package persistentscheduler.scaladsl

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern._
import akka.util.Timeout
import persistentscheduler._
import persistentscheduler.impl.PersistentScheduler
import persistentscheduler.impl.PersistentScheduler._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object PersistentSchedulerExtension {
  def apply(persistence: SchedulerPersistence, settings: SchedulerSettings)(implicit
      system: ActorSystem): PersistentSchedulerExtension = {
    new PersistentSchedulerExtension(persistence, settings)
  }

  def apply(persistence: SchedulerPersistence)(implicit system: ActorSystem): PersistentSchedulerExtension = {
    new PersistentSchedulerExtension(persistence, SchedulerSettings.Defaults)
  }
}

class PersistentSchedulerExtension(persistence: SchedulerPersistence, settings: SchedulerSettings)(implicit
    system: ActorSystem) {

  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val askTimeout: Timeout  = settings.askTimeout

  private lazy val scheduler: ActorRef = {
    val actor = system.actorSelection("/user/akka-persistent-scheduler")

    val actorRefResult = actor
      .ask(Request.IsAlive)
      .mapTo[Result.Info]
      .map(_.self)
      .recover { case _ =>
        system.actorOf(PersistentScheduler.props(persistence, settings), "akka-persistent-scheduler")
      }

    Await.result(actorRefResult, 30.seconds)
  }

  def schedule(event: TimedEvent): Future[Unit] = {
    val result = scheduler ? Request.Schedule(event)
    result.mapTo[Unit]
  }

  def subscribe(eventType: EventType, subscriber: ActorRef): Future[Unit] = {
    val result = scheduler ? Request.SubscribeActorRef(subscriber, eventType)
    result.mapTo[Unit]
  }

  def removeEvents(eventType: EventType, reference: Reference): Future[Unit] = {
    val result = scheduler ? Request.RemoveEventsByReference(eventType, reference)
    result.mapTo[Unit]
  }

  def findEvents(eventType: EventType, reference: Reference): Future[List[TimedEvent]] = {
    (scheduler ? Request.FindEventsByReference(eventType, reference)).mapTo[List[TimedEvent]]
  }

}
