package persistentscheduler

import persistentscheduler.scaladsl.SchedulerPersistence

import scala.concurrent.Future

object InMemorySchedulerPersistence {
  def apply(initialEvents: TimedEvent*): InMemorySchedulerPersistence =
    new InMemorySchedulerPersistence(initialEvents)
}

class InMemorySchedulerPersistence(initialEvents: Seq[TimedEvent] = Seq()) extends SchedulerPersistence {

  private var events: Map[Id, TimedEvent] = initialEvents.map(e => (e.id, e)).toMap

  override def delete(id: Id): Future[Unit] = {
    events = events - id
    Future.successful(())
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

  override def delete(eventType: EventType, reference: Reference): Future[Unit] = {
    events = events.filterNot { case (_, TimedEvent(_, _, et, r, _)) =>
      eventType == et && r.contains(reference)
    }
    Future.successful(())
  }

  override def find(eventType: EventType, reference: Reference): Future[List[TimedEvent]] = {
    val evts = events
      .filter { case (_, TimedEvent(_, _, et, r, _)) =>
        eventType == et && r.contains(reference)
      }
      .values
      .toList

    Future.successful(evts)
  }
}
