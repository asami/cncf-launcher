package cncf.launcher

/*
 * @since   May. 17, 2026
 * @version May. 18, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait CncfCommand

object CncfCommand {
  sealed trait Dev extends CncfCommand {
    def options: DevOptions
  }

  final case class DevOptions(
    project: Option[String] = None,
    runtimeVersion: Option[String] = None,
    runtimeDevDir: Option[String] = None,
    port: Option[String] = None,
    componentDevDirs: Vector[String] = Vector.empty,
    runtimeArgs: Vector[String] = Vector.empty,
    useProjectClasspath: Boolean = true,
    projectActivation: ProjectActivation = ProjectActivation.Auto,
    includeProjectComponentDevDir: Boolean = true,
    passthrough: Vector[String] = Vector.empty
  )

  object Dev {
    final case class Classpath(options: DevOptions) extends Dev
    final case class Check(options: DevOptions) extends Dev
    final case class Server(options: DevOptions) extends Dev
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
    case object Channels extends Runtime
    final case class Install(version: String) extends Runtime
    final case class Use(version: String, target: RuntimeUseTarget) extends Runtime
    final case class CacheStatus() extends Runtime
    final case class ConfigShow() extends Runtime
  }


  enum ProjectActivation {
    case Auto, None, DevDir, ComponentDir
  }

  enum RuntimeUseTarget {
    case Auto, Global, Project
  }

  case object Help extends CncfCommand
}

object CncfCommandParser {
  def parse(args: Vector[String]): CncfCommand = {
    if (args.isEmpty || args.contains("--help") || args.contains("-h")) {
      CncfCommand.Help
    } else {
      val (runtimeversion, runtimedevdir, rest) = _take_global_runtime_options(args)
      rest.headOption match {
        case Some("dev") =>
          _parse_dev(rest.tail, runtimeversion, runtimedevdir)
        case Some("runtime") =>
          _parse_runtime(rest.tail)
        case Some(other) =>
          throw CncfException(s"unknown cncf command: $other")
        case None =>
          CncfCommand.Help
      }
    }
  }

  private def _take_global_runtime_options(args: Vector[String]): (Option[String], Option[String], Vector[String]) = {
    val out = Vector.newBuilder[String]
    var runtime: Option[String] = None
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
    (runtime, runtimedevdir, out.result())
  }

  private def _parse_dev(
    args: Vector[String],
    runtimeversion: Option[String],
    runtimedevdir: Option[String]
  ): CncfCommand.Dev = {
    if (args.isEmpty)
      throw CncfException("cncf dev requires a subcommand")
    val subcommand = args.head
    val (pre, passthrough) = args.tail.span(_ != "--")
    val (options, rest) = _parse_dev_options(pre, runtimeversion, runtimedevdir)
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
    globalruntimedevdir: Option[String]
  ): (CncfCommand.DevOptions, Vector[String]) = {
    val rest = Vector.newBuilder[String]
    var project: Option[String] = None
    var runtimedevdir = globalruntimedevdir
    var port: Option[String] = None
    var componentdevdirs = Vector.empty[String]
    var runtimeargs = Vector.empty[String]
    var useprojectclasspath = true
    var projectactivation = CncfCommand.ProjectActivation.Auto
    var includeprojectcomponentdevdir = true
    var i = 0
    while (i < args.length) {
      args(i) match {
        case "--project" =>
          if (i + 1 >= args.length) throw CncfException("--project requires a value")
          project = Some(args(i + 1))
          i += 2
        case x if x.startsWith("--project=") =>
          project = Some(x.stripPrefix("--project="))
          i += 1
        case "--runtime-dev-dir" | "--cncf-dev-dir" =>
          if (i + 1 >= args.length) throw CncfException(s"${args(i)} requires a value")
          runtimedevdir = Some(args(i + 1))
          i += 2
        case x if x.startsWith("--runtime-dev-dir=") =>
          runtimedevdir = Some(x.stripPrefix("--runtime-dev-dir="))
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
          projectactivation = CncfCommand.ProjectActivation.None
          includeprojectcomponentdevdir = false
          i += 1
        case "--no-project-component-dev-dir" =>
          projectactivation = CncfCommand.ProjectActivation.None
          includeprojectcomponentdevdir = false
          i += 1
        case "--project-activation" =>
          if (i + 1 >= args.length) throw CncfException("--project-activation requires a value")
          projectactivation = _project_activation(args(i + 1))
          includeprojectcomponentdevdir = projectactivation != CncfCommand.ProjectActivation.None
          i += 2
        case x if x.startsWith("--project-activation=") =>
          projectactivation = _project_activation(x.stripPrefix("--project-activation="))
          includeprojectcomponentdevdir = projectactivation != CncfCommand.ProjectActivation.None
          i += 1
        case "--component-dev-dir" =>
          if (i + 1 >= args.length) throw CncfException("--component-dev-dir requires a value")
          componentdevdirs :+= args(i + 1)
          i += 2
        case x if x.startsWith("--component-dev-dir=") =>
          componentdevdirs :+= x.stripPrefix("--component-dev-dir=")
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
      project = project,
      runtimeVersion = runtimeversion,
      runtimeDevDir = runtimedevdir,
      port = port,
      componentDevDirs = componentdevdirs,
      runtimeArgs = runtimeargs,
      useProjectClasspath = useprojectclasspath,
      projectActivation = projectactivation,
      includeProjectComponentDevDir = includeprojectcomponentdevdir
    ), rest.result())
  }


  private def _project_activation(value: String): CncfCommand.ProjectActivation =
    value match {
      case "auto" => CncfCommand.ProjectActivation.Auto
      case "none" => CncfCommand.ProjectActivation.None
      case "dev-dir" => CncfCommand.ProjectActivation.DevDir
      case "component-dir" => CncfCommand.ProjectActivation.ComponentDir
      case other => throw CncfException(s"unknown project activation: $other")
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
      |  cncf dev classpath [--project <dir>]
      |  cncf dev check [--project <dir>] [--runtime-dev-dir <dir>]
      |  cncf dev server [--project <dir>] [--runtime-dev-dir <dir>] [--port <port>] [--project-activation auto|none|dev-dir|component-dir] [--component-dev-dir <dir>...] [runtime args...]
      |  cncf dev server-emulation [--project <dir>] [--runtime-dev-dir <dir>] <component.service.operation|component/service/operation|url>
      |  cncf dev client [--project <dir>] [--runtime-dev-dir <dir>] [args...]
      |  cncf dev command [--project <dir>] [--runtime-dev-dir <dir>] [--project-activation auto|none|dev-dir|component-dir] [--no-project-classpath] [runtime args...] <operation> [params...]
      |  cncf runtime current
      |  cncf runtime list
      |  cncf runtime local list
      |  cncf runtime remote list
      |  cncf runtime refresh
      |  cncf runtime catalog show
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
      |  --runtime-dev-dir <dir> uses a local CNCF development checkout for dev commands.
      |  Runtime args before server/client/command are forwarded to CncfMain.
      |  --project-activation controls how --project becomes a runtime component source.
      |  --project-activation auto uses component.d artifacts, suppresses project activation when explicit runtime source args are present, otherwise uses component-dev-dir.
      |  --no-project-classpath invokes packaged CAR/SAR artifacts without current project classes.
      |  --no-project-component-dev-dir keeps project classes without adding project as component-dev-dir.
      |""".stripMargin
}
