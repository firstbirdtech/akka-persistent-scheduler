package persistentscheduler.javadsl

import java.util.concurrent.CompletionStage
import java.util.{UUID, List => JList}

import persistentscheduler.TimedEvent

trait SchedulerPersistence {

  def delete(eventType: String, reference: String): CompletionStage[Unit]
  def delete(id: UUID): CompletionStage[Unit]
  def find(eventType: String, reference: String): CompletionStage[JList[TimedEvent]]
  def save(event: TimedEvent): CompletionStage[TimedEvent]
  def next(n: Int): CompletionStage[JList[TimedEvent]]
  def count(): CompletionStage[java.lang.Long]

}
