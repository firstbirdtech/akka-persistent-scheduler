package persistentscheduler

import java.util.{UUID, List => JList}

import org.joda.time.DateTime
import persistentscheduler.persistence.SchedulerPersistence

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._

object InMemorySchedulerPersistence {
  def apply(): InMemorySchedulerPersistence = new InMemorySchedulerPersistence()

  def apply(initialEvents: Seq[TimedEvent]): InMemorySchedulerPersistence =
    new InMemorySchedulerPersistence(initialEvents)
}

class InMemorySchedulerPersistence(initialEvents: Seq[TimedEvent] = Seq()) extends SchedulerPersistence {

  implicit val dateTimeOrdering: Ordering[DateTime] = (x: DateTime, y: DateTime) => x.compareTo(y)

  private var events: Map[UUID, TimedEvent] = initialEvents.map(e => (e.id, e)).toMap

  override def delete(id: UUID): Unit = {
    events = events - id
  }

  override def save(event: TimedEvent): TimedEvent = {
    events = events + (event.id -> event)
    event
  }

  override def next(n: Int): JList[TimedEvent] = {
    events.values.toList.sortBy(_.date).take(2).asJava
  }

  override def count(): Long = events.size

  override def delete(eventType: String, reference: String): Unit = {
    events = events.filterNot {
      case (_, TimedEvent(_, _, et, r, _)) => eventType == et && Some(reference).asJava == r
    }
  }

  override def find(eventType: String, reference: String): JList[TimedEvent] = {
    events
      .filter {
        case (_, TimedEvent(_, _, et, r, _)) => eventType == et && Some(reference).asJava == r
      }
      .values
      .toList
      .asJava
  }
}
