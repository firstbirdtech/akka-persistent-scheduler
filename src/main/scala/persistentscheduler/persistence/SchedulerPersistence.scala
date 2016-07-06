package persistentscheduler.persistence

import java.util.{UUID, List => JList}

import persistentscheduler.TimedEvent

trait SchedulerPersistence {

  def delete(id: UUID): Unit

  def save(event: TimedEvent): TimedEvent

  def next(n: Int): JList[TimedEvent]

  def count(): Long
}
