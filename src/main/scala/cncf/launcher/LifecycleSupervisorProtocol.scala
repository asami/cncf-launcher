package cncf.launcher

import java.time.Instant

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

/*
 * @version Jul. 22, 2026
 */
enum LifecycleAction {
  case Start, Stop, Restart

  def mark: String = toString.toLowerCase
}

object LifecycleAction {
  def parse(value: String): Option[LifecycleAction] =
    values.find(_.mark == value.trim.toLowerCase)

  given Encoder[LifecycleAction] = Encoder.encodeString.contramap(_.mark)
  given Decoder[LifecycleAction] = Decoder.decodeString.emap(value => parse(value).toRight(s"Unsupported lifecycle action: $value"))
}

case class LifecycleSupervisorRequest(
  requestId: String,
  idempotencyKey: String,
  artifactId: String,
  action: LifecycleAction,
  operatorSubjectId: String,
  deadlineAt: Instant
)

case class LifecycleSupervisorResult(
  requestId: String,
  state: String,
  diagnosticCode: Option[String],
  diagnostic: Option[String],
  supervisorId: String,
  instanceId: Option[String],
  acceptedAt: Option[Instant],
  completedAt: Option[Instant]
)

object LifecycleSupervisorProtocol {
  given Encoder[LifecycleSupervisorRequest] = deriveEncoder
  given Decoder[LifecycleSupervisorRequest] = deriveDecoder
  given Encoder[LifecycleSupervisorResult] = deriveEncoder
  given Decoder[LifecycleSupervisorResult] = deriveDecoder

  def rejected(
    request: LifecycleSupervisorRequest,
    supervisorid: String,
    code: String,
    now: Instant = Instant.now()
  ): LifecycleSupervisorResult =
    LifecycleSupervisorResult(request.requestId, "rejected", Some(code), Some(code), supervisorid, None, None, Some(now))
}
