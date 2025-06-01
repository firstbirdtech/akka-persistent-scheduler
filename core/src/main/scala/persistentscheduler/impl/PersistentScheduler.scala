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

package persistentscheduler.impl

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.pattern.pipe
import persistentscheduler.impl.PersistentScheduler._
import persistentscheduler.scaladsl.SchedulerPersistence
import persistentscheduler.{SchedulerSettings, TimedEvent, _}

import java.time.Instant
import scala.annotation.nowarn
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

private[persistentscheduler] object PersistentScheduler {

  sealed trait Result
  object Result {
    final case class Info(self: ActorRef)              extends Result
    final case class SubscribedActorRef(ref: ActorRef) extends Result
  }

  sealed trait Request
  object Request {
    final case object IsAlive                                                            extends Request
    final case class Schedule(event: TimedEvent)                                         extends Request
    final case class SubscribeActorRef(subscriber: ActorRef, eventType: EventType)       extends Request
    final case class FindEventsByReference(eventType: EventType, reference: Reference)   extends Request
    final case class RemoveEventsByReference(eventType: EventType, reference: Reference) extends Request

  }

  final case class ScheduleNextEvent(event: Option[TimedEvent])
  final case class State(subscriptions: Set[Subscription])
  final case class Subscription(eventType: EventType, subscriber: ActorRef)

  def props(persistence: SchedulerPersistence, settings: SchedulerSettings): Props = {
    Props(new PersistentScheduler(persistence, settings))
  }

  def apply(persistence: SchedulerPersistence, settings: SchedulerSettings): PersistentScheduler = {
    new PersistentScheduler(persistence, settings)
  }
}

private[impl] class PersistentScheduler(persistence: SchedulerPersistence, settings: SchedulerSettings)
    extends Actor
    with AkkaScheduler {

  implicit private val ec: ExecutionContext = context.dispatcher

  private var subscriptions: Set[Subscription]     = Set.empty
  private var nextEvent: Option[TimedEvent]        = None
  private var nextCancellable: Option[Cancellable] = None

  override def receive: Receive = {
    case Request.IsAlive                                       => sender().tell(Result.Info(self), sender())
    case Request.SubscribeActorRef(subscriber, eventType)      => addSubscription(Subscription(eventType, subscriber))
    case Request.Schedule(event)                               => scheduleEvent(event)
    case Request.FindEventsByReference(eventType, reference)   => findEventsByReference(eventType, reference)
    case Request.RemoveEventsByReference(eventType, reference) => removeEventsByReference(eventType, reference)
    case State(subs)                                           =>
      this.subscriptions = subs;
    case ScheduleNextEvent(event) =>
      nextCancellable.foreach(_.cancel())
      nextEvent = event
      nextCancellable = event.flatMap(schedule)

  }

  @nowarn("msg=deprecated")
  override def preStart(): Unit = {
    scheduler.schedule(settings.delay, settings.interval)(scheduleNextEventFromPersistence())
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    self.tell(State(subscriptions), sender())
  }

  private def addSubscription(subscription: Subscription): Unit = {
    this.subscriptions = this.subscriptions + subscription
    sender().tell(Result.SubscribedActorRef(sender()), sender())
  }

  private def scheduleEvent(event: TimedEvent): Unit = {
    val reschedule = nextEvent.forall(_.date.isAfter(event.date))

    val result = persistence.save(event)

    result.map(_ => ()) pipeTo sender()

    if (reschedule) {
      result.map(Some(_)).map(ScheduleNextEvent) pipeTo self
    }
  }

  private def findEventsByReference(eventType: EventType, reference: Reference): Unit = {
    val result = persistence.find(eventType, reference)
    result pipeTo sender()
  }

  private def removeEventsByReference(eventType: EventType, reference: Reference): Unit = {
    val reschedule = nextEvent.exists(e => e.eventType == eventType && e.reference.contains(reference))
    val result     = persistence.delete(eventType, reference)

    result pipeTo sender()

    if (reschedule) {
      val next = for {
        _    <- result
        next <- persistence.next(1).map(_.headOption)
      } yield next

      next.map(ScheduleNextEvent) pipeTo self
    }
  }

  private def scheduleNextEventFromPersistence(): Unit = {
    val result = persistence
      .next(1)
      .map(_.headOption)

    result.map(ScheduleNextEvent) pipeTo self
  }

  private def publishEvent(e: TimedEvent): Unit = {
    val next = for {
      _    <- persistence.delete(e.id)
      _    <- sendEventToSubscribers(e)
      next <- persistence.next(1).map(_.headOption)
    } yield next

    next.map(ScheduleNextEvent) pipeTo self
  }

  private def sendEventToSubscribers(e: TimedEvent) = {
    Future {
      subscriptions.filter(_.eventType == e.eventType).foreach { subscription =>
        subscription.subscriber.tell(e, sender())
      }
    }
  }

  private def schedule(event: TimedEvent): Option[Cancellable] = {
    val after = math.max(event.date.minusSeconds(Instant.now().getEpochSecond).getEpochSecond, 0).seconds
    Try(scheduler.scheduleOnce(after)(publishEvent(event))).toOption
  }

}
