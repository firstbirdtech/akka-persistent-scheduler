package persistentscheduler.impl

import akka.actor.{Actor, Scheduler}

private[impl] trait AkkaScheduler { this: Actor =>
  def scheduler: Scheduler = context.system.scheduler
}
