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

trait CncfLauncherRuntimeSpecSupport extends CncfLauncherSpecSupport {
  self: org.scalatest.Informing =>
  def runtimeVersion(): Unit = _with_temp_paths { paths =>
    Given("a project selects a CNCF runtime version in launcher config")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"), "runtime:\n  version: 0.1.0\n")
    val invoker = FakeInvoker()
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, invoker)

    When("the launcher version command is executed")
    val code = launcher.run(Vector("version"))

    Then("the launcher delegates version reporting to the selected CNCF runtime")
    _assert_equals(code, 0)
    _assert_equals(resolver.resolvedVersions, Vector("0.1.0"))
    _assert_equals(resolver.resolvedClasspaths, Vector("0.1.0"))
    _assert_equals(invoker.lastClasspath, Vector(paths.cwd.resolve("fake-cncf-0.1.0.jar")))
    _assert_equals(invoker.lastArgs, Vector("version"))
    _assert_equals(CncfCommandParser.parse(Vector("version")), CncfCommand.Runtime.Version(None, None))
    _assert_equals(CncfCommandParser.parse(Vector("--version")), CncfCommand.Runtime.Version(None, None))
  }

  def launcherVersion(): Unit = _with_temp_paths { paths =>
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    val (code, output) = _capture_stdout {
      launcher.run(Vector("launcher", "version"))
    }
    _assert_equals(code, 0)
    _assert_equals(output.trim, s"cncf ${LauncherBuildInfo.version}")
    _assert_equals(CncfCommandParser.parse(Vector("launcher", "version")), CncfCommand.LauncherVersion)
    _assert_equals(CncfCommandParser.parse(Vector("launcher", "--version")), CncfCommand.LauncherVersion)
  }

  def runtimeHelp(): Unit = _with_temp_paths { paths =>
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    val (code, output) = _capture_stdout {
      launcher.run(Vector("help"))
    }
    _assert_equals(code, 0)
    _assert_equals(invoker.lastArgs, Vector("--help"))
    output.contains("Launcher help:") shouldBe true
    output.contains("cncf launcher version") shouldBe true
    output.contains("[--runtime <version>] [--runtime-dev-dir <dir>] install-cli") shouldBe true
    output.contains("[--runtime <version>] [--runtime-dev-dir <dir>] <target> command") shouldBe true
    output.contains("cncf dev is deprecated") shouldBe true
    output.contains("runtime.dev-dir selects a managed development runtime when no explicit --runtime or --runtime-dev-dir is supplied") shouldBe true
    output.contains("ancestor conf/cncf/launcher.yaml and .cncf/launcher.yaml") shouldBe true
    _assert_equals(CncfCommandParser.parse(Vector("help")), CncfCommand.RuntimeHelp)
    _assert_equals(CncfCommandParser.parse(Vector("--help")), CncfCommand.RuntimeHelp)
    _assert_equals(CncfCommandParser.parse(Vector("launcher", "help")), CncfCommand.LauncherHelp)
  }

  def configMerge(): Unit = _with_temp_paths { paths =>
    _write(paths.cncfHome.resolve("launcher.yaml"),
      """runtime:
        |  version: 0.1.0
        |  devDir: ../global-cncf
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
    _write(paths.cwd.resolve("conf").resolve("cncf").resolve("launcher.yaml"),
      """runtime:
        |  version: 0.2.0
        |dev:
        |  project-dev: .
        |  componentDevDirs:
        |    - ../project-component
        |    - ../shared-component
        |repositories:
        |  car:
        |    - https://project.example/car
        |""".stripMargin)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """cncf:
        |  launcher:
        |    dev:
        |      dir: ../launcher-cncf
        |runtime:
        |  devDir: ../local-cncf
        |dev:
        |  componentDevDirs:
        |    - ../local-component
        |repositories:
        |  car:
        |    - https://local.example/car
        |""".stripMargin)
    val config = LauncherConfig.load(paths)
    _assert_equals(config.launcherDevDir, Some("../launcher-cncf"))
    _assert_equals(config.runtimeVersion, Some("0.2.0"))
    _assert_equals(config.runtimeDevDir, Some("../local-cncf"))
    _assert_equals(config.runtimeCatalogUrl, Some("https://global.example/catalog.yaml"))
    _assert_equals(config.devProjectDev, Some("."))
    _assert_equals(config.devPort, Some("19000"))
    _assert_equals(config.devComponentDevDirs, Vector("../local-component", "../project-component", "../shared-component", "../global-component"))
    config.carRepositories.head == "https://local.example/car" shouldBe true
    config.carRepositories(1) == "https://project.example/car" shouldBe true
    config.carRepositories(2) == "https://global.example/car" shouldBe true
    config.sarRepositories.head == "https://global.example/sar" shouldBe true
    config.carRepositories.contains(paths.localCarRepository.toString) shouldBe true
    config.sarRepositories.contains(paths.localSarRepository.toString) shouldBe true
    config.carRepositories.contains(paths.cacheCarRepository.toString) shouldBe true
    config.sarRepositories.contains(paths.cacheSarRepository.toString) shouldBe true
    config.carRepositories.contains("https://www.simplemodeling.org/repository/car") shouldBe true
  }

  def launcherDevDirDelegatesToDevelopmentLauncher(): Unit = _with_temp_paths { paths =>
    val launcherdevdir = paths.cwd.resolve("launcher-cncf")
    Files.createDirectories(launcherdevdir)
    _write(paths.cwd.resolve("conf").resolve("cncf").resolve("launcher.yaml"),
      """cncf:
        |  launcher:
        |    dev:
        |      dir: launcher-cncf
        |runtime:
        |  version: 0.5.0
        |""".stripMargin)
    val invoker = FakeLauncherDevInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), SbtRuntimeClasspathExporter, DevServerProcessManager.System, invoker)

    val code = launcher.run(Vector("launcher", "version"))

    _assert_equals(code, 0)
    _assert_equals(invoker.devDir, Some(launcherdevdir.toAbsolutePath.normalize))
    _assert_equals(invoker.args, Vector("launcher", "version"))
    _assert_equals(invoker.cwd, Some(paths.cwd.toAbsolutePath.normalize))
  }

  def launcherDevDirRejectsStaleDevelopmentClasspath(): Unit = _with_temp_paths { paths =>
    val launcherdevdir = paths.cwd.resolve("launcher-cncf")
    val staleclassdir = paths.cwd.resolve("stale-cncf-runtime-classes")
    Files.createDirectories(launcherdevdir)
    Files.createDirectories(staleclassdir)
    _write(launcherdevdir.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), staleclassdir.toString)

    val e = intercept[CncfException] {
      LauncherDevInvoker.System.invoke(launcherdevdir, Vector("launcher", "version"), paths.cwd)
    }

    e.getMessage.contains("does not contain cncf.launcher.CncfLauncherMain") shouldBe true
    e.getMessage.contains("run sbt --batch compile and cncf dev classpath") shouldBe true
  }

  def configSupportsAdditionalRdfNamespaces(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("conf").resolve("cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  version: 0.5.0
         |  catalog:
         |    url: ${paths.cwd.resolve("missing-runtime-catalog.yaml")}
         |textus:
         |  knowledge:
         |    rdf:
         |      current-prefix: acme
         |      namespace-prefixes:
         |        - acme
         |        - sm
         |      namespaces:
         |        acme: https://example.com/acme
         |        sm: https://www.simplemodeling.org
         |""".stripMargin)
    val config = LauncherConfig.load(paths)
    _assert_equals(config.textusKnowledgeRdfNodePrefix, Some("acme"))
    _assert_equals(config.textusKnowledgeRdfNamespacePrefixes, Some("acme,sm"))
    config.textusKnowledgeRdfNamespaces.contains("acme" -> "https://example.com/acme") shouldBe true
    config.textusKnowledgeRdfNamespaces.contains("sm" -> "https://www.simplemodeling.org") shouldBe true

    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    launcher.run(Vector("dev", "server"))
    invoker.lastArgs.contains("--textus.knowledge.rdf.node-prefix=acme") shouldBe true
    invoker.lastArgs.contains("--textus.knowledge.rdf.namespace-prefixes=acme,sm") shouldBe true
    invoker.lastArgs.contains("--textus.knowledge.rdf.namespaces.acme=https://example.com/acme") shouldBe true
    invoker.lastArgs.contains("--textus.knowledge.rdf.namespaces.sm=https://www.simplemodeling.org") shouldBe true
  }

  def configFileOptionOverridesProjectConfig(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("conf").resolve("cncf").resolve("launcher.yaml"),
      """runtime:
        |  version: 0.1.0
        |dev:
        |  port: 19500
        |""".stripMargin)
    _write(paths.cwd.resolve("etc").resolve("debug.yaml"),
      """runtime:
        |  version: 0.2.0
        |dev:
        |  port: 19600
        |""".stripMargin)
    val resolver = FakeResolver()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, resolver, invoker)

    launcher.run(Vector("--config", "etc/debug.yaml", "dev", "server"))

    _assert_equals(resolver.resolvedClasspaths, Vector("0.2.0"))
    invoker.lastArgs.contains("--config") shouldBe false
    invoker.lastArgs.contains("etc/debug.yaml") shouldBe false
  }

  def workspaceRootConfigAppliesToNestedCwd(): Unit = _with_temp_paths { paths =>
    val workspace = paths.cwd
    val sample = workspace.resolve("samples").resolve("01-hello")
    _write(workspace.resolve(".cncf").resolve("launcher.yaml"),
      """runtime:
        |  version: root
        |  dev-dir: ../cncf-runtime
        |""".stripMargin)
    _write(workspace.resolve(".cncf").resolve("config.yaml"),
      """textus:
        |  knowledge:
        |    rdf:
        |      current-prefix: root
        |""".stripMargin)
    _write(sample.resolve(".cncf").resolve("launcher.yaml"),
      """runtime:
        |  version: sample
        |""".stripMargin)

    val config = LauncherConfig.load(paths.withCwd(sample))

    _assert_equals(config.runtimeVersion, Some("sample"))
    _assert_equals(config.runtimeDevDir, Some("../cncf-runtime"))
    config.cncfConfigFiles.exists(_.contains("work/.cncf/config.yaml")) shouldBe true
  }

  def launcherConfigControlsDevelopmentRuntime(): Unit = _with_temp_paths { paths =>
    Given("a launcher config contains disabled development runtime and launcher candidates")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """development:
        |  enabled: false
        |  launcher:
        |    dev-dir: ../candidate-launcher
        |  runtime:
        |    dev-dir: ../candidate-runtime
        |""".stripMargin)

    When("the launcher loads the disabled development configuration")
    val inert = LauncherConfig.load(paths, Vector.empty, Map.empty)

    Then("the development candidates are recorded but not activated")
    _assert_equals(inert.developmentEnabled, Some(false))
    _assert_equals(inert.launcherDevDir, None)
    _assert_equals(inert.runtimeDevDir, None)
    _assert_equals(inert.developmentLauncherDevDir, Some("../candidate-launcher"))
    _assert_equals(inert.developmentRuntimeDevDir, Some("../candidate-runtime"))

    When("development.enabled is changed to true in the same launcher config")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """development:
        |  enabled: true
        |  launcher:
        |    dev-dir: ../candidate-launcher
        |  runtime:
        |    dev-dir: ../candidate-runtime
        |""".stripMargin)
    val active = LauncherConfig.load(paths, Vector.empty, Map.empty)

    Then("the development launcher and runtime candidates become active")
    _assert_equals(active.developmentEnabled, Some(true))
    _assert_equals(active.launcherDevDir, Some("../candidate-launcher"))
    _assert_equals(active.runtimeDevDir, Some("../candidate-runtime"))

    When("the launcher section enables only the development launcher")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """development:
        |  enabled: false
        |  launcher:
        |    enabled: true
        |    dev-dir: ../candidate-launcher
        |  runtime:
        |    enabled: false
        |    dev-dir: ../candidate-runtime
        |""".stripMargin)
    val launcheronly = LauncherConfig.load(paths, Vector.empty, Map.empty)

    Then("the runtime remains published while the launcher delegates to its checkout")
    _assert_equals(launcheronly.launcherDevDir, Some("../candidate-launcher"))
    _assert_equals(launcheronly.runtimeDevDir, None)

    When("the runtime section enables only the development runtime")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """development:
        |  enabled: false
        |  launcher:
        |    enabled: false
        |    dev-dir: ../candidate-launcher
        |  runtime:
        |    enabled: true
        |    dev-dir: ../candidate-runtime
        |""".stripMargin)
    val runtimeonly = LauncherConfig.load(paths, Vector.empty, Map.empty)

    Then("the installed launcher selects only the runtime checkout")
    _assert_equals(runtimeonly.launcherDevDir, None)
    _assert_equals(runtimeonly.runtimeDevDir, Some("../candidate-runtime"))

    Given("the launcher development switch is enabled without a candidate directory")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """development:
        |  enabled: false
        |  launcher:
        |    enabled: true
        |  runtime:
        |    enabled: false
        |""".stripMargin)

    When("the incomplete launcher configuration is loaded")
    val missinglauncher = intercept[CncfException] {
      LauncherConfig.load(paths, Vector.empty, Map.empty)
    }

    Then("the missing launcher directory is reported deterministically")
    missinglauncher.getMessage should include("development.launcher.dev-dir is required")

    Given("the runtime development switch is enabled without a candidate directory")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """development:
        |  enabled: false
        |  launcher:
        |    enabled: false
        |  runtime:
        |    enabled: true
        |""".stripMargin)

    When("the incomplete runtime configuration is loaded")
    val missingruntime = intercept[CncfException] {
      LauncherConfig.load(paths, Vector.empty, Map.empty)
    }

    Then("the missing runtime directory is reported deterministically")
    missingruntime.getMessage should include("development.runtime.dev-dir is required")

    Given("both development switches are enabled without configured candidate directories")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """development:
        |  enabled: true
        |""".stripMargin)

    When("emergency environment overrides provide both directories")
    val emergency = LauncherConfig.load(paths, Vector.empty, Map(
      "CNCF_RUNTIME_DEV_DIR" -> "../emergency-runtime",
      "CNCF_LAUNCHER_DEV_DIR" -> "../emergency-launcher"
    ))

    Then("the explicit overrides satisfy the enabled development selections")
    _assert_equals(emergency.runtimeDevDir, Some("../emergency-runtime"))
    _assert_equals(emergency.launcherDevDir, Some("../emergency-launcher"))

    When("the removed environment activation flag is present")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """development:
        |  enabled: false
        |  launcher:
        |    dev-dir: ../candidate-launcher
        |  runtime:
        |    dev-dir: ../candidate-runtime
        |""".stripMargin)
    val legacyenvironment = LauncherConfig.load(paths, Vector.empty, Map("CNCF_USE_DEVELOPMENT" -> "true"))

    Then("the file switch remains authoritative")
    _assert_equals(legacyenvironment.launcherDevDir, None)
    _assert_equals(legacyenvironment.runtimeDevDir, None)

    When("a higher-priority project config disables globally enabled development candidates")
    _write(paths.cncfHome.resolve("launcher.yaml"),
      """development:
        |  enabled: true
        |  launcher:
        |    dev-dir: ../global-launcher
        |  runtime:
        |    dev-dir: ../global-runtime
        |""".stripMargin)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"), "development:\n  enabled: false\n")
    val disabledoverride = LauncherConfig.load(paths, Vector.empty, Map.empty)

    Then("the project switch disables inherited development directories")
    _assert_equals(disabledoverride.launcherDevDir, None)
    _assert_equals(disabledoverride.runtimeDevDir, None)

    When("explicit environment overrides are supplied")
    val env = LauncherConfig.load(paths, Vector.empty, Map(
      "CNCF_VERSION" -> "0.4.12-SNAPSHOT",
      "CNCF_RUNTIME_DEV_DIR" -> "../env-runtime",
      "CNCF_LAUNCHER_DEV_DIR" -> "../env-launcher"
    ))

    Then("explicit runtime and launcher development directories take precedence")
    _assert_equals(env.runtimeVersion, Some("0.4.12-SNAPSHOT"))
    _assert_equals(env.runtimeDevDir, Some("../env-runtime"))
    _assert_equals(env.launcherDevDir, Some("../env-launcher"))
  }

  def launcherConfigSupportsPropertiesAndConfFiles(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("etc").resolve("launcher.properties"),
      """runtime.version = 0.2.0
        |dev.port = 19601
        |""".stripMargin)
    _write(paths.cwd.resolve("etc").resolve("launcher.conf"),
      """dev.restart = true
        |cncf.config.file = etc/runtime-debug.yaml
        |""".stripMargin)
    val resolver = FakeResolver()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, resolver, invoker)

    launcher.run(Vector("--config", "etc/launcher.properties", "--config", "etc/launcher.conf", "dev", "server"))

    _assert_equals(resolver.resolvedClasspaths, Vector("0.2.0"))
    invoker.lastArgs.exists(_.startsWith("--cncf.config.files=")) shouldBe true
    invoker.lastArgs.exists(_.contains("runtime-debug.yaml")) shouldBe true
  }

  def defaultRuntimeConfigFilesAreForwarded(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.globalRuntimeConfig, "textus:\n  global: true\n")
    _write(paths.projectRuntimeConfig, "textus:\n  project: true\n")
    _write(paths.projectLocalRuntimeConfig, "textus:\n  local: true\n")
    _write(paths.cwd.resolve("etc").resolve("runtime-extra.yaml"), "textus:\n  extra: true\n")
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("--cncf-config", "etc/runtime-extra.yaml", "dev", "server"))

    val configarg = invoker.lastArgs.find(_.startsWith("--cncf.config.files=")).getOrElse("")
    val values = configarg.stripPrefix("--cncf.config.files=").split(",").toVector
    _assert_equals(values, Vector(
      paths.globalRuntimeConfig.toAbsolutePath.normalize.toString,
      paths.projectRuntimeConfig.toAbsolutePath.normalize.toString,
      paths.projectLocalRuntimeConfig.toAbsolutePath.normalize.toString,
      paths.cwd.resolve("etc").resolve("runtime-extra.yaml").toAbsolutePath.normalize.toString
    ))
    invoker.lastArgs.contains("--cncf-config") shouldBe false
  }

  def configFileProjectDevSurvivesTargetCwdSwitch(): Unit = _with_temp_paths { paths =>
    val project = paths.cwd.resolve("blog")
    val classdir = project.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(project.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("etc").resolve("debug.yaml"),
      """runtime:
        |  version: 0.2.0
        |dev:
        |  project-dev: blog
        |  restart: true
        |""".stripMargin)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("--config", "etc/debug.yaml", "dev", "server"))

    invoker.lastArgs.contains(project.toAbsolutePath.normalize.toString) shouldBe true
    invoker.lastArgs.contains("--config") shouldBe false
    invoker.lastArgs.contains("etc/debug.yaml") shouldBe false
  }

  def cncfConfigOptionIsForwardedToRuntime(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve("etc").resolve("runtime-debug.yaml"),
      """textus:
        |  knowledge:
        |    rdf:
        |      current-prefix: sm
        |""".stripMargin)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("--cncf-config", "etc/runtime-debug.yaml", "dev", "server"))

    invoker.lastArgs.exists(_.startsWith("--cncf.config.files=")) shouldBe true
    invoker.lastArgs.exists(_.contains("runtime-debug.yaml")) shouldBe true
    invoker.lastArgs.contains("--cncf-config") shouldBe false
    invoker.lastArgs.contains("etc/runtime-debug.yaml") shouldBe false
  }

  def configFileOptionRequiresExistingFile(): Unit = _with_temp_paths { paths =>
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    val failed =
      try {
        launcher.run(Vector("--config", "etc/missing.conf", "runtime", "config", "show"))
        false
      } catch {
        case e: CncfException =>
          e.getMessage.contains("launcher config file not found") &&
            e.getMessage.contains("etc/missing.conf")
      }
    failed shouldBe true
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
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    launcher.run(Vector("runtime", "use", "latest"))
    _assert_equals(Files.readString(paths.globalVersion).trim, "latest")
    launcher.run(Vector("runtime", "use", "0.2.0", "--global"))
    launcher.run(Vector("runtime", "use", "0.3.0", "--project"))
    _assert_equals(Files.readString(paths.globalVersion).trim, "0.2.0")
    _assert_equals(Files.readString(paths.projectVersion).trim, "0.3.0")
    val isolatedhome = paths.cwd.resolve("isolated-launcher-home")
    launcher.run(Vector("--launcher-home", isolatedhome.toString, "runtime", "use", "0.4.0", "--global"))
    _assert_equals(Files.readString(isolatedhome.resolve(".cncf").resolve("version")).trim, "0.4.0")
    _assert_equals(Files.readString(paths.globalVersion).trim, "0.2.0")
    val passthroughhome = "runtime-passthrough-home"
    launcher.run(Vector("server", "--", "--launcher-home", passthroughhome))
    invoker.lastArgs.contains("--launcher-home") shouldBe true
    invoker.lastArgs.contains(passthroughhome) shouldBe true
    Files.exists(paths.cwd.resolve(passthroughhome).resolve(".cncf")) shouldBe false
  }

  def runtimeUseSelectsExactLocalSnapshot(): Unit = _with_temp_paths { paths =>
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())

    launcher.run(Vector("runtime", "use", "0.5.1-SNAPSHOT", "--global"))

    _assert_equals(Files.readString(paths.globalVersion).trim, "0.5.1-SNAPSHOT")
    _assert_equals(resolver.resolvedVersions, Vector("0.5.1-SNAPSHOT"))
  }

  def snapshotRuntimeRefreshesCachedClasspath(): Unit = _with_temp_paths { paths =>
    Given("a stale cached classpath for an exact SNAPSHOT runtime")
    _write(paths.runtimeCatalog, _catalog_text)
    _write(paths.runtimeRoot.resolve("0.5.2-SNAPSHOT/classpath.txt"), paths.cwd.resolve("stale-runtime.jar").toString)
    val resolver = CoursierCncfRuntimeResolver("false")

    When("the SNAPSHOT runtime is resolved")
    val failure = intercept[CncfException] {
      resolver.resolve("0.5.2-SNAPSHOT", LauncherConfig().normalizedWithDefaults(paths), paths)
    }

    Then("the external resolver is invoked rather than accepting the stale classpath")
    failure.getMessage should include("failed to resolve CNCF runtime 0.5.2-SNAPSHOT with Coursier")
  }

  def runtimeUseAutoSelectsProjectWhenCncfDirectoryExists(): Unit = _with_temp_paths { paths =>
    Files.createDirectories(paths.cwd.resolve(".cncf"))
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    launcher.run(Vector("runtime", "use", "latest"))
    _assert_equals(Files.readString(paths.projectVersion).trim, "latest")
    Files.isRegularFile(paths.globalVersion) shouldBe false
  }

  def installCliWritesDevelopmentCommand(): Unit = _with_temp_paths { paths =>
    _write(
      paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"launcher:\n  dev-dir: ${paths.cwd.resolve("launcher-dev").toAbsolutePath.normalize}\nruntime:\n  version: 0.4.12\n"
    )
    val launcher = new CncfLauncher(
      paths,
      FakeResolver(),
      FakeInvoker(),
      environment = Map("CNCF_LAUNCHER_DEV_DELEGATED" -> "1")
    )
    val code = launcher.run(Vector(
      "install-cli",
      "sanpomap",
      "--project-dev",
      ".",
      "--component-dev-dir",
      "../textus-georesolver"
    ))

    _assert_equals(code, 0)
    val command = paths.home.resolve("bin").resolve("sanpomap-dev")
    val script = Files.readString(command)
    script.contains(s"fixed_target='${paths.cwd.toAbsolutePath.normalize}'") shouldBe true
    script.contains("operation_prefix=''") shouldBe true
    script.contains("runtime_version=''") shouldBe true
    script.contains("runtime_dev_dir=''") shouldBe true
    script.contains("declare -a cncf_args=(\"cncf\")") shouldBe true
    script.contains(s"launcher_dev_dir='${paths.cwd.resolve("launcher-dev").toAbsolutePath.normalize}'") shouldBe true
    script.contains("export CNCF_LAUNCHER_DEV_DIR=\"$launcher_dev_dir\"") shouldBe true
    script.contains("component_dev_dirs=(") shouldBe true
    script.contains(s"'${paths.cwd.resolve("../textus-georesolver").toAbsolutePath.normalize}'") shouldBe true
    script.contains("component_dev_args+=(\"--component-dev-dir\" \"$dir\")") shouldBe true
    script.contains("exec \"${cncf_args[@]}\" \"$fixed_target\" command \"${component_dev_args[@]}\" \"${command_args[@]}\"") shouldBe true
    Files.isExecutable(command) shouldBe true
  }

  def installCliPinsExplicitRuntimeVersion(): Unit = _with_temp_paths { paths =>
    val catalogfile = paths.cwd.resolve("runtime-catalog.yaml")
    _write(catalogfile, _catalog_text)
    _write(
      paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"runtime:\n  catalog:\n    url: $catalogfile\n"
    )
    val launcher = new CncfLauncher(
      paths,
      FakeResolver(),
      FakeInvoker(),
      environment = Map("CNCF_LAUNCHER_DEV_DELEGATED" -> "1")
    )

    val code = launcher.run(Vector(
      "--runtime",
      "0.2.0",
      "install-cli",
      "sanpomap",
      "--project-dev",
      "."
    ))

    _assert_equals(code, 0)
    val script = Files.readString(paths.home.resolve("bin").resolve("sanpomap-dev"))
    script.contains("runtime_version='0.2.0'") shouldBe true
    script.contains("runtime_dev_dir=''") shouldBe true
  }

  def installCliPinsDevelopmentRuntimeWithoutCatalog(): Unit = _with_temp_paths { paths =>
    val runtimeproject = paths.cwd.resolve("../cncf-runtime").normalize
    val georesolver = paths.cwd.resolve("../textus-georesolver").normalize
    val toolchainrunner = paths.cwd.resolve("../textus-toolchain-runner").normalize
    _write(runtimeproject.resolve("build.sbt"), "ThisBuild / version := \"0.5.1-SNAPSHOT\"\n")
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("0.5.1-SNAPSHOT", Vector("0.5.1-SNAPSHOT")))
    _write(georesolver.resolve("project.yaml"), _project_yaml("0.5.0", Vector("0.5.0")))
    _write(toolchainrunner.resolve("project.yaml"), _project_yaml("0.5.0", Vector("0.5.0")))
    val launcher = new CncfLauncher(
      paths,
      FakeResolver(),
      FakeInvoker(),
      environment = Map("CNCF_LAUNCHER_DEV_DELEGATED" -> "1")
    )

    val code = launcher.run(Vector(
      "--runtime-dev-dir", runtimeproject.toString,
      "install-cli",
      "sanpomap",
      "--project-dev", ".",
      "--component-dev-dir", georesolver.toString,
      "--component-dev-dir", toolchainrunner.toString
    ))

    _assert_equals(code, 0)
    val script = Files.readString(paths.home.resolve("bin").resolve("sanpomap-dev"))
    script.contains("runtime_version=''") shouldBe true
    script.contains(s"runtime_dev_dir='${runtimeproject.toAbsolutePath.normalize}'") shouldBe true
    script.contains(s"'${georesolver.toAbsolutePath.normalize}'") shouldBe true
    script.contains(s"'${toolchainrunner.toAbsolutePath.normalize}'") shouldBe true
  }

  def installCliRejectsIncompatibleDevelopmentRuntime(): Unit = _with_temp_paths { paths =>
    val runtimeproject = paths.cwd.resolve("../cncf-runtime").normalize
    _write(runtimeproject.resolve("build.sbt"), "ThisBuild / version := \"0.5.1-SNAPSHOT\"\n")
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("0.5.2", Vector("0.5.2")))
    val launcher = new CncfLauncher(
      paths,
      FakeResolver(),
      FakeInvoker(),
      environment = Map("CNCF_LAUNCHER_DEV_DELEGATED" -> "1")
    )

    val failure = try {
      launcher.run(Vector(
        "--runtime-dev-dir", runtimeproject.toString,
        "install-cli",
        "sanpomap",
        "--project-dev", "."
      ))
      None
    } catch {
      case e: CncfException => Some(e)
    }

    failure.map(_.getMessage) shouldBe Some("CNCF runtime 0.5.1-SNAPSHOT is not compatible with component requirements: main-target")
    Files.exists(paths.home.resolve("bin").resolve("sanpomap-dev")) shouldBe false
  }

  def textusControlCenterRegistrationLifecycle(): Unit = _with_temp_paths { paths =>
    Given("a CNCF launcher with opt-in Textus Control Center registration")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """textus-control-center:
        |  registration:
        |    enabled: true
        |    endpoint: https://admin.example.test/rest/v1/org-simplemodeling-textus-control-center/subsystem-inventory
        |    token-env: TEXTUS_ADMIN_REGISTRATION_TOKEN
        |    timeout: 2s
        |    heartbeat-interval: 30s
        |    host-label: acceptance
        |    base-url: https://subsystem.example.test
        |""".stripMargin)
    _write(paths.cwd.resolve("project.yaml"),
      """project:
        |  name: textus-current
        |  component:
        |    name: current-component
        |""".stripMargin)
    val reporter = FakeCncfTextusControlCenterRegistrationReporter()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(
      paths,
      FakeResolver(),
      invoker,
      environment = Map("TEXTUS_ADMIN_REGISTRATION_TOKEN" -> "secret-token"),
      registrationreporter = reporter
    )

    When("the target-first canonical server command completes")
    val targetfirstcode = launcher.run(Vector("textus-registration:0.1.0", "server"))

    Then("the launcher reports the resolved target, version, and only the selected credential")
    _assert_equals(targetfirstcode, 0)
    _assert_equals(reporter.starts.size, 1)
    _assert_equals(reporter.closes, 1)
    _assert_equals(reporter.starts.head._1.target, "textus-registration")
    _assert_equals(reporter.starts.head._1.executionMode, "repository")
    _assert_equals(reporter.starts.head._1.developmentDirectory, None)
    _assert_equals(reporter.starts.head._1.subsystemName, Some("textus-registration"))
    _assert_equals(reporter.starts.head._1.subsystemVersion, Some("0.1.0"))
    _assert_equals(reporter.starts.head._2, Some("secret-token"))
    invoker.lastArgs.indexOf("server") should be > 0

    When("the current-project canonical server command completes")
    val currentprojectcode = launcher.run(Vector("server", "--textus.server.port=18014"))

    Then("it reports the current component development directory through the same lifecycle")
    _assert_equals(currentprojectcode, 0)
    _assert_equals(reporter.starts.size, 2)
    _assert_equals(reporter.closes, 2)
    _assert_equals(reporter.starts(1)._1.target, "current-component")
    _assert_equals(reporter.starts(1)._1.artifactId, Some("textus-current"))
    _assert_equals(reporter.starts(1)._1.executionMode, "development")
    _assert_equals(reporter.starts(1)._1.developmentDirectory, Some(paths.cwd.toAbsolutePath.normalize.toString))
    _assert_equals(reporter.starts(1)._1.subsystemName, Some("current-component"))

    Given("a canonical CAR project identity without legacy project or component names")
    _write(paths.cwd.resolve("project.yaml"),
      """project:
        |  namespace: org.simplemodeling.textus
        |  id: CbdSupport
        |  kind: car
        |packaging:
        |  kind: car
        |""".stripMargin)

    When("the canonical current-project server command completes")
    val canonicalprojectcode = launcher.run(Vector("server", "--textus.server.port=18015"))

    Then("it reports the canonical component id and namespace-derived artifact identity")
    _assert_equals(canonicalprojectcode, 0)
    _assert_equals(reporter.starts.size, 3)
    _assert_equals(reporter.closes, 3)
    _assert_equals(reporter.starts(2)._1.target, "CbdSupport")
    _assert_equals(reporter.starts(2)._1.artifactId, Some("textus-cbd-support"))
    _assert_equals(reporter.starts(2)._1.executionMode, "development")
    _assert_equals(reporter.starts(2)._1.developmentDirectory, Some(paths.cwd.toAbsolutePath.normalize.toString))
    _assert_equals(reporter.starts(2)._1.subsystemName, Some("CbdSupport"))

    And("registration setup failure does not prevent canonical server startup")
    val outageinvoker = FakeInvoker()
    val outage = new CncfTextusControlCenterRegistrationOutageReporter
    val outagelauncher = new CncfLauncher(
      paths,
      FakeResolver(),
      outageinvoker,
      environment = Map("TEXTUS_ADMIN_REGISTRATION_TOKEN" -> "secret-token"),
      registrationreporter = outage
    )
    val outagecode = outagelauncher.run(Vector("textus-registration:0.1.0", "server"))
    _assert_equals(outagecode, 0)
    outageinvoker.lastArgs.indexOf("server") should be > 0

    When("a supervisor-created server command supplies its opaque instance identity")
    val correlatedinstanceid = "30303030-3030-4030-8030-303030303030"
    val correlatedcode = launcher.run(Vector("server", s"--textus.control-center.registration-instance-id=$correlatedinstanceid"))

    Then("the launcher reuses that identity for registration while withholding the internal argument from the runtime")
    _assert_equals(correlatedcode, 0)
    _assert_equals(reporter.starts.size, 4)
    _assert_equals(reporter.starts.last._1.instanceId, correlatedinstanceid)
    invoker.lastArgs should not contain s"--textus.control-center.registration-instance-id=$correlatedinstanceid"
  }

  def controlCenterConfigAppliesOnlyToCurrentProject(): Unit = _with_temp_paths { paths =>
    Given("a Control Center development checkout and its private standalone server configuration")
    _write(paths.cwd.resolve("project.yaml"),
      """project:
        |  namespace: "org.simplemodeling.textus"
        |  id: "ControlCenter"
        |  title: "Textus Control Center"
        |  kind: car
        |  component:
        |    displayName: "Textus Control Center"
        |    version: "0.1.0-SNAPSHOT"
        |""".stripMargin)
    val configfile = paths.cncfHome.resolve("textus-control-center").resolve("server-config.yaml")
    _write(configfile, "textus: {}\n")
    val currentinvoker = FakeInvoker()
    val targetinvoker = FakeInvoker()

    When("the current project and an explicit repository target are started from the same directory")
    new CncfLauncher(paths, FakeResolver(), currentinvoker).run(Vector(".", "server"))
    new CncfLauncher(paths, FakeResolver(), targetinvoker).run(Vector("other-component:0.1.0", "server"))

    Then("only the current Control Center server receives the private standalone configuration")
    currentinvoker.lastArgs.exists(_.contains(configfile.toString)) shouldBe true
    targetinvoker.lastArgs.exists(_.contains(configfile.toString)) shouldBe false
  }

  def standaloneControlCenterLocatorLifecycle(): Unit = _with_temp_paths { paths =>
    Given("a machine-local standalone Control Center locator and owner-only launcher token")
    _write(paths.cwd.resolve("project.yaml"),
      """project:
        |  namespace: org.simplemodeling.textus
        |  id: ControlCenter
        |  name: textus-control-center
        |  kind: car
        |""".stripMargin)
    val root = paths.cncfHome.resolve("textus-control-center")
    val token = root.resolve("credentials").resolve("launcher-registration.token")
    _write(token, "standalone-token\n")
    Files.setPosixFilePermissions(token, java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ, java.nio.file.attribute.PosixFilePermission.OWNER_WRITE))
    val locator = root.resolve("standalone-locator.yaml")
    _write(locator,
      """schemaVersion: 1
        |profile: standalone
        |scopeId: scope-test
        |installationId: standalone-test
        |endpoint: http://127.0.0.1:18013/rest/v1/org-simplemodeling-textus-control-center/subsystem-inventory
        |credentialRef: credentials/launcher-registration.token
        |timeout: 2s
        |heartbeatInterval: 30s
        |hostLabel: standalone-test
        |""".stripMargin)
    val reporter = FakeCncfTextusControlCenterRegistrationReporter()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker, registrationreporter = reporter)

    When("both canonical CNCF server forms run without inline registration configuration")
    val currentprojectcode = launcher.run(Vector("server", "--textus.server.port=18014"))
    val targetfirstcode = launcher.run(Vector("textus-registration:0.1.0", "server"))

    Then("the intrinsic Control Center does not self-register while an ordinary target uses the locator credential")
    _assert_equals(currentprojectcode, 0)
    _assert_equals(targetfirstcode, 0)
    _assert_equals(reporter.starts.size, 1)
    reporter.starts.map(_._2) shouldBe Vector(Some("standalone-token"))
    _assert_equals(reporter.closes, 1)

    When("the locator advertises the legacy inventory route")
    _write(locator, Files.readString(locator).replace(
      "/rest/v1/org-simplemodeling-textus-control-center/subsystem-inventory",
      "/rest/v1/textus-control-center/subsystem-inventory"
    ))

    Then("the legacy locator route is rejected without compatibility fallback")
    CncfTextusControlCenterStandaloneLocator.resolve(paths) shouldBe None

    Given("the canonical locator route is restored for the following credential scenario")
    _write(locator, Files.readString(locator).replace(
      "/rest/v1/textus-control-center/subsystem-inventory",
      "/rest/v1/org-simplemodeling-textus-control-center/subsystem-inventory"
    ))

    When("the shared credential is no longer owner-readable and owner-writable only")
    Files.setPosixFilePermissions(token, java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ))
    val invalidlocatorcode = launcher.run(Vector("server"))

    Then("the invalid locator is ignored without changing server startup")
    _assert_equals(invalidlocatorcode, 0)
    _assert_equals(reporter.starts.size, 1)

    When("an explicit registration disable is configured")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """textus-control-center:
        |  registration:
        |    enabled: false
        |""".stripMargin)
    val disabledlauncher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), registrationreporter = reporter)
    val disabledcode = disabledlauncher.run(Vector("server"))

    Then("the machine locator is not used")
    _assert_equals(disabledcode, 0)
    _assert_equals(reporter.starts.size, 1)
  }

  def textusControlCenterRegistrationHttpLifecycle(): Unit = {
    Given("a reachable Textus Control Center automatic REST endpoint")
    val requests = new ConcurrentLinkedQueue[(String, String)]()
    val rejectnextheartbeat = new java.util.concurrent.atomic.AtomicBoolean(false)
    val rejectnextregistration = new java.util.concurrent.atomic.AtomicBoolean(false)
    val baseurlpropertykey = "textus.server.bound-base-url"
    val applicationpathpropertykey = "textus.server.bound-application-path"
    val snapshotpropertykey = "textus.server.bound-snapshot"
    val previousbaseurl = sys.props.get(baseurlpropertykey)
    val previousapplicationpath = sys.props.get(applicationpathpropertykey)
    val previoussnapshot = sys.props.get(snapshotpropertykey)
    def _publish_snapshot_(baseurl: String, applicationpath: String = ""): Unit =
      sys.props.update(snapshotpropertykey, s"v1\n$baseurl\n$applicationpath")
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        val path = exchange.getRequestURI.toString
        requests.add(path -> exchange.getRequestHeaders.getFirst("Authorization"))
        val status =
          if (path.contains("register-subsystem") && rejectnextregistration.compareAndSet(true, false)) 503
          else if (path.contains("heartbeat-subsystem") && rejectnextheartbeat.compareAndSet(true, false)) 503
          else 204
        exchange.sendResponseHeaders(status, -1)
        exchange.close()
      }
    })
    server.start()
    try {
      sys.props.update(applicationpathpropertykey, "/web")
      sys.props.update(baseurlpropertykey, "http://127.0.0.1:38000")
      _publish_snapshot_("http://127.0.0.1:38000", "/web")
      val config = CncfTextusControlCenterRegistrationConfig(
        endpoint = s"http://127.0.0.1:${server.getAddress.getPort}/rest/v1/org-simplemodeling-textus-control-center/subsystem-inventory",
        tokenEnv = "TEXTUS_ADMIN_REGISTRATION_TOKEN",
        timeout = java.time.Duration.ofSeconds(1),
        heartbeatInterval = java.time.Duration.ofSeconds(30),
        hostLabel = "acceptance",
        baseUrl = "https://subsystem.example.test"
      )
      val report = CncfTextusControlCenterRegistrationReport(
        instanceId = "cncf-registration-http-spec",
        target = "textus-registration",
        artifactId = Some("textus-registration"),
        executionMode = "repository",
        developmentDirectory = None,
        subsystemName = Some("textus-registration"),
        subsystemVersion = Some("0.1.0"),
        runtimeVersion = "0.5.0",
        startedAt = java.time.Instant.parse("2026-07-18T00:00:00Z")
      )

      When("the reporter starts and closes one server registration session")
      val session = CncfTextusControlCenterRegistrationReporter.System.start(config, report, Some("test-token"))
      session.close()
      session.close()

      Then("it sends one register, an immediate running heartbeat, and one deregister operation with the configured bearer credential")
      _assert_equals(requests.size, 3)
      requests.iterator.asScala.map(_._1).exists(_.contains("register-subsystem")) shouldBe true
      requests.iterator.asScala.map(_._1).exists(_.contains("heartbeat-subsystem")) shouldBe true
      requests.iterator.asScala.map(_._1).exists(_.contains("deregister-subsystem")) shouldBe true
      requests.iterator.asScala.forall(_._2 == "Bearer test-token") shouldBe true
      requests.iterator.asScala.forall { case (path, _) => path.contains("instanceId=cncf-registration-http-spec") } shouldBe true
      requests.iterator.asScala.forall { case (path, _) => path.contains("artifactId=textus-registration") } shouldBe true
      requests.iterator.asScala.forall { case (path, _) => path.contains("?protocolVersion=1&instanceId=") } shouldBe true
      requests.iterator.asScala.forall { case (path, _) => path.contains("applicationUrl=https%3A%2F%2Fsubsystem.example.test%2Fweb") } shouldBe true

      Given("an explicit public base and an explicit default application path")
      requests.clear()
      sys.props.update(applicationpathpropertykey, "/web/portal")
      _publish_snapshot_("http://127.0.0.1:38000", "/web/portal")

      When("the ready framework snapshot activates the registration")
      val explicitpathsession = CncfTextusControlCenterRegistrationReporter.System.start(config, report, Some("test-token"))
      explicitpathsession.close()

      Then("the registration preserves its public authority while composing the explicit application path")
      _assert_equals(requests.size, 3)
      requests.iterator.asScala.forall(_._1.contains("baseUrl=https%3A%2F%2Fsubsystem.example.test")) shouldBe true
      requests.iterator.asScala.forall(_._1.contains("applicationUrl=https%3A%2F%2Fsubsystem.example.test%2Fweb%2Fportal")) shouldBe true

      Given("a ready server with no selected application path")
      requests.clear()
      sys.props.remove(applicationpathpropertykey)
      _publish_snapshot_("http://127.0.0.1:38000")

      When("the public-base registration activates and closes")
      val noappsession = CncfTextusControlCenterRegistrationReporter.System.start(config, report, Some("test-token"))
      noappsession.close()

      Then("the public registration base is retained while applicationUrl is omitted")
      _assert_equals(requests.size, 3)
      requests.iterator.asScala.forall(_._1.contains("baseUrl=https%3A%2F%2Fsubsystem.example.test")) shouldBe true
      requests.iterator.asScala.forall { case (path, _) => !path.contains("applicationUrl=") } shouldBe true

      Given("an invalid configured public base path")
      requests.clear()

      When("the pending reporter sees ready framework state")
      val invalidbasesession = CncfTextusControlCenterRegistrationReporter.System.start(config.copy(baseUrl = "https://subsystem.example.test/prefix"), report, Some("test-token"))
      invalidbasesession.close()

      Then("registration remains best-effort isolated and does not publish a prefixed application URL")
      _assert_equals(requests.size, 0)

      Given("an incomplete atomic snapshot while legacy values appear complete")
      requests.clear()
      sys.props.update(baseurlpropertykey, "http://127.0.0.1:38001")
      sys.props.update(applicationpathpropertykey, "/web/legacy")
      sys.props.update(snapshotpropertykey, "v1\nhttp://127.0.0.1:38001")

      When("the reporter cannot parse the incomplete readiness generation")
      val incompletesession = CncfTextusControlCenterRegistrationReporter.System.start(config.copy(baseUrl = ""), report, Some("test-token"))
      incompletesession.close()

      Then("registration fails closed instead of combining legacy readiness properties")
      _assert_equals(requests.size, 0)

      Given("registration without an explicit public base URL")
      requests.clear()
      sys.props.remove(baseurlpropertykey)
      sys.props.remove(applicationpathpropertykey)
      sys.props.remove(snapshotpropertykey)

      When("CNCF publishes the endpoint after the server has bound")
      val dynamicsession = CncfTextusControlCenterRegistrationReporter.System.start(config.copy(baseUrl = ""), report, Some("test-token"))
      sys.props.update(applicationpathpropertykey, "/web")
      sys.props.update(baseurlpropertykey, "http://127.0.0.1:38000")
      _publish_snapshot_("http://127.0.0.1:38000", "/web")
      val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2)
      while (requests.size < 1 && System.nanoTime() < deadline)
        Thread.sleep(10L)
      dynamicsession.close()
      val closedeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2)
      while (requests.size < 3 && System.nanoTime() < closedeadline)
        Thread.sleep(10L)

      Then("registration uses the bound endpoint and exact encoded application endpoint rather than guessing either")
      _assert_equals(requests.size, 3)
      requests.iterator.asScala.forall(_._1.contains("baseUrl=http%3A%2F%2F127.0.0.1%3A38000")) shouldBe true
      requests.iterator.asScala.forall(_._1.contains("applicationUrl=http%3A%2F%2F127.0.0.1%3A38000%2Fweb")) shouldBe true
      sys.props.remove(applicationpathpropertykey)
      sys.props.remove(baseurlpropertykey)
      sys.props.remove(snapshotpropertykey)

      Given("an atomic generation A readiness snapshot while legacy properties contain generation B")
      requests.clear()
      sys.props.update(applicationpathpropertykey, "/web/legacy")
      sys.props.update(baseurlpropertykey, "http://127.0.0.1:38011")
      _publish_snapshot_("http://127.0.0.1:38010", "/web/atomic")

      When("the launcher activates from the coherent snapshot")
      val interleavingsession = CncfTextusControlCenterRegistrationReporter.System.start(config.copy(baseUrl = ""), report, Some("test-token"))
      interleavingsession.close()

      Then("register and deregister retain only atomic generation A")
      _assert_equals(requests.size, 3)
      requests.iterator.asScala.forall(_._1.contains("baseUrl=http%3A%2F%2F127.0.0.1%3A38010")) shouldBe true
      requests.iterator.asScala.forall(_._1.contains("applicationUrl=http%3A%2F%2F127.0.0.1%3A38010%2Fweb%2Fatomic")) shouldBe true
      requests.iterator.asScala.forall(path => !path._1.contains("38011") && !path._1.contains("legacy")) shouldBe true

      Given("an initial registration attempt that is temporarily unavailable")
      requests.clear()
      rejectnextregistration.set(true)

      When("the scheduled registration retry reaches Control Center")
      val retrysession = CncfTextusControlCenterRegistrationReporter.System.start(
        config.copy(heartbeatInterval = java.time.Duration.ofMillis(20)),
        report,
        Some("test-token")
      )
      val retrydeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2)
      while ((requests.iterator.asScala.count(_._1.contains("register-subsystem")) < 2 || requests.iterator.asScala.count(_._1.contains("heartbeat-subsystem")) < 1) && System.nanoTime() < retrydeadline)
        Thread.sleep(10L)
      retrysession.close()
      val retryrequests = requests.iterator.asScala.map(_._1).toVector

      Then("a successful retry immediately publishes running before later heartbeat intervals")
      retryrequests.take(3).map { path =>
        if (path.contains("register-subsystem")) "register"
        else if (path.contains("heartbeat-subsystem")) "heartbeat"
        else "other"
      } shouldBe Vector("register", "register", "heartbeat")
      retryrequests.count(_.contains("/register-subsystem?")) shouldBe 2
      retryrequests.count(_.contains("/heartbeat-subsystem?")) should be >= 1
      retryrequests.count(_.contains("/deregister-subsystem?")) shouldBe 1

      Given("a registered server whose heartbeat has one transient communication failure")
      requests.clear()
      rejectnextheartbeat.set(true)
      sys.props.update(applicationpathpropertykey, "/web")
      sys.props.update(baseurlpropertykey, "http://127.0.0.1:38000")
      _publish_snapshot_("http://127.0.0.1:38000", "/web")

      When("the following heartbeat interval reaches Control Center again")
      val recoveringsession = CncfTextusControlCenterRegistrationReporter.System.start(
        config.copy(heartbeatInterval = java.time.Duration.ofMillis(20)),
        report,
        Some("test-token")
      )
      sys.props.update(applicationpathpropertykey, "/web/changed-after-activation")
      sys.props.update(baseurlpropertykey, "http://127.0.0.1:39000")
      _publish_snapshot_("http://127.0.0.1:39000", "/web/changed-after-activation")
      val recoverydeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2)
      while (requests.iterator.asScala.count(_._1.contains("heartbeat-subsystem")) < 2 && System.nanoTime() < recoverydeadline)
        Thread.sleep(10L)
      recoveringsession.close()
      val recoveryrequests = requests.iterator.asScala.map(_._1).toVector

      Then("the launcher retains running state and retries heartbeat without registering as starting again")
      recoveryrequests.count(_.contains("/register-subsystem?")) shouldBe 1
      recoveryrequests.count(_.contains("/heartbeat-subsystem?")) should be >= 2
      recoveryrequests.count(_.contains("/deregister-subsystem?")) shouldBe 1
      recoveryrequests.forall(_.contains("applicationUrl=https%3A%2F%2Fsubsystem.example.test%2Fweb")) shouldBe true
      recoveryrequests.exists(_.contains("changed-after-activation")) shouldBe false
      sys.props.remove(applicationpathpropertykey)
      sys.props.remove(baseurlpropertykey)
      sys.props.remove(snapshotpropertykey)

      And("the previous canonical textus-admin key remains readable during migration")
      val legacyvalues = LauncherConfigParser.parse(
        Path.of("legacy-launcher.yaml"),
        """textus-admin:
          |  registration:
          |    enabled: true
          |    endpoint: https://admin.example.test/inventory
          |    token-env: TOKEN
          |    host-label: legacy
          |""".stripMargin
      )
      CncfTextusControlCenterRegistrationConfig.fromParsed(legacyvalues).map(_.hostLabel) shouldBe Some("legacy")
    } finally {
      previousbaseurl match {
        case Some(value) => sys.props.update(baseurlpropertykey, value)
        case None => sys.props.remove(baseurlpropertykey)
      }
      previousapplicationpath match {
        case Some(value) => sys.props.update(applicationpathpropertykey, value)
        case None => sys.props.remove(applicationpathpropertykey)
      }
      previoussnapshot match {
        case Some(value) => sys.props.update(snapshotpropertykey, value)
        case None => sys.props.remove(snapshotpropertykey)
      }
      server.stop(0)
    }
  }

  def textusControlCenterRegistrationHttpFailureIsolation(): Unit = {
    Given("a Textus Control Center endpoint that rejects a launcher registration")
    val requests = scala.collection.mutable.ArrayBuffer.empty[String]
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        requests += exchange.getRequestURI.toString
        exchange.sendResponseHeaders(401, -1)
        exchange.close()
      }
    })
    server.start()
    val baseurlpropertykey = "textus.server.bound-base-url"
    val applicationpathpropertykey = "textus.server.bound-application-path"
    val snapshotpropertykey = "textus.server.bound-snapshot"
    val previousbaseurl = sys.props.get(baseurlpropertykey)
    val previousapplicationpath = sys.props.get(applicationpathpropertykey)
    val previoussnapshot = sys.props.get(snapshotpropertykey)
    try {
      sys.props.update(baseurlpropertykey, "http://127.0.0.1:38000")
      sys.props.update(applicationpathpropertykey, "/web")
      sys.props.update(snapshotpropertykey, "v1\nhttp://127.0.0.1:38000\n/web")
      val config = CncfTextusControlCenterRegistrationConfig(
        endpoint = s"http://127.0.0.1:${server.getAddress.getPort}/rest/v1/org-simplemodeling-textus-control-center/subsystem-inventory",
        tokenEnv = "TEXTUS_ADMIN_REGISTRATION_TOKEN",
        timeout = java.time.Duration.ofSeconds(1),
        heartbeatInterval = java.time.Duration.ofSeconds(30),
        hostLabel = "acceptance",
        baseUrl = "https://subsystem.example.test"
      )
      val report = CncfTextusControlCenterRegistrationReport(
        instanceId = "cncf-registration-http-failure-spec",
        target = "textus-registration",
        artifactId = Some("textus-registration"),
        executionMode = "repository",
        developmentDirectory = None,
        subsystemName = Some("textus-registration"),
        subsystemVersion = Some("0.1.0"),
        runtimeVersion = "0.5.0",
        startedAt = java.time.Instant.parse("2026-07-18T00:00:00Z")
      )

      When("the reporter receives authorization rejection for registration")
      val session = CncfTextusControlCenterRegistrationReporter.System.start(config, report, Some("rejected-token"))
      session.close()

      Then("it records one bounded registration attempt and never deregisters an unregistered subsystem")
      _assert_equals(requests.size, 1)
      requests.exists(_.contains("register-subsystem")) shouldBe true
      requests.exists(_.contains("deregister-subsystem")) shouldBe false
    } finally {
      previousbaseurl match {
        case Some(value) => sys.props.update(baseurlpropertykey, value)
        case None => sys.props.remove(baseurlpropertykey)
      }
      previousapplicationpath match {
        case Some(value) => sys.props.update(applicationpathpropertykey, value)
        case None => sys.props.remove(applicationpathpropertykey)
      }
      previoussnapshot match {
        case Some(value) => sys.props.update(snapshotpropertykey, value)
        case None => sys.props.remove(snapshotpropertykey)
      }
      server.stop(0)
    }
  }

  def executeTargetFirstDelegatesToRuntime(): Unit = _with_temp_paths { paths =>
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"), "runtime:\n  version: 0.4.12\n")
    val resolver = FakeResolver()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, resolver, invoker)

    val code = launcher.run(Vector("textus-sanpomap:1", "command", "validate-presentation", "--format", "yaml"))

    _assert_equals(code, 0)
    _assert_equals(resolver.resolvedClasspaths, Vector("0.4.12"))
    _assert_equals(invoker.lastArgs, Vector("--textus.component=textus-sanpomap", "--textus.component.version=1", "command", "validate-presentation", "--format", "yaml"))
  }

  def launcherHomeEnvironmentPropagatesToRuntime(): Unit = _with_temp_paths { paths =>
    Given("an isolated HOME and a different JVM user.home")
    val isolatedhome = paths.cwd.resolve("isolated-home").toAbsolutePath.normalize
    val previoushome = sys.props.get("user.home")
    val originalhome = paths.cwd.resolve("original-jvm-home").toAbsolutePath.normalize.toString
    sys.props.update("user.home", originalhome)
    val invoker = FakeInvoker()
    var observedhome = Option.empty[String]
    invoker.onInvoke = () => observedhome = sys.props.get("user.home")
    val launcher = new CncfLauncher(
      paths,
      FakeResolver(),
      invoker,
      environment = Map("HOME" -> isolatedhome.toString)
    )

    try {
      When("the launcher invokes the selected CNCF runtime")
      launcher.run(Vector("textus-sanpomap:1", "command", "validate-presentation")) shouldBe 0

      Then("HOME is visible during runtime invocation and the prior JVM property is restored")
      LauncherPaths.defaultHome(Map("HOME" -> isolatedhome.toString), Map("user.home" -> originalhome)) shouldBe isolatedhome
      val admittedproperties = scala.collection.mutable.Map("user.home" -> originalhome)
      LauncherPaths.admitApplicationHome(Map("HOME" -> isolatedhome.toString), admittedproperties)
      admittedproperties.get("user.home") shouldBe Some(isolatedhome.toString)
      observedhome shouldBe Some(isolatedhome.toString)
      sys.props.get("user.home") shouldBe Some(originalhome)
    } finally {
      previoushome match {
        case Some(value) => sys.props.update("user.home", value)
        case None => sys.props.remove("user.home")
      }
    }
  }

  def canonicalDevelopmentTargetsUseConfiguredRuntime(): Unit = _with_temp_paths { paths =>
    Given("current and external CAR checkouts plus a configured CNCF development runtime")
    val externalcar = paths.cwd.resolve("external-car")
    val configuredruntime = paths.cwd.resolve("configured-runtime")
    val configuredruntimeclasspath = configuredruntime.resolve("target").resolve("classes")
    val configureddependency = paths.cwd.resolve("configured-dependency")
    _write(paths.cwd.resolve("project.yaml"), _component_project_yaml("current-car", "car", "1.0.0-SNAPSHOT"))
    _write(paths.cwd.resolve("build.sbt"), "version := \"1.0.0-SNAPSHOT\"\n")
    _write(externalcar.resolve("project.yaml"), _component_project_yaml("external-car", "car", "1.0.0-SNAPSHOT", 18123))
    _write(externalcar.resolve("build.sbt"), "version := \"1.0.0-SNAPSHOT\"\n")
    _write(externalcar.resolve("conf/cncf/assembly-standalone.yaml"), "textus: {}\n")
    _write(configuredruntime.resolve("build.sbt"), "ThisBuild / version := \"0.5.1-SNAPSHOT\"\n")
    _write(DevSupport.runtimeClasspathFile(configuredruntime), configuredruntimeclasspath.toString)
    _write(
      paths.cwd.resolve(".cncf/launcher.yaml"),
      s"""runtime:
         |  dev-dir: ${configuredruntime.toString}
         |dev:
         |  component-dev-dirs:
         |    - ${configureddependency.getFileName}
         |""".stripMargin
    )
    val resolver = FakeResolver()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, resolver, invoker)

    When("the explicit current target, current-CAR shorthand, and external CAR target start")
    launcher.run(Vector(".", "server")) shouldBe 0
    launcher.run(Vector("server")) shouldBe 0
    launcher.run(Vector(externalcar.toString, "server")) shouldBe 0

    Then("all three search the server target and configured dependencies while using the configured runtime")
    _assert_equals(resolver.resolvedClasspaths, Vector.empty)
    invoker.lastClasspath should contain (configuredruntimeclasspath)
    invoker.lastArgs.takeWhile(_ != "server") should contain (s"--repository-component-dev-dir=${configureddependency.toAbsolutePath.normalize}")
    invoker.lastArgs should contain (s"--repository-component-dev-dir=${externalcar.toAbsolutePath.normalize}")
    invoker.lastArgs should not contain (s"--component-dev-dir=${externalcar.toString}")
    invoker.lastArgs should contain (s"--textus.assembly.descriptor=${externalcar.resolve("conf/cncf/assembly-standalone.yaml")}")
    invoker.lastArgs should contain ("--cncf.server.port=18123")
    val evidence = CncfLocalServerEvidenceStore(paths).latestDevelopmentProfile("external-car").toOption.flatten
    evidence.flatMap(_.developmentDirectory) shouldBe Some(externalcar.toAbsolutePath.normalize.toString)

    When("an external CAR command supplies an explicit Subsystem descriptor")
    launcher.run(Vector(
      externalcar.toString,
      "command",
      s"--textus.subsystem.file=${externalcar.resolve("src/main/car/assembly-descriptor.yaml")}",
      "validate-presentation"
    )) shouldBe 0

    Then("the assembly-driven command searches the target instead of replacing its Subsystem")
    invoker.lastArgs should contain (s"--repository-component-dev-dir=${externalcar.toAbsolutePath.normalize}")
    invoker.lastArgs should not contain (s"--component-dev-dir=${externalcar.toString}")
  }

  def canonicalDevelopmentTargetSelectsCompatibleInstalledRuntime(): Unit = _with_temp_paths { paths =>
    Given("a CAR requiring CNCF 0.5.2-SNAPSHOT while 0.5.1-SNAPSHOT is stored")
    _write(
      paths.cwd.resolve("project.yaml"),
      """project:
        |  name: current-car
        |  kind: car
        |packaging:
        |  kind: car
        |  car:
        |    runtime:
        |      cncf:
        |        minimum: 0.5.2-SNAPSHOT
        |        tested:
        |          - 0.5.2-SNAPSHOT
        |""".stripMargin
    )
    _write(paths.globalVersion, "0.5.1-SNAPSHOT\n")
    _write(paths.runtimeCatalog, _catalog_text)
    _write(paths.runtimeRoot.resolve("0.5.1-SNAPSHOT/classpath.txt"), "runtime-0.5.1\n")
    _write(paths.runtimeRoot.resolve("0.5.2-SNAPSHOT/classpath.txt"), "runtime-0.5.2\n")
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())

    When("the canonical current-project server command starts")
    launcher.run(Vector(".", "server")) shouldBe 0

    Then("the compatible installed SNAPSHOT is selected before runtime invocation")
    resolver.resolvedClasspaths.last shouldBe "0.5.2-SNAPSHOT"
  }

  def canonicalDevelopmentTargetRefreshesStaleRuntimeCatalog(): Unit = _with_temp_paths { paths =>
    Given("a current CAR requiring a version absent from its readable cached catalog")
    val remotecatalog = paths.cwd.resolve("runtime-catalog.yaml")
    val remoteversion =
      """  - version: 0.5.2
        |    channel: stable
        |    status: active
        |    scalaBinaryVersion: "3"
        |    module: org.goldenport:goldenport-cncf_3:0.5.2
        |    publishedAt: 2026-08-01T00:00:00Z
        |""".stripMargin
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("0.5.2", Vector("0.5.2")))
    _write(paths.runtimeCatalog, _catalog_text)
    _write(remotecatalog, _catalog_text.replace("recommended: 0.2.0", "recommended: 0.5.2").replace("latestStable: 0.2.0", "latestStable: 0.5.2") + remoteversion)
    _write(
      paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: $remotecatalog
         |""".stripMargin
    )
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())

    When("the current-project server command selects its runtime")
    launcher.run(Vector(".", "server")) shouldBe 0

    Then("the compatible remote version is selected and persisted in the cache")
    resolver.resolvedClasspaths.last shouldBe "0.5.2"
    RuntimeCatalog.parse(Files.readString(paths.runtimeCatalog)).versions.map(_.version) should contain("0.5.2")
  }

  def canonicalDevelopmentTargetRetainsStaleRuntimeCatalogWhenRefreshFails(): Unit = _with_temp_paths { paths =>
    Given("a current CAR requiring a version absent from its readable cache and an unavailable refresh source")
    _write(paths.cwd.resolve("project.yaml"), _project_yaml("0.5.2", Vector("0.5.2")))
    _write(paths.runtimeCatalog, _catalog_text)
    _write(
      paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("missing-runtime-catalog.yaml")}
         |  cncf:
         |    no-compatible-policy: newest
         |""".stripMargin
    )
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())

    When("the current-project server command permits the newest cached runtime after refresh fails")
    launcher.run(Vector(".", "server")) shouldBe 0

    Then("the cached catalog remains selectable and its contents are preserved")
    resolver.resolvedClasspaths.last shouldBe "0.3.0-SNAPSHOT"
    RuntimeCatalog.parse(Files.readString(paths.runtimeCatalog)).versions.map(_.version) shouldBe
      Vector("0.1.0", "0.2.0", "0.3.0-SNAPSHOT")
  }

  def canonicalDevelopmentTargetUsesExplicitRuntimeVersion(): Unit = _with_temp_paths { paths =>
    Given("an external CAR target, a configured development runtime, and an explicit runtime version")
    val externalcar = paths.cwd.resolve("external-car")
    val configuredruntime = paths.cwd.resolve("configured-runtime")
    _write(externalcar.resolve("project.yaml"), _component_project_yaml("external-car", "car", "1.0.0-SNAPSHOT"))
    _write(configuredruntime.resolve("build.sbt"), "ThisBuild / version := \"9.9.9-SNAPSHOT\"\n")
    _write(DevSupport.runtimeClasspathFile(configuredruntime), configuredruntime.resolve("target/classes").toString)
    _write(paths.cwd.resolve(".cncf/launcher.yaml"),
      s"""runtime:
         |  version: 0.4.12
         |  dev-dir: ${configuredruntime.toString}
         |""".stripMargin)
    val resolver = FakeResolver()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, resolver, invoker)

    When("the external CAR is started with the explicit runtime version")
    launcher.run(Vector("--runtime", "0.4.13", externalcar.toString, "server")) shouldBe 0

    Then("the explicit version wins without changing the CAR server search target")
    resolver.resolvedClasspaths.last shouldBe "0.4.13"
    invoker.lastArgs should contain (s"--repository-component-dev-dir=${externalcar.toAbsolutePath.normalize}")
    invoker.lastClasspath should not contain configuredruntime.resolve("target/classes")
  }

  def canonicalDevelopmentTargetUsesExplicitDevelopmentRuntime(): Unit = _with_temp_paths { paths =>
    Given("an external CAR target and an explicit CNCF development runtime")
    val externalcar = paths.cwd.resolve("external-car")
    val explicitruntime = paths.cwd.resolve("explicit-runtime")
    val explicitruntimeclasspath = explicitruntime.resolve("target").resolve("classes")
    _write(externalcar.resolve("project.yaml"), _component_project_yaml("external-car", "car", "1.0.0-SNAPSHOT"))
    _write(explicitruntime.resolve("build.sbt"), "ThisBuild / version := \"0.5.1-SNAPSHOT\"\n")
    _write(DevSupport.runtimeClasspathFile(explicitruntime), explicitruntimeclasspath.toString)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    When("the CAR is started with the explicit development runtime")
    launcher.run(Vector("--runtime-dev-dir", explicitruntime.toString, externalcar.toString, "server")) shouldBe 0

    Then("only the runtime classpath changes and the CAR remains the server search target")
    invoker.lastClasspath should contain (explicitruntimeclasspath)
    invoker.lastArgs should contain (s"--repository-component-dev-dir=${externalcar.toAbsolutePath.normalize}")
  }

  def canonicalServerRejectsRuntimeCheckoutTarget(): Unit = _with_temp_paths { paths =>
    Given("a CNCF runtime checkout without CAR or SAR packaging metadata")
    val runtimetarget = paths.cwd.resolve("cncf-runtime-checkout")
    _write(runtimetarget.resolve("build.sbt"), "ThisBuild / version := \"0.5.2-SNAPSHOT\"\n")
    _write(runtimetarget.resolve("project.yaml"), "project:\n  name: cloud-native-component-framework\n")
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())

    When("a CNCF runtime checkout is passed as the positional component target")
    val failure = intercept[CncfException] {
      launcher.run(Vector(runtimetarget.toString, "server"))
    }

    Then("it is rejected instead of being reinterpreted as a runtime development override")
    failure.getMessage should include("is not a CAR/SAR development target")
  }

  def deprecatedDevSeparatesDevelopmentTargetFromRuntime(): Unit = _with_temp_paths { paths =>
    Given("a deprecated dev target classpath and a separate CNCF development runtime classpath")
    val explicitruntime = paths.cwd.resolve("explicit-runtime")
    val explicitruntimeclasspath = explicitruntime.resolve("target").resolve("classes")
    val appclasspath = paths.cwd.resolve("target").resolve("classes")
    _write(explicitruntime.resolve("build.sbt"), "ThisBuild / version := \"0.5.1-SNAPSHOT\"\n")
    _write(DevSupport.runtimeClasspathFile(explicitruntime), explicitruntimeclasspath.toString)
    _write(DevSupport.runtimeClasspathFile(paths.cwd), appclasspath.toString)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    When("the deprecated dev server is started with the explicit runtime checkout")
    launcher.run(Vector("dev", "server", "--runtime-dev-dir", explicitruntime.toString)) shouldBe 0

    Then("the invocation contains both independent classpaths")
    invoker.lastClasspath should contain (explicitruntimeclasspath)
    invoker.lastClasspath should contain (appclasspath)
  }

  def serverExecutionDelegatesDefaultPortResolutionToRuntime(): Unit = _with_temp_paths { paths =>
    Given("target-first CAR and SAR server invocations without an explicit server port")
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"), "runtime:\n  version: 0.4.12\n")
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    When("the launcher delegates the CAR invocation to CNCF")
    val carcode = launcher.run(Vector("textus-sanpomap:1", "server"))

    Then("CAR activation is forwarded without a launcher-owned port override")
    _assert_equals(carcode, 0)
    _assert_equals(invoker.lastArgs, Vector("--textus.component=textus-sanpomap", "--textus.component.version=1", "server"))
    invoker.lastArgs.exists(_.startsWith("--textus.server.port=")) shouldBe false
    invoker.lastArgs.exists(_.startsWith("--cncf.server.port=")) shouldBe false

    When("the launcher delegates the SAR invocation to CNCF")
    val sarcode = launcher.run(Vector("textus-platform.sar", "server"))

    Then("SAR activation is forwarded without a launcher-owned port override")
    _assert_equals(sarcode, 0)
    _assert_equals(invoker.lastArgs, Vector("--subsystem-file=textus-platform.sar", "server"))
    invoker.lastArgs.exists(_.startsWith("--textus.server.port=")) shouldBe false
    invoker.lastArgs.exists(_.startsWith("--cncf.server.port=")) shouldBe false
  }

  def runtimeCatalogCommands(): Unit = _with_temp_paths { paths =>
    val catalogfile = paths.cwd.resolve("runtime-catalog.yaml")
    _write(catalogfile, _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: $catalogfile
         |""".stripMargin)
    val launcher = new CncfLauncher(paths, CoursierCncfRuntimeResolver("false"), FakeInvoker())
    launcher.run(Vector("runtime", "refresh"))
    Files.isRegularFile(paths.runtimeCatalog) shouldBe true
    launcher.run(Vector("runtime", "remote", "list"))
    launcher.run(Vector("runtime", "catalog", "show"))
    launcher.run(Vector("runtime", "channels"))
    launcher.run(Vector("runtime", "use", "recommended", "--project"))
    _assert_equals(Files.readString(paths.projectVersion).trim, "recommended")
    launcher.run(Vector("runtime", "current"))
  }

  def runtimeCurrentWarnsWhenCachedRecommendedIsStale(): Unit = _with_temp_paths { paths =>
    val remotecatalog = paths.cwd.resolve("runtime-catalog.yaml")
    _write(paths.runtimeCatalog, _catalog_text)
    _write(remotecatalog, _catalog_text.replace("recommended: 0.2.0", "recommended: 0.3.0-SNAPSHOT"))
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: $remotecatalog
         |""".stripMargin)
    val launcher = new CncfLauncher(paths, CoursierCncfRuntimeResolver("false"), FakeInvoker())

    val (code, stdout, stderr) = _capture_stdout_stderr {
      launcher.run(Vector("runtime", "current"))
    }

    _assert_equals(code, 0)
    _assert_equals(stdout.trim, "0.2.0")
    stderr.contains("cached CNCF runtime catalog resolves recommended to 0.2.0") shouldBe true
    stderr.contains("remote catalog resolves it to 0.3.0-SNAPSHOT") shouldBe true
    stderr.contains("cncf runtime refresh") shouldBe true
  }

  def runtimeDescriptorCommands(): Unit = _with_temp_paths { paths =>
    val catalogfile = paths.cwd.resolve("runtime-catalog.yaml")
    _write(catalogfile, _catalog_text)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: $catalogfile
         |""".stripMargin)
    val launcher = new CncfLauncher(paths, CoursierCncfRuntimeResolver("false"), FakeInvoker())
    val (_, descriptor) = _capture_stdout {
      launcher.run(Vector("runtime", "descriptor", "--format", "yaml"))
    }
    descriptor.contains("runtime: cncf") shouldBe true
    descriptor.contains("version: 0.2.0") shouldBe true
    descriptor.contains("module: org.goldenport:goldenport-cncf_3:0.2.0") shouldBe true
    descriptor.contains("baseProvided:") shouldBe true
    descriptor.contains("org.typelevel:cats-core_3") shouldBe true

    val (_, baseprovided) = _capture_stdout {
      launcher.run(Vector("runtime", "base-provided", "--format=yaml"))
    }
    baseprovided.contains("baseProvided:") shouldBe true
    baseprovided.contains("org.goldenport:goldenport-cncf_3") shouldBe true
  }

  def runtimeDescriptorPrefersRuntimeJarDescriptor(): Unit = _with_temp_paths { paths =>
    val catalogfile = paths.cwd.resolve("runtime-catalog.yaml")
    val jar = paths.cwd.resolve("goldenport-cncf.jar")
    _write(catalogfile, _catalog_text)
    _write_zip(
      jar,
      "META-INF/cncf/runtime.yaml",
      """schemaVersion: 1
        |runtime: cncf
        |version: 0.2.0
        |scalaBinaryVersion: "3"
        |module: org.goldenport:goldenport-cncf_3:0.2.0
        |baseProvided:
        |  - org.goldenport:goldenport-cncf_3
        |  - org.typelevel:spire_3
        |""".stripMargin
    )
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: $catalogfile
         |""".stripMargin)
    val launcher = new CncfLauncher(paths, FakeResolver(Some(Vector(jar))), FakeInvoker())

    val (_, descriptor) = _capture_stdout {
      launcher.run(Vector("runtime", "descriptor"))
    }
    val (_, baseprovided) = _capture_stdout {
      launcher.run(Vector("runtime", "base-provided"))
    }

    descriptor.contains("org.typelevel:spire_3") shouldBe true
    descriptor.contains("org.typelevel:cats-core_3") shouldBe false
    baseprovided.contains("org.typelevel:spire_3") shouldBe true
    baseprovided.contains("org.typelevel:cats-core_3") shouldBe false
  }

  def devConfigCanSelectExecutionProfile(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      """dev:
        |  profile: local-persistent
        |""".stripMargin)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("dev", "server"))

    invoker.lastArgs.exists(_.startsWith("--textus.datastore.sqlite.path=")) shouldBe true
  }

  def devProjectLoadsTargetProjectConfig(): Unit = _with_temp_paths { paths =>
    val project = paths.cwd.resolve("blog")
    val classdir = project.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(project.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    _write(project.resolve("conf").resolve("cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  version: 0.5.0
         |  catalog:
         |    url: ${project.resolve("missing-runtime-catalog.yaml")}
         |dev:
         |  port: 19700
         |""".stripMargin)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    launcher.run(Vector("dev", "server", "--project-dev", "blog"))
    invoker.lastArgs.contains(project.toAbsolutePath.normalize.toString) shouldBe true
    invoker.lastArgs.exists(_.startsWith("--cncf.server.port=")) shouldBe false
  }

  def runtimeCommandDoesNotLoadCncf(): Unit = _with_temp_paths { paths =>
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"), "runtime:\n  version: 0.5.0\n")
    val resolver = FakeResolver()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, resolver, invoker)
    launcher.run(Vector("runtime", "current"))
    _assert_equals(resolver.resolvedClasspaths, Vector.empty)
    _assert_equals(invoker.lastArgs, Vector.empty)
  }

  def latestRuntimeIsConcrete(): Unit = _with_temp_paths { paths =>
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
      s"""runtime:
         |  catalog:
         |    url: ${paths.cwd.resolve("missing-runtime-catalog.yaml")}
         |""".stripMargin)
    val resolver = FakeResolver()
    val launcher = new CncfLauncher(paths, resolver, FakeInvoker())
    launcher.run(Vector("runtime", "current"))
    _assert_equals(resolver.resolvedVersions, Vector(LauncherConfig.DEFAULT_RUNTIME_VERSION))
  }

  def localServerEvidenceProjection(): Unit = _with_temp_paths { paths =>
    Given("a shared launcher evidence record containing a local development directory")
    val evidence = CncfLocalServerEvidenceStore(paths)
    val report = CncfTextusControlCenterRegistrationReport(
      "textus-instance",
      "textus-control-center",
      Some("textus-control-center"),
      "development",
      Some("/private/work/textus-control-center"),
      Some("Textus Control Center"),
      Some("0.1.0-SNAPSHOT"),
      "0.5.0-SNAPSHOT",
      java.time.Instant.parse("2026-07-22T00:00:00Z")
    )
    evidence.started(report, "textus")
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())

    When("the CNCF Launcher projects list and protected-detail evidence")
    val (listcode, listoutput, listerror) = _capture_stdout_stderr(launcher.run(Vector("launcher", "evidence", "list", "--format", "json")))
    val (showcode, showoutput, showerror) = _capture_stdout_stderr(launcher.run(Vector("launcher", "evidence", "show", "textus-instance", "--format=json")))

    Then("the common list omits the path while detail retains the selected local record")
    listcode shouldBe 0
    listerror shouldBe empty
    listoutput should include("cncf.launcher.evidence-projection.v1")
    listoutput should include("textus-instance")
    listoutput should not include "/private/work/textus-control-center"
    showcode shouldBe 0
    showerror shouldBe empty
    showoutput should include("/private/work/textus-control-center")
    CncfCommandParser.parse(Vector("launcher", "evidence", "list", "--format", "json")) shouldBe CncfCommand.Evidence.List("json")
  }

  def localServerEvidenceRetentionAndRecovery(): Unit = _with_temp_paths { paths =>
    import CncfLocalServerEvidenceSnapshot.given

    Given("stale, bounded, malformed, and independently written shared evidence")
    val now = java.time.Instant.now()
    val stale = CncfLocalServerEvidenceEntry("cncf", "stale", "stale", Some("stale"), "development", None, None, None, "0.5.0", now.minus(CncfLocalServerEvidenceStore.Retention).minusSeconds(1), now.minus(CncfLocalServerEvidenceStore.Retention).minusSeconds(1), Some(now.minus(CncfLocalServerEvidenceStore.Retention).minusSeconds(1)))
    _write(paths.serverEvidence, CncfLocalServerEvidenceSnapshot(CncfLocalServerEvidenceSnapshot.Schema, Vector(stale)).asJson.noSpaces)
    val store = CncfLocalServerEvidenceStore(paths)

    When("a canonical Launcher update follows the retention boundary")
    store.started(_local_evidence_report("fresh"), "cncf")
    val retained = store.listProjection().toOption.get.entries.map(_.instanceId)

    And("a bounded store receives one entry beyond its maximum")
    val bounded = Vector.tabulate(CncfLocalServerEvidenceStore.MaximumEntries)(index =>
      CncfLocalServerEvidenceEntry("cncf", s"bounded-$index", s"bounded-$index", Some(s"bounded-$index"), "development", None, None, None, "0.5.0", now, now, None)
    )
    _write(paths.serverEvidence, CncfLocalServerEvidenceSnapshot(CncfLocalServerEvidenceSnapshot.Schema, bounded).asJson.noSpaces)
    store.started(_local_evidence_report("bounded-fresh"), "cncf")
    val capped = store.listProjection().toOption.get.entries.map(_.instanceId)

    And("the next update encounters malformed content before independent writer updates")
    _write(paths.serverEvidence, "{malformed")
    store.started(_local_evidence_report("recovered"), "cncf")
    CncfLocalServerEvidenceStore(paths).started(_local_evidence_report("textus-writer"), "textus")
    val recovered = store.listProjection().toOption.get.entries.map(_.instanceId)
    val files = Files.list(paths.serverEvidence.getParent)
    val recovery = try files.iterator.asScala.toVector.find(_.getFileName.toString.startsWith("server-evidence.recovery-")) finally files.close()

    Then("stale entries are pruned, mutation order enforces the cap, malformed bytes are retained, and writers preserve each other")
    retained shouldBe Vector("fresh")
    capped should have size CncfLocalServerEvidenceStore.MaximumEntries
    capped should not contain "bounded-0"
    capped should contain("bounded-fresh")
    recovered should contain allOf ("recovered", "textus-writer")
    recovery.map(Files.readString) shouldBe Some("{malformed")
  }
}
