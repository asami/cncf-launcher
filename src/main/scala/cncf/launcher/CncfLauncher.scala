package cncf.launcher

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.ZipFile
import scala.util.Try

import io.circe.syntax.*

/*
 * @since   May. 17, 2026
 *  version May. 27, 2026
 *  version Jul. 28, 2026
 * @version Aug.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfLauncher(
  paths: LauncherPaths = LauncherPaths(),
  runtimeresolver: CncfRuntimeResolver = CoursierCncfRuntimeResolver(),
  cncfinvoker: CncfInvoker = CncfInvoker(),
  classpathexporter: RuntimeClasspathExporter = SbtRuntimeClasspathExporter,
  processmanager: DevServerProcessManager = DevServerProcessManager.System,
  launcherdevinvoker: LauncherDevInvoker = LauncherDevInvoker.System,
  environment: Map[String, String] = sys.env,
  registrationreporter: CncfTextusControlCenterRegistrationReporter = CncfTextusControlCenterRegistrationReporter.System,
  supervisorhost: LifecycleSupervisorDaemonHost = LifecycleSupervisorDaemonHost.System,
  supervisorauthority: LifecycleSupervisorAuthority = LifecycleSupervisorAuthority.System
) {
  def run(args: Vector[String]): Int =
    _launcher_home(args).fold(_run(args)) { case (home, commandargs) =>
      new CncfLauncher(paths.copy(home = home), runtimeresolver, cncfinvoker, classpathexporter, processmanager, launcherdevinvoker, environment, registrationreporter, supervisorhost, supervisorauthority).run(commandargs)
    }

  private def _run(args: Vector[String]): Int = {
    val (configfiles, cncfconfigfiles, commandargs) = _take_config_options(args)
    val effectivecncfconfigfiles = _implicit_control_center_config(commandargs, cncfconfigfiles)
    val config = LauncherConfig.load(paths, configfiles, environment)
      .mergeHigher(LauncherConfig(cncfConfigFiles = effectivecncfconfigfiles))
    _delegate_launcher_dev_dir(config, args) match {
      case Some(code) => return code
      case None => ()
    }
    val command = CncfCommandParser.parse(commandargs)
    command match {
      case CncfCommand.LauncherVersion =>
        println(s"${LauncherBuildInfo.name} ${LauncherBuildInfo.version}")
        0
      case CncfCommand.RuntimeHelp =>
        val code = _run_runtime_help(config)
        println()
        println("Launcher help:")
        println(CncfCommandParser.helpText)
        code
      case CncfCommand.LauncherHelp =>
        println(CncfCommandParser.helpText)
        0
      case runtime: CncfCommand.Runtime =>
        _run_runtime(runtime, config)
      case repository: CncfCommand.Repository =>
        _run_repository(repository)
      case supervisor: CncfCommand.Supervisor =>
        _run_supervisor(supervisor)
      case lifecycle: CncfCommand.Lifecycle =>
        _run_lifecycle(lifecycle)
      case evidence: CncfCommand.Evidence =>
        _run_evidence(evidence)
      case install: CncfCommand.InstallCli =>
        _run_install_cli(install, configfiles, cncfconfigfiles)
      case execute: CncfCommand.Execute =>
        _run_execute(execute, config)
      case dev: CncfCommand.Dev =>
        _run_dev(dev, configfiles, cncfconfigfiles)
    }
  }

  private def _launcher_home(args: Vector[String]): Option[(Path, Vector[String])] = {
    val launcherscope = args.take(args.indexOf("--") match {
      case -1 => args.length
      case index => index
    })
    val index = launcherscope.indexOf("--launcher-home")
    if (index < 0) None
    else if (index + 1 >= args.length) throw CncfException("--launcher-home requires a directory")
    else if (launcherscope.indexOf("--launcher-home", index + 1) >= 0) throw CncfException("--launcher-home may be specified only once")
    else Some(paths.cwd.resolve(args(index + 1)).normalize.toAbsolutePath.normalize -> (args.take(index) ++ args.drop(index + 2)))
  }

  private def _run_repository(command: CncfCommand.Repository): Int = {
    val discovery = CncfComponentRepositoryDiscovery(paths)
    command match {
      case CncfCommand.Repository.ListArtifacts(kind, includedevelopment, developmentdirs) =>
        val result = discovery.list(kind, includedevelopment, developmentdirs)
        result.diagnostics.foreach(message => Console.err.println(s"warning: $message"))
        result.artifacts.foreach(artifact => println(artifact.render))
        0
      case CncfCommand.Repository.Show(target, kind, includedevelopment, developmentdirs) =>
        val result = discovery.show(target, kind, includedevelopment, developmentdirs)
        result.diagnostics.foreach(message => Console.err.println(s"warning: $message"))
        println(result.artifact.renderDetailed)
        0
    }
  }

  private def _run_supervisor(command: CncfCommand.Supervisor): Int =
    command match {
      case CncfCommand.Supervisor.Serve =>
        LifecycleSupervisorDaemonConfiguration.resolve(paths).fold(code => throw CncfException(code), { configuration =>
          environment.get(configuration.tokenEnv).filter(_.nonEmpty).fold(
            throw CncfException(LifecycleSupervisorDaemonConfiguration.CREDENTIAL_UNAVAILABLE)
          )(token => supervisorhost.serve(configuration, token, paths))
        })
    }

  private def _run_lifecycle(command: CncfCommand.Lifecycle): Int = {
    import LifecycleSupervisorProtocol.given
    import io.circe.syntax.*
    def configuration: Either[String, (LifecycleSupervisorDaemonConfiguration, String)] =
      LifecycleSupervisorDaemonConfiguration.resolve(paths).flatMap { value =>
        environment.get(value.tokenEnv).filter(_.nonEmpty).toRight(LifecycleSupervisorDaemonConfiguration.CREDENTIAL_UNAVAILABLE).map(value -> _)
      }
    def unavailable(request: LifecycleSupervisorRequest, code: String): Int = {
      println(LifecycleSupervisorProtocol.rejected(request, "", code).asJson.noSpaces)
      0
    }
    command match {
      case CncfCommand.Lifecycle.Ensure =>
        configuration.fold(code => throw CncfException(code), { case (value, token) =>
          supervisorauthority.ensure(value, token, paths).fold(code => throw CncfException(code), _ => {
            println(s"{\"supervisorId\":\"${value.supervisorId}\",\"state\":\"available\"}")
            0
          })
        })
      case CncfCommand.Lifecycle.Submit(request) =>
        configuration.fold(code => unavailable(request, code), { case (value, token) =>
          supervisorauthority.ensure(value, token, paths).fold(code => unavailable(request, code), _ =>
            LifecycleSupervisorHttpClient.submit(value, token, request).fold(code => unavailable(request, code), { result =>
              println(result.asJson.noSpaces)
              0
            })
          )
        })
      case CncfCommand.Lifecycle.Lookup(requestid) =>
        configuration.fold(code => throw CncfException(code), { case (value, token) =>
          supervisorauthority.ensure(value, token, paths).fold(code => throw CncfException(code), _ =>
            LifecycleSupervisorHttpClient.lookup(value, token, requestid).fold(1) { result =>
              println(result.asJson.noSpaces)
              0
            }
          )
        })
    }
  }

  private def _run_evidence(command: CncfCommand.Evidence): Int = {
    import CncfLocalServerEvidenceSnapshot.given
    val evidence = CncfLocalServerEvidenceStore(paths)
    command match {
      case CncfCommand.Evidence.List("json") =>
        evidence.listProjection().fold(code => throw CncfException(code), projection => println(projection.asJson.noSpaces))
        0
      case CncfCommand.Evidence.Show(instanceid, "json") =>
        evidence.detailProjection(instanceid).fold(code => throw CncfException(code), {
          case Some(projection) => println(projection.asJson.noSpaces)
          case None => throw CncfException("launcher-evidence-not-found")
        })
        0
      case _ =>
        throw CncfException("cncf launcher evidence format must be json")
    }
  }

  private def _delegate_launcher_dev_dir(
    config: LauncherConfig,
    args: Vector[String]
  ): Option[Int] =
    if (environment.get("CNCF_LAUNCHER_DEV_DELEGATED").contains("1"))
      None
    else
      config.launcherDevDir.map { dir =>
        val path = paths.cwd.resolve(dir).normalize.toAbsolutePath.normalize
        launcherdevinvoker.invoke(path, args, paths.cwd.toAbsolutePath.normalize)
      }

  private def _run_runtime_help(config: LauncherConfig): Int = {
    val store = RuntimeVersionStore(paths)
    val runtimeversion = store.current(None, config)
    val classpath = runtimeresolver.resolve(runtimeversion, config, paths)
    _invoke_cncf(classpath, Vector("--help"))
  }

  private def _run_runtime(
    command: CncfCommand.Runtime,
    config: LauncherConfig
  ): Int = {
    val store = RuntimeVersionStore(paths)
    val catalogstore = RuntimeCatalogStore(paths)
    command match {
      case CncfCommand.Runtime.Version(runtimeversion, runtimedevdir) =>
        _run_runtime_version(runtimeversion, runtimedevdir, store, config)
      case CncfCommand.Runtime.Current =>
        _run_runtime_current(store, catalogstore, config)
      case CncfCommand.Runtime.LocalList =>
        val installed =
          if (Files.isDirectory(paths.runtimeRoot)) {
            val stream = Files.list(paths.runtimeRoot)
            try {
              import scala.jdk.CollectionConverters.*
              stream.iterator().asScala.filter(Files.isDirectory(_)).map(_.getFileName.toString).toVector.sorted
            } finally {
              stream.close()
            }
          } else {
            Vector.empty
          }
        installed.foreach(println)
        0
      case CncfCommand.Runtime.RemoteList =>
        val catalog = catalogstore.loadOrRefresh(config)
          .getOrElse(throw CncfException("failed to load Cncf runtime catalog"))
        println(catalog.renderRemoteList)
        0
      case CncfCommand.Runtime.Refresh =>
        catalogstore.refresh(config)
        println(s"refreshed Cncf runtime catalog: ${paths.runtimeCatalog}")
        0
      case CncfCommand.Runtime.CatalogShow =>
        val catalog = catalogstore.loadOrRefresh(config)
          .getOrElse(throw CncfException("failed to load Cncf runtime catalog"))
        println(catalog.render)
        0
      case CncfCommand.Runtime.Descriptor(format) =>
        _validate_yaml_format(format)
        println(_runtime_descriptor(store, config, catalogstore.loadOrRefresh(config)))
        0
      case CncfCommand.Runtime.BaseProvided(format) =>
        _validate_yaml_format(format)
        println(RuntimeCatalog.parse(_runtime_descriptor(store, config, catalogstore.loadOrRefresh(config))).renderBaseProvided)
        0
      case CncfCommand.Runtime.Channels =>
        val catalog = catalogstore.loadOrRefresh(config)
          .getOrElse(throw CncfException("failed to load Cncf runtime catalog"))
        println(catalog.renderChannels)
        0
      case CncfCommand.Runtime.Install(version) =>
        val concreteversion = runtimeresolver.resolveVersion(version, config, paths)
        runtimeresolver.resolve(concreteversion, config, paths)
        println(s"installed CNCF runtime $concreteversion")
        0
      case CncfCommand.Runtime.Use(version, target) =>
        val concreteversion = runtimeresolver.resolveVersion(version, config, paths)
        val resolvedtarget = _resolve_runtime_use_target(target)
        resolvedtarget match {
          case CncfCommand.RuntimeUseTarget.Global => store.useGlobal(version)
          case CncfCommand.RuntimeUseTarget.Project => store.useProject(version)
          case CncfCommand.RuntimeUseTarget.Auto => throw CncfException("unresolved runtime use target")
        }
        println(s"using CNCF runtime $version -> $concreteversion (${resolvedtarget.toString.toLowerCase})")
        0
      case CncfCommand.Runtime.CacheStatus() =>
        println(s"cncf home: ${paths.cncfHome}")
        println(s"local repository: ${paths.localRepository}")
        println(s"artifact cache: ${paths.cacheRepository}")
        println(s"runtime cache: ${paths.runtimeRoot}")
        println(s"coursier cache: ${paths.coursierCache}")
        0
      case CncfCommand.Runtime.ConfigShow() =>
        println(LauncherConfig.render(config))
        0
    }
  }

  private def _run_runtime_version(
    runtimeversion: Option[String],
    runtimedevdir: Option[String],
    store: RuntimeVersionStore,
    config: LauncherConfig
  ): Int = {
    val devdir = runtimedevdir.orElse(config.runtimeDevDir)
    val classpath = devdir match {
      case Some(dir) =>
        val project = paths.cwd.resolve(dir).normalize.toAbsolutePath.normalize
        DevSupport(paths, classpathexporter, processmanager).cncfRuntimeClasspath(project)
      case None =>
        val selector = store.current(runtimeversion, config)
        runtimeresolver.resolve(selector, config, paths)
    }
    _invoke_cncf(classpath, Vector("version"))
  }

  private def _run_runtime_current(
    store: RuntimeVersionStore,
    catalogstore: RuntimeCatalogStore,
    config: LauncherConfig
  ): Int = {
    config.runtimeDevDir match {
      case Some(dir) =>
        println(_development_runtime_version(paths.cwd.resolve(dir).normalize.toAbsolutePath.normalize))
        0
      case None =>
        val selector = store.current(None, config)
        val current = runtimeresolver.resolveVersion(selector, config, paths)
        println(current)
        _warn_if_runtime_catalog_is_stale(selector, current, catalogstore, config)
        0
    }
  }

  private def _development_runtime_version(project: java.nio.file.Path): String = {
    val build = project.resolve("build.sbt")
    if (!Files.isRegularFile(build))
      throw CncfException(s"CNCF runtime development directory has no build.sbt: ${project}")
    val text = Files.readString(build, StandardCharsets.UTF_8)
    val versionregex = """(?m)(?:ThisBuild\s*/\s*)?version\s*:=\s*"([^"\n]+)""".r
    versionregex.findFirstMatchIn(text).map(_.group(1)).getOrElse(
      throw CncfException(s"failed to read CNCF runtime development version from ${build}")
    )
  }

  private def _warn_if_runtime_catalog_is_stale(
    selector: String,
    current: String,
    catalogstore: RuntimeCatalogStore,
    config: LauncherConfig
  ): Unit =
    if (_is_dynamic_runtime_selector(selector) && !_is_dynamic_runtime_selector(current)) {
      val remoteversion =
        try Some(catalogstore.fetch(config).resolve(selector).version)
        catch {
          case _: Throwable => None
        }
      remoteversion.filter(_ != current).foreach { version =>
        Console.err.println(
          s"warning: cached CNCF runtime catalog resolves $selector to $current, but remote catalog resolves it to $version."
        )
        Console.err.println("Run 'cncf runtime refresh' to update the local runtime catalog cache.")
      }
    }

  private def _is_dynamic_runtime_selector(selector: String): Boolean =
    selector match {
      case "recommended" | "latest" | "latest-stable" | "latest.release" | "latest-snapshot" | "newest" => true
      case _ => false
    }

  private def _current_runtime_catalog_version(
    store: RuntimeVersionStore,
    config: LauncherConfig,
    catalog: RuntimeCatalog
  ): RuntimeCatalogVersion =
    catalog.resolve(store.current(None, config))

  private def _validate_yaml_format(format: String): Unit =
    if (format != "yaml")
      throw CncfException(s"unsupported runtime descriptor format: $format")

  private def _runtime_descriptor(
    store: RuntimeVersionStore,
    config: LauncherConfig,
    catalog: Option[RuntimeCatalog]
  ): String = {
    val selector = store.current(None, config)
    val version = catalog.map(_.resolve(selector)).getOrElse(_runtime_catalog_version_without_catalog(selector, config))
    val effectiveconfig = catalog.map(config.withCatalog).getOrElse(config)
    val descriptor =
      Try(runtimeresolver.resolve(version.version, effectiveconfig, paths)).toOption.
        flatMap(_runtime_descriptor_from_classpath)
    descriptor.getOrElse {
      catalog.map(_.renderRuntimeDescriptor(version)).
        getOrElse(RuntimeCatalog.empty.renderRuntimeDescriptor(version))
    }
  }

  private def _runtime_catalog_version_without_catalog(
    selector: String,
    config: LauncherConfig
  ): RuntimeCatalogVersion =
    RuntimeCatalogVersion(
      version = runtimeresolver.resolveVersion(selector, config, paths),
      channel = None,
      status = Some("active"),
      scalaBinaryVersion = Some("3"),
      module = None,
      publishedAt = None,
      checksumUrl = None,
      metadataUrl = None
    )

  private def _runtime_descriptor_from_classpath(
    classpath: Vector[java.nio.file.Path]
  ): Option[String] =
    classpath.iterator.flatMap(_runtime_descriptor_from_jar).nextOption()

  private def _runtime_descriptor_from_jar(path: java.nio.file.Path): Option[String] =
    if (Files.isRegularFile(path) && path.getFileName.toString.endsWith(".jar"))
      Try {
        val zip = new ZipFile(path.toFile)
        try {
          Option(zip.getEntry("META-INF/cncf/runtime.yaml")).map { entry =>
            val in = zip.getInputStream(entry)
            try new String(in.readAllBytes(), StandardCharsets.UTF_8)
            finally in.close()
          }
        } finally {
          zip.close()
        }
      }.toOption.flatten
    else
      None

  private def _resolve_runtime_use_target(
    target: CncfCommand.RuntimeUseTarget
  ): CncfCommand.RuntimeUseTarget =
    target match {
      case CncfCommand.RuntimeUseTarget.Auto =>
        if (Files.exists(paths.cwd.resolve(".cncf")))
          CncfCommand.RuntimeUseTarget.Project
        else
          CncfCommand.RuntimeUseTarget.Global
      case x => x
    }

  private def _run_execute(
    command: CncfCommand.Execute,
    config: LauncherConfig
  ): Int = {
    val store = RuntimeVersionStore(paths)
    val developmenttarget = command.developmentTarget.map(_resolve_development_target)
    val runtimedevdir =
      command.runtimeDevDir
        .orElse(Option.when(command.runtimeVersion.isEmpty)(config.runtimeDevDir).flatten)
        .map(dir => paths.cwd.resolve(dir).normalize.toAbsolutePath.normalize)
    val runtimeversion = runtimedevdir match {
      case Some(dir) =>
        _development_runtime_version(dir)
      case None =>
        runtimeresolver.resolveVersion(store.current(command.runtimeVersion, config), config, paths)
    }
    val classpath = runtimedevdir match {
      case Some(dir) =>
        DevSupport(paths, classpathexporter, processmanager)
          .cncfRuntimeClasspath(dir)
      case None =>
        runtimeresolver.resolve(runtimeversion, config, paths)
    }
    val developmentargs = _development_server_args(command, developmenttarget)
    val effectivecommand = command.copy(args = _configured_component_development_args(developmentargs, config))
    val report = _server_report(command, runtimeversion)
    val evidencesession = report.map(_local_evidence_session).getOrElse(CncfLocalServerEvidenceSession.noop)
    val registrationsession = report.map(_registration_session(effectivecommand, config, _)).getOrElse(CncfTextusControlCenterRegistrationSession.noop)
    val shutdownhook = new Thread(
      () => {
        registrationsession.close()
        evidencesession.close()
      },
      "cncf-server-lifecycle-shutdown"
    )
    Runtime.getRuntime.addShutdownHook(shutdownhook)
    try {
      _invoke_cncf(classpath, _cncf_config_args(config) ++ _textus_knowledge_rdf_args(config) ++ _runtime_command_args(effectivecommand.args))
    } finally {
      scala.util.Try(Runtime.getRuntime.removeShutdownHook(shutdownhook))
      registrationsession.close()
      evidencesession.close()
    }
  }

  private def _resolve_development_target(
    target: CncfCommand.ExecuteDevelopmentTarget
  ): Path = {
    val directory = _development_directory(target.path)
    if (target.explicit && !_is_car_or_sar_development_target(directory))
      throw CncfException(s"${target.path} is not a CAR/SAR development target")
    directory
  }

  private def _is_car_or_sar_development_target(directory: Path): Boolean = {
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

  private def _implicit_control_center_config(
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
      developmenttarget.flatMap(_project_artifact_id).contains("textus-control-center") &&
      Files.isRegularFile(file) &&
      !explicit.contains(file.toString)
    )
      Vector(file.toString) ++ explicit
    else
      explicit
  }

  private def _development_server_args(
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

  private def _configured_component_development_args(
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

  private def _project_default_port(project: Path): Option[String] = {
    val file = project.resolve("project.yaml")
    scala.util.Try {
      LauncherConfigParser.parse(file, Files.readString(file, StandardCharsets.UTF_8))
        .get("project.component.config.textus.server.default-port")
        .flatMap(_.headOption)
        .map(_.trim)
        .filter(_.matches("[0-9]+"))
    }.toOption.flatten
  }

  private def _server_report(
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

  private def _local_evidence_session(
    report: CncfTextusControlCenterRegistrationReport
  ): CncfLocalServerEvidenceSession =
    try {
      CncfLocalServerEvidenceSession.start(paths, report, "cncf")
    } catch {
      case _: Throwable =>
        Console.err.println("warning: local CNCF server evidence could not be recorded; continuing server startup.")
        CncfLocalServerEvidenceSession.noop
    }

  private def _registration_session(
    command: CncfCommand.Execute,
    config: LauncherConfig,
    report: CncfTextusControlCenterRegistrationReport
  ): CncfTextusControlCenterRegistrationSession = {
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

  private def _registration_instance_id(args: Vector[String]): Option[String] =
    args.collectFirst {
      case value if value.startsWith("--textus.control-center.registration-instance-id=") =>
        value.stripPrefix("--textus.control-center.registration-instance-id=").trim
    }.filter(_.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))

  private def _runtime_command_args(args: Vector[String]): Vector[String] =
    args.filterNot(_.startsWith("--textus.control-center.registration-instance-id="))

  private final case class RegistrationTarget(
    name: String,
    artifactId: Option[String],
    executionMode: String,
    developmentDirectory: Option[String],
    subsystemName: Option[String],
    subsystemVersion: Option[String]
  )

  private def _registration_target(args: Vector[String]): RegistrationTarget = {
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

  private def _development_directory(value: String): java.nio.file.Path =
    paths.cwd.resolve(value).normalize.toAbsolutePath.normalize

  private def _project_component_name(directory: java.nio.file.Path): Option[String] = {
    val project = directory.resolve("project.yaml")
    scala.util.Try {
      LauncherConfigParser.parse(project, Files.readString(project, StandardCharsets.UTF_8))
        .get("project.component.name")
        .flatMap(_.headOption)
        .map(_.trim)
        .filter(_.nonEmpty)
    }.toOption.flatten
  }

  private def _project_artifact_id(directory: java.nio.file.Path): Option[String] = {
    val project = directory.resolve("project.yaml")
    scala.util.Try {
      LauncherConfigParser.parse(project, Files.readString(project, StandardCharsets.UTF_8))
        .get("project.name")
        .flatMap(_.headOption)
        .map(_.trim)
        .filter(_.nonEmpty)
    }.toOption.flatten
  }

  private def _standalone_registration_base_url(command: CncfCommand.Execute): Option[String] =
    command.args.collectFirst {
      case value if value.startsWith("--textus.server.port=") => value.stripPrefix("--textus.server.port=")
      case value if value.startsWith("--cncf.server.port=") => value.stripPrefix("--cncf.server.port=")
    }.flatMap { value =>
      scala.util.Try(value.toInt).toOption.filter(port => port >= 1 && port <= 65535).map(port => s"http://127.0.0.1:$port")
    }

  private def _run_install_cli(
    command: CncfCommand.InstallCli,
    configfiles: Vector[String],
    cncfconfigfiles: Vector[String]
  ): Int = {
    val prepared = _prepare_install_cli(command, configfiles, cncfconfigfiles)
    val path = CliInstaller.installCncf(paths, prepared)
    println(s"installed CLI command ${prepared.installedName}: ${path}")
    0
  }

  private def _prepare_install_cli(
    command: CncfCommand.InstallCli,
    configfiles: Vector[String],
    cncfconfigfiles: Vector[String]
  ): CncfCommand.InstallCli = {
    val shouldpinruntime =
      command.runtimeVersion.nonEmpty ||
        command.runtimeSelectionPolicy.nonEmpty ||
        command.runtimeNoCompatiblePolicy.nonEmpty ||
        command.runtimeDevDir.nonEmpty
    val options = CncfCommand.DevOptions(
      target = CncfCommand.DevTarget.ProjectDev(command.projectDev),
      runtimeVersion = command.runtimeVersion,
      runtimeSelectionPolicy = command.runtimeSelectionPolicy,
      runtimeNoCompatiblePolicy = command.runtimeNoCompatiblePolicy,
      runtimeDevDir = command.runtimeDevDir,
      componentDevDirs = command.componentDevDirs
    )
    val explicitconfig = LauncherConfig(cncfConfigFiles = cncfconfigfiles)
    val initialconfig = LauncherConfig.load(paths, configfiles, environment).mergeHigher(explicitconfig)
    val initialoptions = options.copy(target = _config_dev_target(options.target, initialconfig))
    val effectivepaths = _dev_paths(initialoptions)
    val config =
      if (effectivepaths.cwd == paths.cwd)
        initialconfig
      else
        LauncherConfig.load(effectivepaths, configfiles, environment).mergeHigher(explicitconfig)
    val store = RuntimeVersionStore(effectivepaths)
    val hasdevelopmentruntime = shouldpinruntime && options.runtimeDevDir.orElse(config.runtimeDevDir).isDefined
    val basecatalog =
      if (!shouldpinruntime || hasdevelopmentruntime) None
      else RuntimeCatalogStore(effectivepaths).loadOrRefresh(config)
    val baseconfig = basecatalog.map(config.withCatalog).getOrElse(config)
    val devsupport = new DevSupport(effectivepaths, classpathexporter, processmanager)
    val effectiveoptions = options.copy(
      target = _normalize_dev_target(_config_dev_target(options.target, baseconfig))
    )
    val rawcontext = devsupport.context(effectiveoptions, baseconfig, store)
    val catalog = rawcontext.runtimeDevDir match {
      case Some(dir) => RuntimeCatalogStore.loadRuntimeDevelopmentCatalog(dir)
      case None => basecatalog
    }
    val effectiveconfig = catalog.map(config.withCatalog).getOrElse(config)
    val runtimedevdir =
      if (shouldpinruntime) rawcontext.runtimeDevDir.map(_.toString)
      else None
    val runtimeversion =
      if (!shouldpinruntime) {
        None
      } else {
        rawcontext.runtimeDevDir match {
          case Some(dir) =>
            _validate_development_runtime(dir, rawcontext.runtimeRequirements)
            None
          case None =>
            val selectionpolicy = effectiveoptions.runtimeSelectionPolicy.
              orElse(effectiveconfig.runtimeSelectionPolicy).
              getOrElse(RuntimeSelectionPolicy.CurrentCompatible)
            val policy = effectiveoptions.runtimeNoCompatiblePolicy.orElse(effectiveconfig.runtimeNoCompatiblePolicy).getOrElse(RuntimeNoCompatiblePolicy.Error)
            Some(RuntimeVersionSelection.select(
              requested = effectiveoptions.runtimeVersion,
              stored = store.current(None, effectiveconfig),
              requirements = rawcontext.runtimeRequirements,
              catalog = catalog,
              selectionPolicy = selectionpolicy,
              policy = policy
            ))
        }
      }
    command.copy(
      runtimeVersion = runtimeversion,
      runtimeSelectionPolicy = None,
      runtimeNoCompatiblePolicy = None,
      runtimeDevDir = runtimedevdir,
      launcherDevDir = effectiveconfig.launcherDevDir.map(p => effectivepaths.cwd.resolve(p).normalize.toAbsolutePath.normalize.toString),
      componentDevDirs = rawcontext.componentDevDirs.map(_.toString)
    )
  }

  private def _validate_development_runtime(
    runtimeproject: java.nio.file.Path,
    requirements: Vector[RuntimeRequirement]
  ): Unit = {
    val version = _development_runtime_version(runtimeproject)
    val rejected = requirements.filterNot(_.accepts(version))
    if (rejected.nonEmpty) {
      val sources = rejected.map(_.source).distinct.sorted.mkString(", ")
      throw CncfException(s"CNCF runtime $version is not compatible with component requirements: $sources")
    }
  }

  private def _run_dev(
    command: CncfCommand.Dev,
    configfiles: Vector[String],
    cncfconfigfiles: Vector[String]
  ): Int = {
    val explicitconfig = LauncherConfig(cncfConfigFiles = cncfconfigfiles)
    val initialconfig = LauncherConfig.load(paths, configfiles).mergeHigher(explicitconfig)
    val initialoptions = command.options.copy(target = _config_dev_target(command.options.target, initialconfig))
    val effectivepaths = _dev_paths(initialoptions)
    val config =
      if (effectivepaths.cwd == paths.cwd)
        initialconfig
      else
        LauncherConfig.load(effectivepaths, configfiles).mergeHigher(explicitconfig)
    val store = RuntimeVersionStore(effectivepaths)
    val basecatalog = RuntimeCatalogStore(effectivepaths).loadOrRefresh(config)
    val baseconfig = basecatalog.map(config.withCatalog).getOrElse(config)
    val devsupport = new DevSupport(effectivepaths, classpathexporter, processmanager)
    val effectiveoptions = command.options.copy(
      target = _normalize_dev_target(_config_dev_target(command.options.target, baseconfig)),
      stopExisting = command.options.stopExisting || baseconfig.devRestart.getOrElse(false),
      forceExisting = command.options.forceExisting || baseconfig.devForceExisting.getOrElse(false)
    )
    val rawcontext = devsupport.context(effectiveoptions, baseconfig, store)
    val catalog =
      rawcontext.runtimeDevDir.flatMap(RuntimeCatalogStore.loadRuntimeDevelopmentCatalog)
        .orElse(basecatalog)
    val effectiveconfig = catalog.map(config.withCatalog).getOrElse(config)
    val selectionpolicy = effectiveoptions.runtimeSelectionPolicy.
      orElse(effectiveconfig.runtimeSelectionPolicy).
      getOrElse(RuntimeSelectionPolicy.CurrentCompatible)
    val policy = effectiveoptions.runtimeNoCompatiblePolicy.orElse(effectiveconfig.runtimeNoCompatiblePolicy).getOrElse(RuntimeNoCompatiblePolicy.Error)
    val runtimeversion = RuntimeVersionSelection.select(
      requested = effectiveoptions.runtimeVersion,
      stored = store.current(None, effectiveconfig),
      requirements = rawcontext.runtimeRequirements,
      catalog = catalog,
      selectionPolicy = selectionpolicy,
      policy = policy
    )
    val context = rawcontext.copy(runtimeVersion = runtimeversion)
    command match {
      case CncfCommand.Dev.Classpath(_) =>
        val file = devsupport.writeRuntimeClasspath(context)
        println(s"Wrote ${file}")
        0
      case CncfCommand.Dev.Check(_) =>
        val items = devsupport.check(context)
        items.foreach(item => println(item.render))
        if (items.exists(_.isError)) 2 else 0
      case CncfCommand.Dev.Server(_) =>
        val state = devsupport.prepareDevServerStart(context, effectiveoptions)
        try {
          _invoke_dev(effectivepaths, context, effectiveconfig, devsupport, "server", Vector.empty)
        } finally {
          devsupport.cleanupDevServerState(state)
        }
      case CncfCommand.Dev.Stop(_) =>
        devsupport.stopDevServer(context, effectiveoptions.forceExisting, effectiveoptions.port.isDefined)
      case CncfCommand.Dev.ServerEmulation(_, args) =>
        _invoke_dev(effectivepaths, context, effectiveconfig, devsupport, "server-emulator", args)
      case CncfCommand.Dev.Client(_, args) =>
        _invoke_dev(effectivepaths, context, effectiveconfig, devsupport, "client", args)
      case CncfCommand.Dev.Command(_, operation, args) =>
        _invoke_dev(effectivepaths, context, effectiveconfig, devsupport, "command", operation +: args)
    }
  }

  private def _take_config_options(
    args: Vector[String]
  ): (Vector[String], Vector[String], Vector[String]) = {
    val configfiles = Vector.newBuilder[String]
    val cncfconfigfiles = Vector.newBuilder[String]
    val out = Vector.newBuilder[String]
    var i = 0
    while (i < args.length) {
      args(i) match {
        case "--" =>
          out ++= args.drop(i)
          i = args.length
        case "--config" | "--launcher-config" =>
          if (i + 1 >= args.length)
            throw CncfException(s"${args(i)} requires a value")
          configfiles += _config_file(args(i + 1))
          i += 2
        case x if x.startsWith("--config=") =>
          configfiles += _config_file(x.stripPrefix("--config="))
          i += 1
        case x if x.startsWith("--launcher-config=") =>
          configfiles += _config_file(x.stripPrefix("--launcher-config="))
          i += 1
        case "--cncf-config" =>
          if (i + 1 >= args.length)
            throw CncfException(s"${args(i)} requires a value")
          cncfconfigfiles += _config_file(args(i + 1))
          i += 2
        case x if x.startsWith("--cncf-config=") =>
          cncfconfigfiles += _config_file(x.stripPrefix("--cncf-config="))
          i += 1
        case x =>
          out += x
          i += 1
      }
    }
    (configfiles.result(), cncfconfigfiles.result(), out.result())
  }

  private def _config_file(path: String): String =
    paths.cwd.resolve(path).normalize.toAbsolutePath.normalize.toString

  private def _dev_paths(
    options: CncfCommand.DevOptions
  ): LauncherPaths =
    options.target match {
      case CncfCommand.DevTarget.ProjectDev(Some(path)) =>
        paths.withCwd(paths.cwd.resolve(path).normalize)
      case CncfCommand.DevTarget.ProjectCar(path) =>
        paths.withCwd(paths.cwd.resolve(path).normalize)
      case _ =>
        paths
    }

  private def _config_dev_target(
    target: CncfCommand.DevTarget,
    config: LauncherConfig
  ): CncfCommand.DevTarget =
    target match {
      case CncfCommand.DevTarget.ProjectDev(None) =>
        config.devProjectDev.map(p => CncfCommand.DevTarget.ProjectDev(Some(p))).getOrElse(target)
      case x => x
    }

  private def _normalize_dev_target(
    target: CncfCommand.DevTarget
  ): CncfCommand.DevTarget =
    target match {
      case CncfCommand.DevTarget.ProjectDev(Some(_)) => CncfCommand.DevTarget.ProjectDev(None)
      case CncfCommand.DevTarget.ProjectCar(_) => CncfCommand.DevTarget.ProjectCar(".")
      case x => x
    }

  private def _invoke_dev(
    effectivepaths: LauncherPaths,
    context: DevContext,
    config: LauncherConfig,
    devsupport: DevSupport,
    mode: String,
    args: Vector[String]
  ): Int = {
    val runtimeclasspath = context.runtimeDevDir match {
      case Some(dir) => devsupport.cncfRuntimeClasspath(dir)
      case None => runtimeresolver.resolve(context.runtimeVersion, config, effectivepaths)
    }
    val devclasspath = devsupport.runtimeClasspath(context)
    val cncfargs =
      devsupport.cncfArgs(context.copy(runtimeArgs = context.runtimeArgs ++ _cncf_config_args(config) ++ _textus_knowledge_rdf_args(config)), mode, args)
    _with_dev_system_properties(context) {
      _invoke_cncf(runtimeclasspath ++ devclasspath, cncfargs)
    }
  }

  private def _with_dev_system_properties[A](
    context: DevContext
  )(body: => A): A = {
    val updates = Vector(
      "cncf.server.port" -> context.port,
      "textus.server.port" -> context.port,
      "cncf.http.baseurl" -> s"http://127.0.0.1:${context.port}",
      "textus.http.baseurl" -> s"http://127.0.0.1:${context.port}",
      "user.dir" -> context.project.toString
    )
    val old = updates.map { case (k, _) => k -> sys.props.get(k) }
    try {
      updates.foreach { case (k, v) => sys.props.update(k, v) }
      body
    } finally {
      old.foreach {
        case (k, Some(v)) => sys.props.update(k, v)
        case (k, None) => sys.props.remove(k)
      }
    }
  }

  private def _textus_knowledge_rdf_args(
    config: LauncherConfig
  ): Vector[String] =
    config.textusKnowledgeRdfNodePrefix.toVector.map("--textus.knowledge.rdf.node-prefix=" + _) ++
      config.textusKnowledgeRdfPublicBaseUri.toVector.map("--textus.knowledge.rdf.public-base-uri=" + _) ++
      config.textusKnowledgeRdfNamespacePrefixes.toVector.map("--textus.knowledge.rdf.namespace-prefixes=" + _) ++
      config.textusKnowledgeRdfNamespaces.map { case (prefix, namespaceuri) =>
        s"--textus.knowledge.rdf.namespaces.${prefix}=${namespaceuri}"
      }

  private def _cncf_config_args(
    config: LauncherConfig
  ): Vector[String] =
    if (config.cncfConfigFiles.isEmpty)
      Vector.empty
    else
      Vector(s"--cncf.config.files=${config.cncfConfigFiles.distinct.mkString(",")}")

  private def _invoke_cncf(
    classpath: Vector[Path],
    args: Vector[String]
  ): Int = {
    val applicationhome = environment.get("HOME").filter(_.nonEmpty).map(Path.of(_).toAbsolutePath.normalize.toString)
    val previoushome = sys.props.get("user.home")
    try {
      applicationhome.foreach(sys.props.update("user.home", _))
      cncfinvoker.invoke(classpath, args)
    } finally {
      previoushome match {
        case Some(value) => sys.props.update("user.home", value)
        case None => sys.props.remove("user.home")
      }
    }
  }
}

object CncfLauncher {
  def apply(): CncfLauncher =
    new CncfLauncher()
}
