package persistentscheduler

import java.time.Instant
import java.util.Optional

import scala.compat.java8.OptionConverters._

object TimedEvent {

  /**
   * Java API
   */
  def create(
      id: Id,
      instant: Instant,
      eventType: EventType,
      reference: Optional[Reference],
      payload: Optional[Payload]): TimedEvent = {
    apply(id, instant, eventType, reference.asScala, payload.asScala)
  }

}

final case class TimedEvent(
    id: Id,
    date: Instant,
    eventType: EventType,
    reference: Option[Reference],
    payload: Option[Payload]
) {

  /**
   * Java API
   */
  def getReference(): Optional[Reference] = reference.asJava

  /**
   * Java API
   */
  def getPayload(): Optional[Payload] = payload.asJava

}
