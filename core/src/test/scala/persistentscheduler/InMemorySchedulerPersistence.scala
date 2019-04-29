package persistentscheduler

import java.util.UUID

import org.joda.time.DateTime
import persistentscheduler.scaladsl.SchedulerPersistence

import scala.compat.java8.OptionConverters._
import scala.concurrent.Future

object InMemorySchedulerPersistence {
  def apply(): InMemorySchedulerPersistence = new InMemorySchedulerPersistence()

  def apply(initialEvents: Seq[TimedEvent]): InMemorySchedulerPersistence =
    new InMemorySchedulerPersistence(initialEvents)
}

class InMemorySchedulerPersistence(initialEvents: Seq[TimedEvent] = Seq()) extends SchedulerPersistence {

  private implicit val dateTimeOrdering: Ordering[DateTime] = (x: DateTime, y: DateTime) => x.compareTo(y)

  private var events: Map[UUID, TimedEvent] = initialEvents.map(e => (e.id, e)).toMap

  override def delete(id: UUID): Future[Unit] = {
    events = events - id
    Future.unit
  }

  override def save(event: TimedEvent): Future[TimedEvent] = {
    events = events + (event.id -> event)
    Future.successful(event)
  }

  override def next(n: Int): Future[List[TimedEvent]] = {
    val evts = events.values.toList.sortBy(_.date).take(n)
    Future.successful(evts)
  }

  override def count(): Future[Long] = Future.successful(events.size)

  override def delete(eventType: String, reference: String): Future[Unit] = {
    events = events.filterNot {
      case (_, TimedEvent(_, _, et, r, _)) => eventType == et && Some(reference).asJava == r
    }
    Future.unit
  }

  override def find(eventType: String, reference: String): Future[List[TimedEvent]] = {
    val evts = events
      .filter {
        case (_, TimedEvent(_, _, et, r, _)) => eventType == et && Some(reference).asJava == r
      }
      .values
      .toList

    Future.successful(evts)
  }
}
