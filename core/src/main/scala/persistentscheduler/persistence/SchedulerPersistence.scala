package persistentscheduler.persistence

import java.util.{UUID, List => JList}

import persistentscheduler.TimedEvent

trait SchedulerPersistence {

  def delete(eventType: String, reference: String): Unit

  def delete(id: UUID): Unit

  def find(eventType: String, reference: String): JList[TimedEvent]

  def save(event: TimedEvent): TimedEvent

  def next(n: Int): JList[TimedEvent]

  def count(): Long
}
