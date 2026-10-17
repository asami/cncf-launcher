/*
 * @since   Aug. 28, 2026
 * @version Oct. 17, 2026
 */
package cncf.launcher

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

private[launcher] final class CncfLauncherServerSupport(
  paths: LauncherPaths,
  environment: Map[String, String],
  registrationreporter: CncfTextusControlCenterRegistrationReporter
) {
  private[launcher] def _resolve_development_target(
    target: CncfCommand.ExecuteDevelopmentTarget
  ): Path = {
    val directory = _development_directory(target.path)
    if (target.explicit && !_is_car_or_sar_development_target(directory))
      throw CncfException(s"${target.path} is not a CAR/SAR development target")
    directory
  }

  private[launcher] def _is_car_or_sar_development_target(directory: Path): Boolean = {
    val file = directory.resolve("project.yaml")
    scala.util.Try {
      val values = LauncherConfigParser.parse(file, Files.readString(file, StandardCharsets.UTF_8))
      values
        .get("project.kind")
        .orElse(values.get("packaging.kind"))
        .flatMap(_.headOption)
        .map(_.trim.toLowerCase)
        .exists(kind => kind == "car" || kind == "sar")
    }.getOrElse(false)
  }

  private[launcher] def _implicit_control_center_config(
    commandargs: Vector[String],
    explicit: Vector[String]
  ): Vector[String] = {
    val file = paths.cncfHome.resolve("textus-control-center/server-config.yaml")
    val developmenttarget =
      scala.util.Try(CncfCommandParser.parse(commandargs)).toOption.collect {
        case command: CncfCommand.Execute if command.args.contains("server") =>
          command.developmentTarget.map(target => _development_directory(target.path))
      }.flatten
    if (
      developmenttarget.exists(_is_control_center_project) &&
      Files.isRegularFile(file) &&
      !explicit.contains(file.toString)
    )
      Vector(file.toString) ++ explicit
    else
      explicit
  }

  private[launcher] def _development_server_args(
    command: CncfCommand.Execute,
    developmenttarget: Option[Path]
  ): Vector[String] =
    developmenttarget.fold(command.args) { project =>
      val isserver = command.args.contains("server")
      val isassemblydriven =
        isserver ||
          command.args.exists(_.startsWith("--textus.subsystem.file")) ||
          command.args.exists(_.startsWith("--textus.assembly.descriptor"))
      val runtimeargs = command.args.map {
        case value if value.startsWith("--component-dev-dir=") =>
          val candidate = paths.cwd.resolve(value.stripPrefix("--component-dev-dir=")).normalize.toAbsolutePath.normalize
          if (isassemblydriven && candidate == project.toAbsolutePath.normalize)
            s"--repository-component-dev-dir=${project.toAbsolutePath.normalize}"
          else
            value
        case value => value
      }
      val descriptor = project.resolve("conf/cncf/assembly-standalone.yaml")
      val hasdescriptor = runtimeargs.exists(_.startsWith("--textus.assembly.descriptor"))
      val hasport = runtimeargs.exists(value =>
        value.startsWith("--cncf.server.port") || value.startsWith("--textus.server.port")
      )
      val descriptorargs =
        if (isserver && Files.isRegularFile(descriptor) && !hasdescriptor)
          Vector(s"--textus.assembly.descriptor=${descriptor.toString}")
        else
          Vector.empty
      val portargs =
        if (isserver && !hasport) _project_default_port(project).toVector.map(port => s"--cncf.server.port=$port")
        else Vector.empty
      val serverindex = runtimeargs.indexOf("server")
      if (!isserver || serverindex < 0)
        runtimeargs
      else
        runtimeargs.take(serverindex) ++ descriptorargs ++ portargs ++ runtimeargs.drop(serverindex)
    }

  private[launcher] def _configured_component_development_args(
    args: Vector[String],
    config: LauncherConfig
  ): Vector[String] = {
    val configuredargs = config.devComponentDevDirs
      .map(path => paths.cwd.resolve(path).normalize.toAbsolutePath.normalize)
      .distinct
      .map(path => s"--repository-component-dev-dir=$path")
    val modeindex = args.indexWhere(arg => arg == "command" || arg == "server" || arg == "client")
    if (configuredargs.isEmpty || modeindex < 0)
      args
    else
      args.take(modeindex) ++ configuredargs ++ args.drop(modeindex)
  }

  private[launcher] def _project_default_port(project: Path): Option[String] = {
    val file = project.resolve("project.yaml")
    scala.util.Try {
      LauncherConfigParser.parse(file, Files.readString(file, StandardCharsets.UTF_8))
        .get("project.component.config.textus.server.default-port")
        .flatMap(_.headOption)
        .map(_.trim)
        .filter(_.matches("[0-9]+"))
    }.toOption.flatten
  }

  private[launcher] def _server_report(
    command: CncfCommand.Execute,
    runtimeversion: String
  ): Option[CncfTextusControlCenterRegistrationReport] =
    if (!command.args.contains("server")) {
      None
    } else {
      val target = _registration_target(command.args)
      Some(CncfTextusControlCenterRegistrationReport(
        instanceId = _registration_instance_id(command.args).getOrElse(java.util.UUID.randomUUID().toString),
        target = target.name,
        artifactId = target.artifactId,
        executionMode = target.executionMode,
        developmentDirectory = target.developmentDirectory,
        subsystemName = target.subsystemName,
        subsystemVersion = target.subsystemVersion,
        runtimeVersion = runtimeversion,
        startedAt = java.time.Instant.now()
      ))
    }

  private[launcher] def _local_evidence_session(
    report: CncfTextusControlCenterRegistrationReport
  ): CncfLocalServerEvidenceSession =
    try {
      CncfLocalServerEvidenceSession.start(paths, report, "cncf")
    } catch {
      case _: Throwable =>
        Console.err.println("warning: local CNCF server evidence could not be recorded; continuing server startup.")
        CncfLocalServerEvidenceSession.noop
    }

  private[launcher] def _registration_session(
    command: CncfCommand.Execute,
    config: LauncherConfig,
    report: CncfTextusControlCenterRegistrationReport
  ): CncfTextusControlCenterRegistrationSession = {
    if (report.artifactId.contains("textus-control-center")) {
      CncfTextusControlCenterRegistrationSession.noop
    } else {
      val registration = config.textusControlCenterRegistration.map(value => value -> environment.get(value.tokenEnv)).orElse {
        Option.when(!config.textusControlCenterRegistrationEnabled.contains(false))(
          CncfTextusControlCenterStandaloneLocator.resolve(paths).map { value =>
            val configuration = _standalone_registration_base_url(command).fold(value.config)(baseurl => value.config.copy(baseUrl = baseurl))
            configuration -> Some(value.token)
          }
        ).flatten
      }
      registration match {
        case Some((configuration, token)) =>
          try {
            registrationreporter.start(
              configuration,
              report,
              token
            )
          } catch {
            case _: Throwable =>
              Console.err.println("warning: Textus Control Center registration setup failed; continuing server startup.")
              CncfTextusControlCenterRegistrationSession.noop
          }
        case None => CncfTextusControlCenterRegistrationSession.noop
      }
    }
    }

  private[launcher] def _registration_instance_id(args: Vector[String]): Option[String] =
    args.collectFirst {
      case value if value.startsWith("--textus.control-center.registration-instance-id=") =>
        value.stripPrefix("--textus.control-center.registration-instance-id=").trim
    }.filter(_.nonEmpty)

  private[launcher] def _runtime_command_args(args: Vector[String]): Vector[String] =
    args.filterNot(_.startsWith("--textus.control-center.registration-instance-id="))

  private[launcher] final case class RegistrationTarget(
    name: String,
    artifactId: Option[String],
    executionMode: String,
    developmentDirectory: Option[String],
    subsystemName: Option[String],
    subsystemVersion: Option[String]
  )

  private[launcher] def _registration_target(args: Vector[String]): RegistrationTarget = {
    def _option_(name: String): Option[String] =
      args.collectFirst { case value if value.startsWith(name) => value.stripPrefix(name) }.filter(_.nonEmpty)
    def _file_name_(value: String): String =
      Option(java.nio.file.Path.of(value).getFileName).map(_.toString).filter(_.nonEmpty).getOrElse(value)

    val subsystemname = _option_("--textus.component=")
    val developmentdirectory = _option_("--component-dev-dir=").map(_development_directory)
    developmentdirectory match {
      case Some(directory) =>
        val name = _project_component_name(directory).getOrElse(_file_name_(directory.toString))
        RegistrationTarget(name, _project_artifact_id(directory), "development", Some(directory.toString), Some(name), None)
      case None => subsystemname match {
        case Some(name) =>
          RegistrationTarget(name, None, "repository", None, Some(name), _option_("--textus.component.version="))
        case None => _option_("--component-file=").orElse(_option_("--subsystem-file=")) match {
          case Some(file) =>
            val name = _file_name_(file)
            RegistrationTarget(name, None, "artifact-file", None, Some(name), None)
          case None =>
            val directory = _development_directory(".")
            val name = _project_component_name(directory).getOrElse(_file_name_(directory.toString))
            RegistrationTarget(name, _project_artifact_id(directory), "development", Some(directory.toString), Some(name), None)
        }
      }
    }
  }

  private[launcher] def _development_directory(value: String): java.nio.file.Path =
    paths.cwd.resolve(value).normalize.toAbsolutePath.normalize

  private[launcher] def _project_component_name(directory: java.nio.file.Path): Option[String] = {
    val project = directory.resolve("project.yaml")
    scala.util.Try {
      val values = LauncherConfigParser.parse(project, Files.readString(project, StandardCharsets.UTF_8))
      values
        .get("project.component.name")
        .flatMap(_.headOption)
        .map(_.trim)
        .filter(_.nonEmpty)
        .orElse(values.get("project.id").flatMap(_.headOption).map(_.trim).filter(_.nonEmpty))
    }.toOption.flatten
  }

  private[launcher] def _project_artifact_id(directory: java.nio.file.Path): Option[String] = {
    val project = directory.resolve("project.yaml")
    scala.util.Try {
      val values = LauncherConfigParser.parse(project, Files.readString(project, StandardCharsets.UTF_8))
      val namespace = values.get("project.namespace").flatMap(_.headOption).map(_.trim).filter(_.nonEmpty)
      val id = values.get("project.id").flatMap(_.headOption).map(_.trim).filter(_.nonEmpty)
      namespace
        .flatMap(value => id.map(localid => s"${value.split("\\.").last}-${_kebab_case(localid)}"))
        .orElse(values.get("project.name").flatMap(_.headOption).map(_.trim).filter(_.nonEmpty))
    }.toOption.flatten
  }

  private[launcher] def _kebab_case(value: String): String =
    value.indices.map { index =>
      val current = value.charAt(index)
      val boundary = index > 0 && current.isUpper && (
        value.charAt(index - 1).isLower || value.charAt(index - 1).isDigit ||
          (index > 1 && value.charAt(index - 1).isUpper && value.charAt(index - 2).isUpper && index + 1 < value.length && value.charAt(index + 1).isLower)
      )
      s"${if (boundary) "-" else ""}${current.toLower}"
    }.mkString

  private[launcher] def _is_control_center_project(directory: java.nio.file.Path): Boolean = {
    val project = directory.resolve("project.yaml")
    scala.util.Try {
      val values = LauncherConfigParser.parse(project, Files.readString(project, StandardCharsets.UTF_8))
      def _value_(key: String): Option[String] =
        values.get(key).flatMap(_.headOption).map(_.trim).filter(_.nonEmpty)
      (_value_("project.namespace").contains("org.simplemodeling.textus") &&
        _value_("project.id").contains("ControlCenter")) ||
        _value_("project.name").contains("textus-control-center")
    }.getOrElse(false)
  }

  private[launcher] def _standalone_registration_base_url(command: CncfCommand.Execute): Option[String] =
    command.args.collectFirst {
      case value if value.startsWith("--textus.server.port=") => value.stripPrefix("--textus.server.port=")
      case value if value.startsWith("--cncf.server.port=") => value.stripPrefix("--cncf.server.port=")
    }.flatMap { value =>
      scala.util.Try(value.toInt).toOption.filter(port => port >= 1 && port <= 65535).map(port => s"http://127.0.0.1:$port")
    }
}
