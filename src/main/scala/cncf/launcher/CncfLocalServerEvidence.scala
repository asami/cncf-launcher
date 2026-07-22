package cncf.launcher

import java.nio.charset.StandardCharsets
import java.nio.channels.FileChannel
import java.nio.file.{AtomicMoveNotSupportedException, Files, StandardCopyOption}
import java.nio.file.StandardOpenOption.{CREATE, WRITE}
import java.time.{Duration, Instant}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{Executors, TimeUnit}

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.decode
import io.circe.syntax.*

/*
 * @since   Jul. 22, 2026
 * @version Jul. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final case class CncfLocalServerEvidenceEntry(
  launcherKind: String,
  instanceId: String,
  target: String,
  artifactId: Option[String],
  executionMode: String,
  developmentDirectory: Option[String],
  subsystemName: Option[String],
  subsystemVersion: Option[String],
  runtimeVersion: String,
  startedAt: Instant,
  lastSeenAt: Instant,
  stoppedAt: Option[Instant]
)

final case class CncfLocalServerEvidenceSnapshot(
  schema: String,
  entries: Vector[CncfLocalServerEvidenceEntry]
)

final case class CncfLocalServerEvidenceListEntry(
  launcherKind: String,
  instanceId: String,
  target: String,
  artifactId: Option[String],
  executionMode: String,
  subsystemName: Option[String],
  subsystemVersion: Option[String],
  runtimeVersion: String,
  startedAt: Instant,
  lastSeenAt: Instant,
  stoppedAt: Option[Instant]
)

final case class CncfLocalServerEvidenceListProjection(
  schema: String,
  entries: Vector[CncfLocalServerEvidenceListEntry]
)

final case class CncfLocalServerEvidenceDetailProjection(
  schema: String,
  entry: CncfLocalServerEvidenceEntry
)

object CncfLocalServerEvidenceSnapshot {
  val Schema = "cncf.launcher.server-evidence.v1"

  given Encoder[CncfLocalServerEvidenceEntry] = deriveEncoder
  given Decoder[CncfLocalServerEvidenceEntry] = deriveDecoder
  given Encoder[CncfLocalServerEvidenceSnapshot] = deriveEncoder
  given Decoder[CncfLocalServerEvidenceSnapshot] = deriveDecoder
  given Encoder[CncfLocalServerEvidenceListEntry] = deriveEncoder
  given Encoder[CncfLocalServerEvidenceListProjection] = deriveEncoder
  given Encoder[CncfLocalServerEvidenceDetailProjection] = deriveEncoder
}

final class CncfLocalServerEvidenceStore(paths: LauncherPaths) {
  import CncfLocalServerEvidenceSnapshot.given

  // This is an internal Launcher lookup.  Unlike listProjection and
  // detailProjection it is not a Control Center boundary: it retains the local
  // directory only long enough to validate a lifecycle launch profile.
  def latestDevelopmentProfile(artifactid: String): Either[String, Option[CncfLocalServerEvidenceEntry]] =
    _read().map { snapshot =>
      snapshot.entries
        .filter { entry =>
          entry.artifactId.contains(artifactid) &&
          entry.executionMode == "development" &&
          entry.developmentDirectory.exists(_.trim.nonEmpty)
        }
        .lastOption
    }

  def listProjection(): Either[String, CncfLocalServerEvidenceListProjection] =
    _read().map { snapshot =>
      CncfLocalServerEvidenceListProjection(
        CncfLocalServerEvidenceStore.ProjectionSchema,
        snapshot.entries.sortBy(entry => (entry.target, entry.instanceId)).map { entry =>
          CncfLocalServerEvidenceListEntry(
            entry.launcherKind,
            entry.instanceId,
            entry.target,
            entry.artifactId,
            entry.executionMode,
            entry.subsystemName,
            entry.subsystemVersion,
            entry.runtimeVersion,
            entry.startedAt,
            entry.lastSeenAt,
            entry.stoppedAt
          )
        }
      )
    }

  def detailProjection(instanceid: String): Either[String, Option[CncfLocalServerEvidenceDetailProjection]] =
    _read().map { snapshot =>
      snapshot.entries.find(_.instanceId == instanceid).map(entry => CncfLocalServerEvidenceDetailProjection(CncfLocalServerEvidenceStore.ProjectionSchema, entry))
    }

  def started(report: CncfTextusControlCenterRegistrationReport, launcherkind: String): Unit =
    _update { entries =>
      val now = Instant.now()
      entries.filterNot(_.instanceId == report.instanceId) :+ CncfLocalServerEvidenceEntry(
        launcherkind,
        report.instanceId,
        report.target,
        report.artifactId,
        report.executionMode,
        report.developmentDirectory,
        report.subsystemName,
        report.subsystemVersion,
        report.runtimeVersion,
        report.startedAt,
        now,
        None
      )
    }

  def alive(instanceid: String): Unit =
    _update(_touch(_, instanceid) { entry =>
      if (entry.stoppedAt.isEmpty) Some(entry.copy(lastSeenAt = Instant.now())) else None
    })

  def stopped(instanceid: String): Unit =
    _update(_touch(_, instanceid) { entry =>
      if (entry.stoppedAt.isEmpty) {
        val now = Instant.now()
        Some(entry.copy(lastSeenAt = now, stoppedAt = Some(now)))
      } else None
    })

  private def _update(f: Vector[CncfLocalServerEvidenceEntry] => Vector[CncfLocalServerEvidenceEntry]): Unit = CncfLocalServerEvidenceStore.lock.synchronized {
    Files.createDirectories(paths.serverEvidence.getParent)
    val channel = FileChannel.open(paths.serverEvidence.resolveSibling("server-evidence.lock"), CREATE, WRITE)
    try {
      val lock = channel.lock()
      try {
        val snapshot = _load()
        val updated = CncfLocalServerEvidenceSnapshot(CncfLocalServerEvidenceSnapshot.Schema, _retain(f(snapshot.entries), Instant.now()))
        val temporary = Files.createTempFile(paths.serverEvidence.getParent, ".server-evidence-", ".json")
        try {
          Files.writeString(temporary, updated.asJson.noSpaces + "\n", StandardCharsets.UTF_8)
          Files.move(temporary, paths.serverEvidence, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
          Files.deleteIfExists(temporary)
        }
      } finally {
        lock.release()
      }
    } finally {
      channel.close()
    }
  }

  private def _load(): CncfLocalServerEvidenceSnapshot =
    if (!Files.isRegularFile(paths.serverEvidence))
      CncfLocalServerEvidenceSnapshot(CncfLocalServerEvidenceSnapshot.Schema, Vector.empty)
    else
      decode[CncfLocalServerEvidenceSnapshot](Files.readString(paths.serverEvidence, StandardCharsets.UTF_8)).toOption.
        filter(_.schema == CncfLocalServerEvidenceSnapshot.Schema).
        getOrElse(_recover_malformed())

  private def _recover_malformed(): CncfLocalServerEvidenceSnapshot = {
    val recovery = paths.serverEvidence.resolveSibling(s"server-evidence.recovery-${java.util.UUID.randomUUID().toString}.json")
    try Files.move(paths.serverEvidence, recovery, StandardCopyOption.ATOMIC_MOVE)
    catch {
      case _: AtomicMoveNotSupportedException => Files.move(paths.serverEvidence, recovery)
    }
    CncfLocalServerEvidenceSnapshot(CncfLocalServerEvidenceSnapshot.Schema, Vector.empty)
  }

  private def _touch(
    entries: Vector[CncfLocalServerEvidenceEntry],
    instanceid: String
  )(f: CncfLocalServerEvidenceEntry => Option[CncfLocalServerEvidenceEntry]): Vector[CncfLocalServerEvidenceEntry] =
    entries.find(_.instanceId == instanceid).flatMap(f) match {
      case Some(updated) => entries.filterNot(_.instanceId == instanceid) :+ updated
      case None => entries
    }

  private def _retain(entries: Vector[CncfLocalServerEvidenceEntry], now: Instant): Vector[CncfLocalServerEvidenceEntry] =
    entries.filter(entry => !entry.lastSeenAt.plus(CncfLocalServerEvidenceStore.Retention).isBefore(now)).takeRight(CncfLocalServerEvidenceStore.MaximumEntries)

  private def _read(): Either[String, CncfLocalServerEvidenceSnapshot] =
    if (!Files.isRegularFile(paths.serverEvidence))
      Right(CncfLocalServerEvidenceSnapshot(CncfLocalServerEvidenceSnapshot.Schema, Vector.empty))
    else
      try {
        decode[CncfLocalServerEvidenceSnapshot](Files.readString(paths.serverEvidence, StandardCharsets.UTF_8)).toOption
          .filter(_.schema == CncfLocalServerEvidenceSnapshot.Schema)
          .toRight(CncfLocalServerEvidenceStore.EvidenceUnavailable)
      } catch {
        case _: Throwable => Left(CncfLocalServerEvidenceStore.EvidenceUnavailable)
      }
}

object CncfLocalServerEvidenceStore {
  val ProjectionSchema = "cncf.launcher.evidence-projection.v1"
  val EvidenceUnavailable = "launcher-evidence-unavailable"
  val Retention: Duration = Duration.ofDays(30)
  val MaximumEntries = 512
  private val lock = new Object
}

trait CncfLocalServerEvidenceSession {
  def close(): Unit
}

object CncfLocalServerEvidenceSession {
  def start(
    paths: LauncherPaths,
    report: CncfTextusControlCenterRegistrationReport,
    launcherKind: String,
    heartbeatInterval: Duration = Duration.ofSeconds(30)
  ): CncfLocalServerEvidenceSession = {
    val store = CncfLocalServerEvidenceStore(paths)
    store.started(report, launcherKind)
    val closed = AtomicBoolean(false)
    val executor = Executors.newSingleThreadScheduledExecutor { runnable =>
      val thread = Thread(runnable, "cncf-local-server-evidence-heartbeat")
      thread.setDaemon(true)
      thread
    }
    executor.scheduleAtFixedRate(
      () => if (!closed.get) store.alive(report.instanceId),
      heartbeatInterval.toMillis,
      heartbeatInterval.toMillis,
      TimeUnit.MILLISECONDS
    )
    new CncfLocalServerEvidenceSession {
      def close(): Unit =
        if (closed.compareAndSet(false, true)) {
          executor.shutdownNow()
          store.stopped(report.instanceId)
        }
    }
  }

  val noop: CncfLocalServerEvidenceSession = new CncfLocalServerEvidenceSession {
    def close(): Unit = ()
  }
}
