/*
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
package cncf.launcher

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import java.net.{HttpURLConnection, InetAddress, InetSocketAddress, ServerSocket, URI}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.jdk.CollectionConverters.*
import scala.util.Try
import io.circe.syntax.*
import LifecycleSupervisorProtocol.given
import LifecycleSupervisorStateStore.given

trait CncfLauncherDevelopmentSpecSupport extends CncfLauncherSpecSupport {
  self: org.scalatest.Informing =>
  def devServerRewritesToCncfArgs(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("scala-3.3.7").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  version: 0.5.0
         |  catalog:
         |    url: ${paths.cwd.resolve("missing-runtime-catalog.yaml")}
         |dev:
         |  port: 19600
         |  componentDevDirs:
         |    - ../account
         |textus-control-center:
         |  registration:
         |    enabled: true
         |    endpoint: https://admin.example.test/rest/v1/org-simplemodeling-textus-control-center/subsystem-inventory
         |    token-env: TEXTUS_ADMIN_REGISTRATION_TOKEN
         |    host-label: acceptance
         |    base-url: https://subsystem.example.test
         |""".stripMargin)
    Files.createDirectories(paths.cwd.getParent.resolve("account"))
    _write(paths.cwd.getParent.resolve("account").resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    val invoker = FakeInvoker()
    val reporter = FakeCncfTextusControlCenterRegistrationReporter()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker, registrationreporter = reporter)
    launcher.run(Vector("dev", "server"))
    _assert_equals(invoker.lastArgs.take(4), Vector(
      "--component-dev-dir",
      paths.cwd.toString,
      "--component-dev-dir",
      paths.cwd.getParent.resolve("account").toAbsolutePath.normalize.toString
    ))
    invoker.lastArgs.contains("server") shouldBe true
    invoker.lastClasspath.contains(classdir) shouldBe true
    _assert_equals(reporter.starts, Vector.empty)
  }

  def devServerWritesStateDuringInvocation(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    val processmanager = FakeDevServerProcessManager(1234L)
    val invoker = FakeInvoker()
    invoker.onInvoke = () => {
      val pidfile = DevSupport.devServerPidFile(paths.cwd)
      val jsonfile = DevSupport.devServerJsonFile(paths.cwd)
      Files.isRegularFile(pidfile) shouldBe true
      Files.isRegularFile(jsonfile) shouldBe true
      _assert_equals(Files.readString(pidfile).trim, "1234")
      val json = Files.readString(jsonfile)
      json.contains(""""port": "19601"""") shouldBe true
      json.contains(""""project": """) shouldBe true
    }
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker, SbtRuntimeClasspathExporter, processmanager)

    launcher.run(Vector("dev", "server", "--port", "19601"))

    Files.exists(DevSupport.devServerPidFile(paths.cwd)) shouldBe false
    Files.exists(DevSupport.devServerJsonFile(paths.cwd)) shouldBe false
  }

  def devServerRejectsAliveExistingState(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write_dev_server_state(paths.cwd, 2222L, "19602")
    val processmanager = FakeDevServerProcessManager(3333L)
    processmanager.alive = Set(2222L)
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), SbtRuntimeClasspathExporter, processmanager)
    val failed =
      try {
        launcher.run(Vector("dev", "server", "--port", "19602"))
        false
      } catch {
        case e: CncfException => e.getMessage.contains("dev server already running") && e.getMessage.contains("--restart")
      }
    failed shouldBe true
  }

  def devServerStopExistingBeforeInvocation(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write_dev_server_state(paths.cwd, 2223L, "19603")
    val processmanager = FakeDevServerProcessManager(3334L)
    processmanager.alive = Set(2223L)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker, SbtRuntimeClasspathExporter, processmanager)

    launcher.run(Vector("dev", "server", "--port", "19603", "--stop-existing"))

    _assert_equals(processmanager.gracefulStopped, Vector(2223L))
    invoker.lastArgs.contains("server") shouldBe true
  }

  def devStopStopsExistingWithoutInvocation(): Unit = _with_temp_paths { paths =>
    _write_dev_server_state(paths.cwd, 2224L, "19604")
    val processmanager = FakeDevServerProcessManager(3335L)
    processmanager.alive = Set(2224L)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker, SbtRuntimeClasspathExporter, processmanager)

    launcher.run(Vector("dev", "stop", "--port", "19604"))

    _assert_equals(processmanager.gracefulStopped, Vector(2224L))
    _assert_equals(invoker.lastArgs, Vector.empty)
    Files.exists(DevSupport.devServerJsonFile(paths.cwd)) shouldBe false
  }

  def devStopUsesRecordedPortWhenPortIsOmitted(): Unit = _with_temp_paths { paths =>
    _write_dev_server_state(paths.cwd, 2227L, "19607")
    val processmanager = FakeDevServerProcessManager(3339L)
    processmanager.alive = Set(2227L)
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), SbtRuntimeClasspathExporter, processmanager)

    launcher.run(Vector("dev", "stop"))

    _assert_equals(processmanager.gracefulStopped, Vector(2227L))
  }

  def devServerForceStopsAfterGracefulFailure(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write_dev_server_state(paths.cwd, 2225L, "19605")
    val processmanager = FakeDevServerProcessManager(3336L)
    processmanager.alive = Set(2225L)
    processmanager.gracefulSucceeds = false
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), SbtRuntimeClasspathExporter, processmanager)

    launcher.run(Vector("dev", "server", "--port", "19605", "--stop-existing", "--force-existing"))

    _assert_equals(processmanager.gracefulStopped, Vector(2225L))
    _assert_equals(processmanager.forceStopped, Vector(2225L))
  }

  def devServerRequiresForceForAmbiguousAliveState(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(DevSupport.devServerPidFile(paths.cwd), "2226\n")
    val processmanager = FakeDevServerProcessManager(3338L)
    processmanager.alive = Set(2226L)
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), SbtRuntimeClasspathExporter, processmanager)
    val failed =
      try {
        launcher.run(Vector("dev", "server", "--port", "19606", "--stop-existing"))
        false
      } catch {
        case e: CncfException => e.getMessage.contains("state is ambiguous") && e.getMessage.contains("--force-existing")
      }

    failed shouldBe true
  }

  def devServerRejectsPidReuseWithoutForce(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write_dev_server_state(paths.cwd, 2228L, "19608", java.time.Instant.parse("2026-05-24T00:00:00Z"))
    val processmanager = FakeDevServerProcessManager(3340L)
    processmanager.alive = Set(2228L)
    processmanager.processStarts = Map(2228L -> java.time.Instant.parse("2026-05-24T01:00:00Z"))
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), SbtRuntimeClasspathExporter, processmanager)
    val failed =
      try {
        launcher.run(Vector("dev", "server", "--port", "19608", "--stop-existing"))
        false
      } catch {
        case e: CncfException => e.getMessage.contains("state is ambiguous") && e.getMessage.contains("--force-existing")
      }

    failed shouldBe true
  }

  def devServerProfileAddsLocalPersistentSqliteArgs(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("dev", "server", "--profile", "local-persistent"))

    val sqlitepath = paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime.sqlite").toAbsolutePath.normalize.toString
    invoker.lastArgs.contains(s"--textus.datastore.sqlite.path=$sqlitepath") shouldBe true
    invoker.lastArgs.contains(s"--cncf.datastore.sqlite.path=$sqlitepath") shouldBe true
    invoker.lastArgs.contains("--textus.datastore.sqlite.normalize-column-names=true") shouldBe true
    Files.isDirectory(paths.cwd.resolve("target").resolve("cncf.d")) shouldBe true
  }

  def devServerUsesRuntimeDevelopmentDirectory(): Unit = _with_temp_paths { paths =>
    val appclassdir = paths.cwd.resolve("target").resolve("scala-3.3.7").resolve("classes")
    val runtimeproject = paths.cwd.resolve("cncf-runtime")
    val runtimeclassdir = runtimeproject.resolve("target").resolve("scala-3.3.7").resolve("classes")
    Files.createDirectories(appclassdir)
    Files.createDirectories(runtimeclassdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), appclassdir.toString)
    _write(runtimeproject.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), runtimeclassdir.toString)
    val resolver = FakeResolver()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, resolver, invoker)
    launcher.run(Vector("dev", "server", "--runtime-dev-dir", "cncf-runtime"))
    _assert_equals(resolver.resolvedClasspaths, Vector.empty)
    invoker.lastClasspath.contains(runtimeclassdir) shouldBe true
    invoker.lastClasspath.contains(appclassdir) shouldBe true
  }

  def devServerUsesRuntimeDevelopmentCatalogForSelection(): Unit = _with_temp_paths { paths =>
    val appclassdir = paths.cwd.resolve("target").resolve("scala-3.3.7").resolve("classes")
    val runtimeproject = paths.cwd.resolve("cncf-runtime")
    val runtimeclassdir = runtimeproject.resolve("target").resolve("scala-3.3.7").resolve("classes")
    Files.createDirectories(appclassdir)
    Files.createDirectories(runtimeclassdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), appclassdir.toString)
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("0.4.10-SNAPSHOT", Vector("0.4.10-SNAPSHOT")))
    _write(runtimeproject.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), runtimeclassdir.toString)
    _write(runtimeproject.resolve("target").resolve("cncf.d").resolve("runtime-catalog.yaml"), _runtime_dev_catalog_text)
    val resolver = FakeResolver()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, resolver, invoker)

    launcher.run(Vector("--runtime", "0.4.10-SNAPSHOT", "dev", "server", "--runtime-dev-dir", "cncf-runtime"))

    _assert_equals(resolver.resolvedClasspaths, Vector.empty)
    invoker.lastClasspath.contains(runtimeclassdir) shouldBe true
    invoker.lastClasspath.contains(appclassdir) shouldBe true
  }

  def devCommandPassesRuntimeLeadingArgs(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    launcher.run(Vector(
      "dev", "command",
      "--repository-dir", "repository.d",
      "--component-car-dir", "car.d",
      "--textus.runtime.component=minimal",
      "minimal.main.hello",
      "--format", "yaml"
    ))
    val commandindex = invoker.lastArgs.indexOf("command")
    commandindex > 0 shouldBe true
    _assert_equals(invoker.lastArgs.slice(commandindex - 5, commandindex), Vector(
      "--repository-dir", "repository.d",
      "--component-car-dir", "car.d",
      "--textus.runtime.component=minimal"
    ))
    _assert_equals(invoker.lastArgs.drop(commandindex), Vector("command", "minimal.main.hello", "--format", "yaml"))
  }

  def devCommandKeepsSampleMainClassValueAsRuntimeArg(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    launcher.run(Vector(
      "dev", "command",
      "--sample-main-class", "sample.Main",
      "minimal.main.hello"
    ))
    val commandindex = invoker.lastArgs.indexOf("command")
    commandindex > 0 shouldBe true
    _assert_equals(invoker.lastArgs.slice(commandindex - 2, commandindex), Vector(
      "--sample-main-class", "sample.Main"
    ))
    _assert_equals(invoker.lastArgs.drop(commandindex), Vector("command", "minimal.main.hello"))
  }

  def devCommandCanDisableProjectClasspath(): Unit = _with_temp_paths { paths =>
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    val code = launcher.run(Vector("dev", "command", "--no-project-classpath", "--repository-dir", "repository.d", "minimal.main.hello"))
    _assert_equals(code, 0)
    invoker.lastArgs.contains("--repository-dir") shouldBe true
    invoker.lastArgs.contains(paths.cwd.toAbsolutePath.normalize.toString) shouldBe false
  }

  def devCommandCanDisableProjectComponentDevDir(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    val code = launcher.run(Vector("dev", "command", "--no-project-component-dev-dir", "--discover=classes", "minimal.main.hello"))
    _assert_equals(code, 0)
    invoker.lastClasspath.contains(classdir) shouldBe true
    invoker.lastArgs.contains("--component-dev-dir") shouldBe false
    invoker.lastArgs.contains("--discover=classes") shouldBe true
  }

  def devCommandDoesNotAutoActivateComponentDirArtifacts(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("component.d").resolve("testcomp.car"), "fake-car")
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    val code = launcher.run(Vector("dev", "command", "testcomp.main.hello"))
    _assert_equals(code, 0)
    invoker.lastArgs.contains("--component-dir") shouldBe false
    invoker.lastArgs.contains("--component-dev-dir") shouldBe true
    invoker.lastClasspath.contains(classdir) shouldBe true
  }

  def devTargetOptionsAreMutuallyExclusive(): Unit = {
    val failed =
      try {
        CncfCommandParser.parse(Vector("dev", "server", "--project-dev", "app", "--name", "textus-blog"))
        false
      } catch {
        case e: CncfException => e.getMessage.contains("mutually exclusive")
      }
    failed shouldBe true
    val oldproject =
      try {
        CncfCommandParser.parse(Vector("dev", "server", "--project", "app"))
        false
      } catch {
        case e: CncfException => e.getMessage.contains("--project is no longer supported")
      }
    oldproject shouldBe true
  }

  def devNameTargetUsesLocalSnapshotOnly(): Unit = _with_temp_paths { paths =>
    val name = "textus-demo"
    val version = "0.1.0-SNAPSHOT"
    _write(paths.localCarRepository.resolve(name).resolve(version).resolve(s"$name-$version.car"), "fake-car")
    _write(paths.cacheCarRepository.resolve(name).resolve(version).resolve(s"$name-$version.car"), "wrong-car")
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("dev", "server", "--name", s"$name:$version"))

    invoker.lastArgs.contains(s"--textus.component=$name") shouldBe true
    invoker.lastArgs.contains(s"--textus.component.version=$version") shouldBe true
    invoker.lastArgs.contains(s"--repository-dir=${paths.localCarRepository}") shouldBe true
    invoker.lastArgs.contains(s"--repository-dir=${paths.cacheCarRepository}") shouldBe false
  }

  def devNameTargetSnapshotBypassesReleaseCatalog(): Unit = _with_temp_paths { paths =>
    val name = "textus-demo"
    val releaseversion = "0.1.0"
    val snapshotversion = "0.1.1-SNAPSHOT"
    _write(paths.localCarRepository.resolve(name).resolve(snapshotversion).resolve(s"$name-$snapshotversion.car"), "fake-car")
    _write(paths.localCarRepository.getParent.resolve("catalog").resolve("car").resolve(s"$name.yaml"),
      s"""schemaVersion: 1
         |kind: car
         |artifactId: $name
         |recommended: $releaseversion
         |latestStable: $releaseversion
         |status: active
         |aliases: []
         |versions:
         |  - version: $releaseversion
         |    channel: stable
         |    status: active
         |    component: $name
         |    file: repository/car/$name/$releaseversion/$name-$releaseversion.car
         |""".stripMargin)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("dev", "server", "--name", s"$name:$snapshotversion"))

    invoker.lastArgs.contains(s"--textus.component=$name") shouldBe true
    invoker.lastArgs.contains(s"--textus.component.version=$snapshotversion") shouldBe true
    invoker.lastArgs.contains(s"--repository-dir=${paths.localCarRepository}") shouldBe true
  }

  def devNameTargetUsesReleaseRepositories(): Unit = _with_temp_paths { paths =>
    val name = "textus-demo"
    val version = "0.1.0"
    _write(paths.cacheCarRepository.resolve(name).resolve(version).resolve(s"$name-$version.car"), "fake-car")
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("dev", "server", "--name", s"$name:$version"))

    invoker.lastArgs.contains(s"--textus.component=$name") shouldBe true
    invoker.lastArgs.contains(s"--textus.component.version=$version") shouldBe true
    invoker.lastArgs.contains(s"--repository-dir=${paths.localCarRepository}") shouldBe true
    invoker.lastArgs.contains(s"--repository-dir=${paths.cacheCarRepository}") shouldBe true
  }

  def devNameTargetUsesSelectedArtifactRepository(): Unit = _with_temp_paths { paths =>
    val name = "textus-demo"
    val version = "0.1.0"
    _write(paths.localCarRepository.resolve(name).resolve(version).resolve(s"$name-$version.car"), "local-car")
    _write(paths.cacheCarRepository.resolve(name).resolve(version).resolve(s"$name-$version.car"), "cache-car")
    val selectedrepository = paths.cacheCarRepository.toString
    val resolved = CncfArtifactResolver().resolve(
      CncfArtifactSelector.parse(s"$name:$version"),
      LauncherConfig.load(paths),
      Some(selectedrepository)
    )
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("dev", "server", "--name", s"$name:$version", "--artifact-repository", selectedrepository))

    resolved.repositories shouldBe Vector(selectedrepository)
    invoker.lastArgs shouldBe Vector(
      "--no-default-components",
      s"--repository-dir=$selectedrepository",
      s"--textus.component=$name",
      s"--textus.component.version=$version",
      "server"
    )
  }

  def devArtifactRepositoryRejectsInvalidTarget(): Unit = {
    val withoutname = Try(CncfCommandParser.parse(Vector("dev", "server", "--artifact-repository", "repository"))).failed.toOption
    val emptyvalue = Try(CncfCommandParser.parse(Vector("dev", "server", "--name", "textus-demo", "--artifact-repository="))).failed.toOption

    withoutname.map(_.getMessage) shouldBe Some("--artifact-repository requires --name")
    emptyvalue.map(_.getMessage) shouldBe Some("--artifact-repository requires a nonempty value")
  }

  def devServerEmulationRewritesToCncfArgs(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  version: 0.5.0
         |  catalog:
         |    url: ${paths.cwd.resolve("missing-runtime-catalog.yaml")}
         |""".stripMargin)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    launcher.run(Vector("dev", "server-emulation", "blog.component.search"))
    invoker.lastArgs.contains("server-emulator") shouldBe true
    invoker.lastArgs.contains("blog.component.search") shouldBe true
  }

  def devHelpExplainsResolutionModel(): Unit = {
    val help = CncfCommandParser.helpText
    help.contains("Execution:") shouldBe true
    help.contains("cncf command/server/client forwards to CncfMain") shouldBe true
    help.contains("Target-first execution adds target activation") shouldBe true
    help.contains("Low-level dev commands:") shouldBe false
    help.contains("cncf dev server") shouldBe false
    help.contains("cncf dev is deprecated") shouldBe true
    help.contains("--artifact-repository <dir|url> to select one exclusive artifact repository") shouldBe true
    help.contains("dev-server.pid") shouldBe false
    help.contains("--component-dev-dir <dir> is a dependency component local override") shouldBe true
    help.contains("cozyPublishLocalCar") shouldBe true
    help.contains("~/.cncf/local is developer local publish state") shouldBe true
    help.contains("Snapshot components are local-only") shouldBe true
    help.contains("descriptor source metadata lives under src/main/web-inf") shouldBe true
    help.contains("textus server <artifact> is the CAR/SAR artifact launcher") shouldBe true
    help.contains("ancestor conf/cncf/config.yaml and .cncf/config.yaml") shouldBe true
    help.contains("--launcher-home <dir> selects an explicit Launcher state-home root") shouldBe true
  }

  def devCheckReportsMainTargetAndDependencyResolution(): Unit = _with_temp_paths { paths =>
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    val (code, output) = _capture_stdout {
      launcher.run(Vector("dev", "check"))
    }
    _assert_equals(code, 2)
    output.contains("dev-target mode=project-dev") shouldBe true
    output.contains("main-target source=local-project") shouldBe true
    output.contains("main-target-repository-lookup disabled in project-dev mode") shouldBe true
    output.contains("dependency-components local dev overrides") shouldBe true
    output.contains("local-repository") shouldBe true
    output.contains(paths.localRepository.toString) shouldBe true
    output.contains("cache-repository") shouldBe true
    output.contains("status=not-created") shouldBe true
    output.contains("web-descriptor-source none; use src/main/web-inf/web.yaml|form.yaml|admin.yaml") shouldBe true
  }

  def devCheckReportsDevServerState(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write_dev_server_state(paths.cwd, 2230L, "19610")
    val processmanager = FakeDevServerProcessManager(3337L)
    processmanager.alive = Set(2230L)
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), SbtRuntimeClasspathExporter, processmanager)
    val (code, output) = _capture_stdout {
      launcher.run(Vector("dev", "check", "--port", "19610"))
    }

    _assert_equals(code, 0)
    output.contains("dev-server") shouldBe true
    output.contains("pid=2230") shouldBe true
    output.contains("status=alive") shouldBe true
    output.contains("port=19610") shouldBe true
  }

  def devCheckTreatsMissingMainTargetClasspathAsError(): Unit = _with_temp_paths { paths =>
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    val (code, output) = _capture_stdout {
      launcher.run(Vector("dev", "check"))
    }
    _assert_equals(code, 2)
    output.contains("ERROR") shouldBe true
    output.contains("runtime-classpath") shouldBe true
    output.contains("prepare the development runtime classpath") shouldBe true
  }

  def devCheckTreatsMissingDependencyClasspathAsError(): Unit = _with_temp_paths { paths =>
    Files.createDirectories(paths.cwd.getParent.resolve("account"))
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    val (code, output) = _capture_stdout {
      launcher.run(Vector("dev", "check", "--component-dev-dir", "../account"))
    }
    _assert_equals(code, 2)
    output.contains("ERROR") shouldBe true
    output.contains("dependency-component-dev-dir") shouldBe true
    output.contains("run cncf dev classpath --project-dev") shouldBe true
  }

  def devServerRequiresPreparedMainTargetClasspath(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    val exporter = FakeClasspathExporter.success(classdir.toString)
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), exporter)

    val failure = intercept[CncfException] {
      launcher.run(Vector("dev", "server"))
    }

    failure.getMessage.contains("development runtime classpath not found") shouldBe true
    exporter.projects shouldBe empty
  }

  def runtimeDevelopmentRequiresPreparedClasspath(): Unit = _with_temp_paths { paths =>
    val runtimeproject = paths.cwd.resolve("runtime")
    val exporter = FakeClasspathExporter.failure("SBT must not run")
    val support = new DevSupport(paths, exporter)

    val failure = intercept[CncfException] {
      support.cncfRuntimeClasspath(runtimeproject)
    }

    failure.getMessage.contains("development runtime classpath not found") shouldBe true
    exporter.projects shouldBe empty
  }

  def runtimeDevelopmentReadsNewlineDelimitedClasspath(): Unit = _with_temp_paths { paths =>
    Given("a prepared runtime classpath file containing two newline-separated absolute paths")
    val runtimeproject = paths.cwd.resolve("runtime")
    val firstpath = runtimeproject.resolve("target").resolve("classes")
    val secondpath = runtimeproject.resolve("dependency").resolve("classes")
    _write(DevSupport.runtimeClasspathFile(runtimeproject), s"$firstpath\n$secondpath\n")
    val support = new DevSupport(paths, FakeClasspathExporter.failure("SBT must not run"))

    When("the runtime classpath is read")
    val classpath = support.cncfRuntimeClasspath(runtimeproject)

    Then("both entries are retained in source order")
    classpath shouldBe Vector(firstpath, secondpath)
  }

  def devUsesCurrentCompatibleRuntimeByDefault(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("0.2.0", Vector("0.2.0", "0.3.0-SNAPSHOT")))
    _write(paths.cwd.resolve("runtime-catalog.yaml"), _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("runtime-catalog.yaml")}
         |""".stripMargin)
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())

    launcher.run(Vector("dev", "server"))

    _assert_equals(resolver.resolvedClasspaths, Vector("0.2.0"))
  }

  def devCanSelectLatestTestedRuntime(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("0.2.0", Vector("0.2.0", "0.3.0-SNAPSHOT")))
    _write(paths.cwd.resolve("runtime-catalog.yaml"), _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("runtime-catalog.yaml")}
         |""".stripMargin)
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())

    launcher.run(Vector("--runtime-selection=tested-latest", "dev", "server"))

    _assert_equals(resolver.resolvedClasspaths, Vector("0.2.0"))
  }

  def devCanSelectLatestCompatibleRuntime(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("0.2.0", Vector("0.2.0")))
    _write(paths.cwd.resolve("runtime-catalog.yaml"), _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("runtime-catalog.yaml")}
         |""".stripMargin)
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())

    launcher.run(Vector("--runtime-selection=latest", "dev", "server"))

    _assert_equals(resolver.resolvedClasspaths, Vector("0.2.0"))
  }

  def devCanSelectNewestCompatibleRuntime(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("0.2.0", Vector("0.2.0")))
    _write(paths.cwd.resolve("runtime-catalog.yaml"), _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("runtime-catalog.yaml")}
         |""".stripMargin)
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())

    launcher.run(Vector("--runtime-selection=newest", "dev", "server"))

    _assert_equals(resolver.resolvedClasspaths, Vector("0.3.0-SNAPSHOT"))
  }

  def devParsesInlineRuntimeRequirementLists(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("project.yaml"),
      """packaging:
        |  kind: car
        |  car:
        |    runtime:
        |      cncf:
        |        minimum: 0.2.0
        |        excluded: []
        |        tested: [0.3.0-SNAPSHOT]
        |""".stripMargin)
    _write(paths.cwd.resolve("runtime-catalog.yaml"), _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("runtime-catalog.yaml")}
         |""".stripMargin)
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())

    launcher.run(Vector("--runtime-selection=tested-latest", "dev", "server"))

    _assert_equals(resolver.resolvedClasspaths, Vector("0.3.0-SNAPSHOT"))
  }

  def devSelectsCommonRuntimeAcrossProjectAndDependency(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    val account = paths.cwd.getParent.resolve("account")
    Files.createDirectories(classdir)
    Files.createDirectories(account.resolve("target").resolve("classes"))
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(account.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("0.2.0", Vector("0.2.0", "0.3.0-SNAPSHOT")))
    _write(account.resolve("project.yaml"), _project_yaml("0.2.0", Vector("0.2.0")))
    _write(paths.cwd.resolve("runtime-catalog.yaml"), _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("runtime-catalog.yaml")}
         |dev:
         |  componentDevDirs:
         |    - ../account
         |""".stripMargin)
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())

    launcher.run(Vector("dev", "server"))

    _assert_equals(resolver.resolvedClasspaths, Vector("0.2.0"))
  }

  def devRuntimeConflictDefaultsToError(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("9.0.0", Vector("9.0.0")))
    _write(paths.cwd.resolve("runtime-catalog.yaml"), _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("runtime-catalog.yaml")}
         |""".stripMargin)
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    val failed =
      try {
        launcher.run(Vector("dev", "server"))
        false
      } catch {
        case e: CncfException => e.getMessage.contains("no compatible CNCF runtime version")
      }
    failed shouldBe true
  }

  def devRuntimeConflictCanUseNewestPolicy(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("9.0.0", Vector("9.0.0")))
    _write(paths.cwd.resolve("runtime-catalog.yaml"), _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("runtime-catalog.yaml")}
         |""".stripMargin)
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())

    launcher.run(Vector("--runtime-no-compatible=newest", "dev", "server"))

    _assert_equals(resolver.resolvedClasspaths, Vector("0.3.0-SNAPSHOT"))
  }

  def noCncfRuntimeLibraryDependencies(): Unit = {
    val build = Files.readString(Path.of("build.sbt"))
    build should not include "goldenport-cncf"
    build should include("goldenport-launcher-core")
  }
}
