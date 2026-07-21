package cncf.launcher

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.util.Try

/*
 * @since   Jul. 22, 2026
 * @version Jul. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final case class LifecycleSupervisorLaunchProfile(
  artifactId: String,
  developmentDirectory: Path,
  defaultPort: Int
)

final class LifecycleSupervisorProfileResolver(paths: LauncherPaths) {
  import LifecycleSupervisorProfileResolver.*

  def resolve(artifactId: String): Either[String, LifecycleSupervisorLaunchProfile] =
    _load().flatMap(_.get(artifactId).toRight(PROFILE_UNAVAILABLE))

  private def _load(): Either[String, Map[String, LifecycleSupervisorLaunchProfile]] =
    if (!Files.isRegularFile(paths.supervisorConfig))
      Left(PROFILE_UNAVAILABLE)
    else
      Try {
        val values = LauncherConfigParser.parse(paths.supervisorConfig, Files.readString(paths.supervisorConfig, StandardCharsets.UTF_8))
        _parse(values)
      }.toEither.left.map(_ => PROFILE_UNAVAILABLE).flatten

  private def _parse(values: Map[String, Vector[String]]): Either[String, Map[String, LifecycleSupervisorLaunchProfile]] = {
    val allowed = values.keys.forall(key => key == "schema" || key.startsWith(DEVELOPMENT_DIRECTORY_PREFIX))
    val schema = values.getOrElse("schema", Vector.empty)
    if (!allowed || schema != Vector(SCHEMA_VERSION))
      Left(PROFILE_UNAVAILABLE)
    else {
      val profiles = values.toVector.collect {
        case (key, Vector(directory)) if key.startsWith(DEVELOPMENT_DIRECTORY_PREFIX) =>
          key.stripPrefix(DEVELOPMENT_DIRECTORY_PREFIX) -> directory.trim
      }
      val malformed = values.exists { case (key, entries) =>
        key.startsWith(DEVELOPMENT_DIRECTORY_PREFIX) && (entries.size != 1 || key.stripPrefix(DEVELOPMENT_DIRECTORY_PREFIX).isEmpty || entries.head.trim.isEmpty)
      }
      if (malformed || profiles.map(_._1).distinct.size != profiles.size)
        Left(PROFILE_UNAVAILABLE)
      else
        profiles.foldLeft[Either[String, Map[String, LifecycleSupervisorLaunchProfile]]](Right(Map.empty)) { case (result, (artifactid, directory)) =>
          result.flatMap { entries =>
            _profile(artifactid, directory).map(profile => entries.updated(artifactid, profile))
          }
        }
    }
  }

  private def _profile(artifactid: String, directory: String): Either[String, LifecycleSupervisorLaunchProfile] =
    Try(Path.of(directory)).toOption.filter(_.isAbsolute) match {
      case Some(path) =>
        val normalized = path.toAbsolutePath.normalize
        _descriptor_identity(normalized) match {
          case Some(("car", identity, port)) if identity == artifactid => Right(LifecycleSupervisorLaunchProfile(artifactid, normalized, port))
          case _ => Left(PROFILE_UNAVAILABLE)
        }
      case None => Left(PROFILE_UNAVAILABLE)
    }

  private def _descriptor_identity(directory: Path): Option[(String, String, Int)] = {
    val descriptor = directory.resolve("project.yaml")
    if (!Files.isRegularFile(descriptor))
      None
    else
      Try {
        val values = LauncherConfigParser.parse(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8))
        val kind = _first(values, "project.kind").orElse(_first(values, "packaging.kind"))
        val artifactid = _first(values, "project.name")
        val port = _first(values, "project.component.config.textus.server.default-port").flatMap(value => Try(value.toInt).toOption).filter(value => value >= 1 && value <= 65535)
        for {
          value <- kind
          identity <- artifactid
          number <- port
        } yield (value, identity, number)
      }.toOption.flatten
  }

  private def _first(values: Map[String, Vector[String]], key: String): Option[String] =
    values.getOrElse(key, Vector.empty).headOption.map(_.trim).filter(_.nonEmpty)
}

object LifecycleSupervisorProfileResolver {
  val SCHEMA_VERSION = "cncf.launcher.supervisor.v1"
  val PROFILE_UNAVAILABLE = "supervisor-launch-profile-unavailable"
  private val DEVELOPMENT_DIRECTORY_PREFIX = "profiles.development-directory."
}
