package persistentscheduler.scaladsl

import persistentscheduler._

import scala.concurrent.Future

trait SchedulerPersistence {

  def delete(eventType: EventType, reference: Reference): Future[Unit]
  def delete(id: Id): Future[Unit]
  def find(eventType: EventType, reference: Reference): Future[List[TimedEvent]]
  def save(event: TimedEvent): Future[TimedEvent]
  def next(n: Int): Future[List[TimedEvent]]
  def count(): Future[Long]

}
