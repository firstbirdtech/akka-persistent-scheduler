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

  implicit private val ec: ExecutionContext = system.dispatcher
  implicit private val askTimeout: Timeout  = settings.askTimeout

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
