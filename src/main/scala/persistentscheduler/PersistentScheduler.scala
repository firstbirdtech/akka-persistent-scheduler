package persistentscheduler

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import org.joda.time.DateTime
import persistentscheduler.PersistentScheduler._
import persistentscheduler.persistence.SchedulerPersistence

import scala.collection.JavaConversions._
import scala.concurrent.duration._

object PersistentScheduler {

  sealed trait Result
  case class Info(self: ActorRef) extends Result
  case class SubscribedActorRef(ref: ActorRef) extends Result
  case class Scheduled(event: TimedEvent) extends Result

  sealed trait Request
  case class IsAlive() extends Request
  case class Schedule(event: TimedEvent) extends Request
  case class SubscribeActorRef(subscriber: ActorRef, eventType: String)
  private case class PublishEvent(event: TimedEvent)

  case class Subscription(eventType: String, subscriber: ActorRef)

  def props(persistence: SchedulerPersistence) = Props(classOf[PersistentScheduler], persistence)

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
  }

  def addSubscription(subscription: Subscription): Unit = {
    subscriptions = subscriptions + subscription
    sender() ! SubscribedActorRef(sender())
  }

  override def preStart(): Unit = {
    scheduleNextEventFromPersistence()
  }

  def scheduleNextEventFromPersistence(): Unit = {
    nextEvent = persistence.next(1).headOption
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

    //    nextEvent = Some(nextEvent.map(n => if (n.date.isBefore(event.date)) n else event).getOrElse(event))
    sender() ! Scheduled(event)
  }

  def schedule(event: TimedEvent): Cancellable = {
    val after = (event.date.getMillis - DateTime.now().getMillis).millis
    scheduler.scheduleOnce(after, self, PublishEvent(event))
  }

}
