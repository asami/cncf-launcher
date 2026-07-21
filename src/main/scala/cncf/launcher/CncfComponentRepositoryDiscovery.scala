package cncf.launcher

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.time.Instant
import scala.util.Try
import io.circe.{ACursor, Decoder, HCursor, Json}
import io.circe.parser

/*
 * Development/local consumer of the CNCF Component Repository identity model.
 * Development directories are admitted explicitly and derive identity from
 * project.yaml rather than filesystem names.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final case class CncfComponentRepositoryEntry(
  kind: String,
  artifactId: String,
  status: String,
  recommended: Option[String],
  latestStable: Option[String],
  latestSnapshot: Option[String],
  source: String,
  origin: String,
  freshness: Instant
) {
  def identity: (String, String) = kind -> artifactId

  def render: String = {
    val selector = recommended.orElse(latestStable).orElse(latestSnapshot).getOrElse("-")
    s"$kind\t$artifactId\t$status\t$selector\t$origin\t${CncfComponentRepositoryDiscovery.safeSource(source)}"
  }

  def renderDetailed: String =
    Vector(
      s"kind: $kind",
      s"artifact-id: $artifactId",
      s"status: $status",
      s"recommended: ${recommended.getOrElse("-")}",
      s"latest-stable: ${latestStable.getOrElse("-")}",
      s"latest-snapshot: ${latestSnapshot.getOrElse("-")}",
      s"source: ${CncfComponentRepositoryDiscovery.safeSource(source)}",
      s"origin: $origin",
      s"freshness: $freshness"
    ).mkString("\n")
}

final case class CncfComponentRepositoryResult(artifacts: Vector[CncfComponentRepositoryEntry], diagnostics: Vector[String])
final case class CncfComponentRepositoryShowResult(artifact: CncfComponentRepositoryEntry, diagnostics: Vector[String])

final class CncfComponentRepositoryDiscovery(paths: LauncherPaths) {
  import CncfComponentRepositoryDiscovery.*

  def list(kind: Option[String], includedevelopment: Boolean, developmentdirs: Vector[String]): CncfComponentRepositoryResult = {
    val (local, localdiagnostics) = _local_entries()
    val admitted = (developmentdirs ++ Option.when(includedevelopment)(".")).distinct
    val attempts = admitted.map(_development_entry)
    val development = attempts.flatMap(_._1)
    val diagnostics = localdiagnostics ++ attempts.flatMap(_._2)
    val candidates = (development ++ local).filter(entry => kind.forall(_ == entry.kind))
    val selected = candidates.groupBy(_.identity).toVector.sortBy(_._1).map { case (identity, values) =>
      val priority = values.map(_priority).min
      val samepriority = values.filter(_priority(_) == priority).sortBy(_.source)
      val warning = Option.when(samepriority.map(_.source).distinct.size > 1)(
        s"conflicting ${identity._1}:${identity._2} entries at equal precedence; selected ${safeSource(samepriority.head.source)}"
      )
      samepriority.head -> warning
    }
    CncfComponentRepositoryResult(selected.map(_._1), diagnostics ++ selected.flatMap(_._2))
  }

  def show(target: String, kind: Option[String], includedevelopment: Boolean, developmentdirs: Vector[String]): CncfComponentRepositoryShowResult = {
    val targetpath = paths.cwd.resolve(target).normalize.toAbsolutePath.normalize
    val direct = Option.when(Files.isDirectory(targetpath))(target)
    val result = list(kind, includedevelopment, developmentdirs ++ direct)
    val matches = direct match {
      case Some(_) => result.artifacts.filter(entry => entry.origin == "development" && entry.source == targetpath.toString)
      case None => result.artifacts.filter(_.artifactId == target)
    }
    val selected = matches match {
      case Vector(value) => value
      case Vector() => throw CncfException(s"component repository artifact not found: ${_safe_target(target)}")
      case _ => throw CncfException(s"component repository artifact is ambiguous; specify --kind: ${_safe_target(target)}")
    }
    CncfComponentRepositoryShowResult(selected, result.diagnostics)
  }

  private def _local_entries(): (Vector[CncfComponentRepositoryEntry], Vector[String]) = {
    val indexpath = paths.localRepository.resolve("repository/catalog/index.json")
    if (!Files.isRegularFile(indexpath)) Vector.empty -> Vector.empty
    else {
      val text = Files.readString(indexpath, StandardCharsets.UTF_8)
      Try(_parse_index(text, paths.localRepository.resolve("repository").toString)).toEither match {
        case Right(entries) => entries -> Vector.empty
        case Left(error) => Vector.empty -> Vector(s"ignored malformed local component repository index ${safeSource(indexpath.toString)}: ${error.getMessage}")
      }
    }
  }

  private def _parse_index(text: String, source: String): Vector[CncfComponentRepositoryEntry] = {
    val json = parser.parse(text).fold(error => throw CncfException(s"invalid component repository index JSON: ${error.message}"), identity)
    val cursor = json.hcursor
    _only_fields(cursor, Set("schemaVersion", "generatedAt", "artifacts"), "index")
    val schema = _required[String](cursor, "schemaVersion", "index")
    if (schema != SCHEMA_VERSION) throw CncfException(s"unsupported component repository index schemaVersion: $schema")
    val generatedtext = _required[String](cursor, "generatedAt", "index")
    val generatedat = Try(Instant.parse(generatedtext)).getOrElse(throw CncfException(s"invalid component repository index generatedAt: $generatedtext"))
    val entries = _required[Vector[Json]](cursor, "artifacts", "index").zipWithIndex.map { case (value, index) =>
      val entrycursor = value.hcursor
      val context = s"artifact[$index]"
      _only_fields(entrycursor, Set("kind", "artifactId", "catalog", "status", "recommended", "latestStable", "latestSnapshot"), context)
      val artifactkind = _required[String](entrycursor, "kind", context)
      val artifactid = _required[String](entrycursor, "artifactId", context)
      val catalog = _required[String](entrycursor, "catalog", context)
      val status = _required[String](entrycursor, "status", context)
      _validate_identity(artifactkind, artifactid, catalog, status)
      val recommended = _optional[String](entrycursor, "recommended", context)
      val lateststable = _optional[String](entrycursor, "latestStable", context)
      val latestsnapshot = _optional[String](entrycursor, "latestSnapshot", context)
      Vector(recommended, lateststable, latestsnapshot).flatten.foreach { selector =>
        if (selector.trim.isEmpty) throw CncfException(s"component repository selector must not be empty: $artifactkind:$artifactid")
      }
      CncfComponentRepositoryEntry(
        artifactkind,
        artifactid,
        status,
        recommended,
        lateststable,
        latestsnapshot,
        source,
        "local",
        generatedat
      )
    }.sortBy(_.identity)
    val duplicates = entries.groupBy(_.identity).collect { case (identity, values) if values.size > 1 => s"${identity._1}:${identity._2}" }.toVector.sorted
    if (duplicates.nonEmpty) throw CncfException(s"duplicate component repository artifacts: ${duplicates.mkString(", ")}")
    entries
  }

  private def _development_entry(value: String): (Option[CncfComponentRepositoryEntry], Vector[String]) = {
    val directory = paths.cwd.resolve(value).normalize.toAbsolutePath.normalize
    val descriptor = directory.resolve("project.yaml")
    if (!Files.isRegularFile(descriptor))
      None -> Vector(s"ignored development directory without project.yaml: ${safeSource(directory.toString)}")
    else {
      Try {
        val values = LauncherConfigParser.parse(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8))
        def _first_(key: String): Option[String] = values.getOrElse(key, Vector.empty).headOption.map(_.trim).filter(_.nonEmpty)
        val artifactkind = _first_("project.kind").orElse(_first_("packaging.kind")).getOrElse(throw CncfException("project.yaml requires project.kind"))
        val artifactid = _first_("project.name").getOrElse(throw CncfException("project.yaml requires project.name"))
        val version = _first_("project.component.version").orElse(_first_("project.version"))
        _validate_development_identity(artifactkind, artifactid)
        CncfComponentRepositoryEntry(
          artifactkind,
          artifactid,
          "active",
          version,
          version.filterNot(_.endsWith("-SNAPSHOT")),
          version.filter(_.endsWith("-SNAPSHOT")),
          directory.toString,
          "development",
          Files.getLastModifiedTime(descriptor).toInstant
        )
      }.toEither match {
        case Right(entry) => Some(entry) -> Vector.empty
        case Left(error) =>
          val message = _safe_error_message(error, Vector(directory.toString, descriptor.toString))
          None -> Vector(s"ignored invalid development descriptor ${safeSource(directory.toString)}: $message")
      }
    }
  }

  private def _validate_identity(kind: String, artifactid: String, catalog: String, status: String): Unit = {
    _validate_development_identity(kind, artifactid)
    if (!VALID_STATUSES.contains(status)) throw CncfException(s"invalid component repository artifact status: $status")
    val extension = Vector(".yaml", ".yml", ".json").find(catalog.endsWith)
    if (!extension.exists(ext => catalog == s"$kind/$artifactid$ext")) throw CncfException(s"invalid component repository catalog path: $catalog")
  }

  private def _validate_development_identity(kind: String, artifactid: String): Unit = {
    if (!VALID_KINDS.contains(kind)) throw CncfException(s"unsupported component artifact kind: $kind")
    if (!ARTIFACT_ID_PATTERN.matches(artifactid)) throw CncfException(s"invalid component repository artifactId: $artifactid")
  }

  private def _required[A: Decoder](cursor: ACursor, field: String, context: String): A =
    cursor.get[A](field).fold(error => throw CncfException(s"component repository index $context requires $field: ${error.message}"), identity)

  private def _optional[A: Decoder](cursor: ACursor, field: String, context: String): Option[A] =
    cursor.get[Option[A]](field).fold(error => throw CncfException(s"invalid component repository index $context $field: ${error.message}"), identity)

  private def _only_fields(cursor: HCursor, expected: Set[String], context: String): Unit = {
    val unknown = cursor.keys.toVector.flatten.filterNot(expected).sorted
    if (unknown.nonEmpty) throw CncfException(s"unknown component repository index $context fields: ${unknown.mkString(", ")}")
  }

  private def _priority(entry: CncfComponentRepositoryEntry): Int = if (entry.origin == "development") 0 else 1
  private def _safe_target(value: String): String = if (value.contains("/") || value.contains("\\")) safeSource(paths.cwd.resolve(value).normalize.toString) else value
  private def _safe_error_message(error: Throwable, sensitivevalues: Vector[String]): String = {
    val original = Option(error.getMessage).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)
    sensitivevalues.distinct.foldLeft(original) { (message, sensitive) => message.replace(sensitive, safeSource(sensitive)) }
  }
}

object CncfComponentRepositoryDiscovery {
  val SCHEMA_VERSION = "cncf.component-repository-index.v1"
  private val VALID_KINDS = Set("car", "sar")
  private val VALID_STATUSES = Set("active", "deprecated", "disabled")
  private val ARTIFACT_ID_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]*".r

  def apply(paths: LauncherPaths): CncfComponentRepositoryDiscovery = new CncfComponentRepositoryDiscovery(paths)

  def safeSource(value: String): String =
    Try {
      val uri = URI.create(value)
      if (uri.getScheme == null) s"local:${Path.of(value).getFileName}#${_sha256(value).take(8)}"
      else new URI(uri.getScheme, null, uri.getHost, uri.getPort, uri.getPath, null, null).toString
    }.getOrElse(s"source#${_sha256(value).take(8)}")

  private def _sha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)).map("%02x".format(_)).mkString
}
