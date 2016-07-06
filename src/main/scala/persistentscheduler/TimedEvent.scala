package persistentscheduler

import java.util.UUID

import org.joda.time.DateTime

case class TimedEvent(
  id: UUID,
  date: DateTime,
  eventType: String,
  reference: String,
  referenceId: String
)