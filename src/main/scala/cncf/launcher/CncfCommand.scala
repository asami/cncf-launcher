package cncf.launcher

/*
 * @since   May. 17, 2026
 * @version Jun. 20, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait CncfCommand

object CncfCommand {
  sealed trait Dev extends CncfCommand {
    def options: DevOptions
  }

  final case class DevOptions(
    target: DevTarget = DevTarget.ProjectDev(None),
    runtimeVersion: Option[String] = None,
    runtimeSelectionPolicy: Option[RuntimeSelectionPolicy] = None,
    runtimeNoCompatiblePolicy: Option[RuntimeNoCompatiblePolicy] = None,
    runtimeDevDir: Option[String] = None,
    executionProfile: Option[DevExecutionProfile] = None,
    port: Option[String] = None,
    componentDevDirs: Vector[String] = Vector.empty,
    runtimeArgs: Vector[String] = Vector.empty,
    useProjectClasspath: Boolean = true,
    includeProjectDevDir: Boolean = true,
    stopExisting: Boolean = false,
    forceExisting: Boolean = false,
    passthrough: Vector[String] = Vector.empty
  )

  object Dev {
    final case class Classpath(options: DevOptions) extends Dev
    final case class Check(options: DevOptions) extends Dev
    final case class Server(options: DevOptions) extends Dev
    final case class Stop(options: DevOptions) extends Dev
    final case class ServerEmulation(options: DevOptions, args: Vector[String]) extends Dev
    final case class Client(options: DevOptions, args: Vector[String]) extends Dev
    final case class Command(options: DevOptions, operation: String, args: Vector[String]) extends Dev
  }

  sealed trait Runtime extends CncfCommand
  object Runtime {
    case object Current extends Runtime
    case object LocalList extends Runtime
    case object RemoteList extends Runtime
    case object Refresh extends Runtime
    case object CatalogShow extends Runtime
    final case class Descriptor(format: String) extends Runtime
    final case class BaseProvided(format: String) extends Runtime
    case object Channels extends Runtime
    final case class Install(version: String) extends Runtime
    final case class Use(version: String, target: RuntimeUseTarget) extends Runtime
    final case class CacheStatus() extends Runtime
    final case class ConfigShow() extends Runtime
  }


  enum DevTarget {
    case ProjectDev(path: Option[String])
    case Name(value: String)
    case CarFile(path: String)
    case ProjectCar(path: String)
  }

  enum RuntimeUseTarget {
    case Auto, Global, Project
  }

  enum DevExecutionProfile {
    case LocalPersistent

    def name: String =
      this match {
        case LocalPersistent => "local-persistent"
      }
  }

  object DevExecutionProfile {
    def parse(value: String): DevExecutionProfile =
      value.trim.toLowerCase match {
        case "local-persistent" | "persistent-dev" =>
          DevExecutionProfile.LocalPersistent
        case other =>
          throw CncfException(s"unknown cncf dev profile: $other")
      }
  }

  case object LauncherVersion extends CncfCommand
  case object Help extends CncfCommand
}

object CncfCommandParser {
  def parse(args: Vector[String]): CncfCommand = {
    if (args == Vector("--version") || args == Vector("version")) {
      CncfCommand.Runtime.Current
    } else if (args == Vector("launcher", "version") || args == Vector("launcher", "--version")) {
      CncfCommand.LauncherVersion
    } else if (args.isEmpty || args.contains("--help") || args.contains("-h")) {
      CncfCommand.Help
    } else {
      val (runtimeversion, selectionpolicy, nocompatiblepolicy, runtimedevdir, rest) = _take_global_runtime_options(args)
      rest.headOption match {
        case Some("dev") =>
          _parse_dev(rest.tail, runtimeversion, selectionpolicy, nocompatiblepolicy, runtimedevdir)
        case Some("runtime") =>
          _parse_runtime(rest.tail)
        case Some(other) =>
          throw CncfException(s"unknown cncf command: $other")
        case None =>
          CncfCommand.Help
      }
    }
  }

  private def _take_global_runtime_options(args: Vector[String]): (Option[String], Option[RuntimeSelectionPolicy], Option[RuntimeNoCompatiblePolicy], Option[String], Vector[String]) = {
    val out = Vector.newBuilder[String]
    var runtime: Option[String] = None
    var selectionpolicy: Option[RuntimeSelectionPolicy] = None
    var nocompatiblepolicy: Option[RuntimeNoCompatiblePolicy] = None
    var runtimedevdir: Option[String] = None
    var i = 0
    while (i < args.length) {
      args(i) match {
        case "--runtime" =>
          if (i + 1 >= args.length)
            throw CncfException("--runtime requires a value")
          runtime = Some(args(i + 1))
          i += 2
        case x if x.startsWith("--runtime=") =>
          runtime = Some(x.stripPrefix("--runtime="))
          i += 1
        case "--runtime-selection" =>
          if (i + 1 >= args.length)
            throw CncfException("--runtime-selection requires a value")
          selectionpolicy = Some(RuntimeSelectionPolicy.parse(args(i + 1)))
          i += 2
        case x if x.startsWith("--runtime-selection=") =>
          selectionpolicy = Some(RuntimeSelectionPolicy.parse(x.stripPrefix("--runtime-selection=")))
          i += 1
        case "--runtime-no-compatible" =>
          if (i + 1 >= args.length)
            throw CncfException("--runtime-no-compatible requires a value")
          nocompatiblepolicy = Some(RuntimeNoCompatiblePolicy.parse(args(i + 1)))
          i += 2
        case x if x.startsWith("--runtime-no-compatible=") =>
          nocompatiblepolicy = Some(RuntimeNoCompatiblePolicy.parse(x.stripPrefix("--runtime-no-compatible=")))
          i += 1
        case "--runtime-dev-dir" | "--cncf-dev-dir" =>
          if (i + 1 >= args.length)
            throw CncfException(s"${args(i)} requires a value")
          runtimedevdir = Some(args(i + 1))
          i += 2
        case x if x.startsWith("--runtime-dev-dir=") =>
          runtimedevdir = Some(x.stripPrefix("--runtime-dev-dir="))
          i += 1
        case x if x.startsWith("--cncf-dev-dir=") =>
          runtimedevdir = Some(x.stripPrefix("--cncf-dev-dir="))
          i += 1
        case x =>
          out += x
          i += 1
      }
    }
    (runtime, selectionpolicy, nocompatiblepolicy, runtimedevdir, out.result())
  }

  private def _parse_dev(
    args: Vector[String],
    runtimeversion: Option[String],
    selectionpolicy: Option[RuntimeSelectionPolicy],
    nocompatiblepolicy: Option[RuntimeNoCompatiblePolicy],
    runtimedevdir: Option[String]
  ): CncfCommand.Dev = {
    if (args.isEmpty)
      throw CncfException("cncf dev requires a subcommand")
    val subcommand = args.head
    val (pre, passthrough) = args.tail.span(_ != "--")
    val (options, rest) = _parse_dev_options(pre, runtimeversion, selectionpolicy, nocompatiblepolicy, runtimedevdir)
    val effectiveoptions =
      if (passthrough.isEmpty) options
      else options.copy(passthrough = passthrough.tail)
    subcommand match {
      case "classpath" =>
        if (rest.nonEmpty) throw CncfException("cncf dev classpath does not take positional arguments")
        CncfCommand.Dev.Classpath(effectiveoptions)
      case "check" =>
        if (rest.nonEmpty) throw CncfException("cncf dev check does not take positional arguments")
        CncfCommand.Dev.Check(effectiveoptions)
      case "server" =>
        if (rest.nonEmpty) throw CncfException(s"unknown cncf dev server arguments: ${rest.mkString(" ")}")
        CncfCommand.Dev.Server(effectiveoptions)
      case "stop" =>
        if (rest.nonEmpty) throw CncfException(s"unknown cncf dev stop arguments: ${rest.mkString(" ")}")
        CncfCommand.Dev.Stop(effectiveoptions)
      case "server-emulation" | "server-emulator" | "emulate" =>
        CncfCommand.Dev.ServerEmulation(effectiveoptions, rest)
      case "client" =>
        CncfCommand.Dev.Client(effectiveoptions, rest)
      case "command" =>
        if (rest.isEmpty) throw CncfException("cncf dev command requires an operation")
        CncfCommand.Dev.Command(effectiveoptions, rest.head, rest.tail)
      case other =>
        throw CncfException(s"unknown cncf dev command: $other")
    }
  }

  private def _parse_dev_options(
    args: Vector[String],
    runtimeversion: Option[String],
    globalselectionpolicy: Option[RuntimeSelectionPolicy],
    nocompatiblepolicy: Option[RuntimeNoCompatiblePolicy],
    globalruntimedevdir: Option[String]
  ): (CncfCommand.DevOptions, Vector[String]) = {
    val rest = Vector.newBuilder[String]
    var target: Option[CncfCommand.DevTarget] = None
    var runtimedevdir = globalruntimedevdir
    var selectionpolicy = globalselectionpolicy
    var runtimepolicy = nocompatiblepolicy
    var executionprofile: Option[CncfCommand.DevExecutionProfile] = None
    var port: Option[String] = None
    var componentdevdirs = Vector.empty[String]
    var runtimeargs = Vector.empty[String]
    var useprojectclasspath = true
    var includeprojectdevdir = true
    var stopexisting = false
    var forceexisting = false
    var i = 0
    while (i < args.length) {
      args(i) match {
        case "--project" | "--project-activation" =>
          throw CncfException(s"${args(i)} is no longer supported; use --project-dev, --name, --car-file, or --project-car")
        case x if x.startsWith("--project=") || x.startsWith("--project-activation=") =>
          throw CncfException(s"${x.takeWhile(_ != '=')} is no longer supported; use --project-dev, --name, --car-file, or --project-car")
        case "--project-dev" =>
          if (i + 1 >= args.length) throw CncfException("--project-dev requires a value")
          target = _set_dev_target(target, CncfCommand.DevTarget.ProjectDev(Some(args(i + 1))))
          i += 2
        case x if x.startsWith("--project-dev=") =>
          target = _set_dev_target(target, CncfCommand.DevTarget.ProjectDev(Some(x.stripPrefix("--project-dev="))))
          i += 1
        case "--name" =>
          if (i + 1 >= args.length) throw CncfException("--name requires a value")
          target = _set_dev_target(target, CncfCommand.DevTarget.Name(args(i + 1)))
          i += 2
        case x if x.startsWith("--name=") =>
          target = _set_dev_target(target, CncfCommand.DevTarget.Name(x.stripPrefix("--name=")))
          i += 1
        case "--car-file" =>
          if (i + 1 >= args.length) throw CncfException("--car-file requires a value")
          target = _set_dev_target(target, CncfCommand.DevTarget.CarFile(args(i + 1)))
          i += 2
        case x if x.startsWith("--car-file=") =>
          target = _set_dev_target(target, CncfCommand.DevTarget.CarFile(x.stripPrefix("--car-file=")))
          i += 1
        case "--project-car" =>
          if (i + 1 >= args.length) throw CncfException("--project-car requires a value")
          target = _set_dev_target(target, CncfCommand.DevTarget.ProjectCar(args(i + 1)))
          i += 2
        case x if x.startsWith("--project-car=") =>
          target = _set_dev_target(target, CncfCommand.DevTarget.ProjectCar(x.stripPrefix("--project-car=")))
          i += 1
        case "--runtime-dev-dir" | "--cncf-dev-dir" =>
          if (i + 1 >= args.length) throw CncfException(s"${args(i)} requires a value")
          runtimedevdir = Some(args(i + 1))
          i += 2
        case x if x.startsWith("--runtime-dev-dir=") =>
          runtimedevdir = Some(x.stripPrefix("--runtime-dev-dir="))
          i += 1
        case "--runtime-selection" =>
          if (i + 1 >= args.length) throw CncfException("--runtime-selection requires a value")
          selectionpolicy = Some(RuntimeSelectionPolicy.parse(args(i + 1)))
          i += 2
        case x if x.startsWith("--runtime-selection=") =>
          selectionpolicy = Some(RuntimeSelectionPolicy.parse(x.stripPrefix("--runtime-selection=")))
          i += 1
        case "--runtime-no-compatible" =>
          if (i + 1 >= args.length) throw CncfException("--runtime-no-compatible requires a value")
          runtimepolicy = Some(RuntimeNoCompatiblePolicy.parse(args(i + 1)))
          i += 2
        case x if x.startsWith("--runtime-no-compatible=") =>
          runtimepolicy = Some(RuntimeNoCompatiblePolicy.parse(x.stripPrefix("--runtime-no-compatible=")))
          i += 1
        case "--profile" | "--execution-profile" | "--dev-profile" =>
          if (i + 1 >= args.length) throw CncfException(s"${args(i)} requires a value")
          executionprofile = Some(CncfCommand.DevExecutionProfile.parse(args(i + 1)))
          i += 2
        case x if x.startsWith("--profile=") =>
          executionprofile = Some(CncfCommand.DevExecutionProfile.parse(x.stripPrefix("--profile=")))
          i += 1
        case x if x.startsWith("--execution-profile=") =>
          executionprofile = Some(CncfCommand.DevExecutionProfile.parse(x.stripPrefix("--execution-profile=")))
          i += 1
        case x if x.startsWith("--dev-profile=") =>
          executionprofile = Some(CncfCommand.DevExecutionProfile.parse(x.stripPrefix("--dev-profile=")))
          i += 1
        case x if x.startsWith("--cncf-dev-dir=") =>
          runtimedevdir = Some(x.stripPrefix("--cncf-dev-dir="))
          i += 1
        case "--port" =>
          if (i + 1 >= args.length) throw CncfException("--port requires a value")
          port = Some(args(i + 1))
          i += 2
        case x if x.startsWith("--port=") =>
          port = Some(x.stripPrefix("--port="))
          i += 1
        case "--no-project-classpath" =>
          useprojectclasspath = false
          includeprojectdevdir = false
          i += 1
        case "--no-project-component-dev-dir" =>
          includeprojectdevdir = false
          i += 1
        case "--component-dev-dir" =>
          if (i + 1 >= args.length) throw CncfException("--component-dev-dir requires a value")
          componentdevdirs :+= args(i + 1)
          i += 2
        case x if x.startsWith("--component-dev-dir=") =>
          componentdevdirs :+= x.stripPrefix("--component-dev-dir=")
          i += 1
        case "--stop-existing" | "--restart" =>
          stopexisting = true
          i += 1
        case "--force-existing" =>
          forceexisting = true
          i += 1
        case x if x.startsWith("--") =>
          runtimeargs :+= x
          if (!x.contains("=") && _runtime_arg_takes_value(x) && i + 1 < args.length) {
            runtimeargs :+= args(i + 1)
            i += 2
          } else {
            i += 1
          }
        case x =>
          rest ++= args.drop(i)
          i = args.length
      }
    }
    (CncfCommand.DevOptions(
      target = target.getOrElse(CncfCommand.DevTarget.ProjectDev(None)),
      runtimeVersion = runtimeversion,
      runtimeSelectionPolicy = selectionpolicy,
      runtimeNoCompatiblePolicy = runtimepolicy,
      runtimeDevDir = runtimedevdir,
      executionProfile = executionprofile,
      port = port,
      componentDevDirs = componentdevdirs,
      runtimeArgs = runtimeargs,
      useProjectClasspath = useprojectclasspath,
      includeProjectDevDir = includeprojectdevdir,
      stopExisting = stopexisting,
      forceExisting = forceexisting
    ), rest.result())
  }

  private def _set_dev_target(
    current: Option[CncfCommand.DevTarget],
    next: CncfCommand.DevTarget
  ): Option[CncfCommand.DevTarget] =
    current match {
      case Some(_) => throw CncfException("cncf dev target options are mutually exclusive: use only one of --project-dev, --name, --car-file, --project-car")
      case None => Some(next)
    }

  private def _runtime_arg_takes_value(arg: String): Boolean = {
    val name = arg.takeWhile(_ != '=')
    name.startsWith("--textus.") ||
      name.startsWith("--cncf.") ||
      Set(
        "--workspace",
        "--repository-dir",
        "--component-dir",
        "--component-car-dir",
        "--component-sar-dir",
        "--component-factory-class",
        "--sample-main-class",
        "--subsystem-sar-dir",
        "--subsystem-dir",
        "--subsystem"
      ).contains(name)
  }

  private def _parse_runtime(args: Vector[String]): CncfCommand.Runtime =
    args match {
      case Vector("current") => CncfCommand.Runtime.Current
      case Vector("list") => CncfCommand.Runtime.LocalList
      case Vector("local", "list") => CncfCommand.Runtime.LocalList
      case Vector("remote", "list") => CncfCommand.Runtime.RemoteList
      case Vector("refresh") => CncfCommand.Runtime.Refresh
      case Vector("catalog", "show") => CncfCommand.Runtime.CatalogShow
      case Vector("descriptor") => CncfCommand.Runtime.Descriptor("yaml")
      case Vector("descriptor", "--format", format) => CncfCommand.Runtime.Descriptor(format)
      case Vector("descriptor", format) if format.startsWith("--format=") =>
        CncfCommand.Runtime.Descriptor(format.stripPrefix("--format="))
      case Vector("base-provided") => CncfCommand.Runtime.BaseProvided("yaml")
      case Vector("base-provided", "--format", format) => CncfCommand.Runtime.BaseProvided(format)
      case Vector("base-provided", format) if format.startsWith("--format=") =>
        CncfCommand.Runtime.BaseProvided(format.stripPrefix("--format="))
      case Vector("channels") => CncfCommand.Runtime.Channels
      case Vector("install", version) => CncfCommand.Runtime.Install(version)
      case Vector("use", version) =>
        CncfCommand.Runtime.Use(version, CncfCommand.RuntimeUseTarget.Auto)
      case Vector("use", version, "--global") =>
        CncfCommand.Runtime.Use(version, CncfCommand.RuntimeUseTarget.Global)
      case Vector("use", version, "--project") =>
        CncfCommand.Runtime.Use(version, CncfCommand.RuntimeUseTarget.Project)
      case Vector("cache", "status") => CncfCommand.Runtime.CacheStatus()
      case Vector("config", "show") => CncfCommand.Runtime.ConfigShow()
      case other =>
        throw CncfException(s"unknown cncf runtime command: ${other.mkString(" ")}")
    }

  val helpText: String =
    """Usage:
      |  cncf --version
      |  cncf version
      |  cncf launcher version
      |  cncf --config etc/launcher/debug.yaml --cncf-config etc/debug.yaml dev server
      |  cncf dev classpath [--project-dev <dir>]
      |  cncf dev check [--project-dev <dir>] [--runtime-dev-dir <dir>]
      |  cncf dev server [--project-dev <dir>|--name <artifact>[:<version>]|--car-file <file>|--project-car <dir>] [--runtime-dev-dir <dir>] [--port <port>] [--stop-existing|--restart] [--force-existing] [--profile local-persistent] [--component-dev-dir <dir>...] [runtime args...]
      |  cncf dev stop [--project-dev <dir>] [--port <port>] [--force-existing]
      |  cncf dev server-emulation [--project-dev <dir>] [--runtime-dev-dir <dir>] <component.service.operation|component/service/operation|url>
      |  cncf dev client [--project-dev <dir>] [--runtime-dev-dir <dir>] [args...]
      |  cncf dev command [--project-dev <dir>] [--runtime-dev-dir <dir>] [--no-project-classpath] [runtime args...] <operation> [params...]
      |  cncf runtime current
      |  cncf runtime list
      |  cncf runtime local list
      |  cncf runtime remote list
      |  cncf runtime refresh
      |  cncf runtime catalog show
      |  cncf runtime descriptor [--format yaml]
      |  cncf runtime base-provided [--format yaml]
      |  cncf runtime channels
      |  cncf runtime install <version>
      |  cncf runtime use <version>
      |  cncf runtime use <version> --global
      |  cncf runtime use <version> --project
      |  cncf runtime cache status
      |  cncf runtime config show
      |
      |Runtime:
      |  --runtime <version> overrides .cncf/version and ~/.cncf/version.
      |  Without --runtime, project/component runtime.cncf requirements use current-compatible selection by default.
      |  --runtime-selection=current-compatible|tested-latest|latest-compatible|newest-compatible selects the compatible runtime preference.
      |  --runtime-no-compatible=error|newest controls the fallback when no compatible runtime exists.
      |  --runtime-dev-dir <dir> uses a local CNCF development checkout for dev commands.
      |  --config <file> loads an additional launcher config file; CLI config wins over global/project config.
      |  --cncf-config <file> loads an additional CNCF runtime config file.
      |  Launcher config files may use yaml/yml, properties, props, or lightweight conf.
      |  Launcher conf/properties files support dotted key assignments such as runtime.dev-dir = <cncf-runtime-checkout>.
      |  Config cncf.launcher.dev.dir selects an sbt checkout for the launcher itself.
      |  Full JSON/XML/HOCON are CNCF runtime config formats, not launcher config formats.
      |  Runtime args before server/client/command are forwarded to CncfMain.
      |  --profile local-persistent configures target/cncf.d/runtime.sqlite as the local SQLite DataStore for development checks.
      |  Config dev.project-dev is the configuration equivalent of --project-dev.
      |  Config dev.restart: true is the configuration equivalent of --restart / --stop-existing.
      |
      |Development resolution:
      |  cncf dev server defaults to --project-dev . and starts the current development project from source.
      |  --project-dev <dir> selects a development project; repository lookup is disabled for this main target.
      |  --name <artifact>[:<version>] starts a CAR/SAR artifact from configured repositories.
      |  --car-file <file> starts a CAR/SAR file directly.
      |  --project-car <dir> starts an explicitly generated CAR/SAR from a project target directory.
      |  The project-dev main target uses target/cncf.d/runtime-classpath.txt.
      |  Missing or empty main target classpath is generated automatically; run cncf dev classpath --project-dev <dir> to prepare it manually.
      |  cncf dev server records process state in target/cncf.d/dev-server.pid and dev-server.json.
      |  A live server for the same project and port is not stopped by default; use --stop-existing or --restart to stop it before starting.
      |  --force-existing permits force stop after graceful stop fails or ambiguous state overwrite.
      |  cncf dev stop stops the recorded dev server for the selected project and port without starting a new server.
      |  --profile local-persistent stores development DataStore state in target/cncf.d/runtime.sqlite.
      |  The same profile can be configured as dev.profile: local-persistent in conf/cncf/launcher.yaml.
      |  Launcher settings load from ~/.cncf/launcher.yaml, conf/cncf/launcher.yaml, then .cncf/launcher.yaml.
      |  CNCF runtime settings load from ~/.cncf/config.yaml, conf/cncf/config.yaml, then .cncf/config.yaml.
      |  --component-dev-dir <dir> is a dependency component local override; missing dependency classpath is an error.
      |  Dependency components without local overrides are resolved by CNCF component repositories at runtime.
      |  The default local component repository is ~/.cncf/local/repository/car and ~/.cncf/local/repository/sar.
      |  Publish dependency components there with sbt cozyPublishLocalCar or sbt cozyPublishLocalSar.
      |  ~/.cncf/local is developer local publish state; ~/.cncf/cache is runtime-managed remote artifact cache.
      |  Snapshot components are local-only by default; missing snapshots should be published with sbt cozyPublishLocalCar.
      |  Web app source lives under src/main/web; descriptor source metadata lives under src/main/web-inf.
      |  src/main/web/WEB-INF is for private Web resources, not generated descriptor source.
      |  textus server <artifact> is the CAR/SAR artifact launcher for repository-based application startup.
      |
      |Dev target policy:
      |  Target options are mutually exclusive. No target option means --project-dev .
      |  component.d and repository.d are not used implicitly by cncf dev server.
      |  --no-project-classpath invokes packaged CAR/SAR artifacts without current project classes.
      |  --no-project-component-dev-dir keeps project classes on the launcher classpath without adding project as component-dev-dir.
      |""".stripMargin
}
