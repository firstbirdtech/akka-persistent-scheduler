/*
 * Copyright (c) 2021 Akka Persistent Scheduler contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package persistentscheduler.javadsl

import akka.actor.{ActorRef, ActorSystem}
import persistentscheduler._
import persistentscheduler.scaladsl.{
  PersistentSchedulerExtension => SPersistentSchedulerExtension,
  SchedulerPersistence => SSchedulerPersistence
}

import java.util.concurrent.CompletionStage
import java.util.{List => JList}
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.language.implicitConversions

object PersistentSchedulerExtension {
  def create(
      persistence: SchedulerPersistence,
      settings: SchedulerSettings,
      system: ActorSystem): PersistentSchedulerExtension = {
    new PersistentSchedulerExtension(persistence, settings, system)
  }

  def create(persistence: SchedulerPersistence, system: ActorSystem): PersistentSchedulerExtension = {
    new PersistentSchedulerExtension(persistence, SchedulerSettings.Defaults, system)
  }
}

class PersistentSchedulerExtension(
    persistence: SchedulerPersistence,
    settings: SchedulerSettings,
    system: ActorSystem) {

  implicit private val implicitSystem: ActorSystem = system
  implicit private val ec: ExecutionContext        = system.dispatcher

  private val asScala: SPersistentSchedulerExtension = new SPersistentSchedulerExtension(persistence, settings)

  def schedule(event: TimedEvent): CompletionStage[Unit] = {
    toJava(asScala.schedule(event)).toCompletableFuture
  }

  def subscribe(eventType: String, subscriber: ActorRef): CompletionStage[Unit] = {
    toJava(asScala.subscribe(EventType(eventType), subscriber)).toCompletableFuture
  }

  def removeEvents(eventType: String, reference: String): CompletionStage[Unit] = {
    toJava(asScala.removeEvents(EventType(eventType), Reference(reference))).toCompletableFuture
  }

  def findEvents(eventType: String, reference: String): CompletionStage[JList[TimedEvent]] = {
    val eventualEvents = asScala.findEvents(EventType(eventType), Reference(reference)).map(_.asJava)
    toJava(eventualEvents).toCompletableFuture
  }

  implicit private def javaToScalaSchedulerPersistence(persistence: SchedulerPersistence): SSchedulerPersistence = {
    new SSchedulerPersistence {
      override def delete(eventType: EventType, reference: Reference): Future[Unit] =
        toScala(persistence.delete(eventType.value, reference.value))

      override def delete(id: Id): Future[Unit] = toScala(persistence.delete(id.value))

      override def find(eventType: EventType, reference: Reference): Future[List[TimedEvent]] =
        toScala(persistence.find(eventType.value, reference.value)).map(_.asScala.toList)

      override def save(event: TimedEvent): Future[TimedEvent] = toScala(persistence.save(event))
      override def next(n: Int): Future[List[TimedEvent]]      = toScala(persistence.next(n)).map(_.asScala.toList)
      override def count(): Future[Long]                       = toScala(persistence.count()).map(l => l)
    }
  }

}
