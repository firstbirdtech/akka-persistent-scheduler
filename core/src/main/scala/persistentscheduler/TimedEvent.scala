/*
 * Copyright (c) 2021 Akka Persistent Scheduler contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package persistentscheduler

import java.time.Instant
import java.util.Optional
import scala.compat.java8.OptionConverters._

object TimedEvent {

  /** Java API
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

  /** Java API
    */
  def getReference(): Optional[Reference] = reference.asJava

  /** Java API
    */
  def getPayload(): Optional[Payload] = payload.asJava

}
