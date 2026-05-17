package cncf.launcher

import java.nio.file.{Files, Path}

/*
 * @since   May. 17, 2026
 * @version May. 17, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfLauncherSpec {
  def main(args: Array[String]): Unit = {
    val spec = new CncfLauncherSpec
    spec.parser()
    spec.configMerge()
    spec.runtimeVersionPrecedence()
    spec.runtimeUseWritesExpectedFiles()
    spec.runtimeUseAutoSelectsProjectWhenCncfDirectoryExists()
    spec.runtimeCatalogParseAndSelectorResolution()
    spec.runtimeCatalogCommands()
    spec.devParser()
    spec.devServerRewritesToCncfArgs()
    spec.devServerEmulationRewritesToCncfArgs()
    spec.devProjectLoadsTargetProjectConfig()
    spec.devCheckReportsMissingClasspath()
    spec.runtimeCommandDoesNotLoadCncf()
    spec.latestRuntimeIsConcrete()
    spec.noRuntimeLibraryDependencies()
    println("CncfLauncherSpec: OK")
  }
}

final class CncfLauncherSpec {
  def parser(): Unit = {
    val autouse = CncfCommandParser.parse(Vector("runtime", "use", "latest"))
      .asInstanceOf[CncfCommand.Runtime.Use]
    _assert_equals(autouse.version, "latest")
    _assert_equals(autouse.target, CncfCommand.RuntimeUseTarget.Auto)
  }

  def configMerge(): Unit = _with_temp_paths { paths =>
    _write(paths.cncfHome.resolve("config.yaml"),
      """runtime:
        |  version: 0.1.0
        |  catalog:
        |    url: https://global.example/catalog.yaml
        |dev:
        |  port: 19000
        |  componentDevDirs:
        |    - ../global-component
        |repositories:
        |  car:
        |    - https://global.example/car
        |  sar:
        |    - https://global.example/sar
        |""".stripMargin)
    _write(paths.cwd.resolve(".cncf").resolve("config.yaml"),
      """runtime:
        |  version: 0.2.0
        |dev:
        |  project: .
        |  componentDevDirs:
        |    - ../project-component
        |repositories:
        |  car:
        |    - https://project.example/car
        |""".stripMargin)
    val config = LauncherConfig.load(paths)
    _assert_equals(config.runtimeVersion, Some("0.2.0"))
    _assert_equals(config.runtimeCatalogUrl, Some("https://global.example/catalog.yaml"))
    _assert_equals(config.devProject, Some("."))
    _assert_equals(config.devPort, Some("19000"))
    _assert_equals(config.devComponentDevDirs, Vector("../project-component", "../global-component"))
    assert(config.carRepositories.head == "https://project.example/car")
    assert(config.carRepositories(1) == "https://global.example/car")
    assert(config.sarRepositories.head == "https://global.example/sar")
    assert(config.carRepositories.contains("https://www.simplemodeling.org/repository/car"))
  }

  def runtimeVersionPrecedence(): Unit = _with_temp_paths { paths =>
    val store = RuntimeVersionStore(paths)
    val config = LauncherConfig(runtimeVersion = Some("0.1.0"))
    _assert_equals(store.current(None, config), "0.1.0")
    store.useGlobal("0.2.0")
    _assert_equals(store.current(None, config), "0.2.0")
    store.useProject("0.3.0")
    _assert_equals(store.current(None, config), "0.3.0")
    _assert_equals(store.current(Some("0.4.0"), config), "0.4.0")
  }

  def runtimeUseWritesExpectedFiles(): Unit = _with_temp_paths { paths =>
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    launcher.run(Vector("runtime", "use", "latest"))
    _assert_equals(Files.readString(paths.globalVersion).trim, "latest")
    launcher.run(Vector("runtime", "use", "0.2.0", "--global"))
    launcher.run(Vector("runtime", "use", "0.3.0", "--project"))
    _assert_equals(Files.readString(paths.globalVersion).trim, "0.2.0")
    _assert_equals(Files.readString(paths.projectVersion).trim, "0.3.0")
  }

  def runtimeUseAutoSelectsProjectWhenCncfDirectoryExists(): Unit = _with_temp_paths { paths =>
    Files.createDirectories(paths.cwd.resolve(".cncf"))
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    launcher.run(Vector("runtime", "use", "latest"))
    _assert_equals(Files.readString(paths.projectVersion).trim, "latest")
    assert(!Files.isRegularFile(paths.globalVersion))
  }

  def runtimeCatalogParseAndSelectorResolution(): Unit = {
    val catalog = RuntimeCatalog.parse(_catalog_text)
    _assert_equals(catalog.resolve("recommended").version, "0.2.0")
    _assert_equals(catalog.resolve("latest").version, "0.2.0")
    _assert_equals(catalog.resolve("latest-stable").version, "0.2.0")
    _assert_equals(catalog.resolve("latest-snapshot").version, "0.3.0-SNAPSHOT")
    _assert_equals(catalog.resolve("newest").version, "0.3.0-SNAPSHOT")
    val disabled =
      try {
        catalog.resolve("0.1.0")
        false
      } catch {
        case e: CncfException => e.getMessage.contains("disabled")
      }
    assert(disabled)
  }

  def runtimeCatalogCommands(): Unit = _with_temp_paths { paths =>
    val catalogfile = paths.cwd.resolve("runtime-catalog.yaml")
    _write(catalogfile, _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("config.yaml"),
      s"""runtime:
         |  catalog:
         |    url: $catalogfile
         |""".stripMargin)
    val launcher = new CncfLauncher(paths, CoursierCncfRuntimeResolver("false"), FakeInvoker())
    launcher.run(Vector("runtime", "refresh"))
    assert(Files.isRegularFile(paths.runtimeCatalog))
    launcher.run(Vector("runtime", "remote", "list"))
    launcher.run(Vector("runtime", "catalog", "show"))
    launcher.run(Vector("runtime", "channels"))
    launcher.run(Vector("runtime", "use", "recommended", "--project"))
    _assert_equals(Files.readString(paths.projectVersion).trim, "recommended")
    launcher.run(Vector("runtime", "current"))
  }

  def devParser(): Unit = {
    val server = CncfCommandParser.parse(Vector(
      "--runtime", "0.4.7",
      "dev", "server",
      "--project", "/tmp/blog",
      "--port", "19599",
      "--component-dev-dir", "../account"
    )).asInstanceOf[CncfCommand.Dev.Server]
    _assert_equals(server.options.runtimeVersion, Some("0.4.7"))
    _assert_equals(server.options.project, Some("/tmp/blog"))
    _assert_equals(server.options.port, Some("19599"))
    _assert_equals(server.options.componentDevDirs, Vector("../account"))

    val command = CncfCommandParser.parse(Vector("dev", "command", "blog.post.search", "limit=10"))
      .asInstanceOf[CncfCommand.Dev.Command]
    _assert_equals(command.operation, "blog.post.search")
    _assert_equals(command.args, Vector("limit=10"))

    val emulation = CncfCommandParser.parse(Vector("dev", "server-emulation", "blog.component.search"))
      .asInstanceOf[CncfCommand.Dev.ServerEmulation]
    _assert_equals(emulation.args, Vector("blog.component.search"))
  }

  def devServerRewritesToCncfArgs(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("scala-3.3.7").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve(".cncf").resolve("config.yaml"),
      s"""runtime:
         |  version: 0.5.0
         |  catalog:
         |    url: ${paths.cwd.resolve("missing-runtime-catalog.yaml")}
         |dev:
         |  port: 19600
         |  componentDevDirs:
         |    - ../account
         |""".stripMargin)
    Files.createDirectories(paths.cwd.getParent.resolve("account"))
    _write(paths.cwd.getParent.resolve("account").resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    launcher.run(Vector("dev", "server"))
    _assert_equals(invoker.lastArgs.take(6), Vector(
      "--component-dev-dir",
      paths.cwd.toString,
      "--component-dev-dir",
      paths.cwd.getParent.resolve("account").toAbsolutePath.normalize.toString,
      "--no-exit",
      "--cncf.server.port=19600"
    ))
    assert(invoker.lastArgs.contains("server"))
    assert(invoker.lastClasspath.contains(classdir))
  }

  def devServerEmulationRewritesToCncfArgs(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve(".cncf").resolve("config.yaml"),
      s"""runtime:
         |  version: 0.5.0
         |  catalog:
         |    url: ${paths.cwd.resolve("missing-runtime-catalog.yaml")}
         |""".stripMargin)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    launcher.run(Vector("dev", "server-emulation", "blog.component.search"))
    assert(invoker.lastArgs.contains("server-emulator"))
    assert(invoker.lastArgs.contains("blog.component.search"))
  }

  def devProjectLoadsTargetProjectConfig(): Unit = _with_temp_paths { paths =>
    val project = paths.cwd.resolve("blog")
    val classdir = project.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(project.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(project.resolve(".cncf").resolve("config.yaml"),
      s"""runtime:
         |  version: 0.5.0
         |  catalog:
         |    url: ${project.resolve("missing-runtime-catalog.yaml")}
         |dev:
         |  port: 19700
         |""".stripMargin)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    launcher.run(Vector("dev", "server", "--project", "blog"))
    assert(invoker.lastArgs.contains("--cncf.server.port=19700"))
    assert(invoker.lastArgs.contains(project.toAbsolutePath.normalize.toString))
  }

  def devCheckReportsMissingClasspath(): Unit = _with_temp_paths { paths =>
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    val code = launcher.run(Vector("dev", "check"))
    _assert_equals(code, 2)
  }

  def runtimeCommandDoesNotLoadCncf(): Unit = _with_temp_paths { paths =>
    _write(paths.cwd.resolve(".cncf").resolve("config.yaml"), "runtime:\n  version: 0.5.0\n")
    val resolver = FakeResolver()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, resolver, invoker)
    launcher.run(Vector("runtime", "current"))
    _assert_equals(resolver.resolvedClasspaths, Vector.empty)
    _assert_equals(invoker.lastArgs, Vector.empty)
  }

  def latestRuntimeIsConcrete(): Unit = _with_temp_paths { paths =>
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())
    launcher.run(Vector("runtime", "current"))
    _assert_equals(resolver.resolvedVersions, Vector(LauncherConfig.DEFAULT_RUNTIME_VERSION))
  }

  def noRuntimeLibraryDependencies(): Unit = {
    val build = Files.readString(Path.of("build.sbt"))
    assert(!build.contains("libraryDependencies +="))
    assert(!build.contains("libraryDependencies ++="))
  }

  private def _with_temp_paths(f: LauncherPaths => Unit): Unit = {
    val root = Files.createTempDirectory("cncf-launcher-spec-")
    val home = root.resolve("home")
    val cwd = root.resolve("work")
    Files.createDirectories(home)
    Files.createDirectories(cwd)
    f(LauncherPaths(home, cwd))
  }

  private def _write(path: Path, value: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value)
  }

  private def _assert_equals[A](actual: A, expected: A): Unit =
    assert(actual == expected, s"expected=$expected actual=$actual")

  private val _catalog_text: String =
    """schemaVersion: 1
      |generatedAt: 2026-05-17T00:00:00Z
      |recommended: 0.2.0
      |latestStable: 0.2.0
      |latestSnapshot: 0.3.0-SNAPSHOT
      |mavenRepositories:
      |  - https://repo.example/maven
      |carRepositories:
      |  - https://repo.example/car
      |sarRepositories:
      |  - https://repo.example/sar
      |coursierRepositories:
      |  - https://repo.example/coursier
      |versions:
      |  - version: 0.1.0
      |    channel: stable
      |    status: disabled
      |    scalaBinaryVersion: "3"
      |    module: org.goldenport:goldenport-cncf_3:0.1.0
      |  - version: 0.2.0
      |    channel: stable
      |    status: active
      |    scalaBinaryVersion: "3"
      |    module: org.goldenport:goldenport-cncf_3:0.2.0
      |    publishedAt: 2026-05-17T01:00:00Z
      |  - version: 0.3.0-SNAPSHOT
      |    channel: snapshot
      |    status: active
      |    scalaBinaryVersion: "3"
      |    module: org.goldenport:goldenport-cncf_3:0.3.0-SNAPSHOT
      |    publishedAt: 2026-05-17T02:00:00Z
      |""".stripMargin
}

final class FakeResolver extends CncfRuntimeResolver {
  var resolvedVersions: Vector[String] = Vector.empty
  var resolvedClasspaths: Vector[String] = Vector.empty
  override def resolveVersion(version: String, config: LauncherConfig, paths: LauncherPaths): String = {
    resolvedVersions :+= version
    if (version == LauncherConfig.DEFAULT_RUNTIME_VERSION) "0.9.0" else version
  }
  def resolve(version: String, config: LauncherConfig, paths: LauncherPaths): Vector[Path] = {
    val concreteversion = resolveVersion(version, config, paths)
    resolvedClasspaths :+= concreteversion
    Vector(paths.cwd.resolve(s"fake-cncf-$concreteversion.jar"))
  }
}

object FakeResolver {
  def apply(): FakeResolver = new FakeResolver()
}

final class FakeInvoker extends CncfInvoker {
  var lastClasspath: Vector[Path] = Vector.empty
  var lastArgs: Vector[String] = Vector.empty
  override def invoke(classpath: Vector[Path], args: Vector[String]): Int = {
    lastClasspath = classpath
    lastArgs = args
    0
  }
}

object FakeInvoker {
  def apply(): FakeInvoker = new FakeInvoker()
}
