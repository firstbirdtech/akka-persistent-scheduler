package persistentscheduler.persistence

import java.util
import java.util.UUID

import org.joda.time.DateTime
import persistentscheduler.TimedEvent

import scala.collection.JavaConversions._

object InMemorySchedulerPersistence {
  def apply(): InMemorySchedulerPersistence = new InMemorySchedulerPersistence()
}

class InMemorySchedulerPersistence extends SchedulerPersistence {

  implicit val dateTimeOrdering: Ordering[DateTime] = new Ordering[DateTime] {
    override def compare(x: DateTime, y: DateTime): Int = x.compareTo(y)
  }

  private var events: Map[UUID, TimedEvent] = Map()

  override def delete(id: UUID): Unit = {
    events = events - id
  }

  override def save(event: TimedEvent): TimedEvent = {
    events = events + (event.id -> event)
    event
  }

  override def next(n: Int): util.List[TimedEvent] = {
    events.values.toList.sortBy(_.date).take(2)
  }

  override def count(): Long = events.size
}
