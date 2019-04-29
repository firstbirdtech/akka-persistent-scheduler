package persistentscheduler.scaladsl

import java.util.UUID

import persistentscheduler.TimedEvent

import scala.concurrent.Future

trait SchedulerPersistence {

  def delete(eventType: String, reference: String): Future[Unit]
  def delete(id: UUID): Future[Unit]
  def find(eventType: String, reference: String): Future[List[TimedEvent]]
  def save(event: TimedEvent): Future[TimedEvent]
  def next(n: Int): Future[List[TimedEvent]]
  def count(): Future[Long]

}
