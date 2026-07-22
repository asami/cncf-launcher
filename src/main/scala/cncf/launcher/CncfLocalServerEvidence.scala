package cncf.launcher

import java.nio.charset.StandardCharsets
import java.nio.channels.FileChannel
import java.nio.file.{Files, StandardCopyOption}
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
    _update(_.map { entry =>
      if (entry.instanceId == instanceid && entry.stoppedAt.isEmpty) entry.copy(lastSeenAt = Instant.now()) else entry
    })

  def stopped(instanceid: String): Unit =
    _update(_.map { entry =>
      if (entry.instanceId == instanceid && entry.stoppedAt.isEmpty) {
        val now = Instant.now()
        entry.copy(lastSeenAt = now, stoppedAt = Some(now))
      } else entry
    })

  private def _update(f: Vector[CncfLocalServerEvidenceEntry] => Vector[CncfLocalServerEvidenceEntry]): Unit = CncfLocalServerEvidenceStore.lock.synchronized {
    Files.createDirectories(paths.serverEvidence.getParent)
    val channel = FileChannel.open(paths.serverEvidence.resolveSibling("server-evidence.lock"), CREATE, WRITE)
    try {
      val lock = channel.lock()
      try {
        val snapshot = _load()
        val updated = CncfLocalServerEvidenceSnapshot(CncfLocalServerEvidenceSnapshot.Schema, f(snapshot.entries))
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
        getOrElse(CncfLocalServerEvidenceSnapshot(CncfLocalServerEvidenceSnapshot.Schema, Vector.empty))

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
