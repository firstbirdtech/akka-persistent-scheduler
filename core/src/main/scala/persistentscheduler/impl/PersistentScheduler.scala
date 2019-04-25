package persistentscheduler.impl

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.pattern.pipe
import org.joda.time.DateTime
import persistentscheduler.impl.PersistentScheduler._
import persistentscheduler.scaladsl.SchedulerPersistence
import persistentscheduler.{SchedulerSettings, TimedEvent}

import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Try

private[persistentscheduler] object PersistentScheduler {

  sealed trait Result
  case class Info(self: ActorRef)              extends Result
  case class SubscribedActorRef(ref: ActorRef) extends Result
  case class Scheduled(event: TimedEvent)      extends Result
  case class RemovedEventsByReference(eventType: String, reference: String)
  case class FoundEventsByReference(eventType: String, reference: String, events: List[TimedEvent])

  sealed trait Request
  case object IsAlive                                                      extends Request
  case class Schedule(event: TimedEvent)                                   extends Request
  case class SubscribeActorRef(subscriber: ActorRef, eventType: String)    extends Request
  case class FindEventsByReference(eventType: String, reference: String)   extends Request
  case class RemoveEventsByReference(eventType: String, reference: String) extends Request

  case class State(subscriptions: Set[Subscription])
  case class Pipe(onReceive: ActorRef => Unit)

  case class Subscription(eventType: String, subscriber: ActorRef)

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

  private var subscriptions: Set[Subscription]     = Set()
  private var nextEvent: Option[TimedEvent]        = None
  private var nextCancellable: Option[Cancellable] = None

  override def receive: Receive = {
    case IsAlive                                       => sender ! Info(self)
    case SubscribeActorRef(subscriber, eventType)      => addSubscription(Subscription(eventType, subscriber))
    case Schedule(event)                               => scheduleEvent(event)
    case FindEventsByReference(eventType, reference)   => findEventsByReference(eventType, reference)
    case RemoveEventsByReference(eventType, reference) => removeEventsByReference(eventType, reference)
    case State(subs)                                   => this.subscriptions = subs;
    case Pipe(onReceive)                               => onReceive(sender)
  }

  override def preStart(): Unit = {
    scheduler.schedule(settings.delay, settings.interval)(scheduleNextEventFromPersistence())
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    self ! State(subscriptions)
  }

  private def addSubscription(subscription: Subscription): Unit = {
    this.subscriptions = this.subscriptions + subscription
    sender ! SubscribedActorRef(sender)
  }

  private def scheduleEvent(event: TimedEvent): Unit = {
    val result = persistence
      .save(event)
      .map { _ =>
        Pipe(_ ! Scheduled(event))
      }

    result to (self, sender)
  }

  private def findEventsByReference(eventType: String, reference: String): Unit = {
    val result = persistence
      .find(eventType, reference)
      .map { events =>
        Pipe(_ ! FoundEventsByReference(eventType, reference, events))
      }

    result to (self, sender)
  }

  private def removeEventsByReference(eventType: String, reference: String): Unit = {
    val pipeRequest = Pipe { originalSender =>
      val removeScheduledEvent =
        nextEvent.forall(e => e.eventType == eventType && e.reference == Some(reference).asJava)
      if (removeScheduledEvent) {
        nextCancellable.foreach(_.cancel())
      }
      originalSender ! RemovedEventsByReference(eventType, reference)
    }

    val result = persistence
      .delete(eventType, reference)
      .map(_ => pipeRequest)

    result to (self, sender)
  }

  private def scheduleNextEventFromPersistence(): Unit = {
    val result = persistence
      .next(1)
      .map(events =>
        Pipe { _ =>
          val event = events.headOption
          nextCancellable.foreach(_.cancel())
          nextEvent = event
          nextCancellable = event.flatMap(schedule)
      })

    result pipeTo self
  }

  private def publishEvent(e: TimedEvent): Unit = {
    val pipeRequest = Pipe { _ =>
      subscriptions.filter(_.eventType == e.eventType).foreach { subscription =>
        subscription.subscriber ! e
      }
    }

    val result = persistence
      .delete(e.id)
      .map(_ => pipeRequest)

    result pipeTo self
  }

  private def schedule(event: TimedEvent): Option[Cancellable] = {
    val after = Math.max(event.date.getMillis - DateTime.now().getMillis, 0L).millis
    Try(scheduler.scheduleOnce(after)(publishEvent(event))).toOption
  }

}
