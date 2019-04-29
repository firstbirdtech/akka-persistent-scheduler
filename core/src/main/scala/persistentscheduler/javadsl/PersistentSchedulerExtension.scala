package persistentscheduler.javadsl

import java.util.concurrent.CompletionStage
import java.util.{UUID, List => JList}

import akka.actor.{ActorRef, ActorSystem}
import persistentscheduler.scaladsl.{
  PersistentSchedulerExtension => SPersistentSchedulerExtension,
  SchedulerPersistence => SSchedulerPersistence
}
import persistentscheduler.{SchedulerSettings, TimedEvent}

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

object PersistentSchedulerExtension {
  def create(persistence: SchedulerPersistence,
             settings: SchedulerSettings,
             system: ActorSystem): PersistentSchedulerExtension = {
    new PersistentSchedulerExtension(persistence, settings, system)
  }

  def create(persistence: SchedulerPersistence, system: ActorSystem): PersistentSchedulerExtension = {
    new PersistentSchedulerExtension(persistence, SchedulerSettings.Defaults, system)
  }
}

class PersistentSchedulerExtension(persistence: SchedulerPersistence,
                                   settings: SchedulerSettings,
                                   system: ActorSystem) {

  private implicit val implicitSystem: ActorSystem = system
  private implicit val ec: ExecutionContext        = system.dispatcher

  private val asScala: SPersistentSchedulerExtension = new SPersistentSchedulerExtension(persistence, settings)

  def schedule(event: TimedEvent): CompletionStage[Unit] = {
    toJava(asScala.schedule(event)).toCompletableFuture
  }

  def subscribe(eventType: String, subscriber: ActorRef): CompletionStage[Unit] = {
    toJava(asScala.subscribe(eventType, subscriber)).toCompletableFuture
  }

  def removeEvents(eventType: String, reference: String): CompletionStage[Unit] = {
    toJava(asScala.removeEvents(eventType, reference)).toCompletableFuture
  }

  def findEvents(eventType: String, reference: String): CompletionStage[JList[TimedEvent]] = {
    val eventualEvents = asScala.findEvents(eventType, reference).map(_.asJava)
    toJava(eventualEvents).toCompletableFuture
  }

  private implicit def javaToScalaSchedulerPersistence(persistence: SchedulerPersistence): SSchedulerPersistence = {
    new SSchedulerPersistence {
      override def delete(eventType: String, reference: String): Future[Unit] =
        toScala(persistence.delete(eventType, reference))

      override def delete(id: UUID): Future[Unit] = toScala(persistence.delete(id))

      override def find(eventType: String, reference: String): Future[scala.List[TimedEvent]] =
        toScala(persistence.find(eventType, reference)).map(_.asScala.toList)

      override def save(event: TimedEvent): Future[TimedEvent]  = toScala(persistence.save(event))
      override def next(n: Int): Future[scala.List[TimedEvent]] = toScala(persistence.next(n)).map(_.asScala.toList)
      override def count(): Future[Long]                        = toScala(persistence.count()).map(l => l)
    }
  }

}
