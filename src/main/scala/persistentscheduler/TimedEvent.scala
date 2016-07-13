package persistentscheduler

import java.util.{Optional, UUID}

import org.joda.time.DateTime

import scala.beans.BeanProperty

case class TimedEvent(
  @BeanProperty id: UUID,
  @BeanProperty date: DateTime,
  @BeanProperty eventType: String,
  @BeanProperty reference: String,
  @BeanProperty referenceId: String,
  @BeanProperty payload: Optional[String]
)