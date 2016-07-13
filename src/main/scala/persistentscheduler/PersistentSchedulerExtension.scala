package persistentscheduler

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern._
import akka.util.Timeout
import persistentscheduler.PersistentScheduler.RemoveEventsByReference
import persistentscheduler.persistence.SchedulerPersistence

import scala.concurrent.Await
import scala.concurrent.duration._

object PersistentSchedulerExtension {
  def apply(persistence: SchedulerPersistence)(implicit system: ActorSystem): PersistentSchedulerExtension = new PersistentSchedulerExtension(persistence, system)

  def create(persistence: SchedulerPersistence, system: ActorSystem): PersistentSchedulerExtension = new PersistentSchedulerExtension(persistence, system)
}

class PersistentSchedulerExtension(persistence: SchedulerPersistence, system: ActorSystem) {

  implicit val ec = system.dispatcher
  implicit val timeout = Timeout(15.seconds)

  lazy val scheduler: ActorRef = {
    val actor = system.actorSelection("/user/akka-persistent-scheduler")

    val actorRefResult = actor.ask(PersistentScheduler.IsAlive())
      .mapTo[PersistentScheduler.Info]
      .map(_.self)
      .recover { case _ => system.actorOf(PersistentScheduler.props(persistence), "akka-persistent-scheduler") }

    Await.result(actorRefResult, 30.seconds)
  }

  def schedule(event: TimedEvent): Unit = {
    scheduler ! PersistentScheduler.Schedule(event)
  }

  def subscribe(eventType: String, subscriber: ActorRef): Unit = {
    scheduler ! PersistentScheduler.SubscribeActorRef(subscriber, eventType)
  }

  def removeEvents(eventType: String, reference: String): Unit = {
    scheduler ! RemoveEventsByReference(eventType, reference)
  }

}