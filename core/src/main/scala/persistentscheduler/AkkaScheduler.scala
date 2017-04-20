package persistentscheduler

import akka.actor.Actor

trait AkkaScheduler {
  this: Actor =>
  def scheduler = context.system.scheduler
}
