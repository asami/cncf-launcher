package cncf.launcher

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}

/*
 * @since   May. 17, 2026
 * @version Jun.  8, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfLauncherSpec {
  def main(args: Array[String]): Unit = {
    val spec = new CncfLauncherSpec
    spec.parser()
    spec.launcherVersion()
    spec.configMerge()
    spec.launcherDevDirDelegatesToDevelopmentLauncher()
    spec.configSupportsAdditionalRdfNamespaces()
    spec.configFileOptionOverridesProjectConfig()
    spec.launcherConfigSupportsPropertiesAndConfFiles()
    spec.defaultRuntimeConfigFilesAreForwarded()
    spec.configFileProjectDevSurvivesTargetCwdSwitch()
    spec.cncfConfigOptionIsForwardedToRuntime()
    spec.configFileOptionRequiresExistingFile()
    spec.runtimeVersionPrecedence()
    spec.runtimeUseWritesExpectedFiles()
    spec.runtimeUseAutoSelectsProjectWhenCncfDirectoryExists()
    spec.runtimeCatalogParseAndSelectorResolution()
    spec.runtimeCatalogCommands()
    spec.runtimeDescriptorCommands()
    spec.runtimeDescriptorPrefersRuntimeJarDescriptor()
    spec.devParser()
    spec.devServerRewritesToCncfArgs()
    spec.devServerParserSupportsProcessManagementOptions()
    spec.devServerWritesStateDuringInvocation()
    spec.devServerRejectsAliveExistingState()
    spec.devServerStopExistingBeforeInvocation()
    spec.devStopStopsExistingWithoutInvocation()
    spec.devStopUsesRecordedPortWhenPortIsOmitted()
    spec.devServerForceStopsAfterGracefulFailure()
    spec.devServerRequiresForceForAmbiguousAliveState()
    spec.devServerRejectsPidReuseWithoutForce()
    spec.devServerProfileAddsLocalPersistentSqliteArgs()
    spec.devConfigCanSelectExecutionProfile()
    spec.devServerUsesRuntimeDevelopmentDirectory()
    spec.devServerUsesRuntimeDevelopmentCatalogForSelection()
    spec.devCommandPassesRuntimeLeadingArgs()
    spec.devCommandKeepsSampleMainClassValueAsRuntimeArg()
    spec.devCommandCanDisableProjectClasspath()
    spec.devCommandCanDisableProjectComponentDevDir()
    spec.devCommandDoesNotAutoActivateComponentDirArtifacts()
    spec.devTargetOptionsAreMutuallyExclusive()
    spec.devNameTargetUsesLocalSnapshotOnly()
    spec.devNameTargetSnapshotBypassesReleaseCatalog()
    spec.devNameTargetUsesReleaseRepositories()
    spec.devServerEmulationRewritesToCncfArgs()
    spec.devProjectLoadsTargetProjectConfig()
    spec.devHelpExplainsResolutionModel()
    spec.devCheckReportsMainTargetAndDependencyResolution()
    spec.devCheckReportsDevServerState()
    spec.devCheckTreatsMissingMainTargetClasspathAsWarning()
    spec.devCheckTreatsMissingDependencyClasspathAsError()
    spec.devServerAutoGeneratesMainTargetClasspath()
    spec.devServerReportsMainTargetClasspathExportFailure()
    spec.devUsesCurrentCompatibleRuntimeByDefault()
    spec.devCanSelectLatestTestedRuntime()
    spec.devCanSelectLatestCompatibleRuntime()
    spec.devCanSelectNewestCompatibleRuntime()
    spec.devParsesInlineRuntimeRequirementLists()
    spec.devSelectsCommonRuntimeAcrossProjectAndDependency()
    spec.devRuntimeConflictDefaultsToError()
    spec.devRuntimeConflictCanUseNewestPolicy()
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

  def launcherVersion(): Unit = _with_temp_paths { paths =>
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    val (code, output) = _capture_stdout {
      launcher.run(Vector("--version"))
    }
    _assert_equals(code, 0)
    _assert_equals(output.trim, s"${LauncherBuildInfo.name} ${LauncherBuildInfo.version}")
    _assert_equals(CncfCommandParser.parse(Vector("version")), CncfCommand.Version)
    _assert_equals(CncfCommandParser.parse(Vector("launcher", "version")), CncfCommand.Version)
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
    assert(config.carRepositories.head == "https://local.example/car")
    assert(config.carRepositories(1) == "https://project.example/car")
    assert(config.carRepositories(2) == "https://global.example/car")
    assert(config.sarRepositories.head == "https://global.example/sar")
    assert(config.carRepositories.contains(paths.localCarRepository.toString))
    assert(config.sarRepositories.contains(paths.localSarRepository.toString))
    assert(config.carRepositories.contains(paths.cacheCarRepository.toString))
    assert(config.sarRepositories.contains(paths.cacheSarRepository.toString))
    assert(config.carRepositories.contains("https://www.simplemodeling.org/repository/car"))
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
    assert(config.textusKnowledgeRdfNamespaces.contains("acme" -> "https://example.com/acme"))
    assert(config.textusKnowledgeRdfNamespaces.contains("sm" -> "https://www.simplemodeling.org"))

    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    launcher.run(Vector("dev", "server"))
    assert(invoker.lastArgs.contains("--textus.knowledge.rdf.node-prefix=acme"))
    assert(invoker.lastArgs.contains("--textus.knowledge.rdf.namespace-prefixes=acme,sm"))
    assert(invoker.lastArgs.contains("--textus.knowledge.rdf.namespaces.acme=https://example.com/acme"))
    assert(invoker.lastArgs.contains("--textus.knowledge.rdf.namespaces.sm=https://www.simplemodeling.org"))
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
    assert(!invoker.lastArgs.contains("--config"))
    assert(!invoker.lastArgs.contains("etc/debug.yaml"))
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
    assert(invoker.lastArgs.exists(_.startsWith("--cncf.config.files=")))
    assert(invoker.lastArgs.exists(_.contains("runtime-debug.yaml")))
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
    assert(!invoker.lastArgs.contains("--cncf-config"))
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

    assert(invoker.lastArgs.contains(project.toAbsolutePath.normalize.toString))
    assert(!invoker.lastArgs.contains("--config"))
    assert(!invoker.lastArgs.contains("etc/debug.yaml"))
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

    assert(invoker.lastArgs.exists(_.startsWith("--cncf.config.files=")))
    assert(invoker.lastArgs.exists(_.contains("runtime-debug.yaml")))
    assert(!invoker.lastArgs.contains("--cncf-config"))
    assert(!invoker.lastArgs.contains("etc/runtime-debug.yaml"))
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
    assert(failed)
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
    _assert_equals(catalog.baseProvided, Vector("org.goldenport:goldenport-cncf_3", "org.typelevel:cats-core_3"))
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
    _write(paths.cwd.resolve(".cncf").resolve("launcher.yaml"),
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
    assert(descriptor.contains("runtime: cncf"))
    assert(descriptor.contains("version: 0.2.0"))
    assert(descriptor.contains("module: org.goldenport:goldenport-cncf_3:0.2.0"))
    assert(descriptor.contains("baseProvided:"))
    assert(descriptor.contains("org.typelevel:cats-core_3"))

    val (_, baseprovided) = _capture_stdout {
      launcher.run(Vector("runtime", "base-provided", "--format=yaml"))
    }
    assert(baseprovided.contains("baseProvided:"))
    assert(baseprovided.contains("org.goldenport:goldenport-cncf_3"))
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

    assert(descriptor.contains("org.typelevel:spire_3"))
    assert(!descriptor.contains("org.typelevel:cats-core_3"))
    assert(baseprovided.contains("org.typelevel:spire_3"))
    assert(!baseprovided.contains("org.typelevel:cats-core_3"))
  }

  def devParser(): Unit = {
    val server = CncfCommandParser.parse(Vector(
      "--runtime", "0.4.7",
      "--runtime-dev-dir", "/tmp/cncf",
      "dev", "server",
      "--project-dev", "/tmp/blog",
      "--port", "19599",
      "--component-dev-dir", "../account",
      "--repository-dir", "repository.d"
    )).asInstanceOf[CncfCommand.Dev.Server]
    _assert_equals(server.options.runtimeVersion, Some("0.4.7"))
    _assert_equals(server.options.runtimeSelectionPolicy, None)
    _assert_equals(server.options.target, CncfCommand.DevTarget.ProjectDev(Some("/tmp/blog")))
    _assert_equals(server.options.runtimeDevDir, Some("/tmp/cncf"))
    _assert_equals(server.options.port, Some("19599"))
    _assert_equals(server.options.executionProfile, None)
    _assert_equals(server.options.componentDevDirs, Vector("../account"))
    _assert_equals(server.options.runtimeArgs, Vector("--repository-dir", "repository.d"))

    val command = CncfCommandParser.parse(Vector("dev", "command", "blog.post.search", "limit=10"))
      .asInstanceOf[CncfCommand.Dev.Command]
    _assert_equals(command.operation, "blog.post.search")
    _assert_equals(command.args, Vector("limit=10"))

    val selection = CncfCommandParser.parse(Vector("--runtime-selection=tested-latest", "dev", "server"))
      .asInstanceOf[CncfCommand.Dev.Server]
    _assert_equals(selection.options.runtimeSelectionPolicy, Some(RuntimeSelectionPolicy.TestedLatest))
    val latestselection = CncfCommandParser.parse(Vector("--runtime-selection=latest", "dev", "server"))
      .asInstanceOf[CncfCommand.Dev.Server]
    _assert_equals(latestselection.options.runtimeSelectionPolicy, Some(RuntimeSelectionPolicy.LatestCompatible))

    val emulation = CncfCommandParser.parse(Vector("dev", "server-emulation", "blog.component.search"))
      .asInstanceOf[CncfCommand.Dev.ServerEmulation]
    _assert_equals(emulation.args, Vector("blog.component.search"))
    val profile = CncfCommandParser.parse(Vector("dev", "server", "--profile", "local-persistent"))
      .asInstanceOf[CncfCommand.Dev.Server]
    _assert_equals(profile.options.executionProfile, Some(CncfCommand.DevExecutionProfile.LocalPersistent))
  }

  def devServerParserSupportsProcessManagementOptions(): Unit = {
    val server = CncfCommandParser.parse(Vector("dev", "server", "--stop-existing", "--force-existing"))
      .asInstanceOf[CncfCommand.Dev.Server]
    _assert_equals(server.options.stopExisting, true)
    _assert_equals(server.options.forceExisting, true)

    val restart = CncfCommandParser.parse(Vector("dev", "server", "--restart"))
      .asInstanceOf[CncfCommand.Dev.Server]
    _assert_equals(restart.options.stopExisting, true)

    val stop = CncfCommandParser.parse(Vector("dev", "stop", "--project-dev", "/tmp/blog", "--port", "19532", "--force-existing"))
      .asInstanceOf[CncfCommand.Dev.Stop]
    _assert_equals(stop.options.target, CncfCommand.DevTarget.ProjectDev(Some("/tmp/blog")))
    _assert_equals(stop.options.port, Some("19532"))
    _assert_equals(stop.options.forceExisting, true)
  }

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
         |""".stripMargin)
    Files.createDirectories(paths.cwd.getParent.resolve("account"))
    _write(paths.cwd.getParent.resolve("account").resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    launcher.run(Vector("dev", "server"))
    _assert_equals(invoker.lastArgs.take(4), Vector(
      "--component-dev-dir",
      paths.cwd.toString,
      "--component-dev-dir",
      paths.cwd.getParent.resolve("account").toAbsolutePath.normalize.toString
    ))
    assert(invoker.lastArgs.contains("server"))
    assert(invoker.lastClasspath.contains(classdir))
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
      assert(Files.isRegularFile(pidfile))
      assert(Files.isRegularFile(jsonfile))
      _assert_equals(Files.readString(pidfile).trim, "1234")
      val json = Files.readString(jsonfile)
      assert(json.contains(""""port": "19601""""))
      assert(json.contains(""""project": """))
    }
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker, SbtRuntimeClasspathExporter, processmanager)

    launcher.run(Vector("dev", "server", "--port", "19601"))

    assert(!Files.exists(DevSupport.devServerPidFile(paths.cwd)))
    assert(!Files.exists(DevSupport.devServerJsonFile(paths.cwd)))
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
    assert(failed)
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
    assert(invoker.lastArgs.contains("server"))
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
    assert(!Files.exists(DevSupport.devServerJsonFile(paths.cwd)))
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

    assert(failed)
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

    assert(failed)
  }

  def devServerProfileAddsLocalPersistentSqliteArgs(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("dev", "server", "--profile", "local-persistent"))

    val sqlitepath = paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime.sqlite").toAbsolutePath.normalize.toString
    assert(invoker.lastArgs.contains(s"--textus.datastore.sqlite.path=$sqlitepath"))
    assert(invoker.lastArgs.contains(s"--cncf.datastore.sqlite.path=$sqlitepath"))
    assert(invoker.lastArgs.contains("--textus.datastore.sqlite.normalize-column-names=true"))
    assert(Files.isDirectory(paths.cwd.resolve("target").resolve("cncf.d")))
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

    assert(invoker.lastArgs.exists(_.startsWith("--textus.datastore.sqlite.path=")))
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
    assert(invoker.lastClasspath.contains(runtimeclassdir))
    assert(invoker.lastClasspath.contains(appclassdir))
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
    assert(invoker.lastClasspath.contains(runtimeclassdir))
    assert(invoker.lastClasspath.contains(appclassdir))
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
    assert(commandindex > 0)
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
    assert(commandindex > 0)
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
    assert(invoker.lastArgs.contains("--repository-dir"))
    assert(!invoker.lastArgs.contains(paths.cwd.toAbsolutePath.normalize.toString))
  }

  def devCommandCanDisableProjectComponentDevDir(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    _write(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"), classdir.toString)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)
    val code = launcher.run(Vector("dev", "command", "--no-project-component-dev-dir", "--discover=classes", "minimal.main.hello"))
    _assert_equals(code, 0)
    assert(invoker.lastClasspath.contains(classdir))
    assert(!invoker.lastArgs.contains("--component-dev-dir"))
    assert(invoker.lastArgs.contains("--discover=classes"))
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
    assert(!invoker.lastArgs.contains("--component-dir"))
    assert(invoker.lastArgs.contains("--component-dev-dir"))
    assert(invoker.lastClasspath.contains(classdir))
  }

  def devTargetOptionsAreMutuallyExclusive(): Unit = {
    val failed =
      try {
        CncfCommandParser.parse(Vector("dev", "server", "--project-dev", "app", "--name", "textus-blog"))
        false
      } catch {
        case e: CncfException => e.getMessage.contains("mutually exclusive")
      }
    assert(failed)
    val oldproject =
      try {
        CncfCommandParser.parse(Vector("dev", "server", "--project", "app"))
        false
      } catch {
        case e: CncfException => e.getMessage.contains("--project is no longer supported")
      }
    assert(oldproject)
  }

  def devNameTargetUsesLocalSnapshotOnly(): Unit = _with_temp_paths { paths =>
    val name = "textus-demo"
    val version = "0.1.0-SNAPSHOT"
    _write(paths.localCarRepository.resolve(name).resolve(version).resolve(s"$name-$version.car"), "fake-car")
    _write(paths.cacheCarRepository.resolve(name).resolve(version).resolve(s"$name-$version.car"), "wrong-car")
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("dev", "server", "--name", s"$name:$version"))

    assert(invoker.lastArgs.contains(s"--textus.component=$name"))
    assert(invoker.lastArgs.contains(s"--textus.component.version=$version"))
    assert(invoker.lastArgs.contains(s"--repository-dir=${paths.localCarRepository}"))
    assert(!invoker.lastArgs.contains(s"--repository-dir=${paths.cacheCarRepository}"))
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

    assert(invoker.lastArgs.contains(s"--textus.component=$name"))
    assert(invoker.lastArgs.contains(s"--textus.component.version=$snapshotversion"))
    assert(invoker.lastArgs.contains(s"--repository-dir=${paths.localCarRepository}"))
  }

  def devNameTargetUsesReleaseRepositories(): Unit = _with_temp_paths { paths =>
    val name = "textus-demo"
    val version = "0.1.0"
    _write(paths.cacheCarRepository.resolve(name).resolve(version).resolve(s"$name-$version.car"), "fake-car")
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    launcher.run(Vector("dev", "server", "--name", s"$name:$version"))

    assert(invoker.lastArgs.contains(s"--textus.component=$name"))
    assert(invoker.lastArgs.contains(s"--textus.component.version=$version"))
    assert(invoker.lastArgs.contains(s"--repository-dir=${paths.localCarRepository}"))
    assert(invoker.lastArgs.contains(s"--repository-dir=${paths.cacheCarRepository}"))
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
    assert(invoker.lastArgs.contains("server-emulator"))
    assert(invoker.lastArgs.contains("blog.component.search"))
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
    assert(invoker.lastArgs.contains(project.toAbsolutePath.normalize.toString))
    assert(!invoker.lastArgs.exists(_.startsWith("--cncf.server.port=")))
  }

  def devHelpExplainsResolutionModel(): Unit = {
    val help = CncfCommandParser.helpText
    assert(help.contains("Development resolution:"))
    assert(help.contains("defaults to --project-dev ."))
    assert(help.contains("--name <artifact>[:<version>]"))
    assert(help.contains("--car-file <file>"))
    assert(help.contains("--project-car <dir>"))
    assert(help.contains("repository lookup is disabled"))
    assert(help.contains("--component-dev-dir <dir> is a dependency component local override"))
    assert(help.contains("cozyPublishLocalCar"))
    assert(help.contains("~/.cncf/local is developer local publish state"))
    assert(help.contains("Snapshot components are local-only"))
    assert(help.contains("component.d and repository.d are not used implicitly"))
    assert(help.contains("cncf dev stop"))
    assert(help.contains("--stop-existing"))
    assert(help.contains("dev-server.pid"))
    assert(help.contains("descriptor source metadata lives under src/main/web-inf"))
    assert(help.contains("textus server <artifact> is the CAR/SAR artifact launcher"))
  }

  def devCheckReportsMainTargetAndDependencyResolution(): Unit = _with_temp_paths { paths =>
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    val (code, output) = _capture_stdout {
      launcher.run(Vector("dev", "check"))
    }
    _assert_equals(code, 0)
    assert(output.contains("dev-target mode=project-dev"))
    assert(output.contains("main-target source=local-project"))
    assert(output.contains("main-target-repository-lookup disabled in project-dev mode"))
    assert(output.contains("dependency-components local dev overrides"))
    assert(output.contains("local-repository"))
    assert(output.contains(paths.localRepository.toString))
    assert(output.contains("cache-repository"))
    assert(output.contains("status=not-created"))
    assert(output.contains("web-descriptor-source none; use src/main/web-inf/web.yaml|form.yaml|admin.yaml"))
  }

  def devCheckReportsDevServerState(): Unit = _with_temp_paths { paths =>
    _write_dev_server_state(paths.cwd, 2230L, "19610")
    val processmanager = FakeDevServerProcessManager(3337L)
    processmanager.alive = Set(2230L)
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), SbtRuntimeClasspathExporter, processmanager)
    val (code, output) = _capture_stdout {
      launcher.run(Vector("dev", "check", "--port", "19610"))
    }

    _assert_equals(code, 0)
    assert(output.contains("dev-server"))
    assert(output.contains("pid=2230"))
    assert(output.contains("status=alive"))
    assert(output.contains("port=19610"))
  }

  def devCheckTreatsMissingMainTargetClasspathAsWarning(): Unit = _with_temp_paths { paths =>
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    val (code, output) = _capture_stdout {
      launcher.run(Vector("dev", "check"))
    }
    _assert_equals(code, 0)
    assert(output.contains("WARN"))
    assert(output.contains("runtime-classpath"))
    assert(output.contains("dev server will run cncf dev classpath automatically"))
  }

  def devCheckTreatsMissingDependencyClasspathAsError(): Unit = _with_temp_paths { paths =>
    Files.createDirectories(paths.cwd.getParent.resolve("account"))
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker())
    val (code, output) = _capture_stdout {
      launcher.run(Vector("dev", "check", "--component-dev-dir", "../account"))
    }
    _assert_equals(code, 2)
    assert(output.contains("ERROR"))
    assert(output.contains("dependency-component-dev-dir"))
    assert(output.contains("run cncf dev classpath --project-dev"))
  }

  def devServerAutoGeneratesMainTargetClasspath(): Unit = _with_temp_paths { paths =>
    val classdir = paths.cwd.resolve("target").resolve("classes")
    Files.createDirectories(classdir)
    val exporter = FakeClasspathExporter.success(classdir.toString)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker, exporter)

    val code = launcher.run(Vector("dev", "server"))

    _assert_equals(code, 0)
    _assert_equals(exporter.projects, Vector(paths.cwd))
    assert(Files.isRegularFile(paths.cwd.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt")))
    assert(invoker.lastClasspath.contains(classdir))
    assert(invoker.lastArgs.contains("--component-dev-dir"))
    assert(invoker.lastArgs.contains(paths.cwd.toString))
  }

  def devServerReportsMainTargetClasspathExportFailure(): Unit = _with_temp_paths { paths =>
    val exporter = FakeClasspathExporter.failure("sbt failed")
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), exporter)
    val failed =
      try {
        launcher.run(Vector("dev", "server"))
        false
      } catch {
        case e: CncfException =>
          e.getMessage.contains("failed to prepare main target runtime classpath") &&
            e.getMessage.contains("run cncf dev classpath --project-dev") &&
            e.getMessage.contains("sbt failed")
      }
    assert(failed)
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
    assert(failed)
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

  private def _capture_stdout(f: => Int): (Int, String) = {
    val out = new java.io.ByteArrayOutputStream()
    val code = Console.withOut(new java.io.PrintStream(out)) {
      f
    }
    (code, out.toString)
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

  private def _write_zip(
    path: Path,
    entry: String,
    value: String
  ): Path = {
    Files.createDirectories(path.getParent)
    val out = new ZipOutputStream(Files.newOutputStream(path))
    try {
      out.putNextEntry(new ZipEntry(entry))
      out.write(value.getBytes(StandardCharsets.UTF_8))
      out.closeEntry()
    } finally {
      out.close()
    }
    path
  }

  private def _write_dev_server_state(
    project: Path,
    pid: Long,
    port: String,
    processstartedat: java.time.Instant = java.time.Instant.parse("2026-05-24T00:00:00Z")
  ): Unit = {
    val state = DevServerState(
      pid = pid,
      project = project.toAbsolutePath.normalize,
      port = port,
      runtimeVersion = "0.9.0",
      executionProfile = None,
      startedAt = java.time.Instant.parse("2026-05-24T00:00:00Z"),
      processStartedAt = Some(processstartedat),
      commandLine = Some("cncf dev server"),
      command = "cncf dev server"
    )
    _write(DevSupport.devServerPidFile(project), s"${pid}\n")
    _write(DevSupport.devServerJsonFile(project), state.renderJson)
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
      |baseProvided:
      |  - org.goldenport:goldenport-cncf_3
      |  - org.typelevel:cats-core_3
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

  private val _runtime_dev_catalog_text: String =
    """schemaVersion: 1
      |generatedAt: 2026-05-25T00:00:00Z
      |recommended: 0.4.9
      |latestStable: 0.4.9
      |latestSnapshot: 0.4.10-SNAPSHOT
      |mavenRepositories:
      |  - https://repo.example/maven
      |baseProvided:
      |  - org.goldenport:goldenport-cncf_3
      |versions:
      |  - version: 0.4.9
      |    channel: stable
      |    status: active
      |    scalaBinaryVersion: "3"
      |    module: org.goldenport:goldenport-cncf_3:0.4.9
      |    publishedAt: 2026-05-24T01:00:00Z
      |  - version: 0.4.10-SNAPSHOT
      |    channel: snapshot
      |    status: active
      |    scalaBinaryVersion: "3"
      |    module: org.goldenport:goldenport-cncf_3:0.4.10-SNAPSHOT
      |    publishedAt: 2026-05-25T01:00:00Z
      |""".stripMargin

  private def _project_yaml(
    minimum: String,
    tested: Vector[String]
  ): String =
    s"""packaging:
       |  kind: car
       |  car:
       |    runtime:
       |      cncf:
       |        minimum: $minimum
       |        excluded: []
       |        tested:
       |${tested.map(v => s"          - $v").mkString("\n")}
       |""".stripMargin
}

final class FakeResolver(
  classpath: Option[Vector[Path]] = None
) extends CncfRuntimeResolver {
  var resolvedVersions: Vector[String] = Vector.empty
  var resolvedClasspaths: Vector[String] = Vector.empty
  override def resolveVersion(version: String, config: LauncherConfig, paths: LauncherPaths): String = {
    resolvedVersions :+= version
    if (version == LauncherConfig.DEFAULT_RUNTIME_VERSION) "0.9.0" else version
  }
  def resolve(version: String, config: LauncherConfig, paths: LauncherPaths): Vector[Path] = {
    val concreteversion = resolveVersion(version, config, paths)
    resolvedClasspaths :+= concreteversion
    classpath.getOrElse(Vector(paths.cwd.resolve(s"fake-cncf-$concreteversion.jar")))
  }
}

object FakeResolver {
  def apply(): FakeResolver = new FakeResolver()
  def apply(classpath: Option[Vector[Path]]): FakeResolver = new FakeResolver(classpath)
}

final class FakeClasspathExporter(
  response: Either[CncfException, String]
) extends RuntimeClasspathExporter {
  var projects: Vector[Path] = Vector.empty

  def exportRuntimeClasspath(project: Path): String = {
    projects :+= project
    response match {
      case Right(value) => value
      case Left(error) => throw error
    }
  }
}

object FakeClasspathExporter {
  def success(value: String): FakeClasspathExporter =
    new FakeClasspathExporter(Right(value))

  def failure(message: String): FakeClasspathExporter =
    new FakeClasspathExporter(Left(CncfException(message)))
}

final class FakeInvoker extends CncfInvoker {
  var lastClasspath: Vector[Path] = Vector.empty
  var lastArgs: Vector[String] = Vector.empty
  var onInvoke: () => Unit = () => ()

  override def invoke(classpath: Vector[Path], args: Vector[String]): Int = {
    lastClasspath = classpath
    lastArgs = args
    onInvoke()
    0
  }
}

object FakeInvoker {
  def apply(): FakeInvoker = new FakeInvoker()
}

final class FakeLauncherDevInvoker extends LauncherDevInvoker {
  var devDir: Option[Path] = None
  var args: Vector[String] = Vector.empty

  def invoke(devdir: Path, args: Vector[String]): Int = {
    this.devDir = Some(devdir)
    this.args = args
    0
  }
}

object FakeLauncherDevInvoker {
  def apply(): FakeLauncherDevInvoker = new FakeLauncherDevInvoker()
}

final class FakeDevServerProcessManager(
  currentpid: Long
) extends DevServerProcessManager {
  var alive: Set[Long] = Set.empty
  var defaultProcessStartedAt: Option[java.time.Instant] =
    Some(java.time.Instant.parse("2026-05-24T00:00:00Z"))
  var processStarts: Map[Long, java.time.Instant] =
    Map(currentpid -> java.time.Instant.parse("2026-05-24T00:00:00Z"))
  var commandLines: Map[Long, String] =
    Map(currentpid -> "cncf dev server")
  var gracefulStopped: Vector[Long] = Vector.empty
  var forceStopped: Vector[Long] = Vector.empty
  var gracefulSucceeds: Boolean = true

  def currentPid: Long =
    currentpid

  def processStartedAt(pid: Long): Option[java.time.Instant] =
    processStarts.get(pid).orElse(defaultProcessStartedAt)

  def commandLine(pid: Long): Option[String] =
    commandLines.get(pid)

  def isAlive(pid: Long): Boolean =
    alive.contains(pid)

  def stopGracefully(pid: Long): Boolean = {
    gracefulStopped :+= pid
    if (gracefulSucceeds) {
      alive -= pid
      true
    } else {
      false
    }
  }

  def stopForcibly(pid: Long): Boolean = {
    forceStopped :+= pid
    alive -= pid
    true
  }
}

object FakeDevServerProcessManager {
  def apply(currentpid: Long): FakeDevServerProcessManager =
    new FakeDevServerProcessManager(currentpid)
}
