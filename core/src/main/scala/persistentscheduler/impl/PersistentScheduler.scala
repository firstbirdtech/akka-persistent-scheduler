package persistentscheduler.impl

import java.time.Instant

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.pattern.pipe
import persistentscheduler.impl.PersistentScheduler._
import persistentscheduler.scaladsl.SchedulerPersistence
import persistentscheduler.{SchedulerSettings, TimedEvent, _}

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

  private implicit val ec: ExecutionContext = context.dispatcher

  private var subscriptions: Set[Subscription]     = Set.empty
  private var nextEvent: Option[TimedEvent]        = None
  private var nextCancellable: Option[Cancellable] = None

  override def receive: Receive = {
    case Request.IsAlive                                       => sender() ! Result.Info(self)
    case Request.SubscribeActorRef(subscriber, eventType)      => addSubscription(Subscription(eventType, subscriber))
    case Request.Schedule(event)                               => scheduleEvent(event)
    case Request.FindEventsByReference(eventType, reference)   => findEventsByReference(eventType, reference)
    case Request.RemoveEventsByReference(eventType, reference) => removeEventsByReference(eventType, reference)
    case State(subs) =>
      this.subscriptions = subs;
    case ScheduleNextEvent(event) =>
      nextCancellable.foreach(_.cancel())
      nextEvent = event
      nextCancellable = event.flatMap(schedule)

  }

  override def preStart(): Unit = {
    scheduler.scheduleAtFixedRate(settings.delay, settings.interval)(() => scheduleNextEventFromPersistence())
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    self ! State(subscriptions)
  }

  private def addSubscription(subscription: Subscription): Unit = {
    this.subscriptions = this.subscriptions + subscription
    sender() ! Result.SubscribedActorRef(sender())
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
        subscription.subscriber ! e
      }
    }
  }

  private def schedule(event: TimedEvent): Option[Cancellable] = {
    val after = math.max(event.date.minusSeconds(Instant.now().getEpochSecond).getEpochSecond, 0).seconds
    Try(scheduler.scheduleOnce(after)(publishEvent(event))).toOption
  }

}
