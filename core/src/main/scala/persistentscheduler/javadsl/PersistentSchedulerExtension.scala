package persistentscheduler.javadsl

import java.util.concurrent.CompletionStage

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import persistentscheduler.TimedEvent
import persistentscheduler.persistence.SchedulerPersistence
import persistentscheduler.scaladsl.{PersistentSchedulerExtension => SPersistentSchedulerExtension}
import java.util.List

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters.toJava
import scala.concurrent.ExecutionContext

class PersistentSchedulerExtension(persistence: SchedulerPersistence, system: ActorSystem, timeout: Timeout) {

  private implicit val implicitSystem: ActorSystem = system
  private implicit val ec: ExecutionContext        = system.dispatcher
  private implicit val implicitTimeout: Timeout    = timeout

  private val asScala: SPersistentSchedulerExtension = new SPersistentSchedulerExtension(persistence)

  def schedule(event: TimedEvent): CompletionStage[Unit] = {
    toJava(asScala.schedule(event)).toCompletableFuture
  }

  def subscribe(eventType: String, subscriber: ActorRef): CompletionStage[Unit] = {
    toJava(asScala.subscribe(eventType, subscriber)).toCompletableFuture
  }

  def removeEvents(eventType: String, reference: String): CompletionStage[Unit] = {
    toJava(asScala.removeEvents(eventType, reference)).toCompletableFuture
  }

  def findEvents(eventType: String, reference: String): CompletionStage[List[TimedEvent]] = {
    val eventualEvents = asScala.findEvents(eventType, reference).map(_.asJava)
    toJava(eventualEvents).toCompletableFuture
  }

}
