package persistentscheduler

import java.time.{Duration => JDuration}

import scala.concurrent.duration.{FiniteDuration, _}

object SchedulerSettings {
  val Defaults: SchedulerSettings = SchedulerSettings(1.minute, 1.minute, 15.seconds)

  def create(delay: JDuration, interval: JDuration, askTimeout: JDuration): SchedulerSettings = {
    SchedulerSettings(
      Duration.fromNanos(delay.toNanos),
      Duration.fromNanos(interval.toNanos),
      Duration.fromNanos(askTimeout.toNanos)
    )
  }
}

case class SchedulerSettings(delay: FiniteDuration, interval: FiniteDuration, askTimeout: FiniteDuration)
