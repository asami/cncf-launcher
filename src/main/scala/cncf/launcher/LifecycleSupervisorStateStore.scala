package cncf.launcher

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import scala.util.Try
import io.circe.parser.decode
import io.circe.syntax.*
import io.circe.generic.semiauto.*
import LifecycleSupervisorProtocol.given

/*
 * @since   Jul. 22, 2026
 * @version Jul. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final case class LifecycleSupervisorStateSnapshot(
  schemaVersion: String,
  supervisorId: String,
  records: Vector[LifecycleSupervisorRequestRecord],
  ownedInstances: Map[String, String]
)

final class LifecycleSupervisorStateStore(paths: LauncherPaths, supervisorid: String) {
  import LifecycleSupervisorStateStore.*
  import LifecycleSupervisorStateStore.given

  def load(): Either[String, LifecycleSupervisorState] =
    if (!Files.isRegularFile(paths.supervisorState))
      Right(LifecycleSupervisorState(supervisorid))
    else
      Try(Files.readString(paths.supervisorState, StandardCharsets.UTF_8)).toEither.left.map(_ => STATE_UNAVAILABLE).flatMap { text =>
        decode[LifecycleSupervisorStateSnapshot](text).left.map(_ => STATE_UNAVAILABLE).flatMap(_state)
      }

  def save(state: LifecycleSupervisorState): Either[String, Unit] =
    Try {
      val parent = paths.supervisorState.getParent
      Files.createDirectories(parent)
      val temporary = Files.createTempFile(parent, "supervisor-state-", ".json")
      try {
        Files.writeString(temporary, _snapshot(state).asJson.noSpaces + "\n", StandardCharsets.UTF_8)
        try Files.move(temporary, paths.supervisorState, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        catch {
          case _: java.nio.file.AtomicMoveNotSupportedException => Files.move(temporary, paths.supervisorState, StandardCopyOption.REPLACE_EXISTING)
        }
      } finally {
        Files.deleteIfExists(temporary)
      }
    }.map(_ => ()).toEither.left.map(_ => STATE_UNAVAILABLE)

  private def _state(snapshot: LifecycleSupervisorStateSnapshot): Either[String, LifecycleSupervisorState] =
    if (snapshot.schemaVersion != SCHEMA_VERSION || snapshot.supervisorId != supervisorid)
      Left(STATE_UNAVAILABLE)
    else {
      val records = snapshot.records.map(record => _key(record.request) -> record)
      val requestids = snapshot.records.map(_.request.requestId)
      val consistent = snapshot.records.forall(record => record.request.requestId == record.result.requestId)
      if (!consistent || records.size != records.map(_._1).distinct.size || requestids.size != requestids.distinct.size)
        Left(STATE_UNAVAILABLE)
      else
        Right(LifecycleSupervisorState(supervisorid, records.toMap, snapshot.ownedInstances))
    }

  private def _snapshot(state: LifecycleSupervisorState): LifecycleSupervisorStateSnapshot =
    LifecycleSupervisorStateSnapshot(SCHEMA_VERSION, state.supervisorId, state.records, state.ownedInstances)

  private def _key(request: LifecycleSupervisorRequest): (String, LifecycleAction, String) =
    (request.artifactId, request.action, request.idempotencyKey)
}

object LifecycleSupervisorStateStore {
  val SCHEMA_VERSION = "cncf.launcher.supervisor-state.v1"
  val STATE_UNAVAILABLE = "supervisor-state-unavailable"
  given io.circe.Encoder[LifecycleSupervisorStateSnapshot] = deriveEncoder
  given io.circe.Decoder[LifecycleSupervisorStateSnapshot] = deriveDecoder
}
