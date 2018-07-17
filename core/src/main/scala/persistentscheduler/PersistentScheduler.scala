package persistentscheduler

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import org.joda.time.DateTime
import persistentscheduler.PersistentScheduler._
import persistentscheduler.persistence.SchedulerPersistence

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration._

object PersistentScheduler {

  sealed trait Result
  case class Info(self: ActorRef) extends Result
  case class SubscribedActorRef(ref: ActorRef) extends Result
  case class Scheduled(event: TimedEvent) extends Result
  case class RemovedEventsByReference(eventType: String, reference: String)
  case class FoundEventsByReference(eventType: String, reference: String, events: List[TimedEvent])

  sealed trait Request
  case class IsAlive() extends Request
  case class Schedule(event: TimedEvent) extends Request
  case class SubscribeActorRef(subscriber: ActorRef, eventType: String) extends Request
  case class RemoveEventsByReference(eventType: String, reference: String) extends Request
  case class PublishEvent(event: TimedEvent) extends Request
  case class CheckPersistenceForEvents() extends Request
  case class FindEventsByReference(eventType: String, reference: String) extends Request

  case class State(subscriptions: Set[Subscription])
  case class Subscription(eventType: String, subscriber: ActorRef)

  def props(persistence: SchedulerPersistence) = Props(new PersistentScheduler(persistence))

  def apply(persistence: SchedulerPersistence): PersistentScheduler = new PersistentScheduler(persistence)
}

class PersistentScheduler(persistence: SchedulerPersistence) extends Actor
  with AkkaScheduler {

  implicit val ec = context.dispatcher

  var subscriptions: Set[Subscription] = Set()
  var nextEvent: Option[TimedEvent] = None
  var nextCancellable: Option[Cancellable] = None

  override def receive: Receive = {
    case IsAlive() =>
      sender() ! Info(self)
    case Schedule(event) =>
      addEvent(event)
    case PublishEvent(event) =>
      publishEvent(event)
    case SubscribeActorRef(subscriber, eventType) =>
      addSubscription(Subscription(eventType, subscriber))
    case RemoveEventsByReference(eventType, reference) =>
      removeEventsByReference(eventType, reference)
    case CheckPersistenceForEvents() =>
      checkPersistenceForEvents()
    case FindEventsByReference(eventType, reference) =>
      findEventsByReference(eventType, reference)
    case State(subscriptions) =>
      this.subscriptions = subscriptions;
  }

  def addSubscription(subscription: Subscription): Unit = {
    subscriptions = subscriptions + subscription
    sender() ! SubscribedActorRef(sender())
  }


  override def preStart(): Unit = {
    scheduleNextEventFromPersistence()
    schedulePersistenceCheckForEvents()
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    self ! State(subscriptions)
  }

  def schedulePersistenceCheckForEvents(): Unit = {
    scheduler.schedule(1.minute, 1.minute, self, CheckPersistenceForEvents())
  }

  def scheduleNextEventFromPersistence(): Unit = {
    nextCancellable.filter(!_.isCancelled).foreach(_.cancel())

    nextEvent = persistence.next(1).asScala.headOption
    nextCancellable = nextEvent.map(schedule(_))
  }

  def publishEvent(e: TimedEvent): Unit = {

    persistence.delete(e.id)
    scheduleNextEventFromPersistence()

    subscriptions.filter(_.eventType == e.eventType).foreach { subscription =>
      subscription.subscriber ! e
    }

  }

  def addEvent(event: TimedEvent): Unit = {
    persistence.save(event)

    val replaceNextEvent = nextEvent.forall(_.date.isAfter(event.date))

    if (replaceNextEvent) {
      nextCancellable.foreach(_.cancel())
      nextEvent = Some(event)
      nextCancellable = nextEvent.map(schedule(_))
    }

    sender() ! Scheduled(event)
  }

  def schedule(event: TimedEvent): Cancellable = {
    val after = Math.max(event.date.getMillis - DateTime.now().getMillis, 0L).millis
    scheduler.scheduleOnce(after, self, PublishEvent(event))
  }

  def removeEventsByReference(eventType: String, reference: String): Unit = {
    persistence.delete(eventType, reference)

    val removeScheduledEvent = nextEvent.forall(e => e.eventType == eventType && e.reference == Some(reference).asJava)

    if (removeScheduledEvent) {
      scheduleNextEventFromPersistence()
    }

    sender() ! RemovedEventsByReference(eventType, reference)
  }

  def findEventsByReference(eventType: String, reference: String): Unit = {
    sender() ! FoundEventsByReference(eventType, reference, persistence.find(eventType, reference).asScala.toList)
  }

  def checkPersistenceForEvents(): Unit = {
    nextCancellable.foreach(_.cancel())
    nextEvent = persistence.next(1).asScala.headOption
    nextCancellable = nextEvent.map(schedule)
  }

}
