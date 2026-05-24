package cncf.launcher

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.Instant
import scala.util.Try
import scala.sys.process.*

/*
 * @since   May. 17, 2026
 * @version May. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final case class DevContext(
  project: Path,
  port: String,
  componentDevDirs: Vector[Path],
  componentDirs: Vector[Path],
  runtimeVersion: String,
  runtimeRequirements: Vector[RuntimeRequirement],
  runtimeDevDir: Option[Path],
  executionProfile: Option[CncfCommand.DevExecutionProfile],
  runtimeArgs: Vector[String],
  useProjectClasspath: Boolean,
  projectActivation: CncfCommand.ProjectActivation,
  includeProjectComponentDevDir: Boolean,
  passthrough: Vector[String]
) {
  def classpathFile: Path =
    DevSupport.runtimeClasspathFile(project)

  def runtimeLabel: String =
    runtimeDevDir.map(p => s"dev:${p}").getOrElse(runtimeVersion)
}

trait RuntimeClasspathExporter {
  def exportRuntimeClasspath(project: Path): String
}

object SbtRuntimeClasspathExporter extends RuntimeClasspathExporter {
  def exportRuntimeClasspath(project: Path): String = {
    val out = new StringBuilder
    val err = new StringBuilder
    val code = Process(Vector("sbt", "--batch", "export Runtime / fullClasspath"), project.toFile)
      .!(ProcessLogger(line => out.append(line).append("\n"), line => err.append(line).append("\n")))
    if (code != 0)
      throw CncfException(s"failed to resolve Runtime / fullClasspath for ${project}: ${err.toString.trim}", 2)
    out.toString.linesIterator
      .map(_.trim)
      .find(line => line.startsWith("/") && line.contains(File.pathSeparator))
      .orElse(out.toString.linesIterator.map(_.trim).find(_.startsWith("/")))
      .getOrElse(throw CncfException(s"failed to find classpath in sbt output for ${project}", 2))
  }
}

final class DevSupport(
  paths: LauncherPaths,
  classpathexporter: RuntimeClasspathExporter = SbtRuntimeClasspathExporter,
  processmanager: DevServerProcessManager = DevServerProcessManager.System
) {
  def context(
    options: CncfCommand.DevOptions,
    config: LauncherConfig,
    store: RuntimeVersionStore
  ): DevContext = {
    val project = _resolve_project(options, config)
    val projectactivation = _resolve_project_activation(project, options)
    val projectdevdirs = projectactivation match {
      case CncfCommand.ProjectActivation.DevDir => Vector(project)
      case _ => Vector.empty
    }
    val projectcomponentdirs = projectactivation match {
      case CncfCommand.ProjectActivation.ComponentDir => Vector(project.resolve("component.d"))
      case _ => Vector.empty
    }
    val devdirs = (projectdevdirs.map(_.toString) ++ config.devComponentDevDirs ++ options.componentDevDirs)
      .map(p => project.resolve(p).normalize)
      .map(_.toAbsolutePath.normalize)
      .distinct
    val componentdirs = projectcomponentdirs
      .map(_.toAbsolutePath.normalize)
      .distinct
    val runtimerequirements =
      (_runtime_requirement(project, "main-target") ++
        devdirs.filterNot(_ == project).flatMap(dir => _runtime_requirement(dir, s"dependency-component:$dir"))).filterNot(_.isEmpty).toVector
    DevContext(
      project = project,
      port = options.port.orElse(config.devPort).getOrElse(LauncherConfig.DEFAULT_DEV_PORT),
      componentDevDirs = devdirs,
      componentDirs = componentdirs,
      runtimeVersion = store.current(options.runtimeVersion, config),
      runtimeRequirements = runtimerequirements,
      runtimeDevDir = _resolve_runtime_dev_dir(project, options, config),
      executionProfile = options.executionProfile.orElse(config.devExecutionProfile),
      runtimeArgs = options.runtimeArgs,
      useProjectClasspath = options.useProjectClasspath,
      projectActivation = projectactivation,
      includeProjectComponentDevDir = options.includeProjectComponentDevDir,
      passthrough = options.passthrough
    )
  }

  def writeRuntimeClasspath(context: DevContext): Path = {
    val classpath = classpathexporter.exportRuntimeClasspath(context.project)
    val entries = _classpath_entries(classpath)
    if (entries.isEmpty)
      throw CncfException(s"Runtime / fullClasspath was empty for ${context.project}")
    if (!entries.exists(Files.isDirectory(_)))
      throw CncfException(s"Runtime / fullClasspath contains no class directories for ${context.project}; run sbt --batch compile first")
    Files.createDirectories(context.classpathFile.getParent)
    Files.writeString(context.classpathFile, classpath + "\n", StandardCharsets.UTF_8)
    context.classpathFile
  }

  def check(context: DevContext): Vector[DevCheckItem] = {
    val classpath =
      if (context.useProjectClasspath) _check_classpath(context)
      else Vector(DevCheckItem.ok("runtime-classpath", "project classpath disabled"))
    val descriptors = _check_descriptors(context.project)
    val webapproots = _check_web_app_roots(context.project)
    val webdescriptorsources = _check_web_descriptor_sources(context.project)
    val webdescriptors = _check_web_descriptors(context.project)
    val runtimedevdir = context.runtimeDevDir.toVector.flatMap(_check_runtime_dev_dir)
    val executionprofile = Vector(_check_execution_profile(context))
    val target = Vector(
      DevCheckItem.ok("main-target", s"source=local-project project=${context.project}"),
      DevCheckItem.ok("main-target-repository-lookup", "disabled in dev mode")
    )
    val dependencyresolution = Vector(
      DevCheckItem.ok("dependency-components", "local dev overrides from --component-dev-dir/.cncf config; otherwise resolved by CNCF component repositories"),
      _check_local_repository(paths.localRepository),
      _check_local_repository(paths.localCarRepository),
      _check_local_repository(paths.localSarRepository)
    )
    val runtimerequirements =
      if (context.runtimeRequirements.isEmpty)
        Vector(DevCheckItem.ok("runtime-requirements", "none"))
      else
        context.runtimeRequirements.map(r => DevCheckItem.ok("runtime-requirement", _render_requirement(r)))
    val dependencydevdirs = context.componentDevDirs.filterNot(_ == context.project)
    val devdirs = dependencydevdirs.map { dir =>
      val file = DevSupport.runtimeClasspathFile(dir)
      if (Files.isRegularFile(file) && Files.size(file) > 0L)
        DevCheckItem.ok("dependency-component-dev-dir", s"source=local-dev-dir path=${dir} classpath=${file}")
      else
        DevCheckItem.error("dependency-component-dev-dir", s"${dir} missing ${file}; run cncf dev classpath --project ${dir}")
    }
    val componentdirs = context.componentDirs.map { dir =>
      if (Files.isDirectory(dir))
        DevCheckItem.ok("dependency-component-dir", s"source=local-artifact-dir path=${dir}")
      else
        DevCheckItem.error("dependency-component-dir", s"${dir} is not a directory")
    }
    Vector(
      DevCheckItem.ok("project", context.project.toString),
      DevCheckItem.ok("runtime", context.runtimeLabel),
      DevCheckItem.ok("port", context.port)
    ) ++ target ++ _check_dev_server(context) ++ dependencyresolution ++ runtimerequirements ++ runtimedevdir ++ executionprofile ++ classpath ++ descriptors ++ webapproots ++ webdescriptorsources ++ webdescriptors ++ componentdirs ++ devdirs
  }

  def prepareDevServerStart(
    context: DevContext,
    options: CncfCommand.DevOptions
  ): DevServerState = {
    _handle_existing_dev_server(context, options.stopExisting, options.forceExisting)
    val state = DevServerState(
      pid = processmanager.currentPid,
      project = context.project,
      port = context.port,
      runtimeVersion = context.runtimeLabel,
      executionProfile = context.executionProfile.map(_.name),
      startedAt = Instant.now(),
      processStartedAt = processmanager.processStartedAt(processmanager.currentPid),
      commandLine = processmanager.commandLine(processmanager.currentPid),
      command = "cncf dev server"
    )
    _write_dev_server_state(state)
    state
  }

  def cleanupDevServerState(state: DevServerState): Unit = {
    val current = readDevServerState(state.project)
    if (current.exists(_.pid == state.pid))
      _delete_dev_server_state(state.project)
  }

  def stopDevServer(
    context: DevContext,
    forceexisting: Boolean,
    portspecified: Boolean
  ): Int = {
    readDevServerState(context.project) match {
      case Some(state) if !_same_path(state.project, context.project) && processmanager.isAlive(state.pid) && !forceexisting =>
        throw CncfException(
          s"dev server state is for a different project (${state.project}) but pid=${state.pid} is alive; use --force-existing to stop or overwrite it"
        )
      case Some(state) if portspecified && state.port.nonEmpty && state.port != context.port =>
        println(s"dev-server: none for project=${context.project} port=${context.port}; recorded port=${state.port}")
        0
      case Some(state) =>
        _stop_dev_server_state(state, forceexisting)
        _delete_dev_server_state(context.project)
        0
      case None =>
        _delete_dev_server_state(context.project)
        println(s"dev-server: none for project=${context.project} port=${context.port}")
        0
    }
  }

  def readDevServerState(project: Path): Option[DevServerState] = {
    val file = DevSupport.devServerJsonFile(project)
    if (Files.isRegularFile(file)) {
      DevServerState.parse(Files.readString(file, StandardCharsets.UTF_8), project)
    } else {
      val pidfile = DevSupport.devServerPidFile(project)
      if (Files.isRegularFile(pidfile))
        Try(Files.readString(pidfile, StandardCharsets.UTF_8).trim.toLong).toOption.map { pid =>
          DevServerState(
            pid = pid,
            project = project,
            port = "",
            runtimeVersion = "",
            executionProfile = None,
            startedAt = Instant.EPOCH,
            processStartedAt = None,
            commandLine = None,
            command = "cncf dev server"
          )
        }
      else
        None
    }
  }

  def cncfArgs(
    context: DevContext,
    mode: String,
    args: Vector[String]
  ): Vector[String] =
    context.componentDevDirs.flatMap(dir => Vector("--component-dev-dir", dir.toString)) ++
      context.componentDirs.flatMap(dir => Vector("--component-dir", dir.toString)) ++
      _execution_profile_args(context) ++
      context.runtimeArgs ++
      Vector(mode) ++
      args ++
      context.passthrough

  def runtimeClasspath(context: DevContext): Vector[Path] = {
    if (!context.useProjectClasspath) {
      Vector.empty
    } else if (!Files.isRegularFile(context.classpathFile) || Files.size(context.classpathFile) == 0L)
      _runtime_classpath_auto(context)
    else {
      _classpath_entries(Files.readString(context.classpathFile, StandardCharsets.UTF_8).trim)
    }
  }

  def cncfRuntimeClasspath(runtimeproject: Path): Vector[Path] = {
    val file = DevSupport.runtimeClasspathFile(runtimeproject)
    val classpath =
      if (Files.isRegularFile(file) && Files.size(file) > 0L) {
        Files.readString(file, StandardCharsets.UTF_8).trim
      } else {
        val exported = classpathexporter.exportRuntimeClasspath(runtimeproject)
        Files.createDirectories(file.getParent)
        Files.writeString(file, exported + "\n", StandardCharsets.UTF_8)
        exported
      }
    val entries = _classpath_entries(classpath)
    if (entries.isEmpty)
      throw CncfException(s"CNCF Runtime / fullClasspath was empty for ${runtimeproject}")
    entries
  }


  private def _resolve_project_activation(
    project: Path,
    options: CncfCommand.DevOptions
  ): CncfCommand.ProjectActivation =
    if (!options.useProjectClasspath) {
      CncfCommand.ProjectActivation.None
    } else {
      options.projectActivation match {
        case CncfCommand.ProjectActivation.Auto =>
          if (_project_component_dir_has_artifacts(project))
            CncfCommand.ProjectActivation.ComponentDir
          else if (_has_explicit_runtime_source(options.runtimeArgs))
            CncfCommand.ProjectActivation.None
          else if (options.includeProjectComponentDevDir)
            CncfCommand.ProjectActivation.DevDir
          else
            CncfCommand.ProjectActivation.None
        case x => x
      }
    }


  private def _has_explicit_runtime_source(args: Vector[String]): Boolean = {
    val names = args.map(_.takeWhile(_ != '='))
    names.exists { name =>
      Set(
        "--repository-dir",
        "--component-dir",
        "--component-car-dir",
        "--component-sar-dir",
        "--component-factory-class",
        "--subsystem-sar-dir",
        "--subsystem-dir",
        "--subsystem"
      ).contains(name)
    }
  }

  private def _project_component_dir_has_artifacts(project: Path): Boolean = {
    val dir = project.resolve("component.d")
    if (!Files.isDirectory(dir)) {
      false
    } else {
      val stream = Files.list(dir)
      try {
        import scala.jdk.CollectionConverters.*
        stream.iterator().asScala.exists { path =>
          val name = path.getFileName.toString
          Files.isRegularFile(path) && (name.endsWith(".car") || name.endsWith(".sar"))
        }
      } finally {
        stream.close()
      }
    }
  }

  private def _resolve_project(
    options: CncfCommand.DevOptions,
    config: LauncherConfig
  ): Path =
    options.project
      .orElse(config.devProject)
      .map(p => paths.cwd.resolve(p).normalize)
      .getOrElse(paths.cwd)
      .toAbsolutePath
      .normalize

  private def _resolve_runtime_dev_dir(
    project: Path,
    options: CncfCommand.DevOptions,
    config: LauncherConfig
  ): Option[Path] =
    options.runtimeDevDir
      .orElse(config.runtimeDevDir)
      .map(p => project.resolve(p).normalize.toAbsolutePath.normalize)

  private def _check_classpath(context: DevContext): Vector[DevCheckItem] =
    if (!Files.isRegularFile(context.classpathFile)) {
      Vector(DevCheckItem.warning("runtime-classpath", s"missing ${context.classpathFile}; dev server will run cncf dev classpath automatically"))
    } else if (Files.size(context.classpathFile) == 0L) {
      Vector(DevCheckItem.warning("runtime-classpath", s"empty ${context.classpathFile}; dev server will run cncf dev classpath automatically"))
    } else {
      val entries = runtimeClasspath(context)
      val directories = entries.filter(Files.isDirectory(_))
      if (directories.isEmpty)
        Vector(DevCheckItem.error("runtime-classpath", s"no class directories in ${context.classpathFile}; run sbt --batch compile"))
      else
        Vector(DevCheckItem.ok("runtime-classpath", s"${context.classpathFile} (${entries.size} entries)"))
    }

  private def _check_local_repository(path: Path): DevCheckItem =
    if (Files.isDirectory(path))
      DevCheckItem.ok("local-repository", s"path=${path} exists=true")
    else
      DevCheckItem.warning("local-repository", s"path=${path} exists=false; run sbt cozyPublishLocalCar in dependency components")

  private def _check_dev_server(context: DevContext): Vector[DevCheckItem] = {
    val pidfile = DevSupport.devServerPidFile(context.project)
    val jsonfile = DevSupport.devServerJsonFile(context.project)
    readDevServerState(context.project) match {
      case Some(state) =>
        val alive = processmanager.isAlive(state.pid)
        val portlabel = if (state.port.isEmpty) "-" else state.port
        val status = if (alive) "alive" else "stale"
        Vector(DevCheckItem.ok(
          "dev-server",
          s"state=${jsonfile} pidFile=${pidfile} pid=${state.pid} status=${status} project=${state.project} port=${portlabel}"
        ))
      case None =>
        Vector(DevCheckItem.ok("dev-server", s"state=${jsonfile} pidFile=${pidfile} status=none"))
    }
  }

  private def _handle_existing_dev_server(
    context: DevContext,
    stopexisting: Boolean,
    forceexisting: Boolean
  ): Unit =
    readDevServerState(context.project).foreach { state =>
      val alive = processmanager.isAlive(state.pid)
      val verified = !alive || _is_verified_dev_server_state(state)
      val projectmatches = _same_path(state.project, context.project)
      val portmatches = state.port.isEmpty || state.port == context.port
      if (alive && !verified && !forceexisting) {
        throw CncfException(
          s"dev server state is ambiguous for project=${context.project} pid=${state.pid}; use --force-existing to overwrite or stop it"
        )
      } else if (!projectmatches && alive && !forceexisting) {
        throw CncfException(
          s"dev server state is for a different project (${state.project}) but pid=${state.pid} is alive; use --force-existing to overwrite it"
        )
      } else if (alive && !portmatches && !forceexisting) {
        throw CncfException(
          s"dev server already running for project=${context.project} on recorded port=${state.port}; use --force-existing to overwrite state for port=${context.port}"
        )
      } else if (!portmatches) {
        println(s"warning: dev server state for recorded port=${state.port} will be overwritten for port=${context.port}")
        _delete_dev_server_state(context.project)
      } else if (alive && state.port.isEmpty && !forceexisting) {
        throw CncfException(
          s"dev server state is ambiguous for project=${context.project} pid=${state.pid}; use --force-existing to overwrite or stop it"
        )
      } else if (alive && !stopexisting) {
        throw CncfException(
          s"dev server already running for project=${context.project} port=${context.port} pid=${state.pid}; use --stop-existing or --restart"
        )
      } else if (alive) {
        _stop_dev_server_state(state, forceexisting)
        _delete_dev_server_state(context.project)
      } else {
        println(s"warning: stale dev server state found at ${DevSupport.devServerJsonFile(context.project)}; overwriting")
        _delete_dev_server_state(context.project)
      }
    }

  private def _stop_dev_server_state(
    state: DevServerState,
    forceexisting: Boolean
  ): Unit = {
    val alive = processmanager.isAlive(state.pid)
    val verified = !alive || _is_verified_dev_server_state(state)
    if (!alive) {
      println(s"warning: stale dev server state for pid=${state.pid}; removing state")
    } else if (!verified && !forceexisting) {
      throw CncfException(
        s"dev server state is ambiguous for project=${state.project} pid=${state.pid}; use --force-existing to stop it"
      )
    } else if (state.port.isEmpty && !forceexisting) {
      throw CncfException(
        s"dev server state is ambiguous for project=${state.project} pid=${state.pid}; use --force-existing to stop it"
      )
    } else {
      val graceful = processmanager.stopGracefully(state.pid)
      if (graceful) {
        println(s"stopped dev server pid=${state.pid} project=${state.project} port=${state.port}")
      } else if (forceexisting && processmanager.stopForcibly(state.pid)) {
        println(s"force-stopped dev server pid=${state.pid} project=${state.project} port=${state.port}")
      } else {
        throw CncfException(
          s"failed to stop dev server pid=${state.pid} project=${state.project} port=${state.port}; use --force-existing to allow force stop"
        )
      }
    }
  }

  private def _write_dev_server_state(state: DevServerState): Unit = {
    val pidfile = DevSupport.devServerPidFile(state.project)
    val jsonfile = DevSupport.devServerJsonFile(state.project)
    Files.createDirectories(pidfile.getParent)
    Files.writeString(pidfile, s"${state.pid}\n", StandardCharsets.UTF_8)
    Files.writeString(jsonfile, state.renderJson + "\n", StandardCharsets.UTF_8)
  }

  private def _delete_dev_server_state(project: Path): Unit = {
    Files.deleteIfExists(DevSupport.devServerPidFile(project))
    Files.deleteIfExists(DevSupport.devServerJsonFile(project))
  }

  private def _same_path(
    lhs: Path,
    rhs: Path
  ): Boolean =
    lhs.toAbsolutePath.normalize == rhs.toAbsolutePath.normalize

  private def _is_verified_dev_server_state(state: DevServerState): Boolean =
    state.processStartedAt.exists { expected =>
      processmanager.processStartedAt(state.pid).contains(expected)
    }

  private def _runtime_classpath_auto(
    context: DevContext
  ): Vector[Path] = {
    try {
      writeRuntimeClasspath(context)
      runtimeClasspath(context)
    } catch {
      case e: CncfException =>
        throw CncfException(
          s"failed to prepare main target runtime classpath for ${context.project}; run cncf dev classpath --project ${context.project}. cause=${e.getMessage}",
          e.code
        )
      case e: Throwable =>
        throw CncfException(
          s"failed to prepare main target runtime classpath for ${context.project}; run cncf dev classpath --project ${context.project}. cause=${e.getMessage}",
          2
        )
    }
  }

  private def _check_runtime_dev_dir(dir: Path): Vector[DevCheckItem] = {
    val file = DevSupport.runtimeClasspathFile(dir)
    if (!Files.isDirectory(dir)) {
      Vector(DevCheckItem.error("runtime-dev-dir", s"${dir} is not a directory"))
    } else if (Files.isRegularFile(file) && Files.size(file) > 0L) {
      Vector(DevCheckItem.ok("runtime-dev-dir", s"${dir} (${file})"))
    } else {
      Vector(DevCheckItem.warning("runtime-dev-dir", s"${dir} missing ${file}; dev invocation will run sbt export Runtime / fullClasspath"))
    }
  }

  private def _check_execution_profile(context: DevContext): DevCheckItem =
    context.executionProfile match {
      case Some(CncfCommand.DevExecutionProfile.LocalPersistent) =>
        DevCheckItem.ok("execution-profile", s"local-persistent datastore=${_local_persistent_sqlite_path(context)}")
      case None =>
        DevCheckItem.ok("execution-profile", "default")
    }

  private def _execution_profile_args(context: DevContext): Vector[String] =
    context.executionProfile match {
      case Some(CncfCommand.DevExecutionProfile.LocalPersistent) =>
        val path = _local_persistent_sqlite_path(context)
        Files.createDirectories(path.getParent)
        Vector(
          s"--textus.datastore.sqlite.path=${path}",
          s"--cncf.datastore.sqlite.path=${path}",
          "--textus.datastore.sqlite.normalize-column-names=true",
          "--cncf.datastore.sqlite.normalize-column-names=true"
        )
      case None =>
        Vector.empty
    }

  private def _local_persistent_sqlite_path(context: DevContext): Path =
    context.project.resolve("target").resolve("cncf.d").resolve("runtime.sqlite").toAbsolutePath.normalize

  private def _runtime_requirement(
    project: Path,
    source: String
  ): Option[RuntimeRequirement] = {
    val file = project.resolve("project.yaml")
    if (!Files.isRegularFile(file)) {
      None
    } else {
      val values = SimpleYaml.parse(Files.readString(file, StandardCharsets.UTF_8))
      val requirement = RuntimeRequirement(
        minimum = _first(values, "packaging.car.runtime.cncf.minimum"),
        maximum = _first(values, "packaging.car.runtime.cncf.maximum"),
        excluded = _list(values, "packaging.car.runtime.cncf.excluded"),
        tested = _list(values, "packaging.car.runtime.cncf.tested"),
        source = source
      )
      Option.when(!requirement.isEmpty)(requirement)
    }
  }

  private def _first(values: Map[String, Vector[String]], key: String): Option[String] =
    values.getOrElse(key, Vector.empty).headOption.map(_.trim).filter(_.nonEmpty)

  private def _list(values: Map[String, Vector[String]], key: String): Vector[String] =
    values.getOrElse(key, Vector.empty).flatMap { value =>
      val clean = value.trim
      if (clean.isEmpty || clean == "[]")
        Vector.empty
      else if (clean.startsWith("[") && clean.endsWith("]"))
        clean.stripPrefix("[").stripSuffix("]").split(",").toVector.map(_unquote).map(_.trim).filter(_.nonEmpty)
      else
        Vector(_unquote(clean))
    }

  private def _unquote(value: String): String =
    value.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")

  private def _render_requirement(requirement: RuntimeRequirement): String =
    s"${requirement.source} minimum=${requirement.minimum.getOrElse("-")} maximum=${requirement.maximum.getOrElse("-")} excluded=${requirement.excluded.mkString("[", ",", "]")} tested=${requirement.tested.mkString("[", ",", "]")}"

  private def _check_descriptors(project: Path): Vector[DevCheckItem] = {
    val dirs = Vector(project.resolve("car.d"), project.resolve("src").resolve("main").resolve("car"))
    val found = dirs.filter(Files.isDirectory(_))
    if (found.isEmpty)
      Vector(DevCheckItem.warning("descriptor", "no car.d or src/main/car directory found"))
    else
      found.map(dir => DevCheckItem.ok("descriptor", dir.toString))
  }

  private def _check_web_app_roots(project: Path): Vector[DevCheckItem] = {
    val roots = Vector(project.resolve("src").resolve("main").resolve("web"), project.resolve("web"))
    val found = roots.filter(Files.isDirectory(_))
    if (found.isEmpty)
      Vector(DevCheckItem.ok("web-app-root", "none"))
    else
      found.map(dir => DevCheckItem.ok("web-app-root", dir.toString))
  }

  private def _check_web_descriptors(project: Path): Vector[DevCheckItem] = {
    val roots = Vector(project.resolve("car.d").resolve("web"), project.resolve("src").resolve("main").resolve("car").resolve("web"))
    val found = roots.filter(Files.isDirectory(_))
    if (found.isEmpty)
      Vector(DevCheckItem.ok("web-descriptor", "none"))
    else
      found.map(dir => DevCheckItem.warning("web-descriptor", s"legacy-car-runtime-metadata path=${dir}; prefer src/main/web-inf source metadata"))
  }

  private def _check_web_descriptor_sources(project: Path): Vector[DevCheckItem] = {
    val root = project.resolve("src").resolve("main").resolve("web-inf")
    val files = Vector("web.yaml", "form.yaml", "admin.yaml").map(root.resolve).filter(Files.isRegularFile(_))
    if (Files.isDirectory(root) || files.nonEmpty)
      DevCheckItem.ok("web-descriptor-source", s"path=${root} files=${files.map(_.getFileName.toString).mkString("[", ",", "]")}") +:
        _check_legacy_web_descriptor_sources(project)
    else
      DevCheckItem.ok("web-descriptor-source", "none; use src/main/web-inf/web.yaml|form.yaml|admin.yaml") +:
        _check_legacy_web_descriptor_sources(project)
  }

  private def _check_legacy_web_descriptor_sources(project: Path): Vector[DevCheckItem] = {
    val legacy = Vector(
      project.resolve("src").resolve("main").resolve("web").resolve("WEB-INF").resolve("web.yaml"),
      project.resolve("src").resolve("main").resolve("web").resolve("WEB-INF").resolve("form.yaml"),
      project.resolve("src").resolve("main").resolve("web").resolve("WEB-INF").resolve("admin.yaml"),
      project.resolve("src").resolve("main").resolve("form").resolve("form.yaml")
    ).filter(Files.isRegularFile(_))
    legacy.map(path => DevCheckItem.warning("web-descriptor-source", s"legacy descriptor source ignored by new scaffold policy: ${path}"))
  }

  private def _classpath_entries(value: String): Vector[Path] =
    value
      .split(File.pathSeparator)
      .toVector
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Path.of(_))
}

object DevSupport {
  def runtimeClasspathFile(project: Path): Path =
    project.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt")

  def devServerPidFile(project: Path): Path =
    project.resolve("target").resolve("cncf.d").resolve("dev-server.pid")

  def devServerJsonFile(project: Path): Path =
    project.resolve("target").resolve("cncf.d").resolve("dev-server.json")
}

final case class DevServerState(
  pid: Long,
  project: Path,
  port: String,
  runtimeVersion: String,
  executionProfile: Option[String],
  startedAt: Instant,
  processStartedAt: Option[Instant],
  commandLine: Option[String],
  command: String
) {
  def renderJson: String =
    Vector(
      "{",
      s"""  "pid": ${pid},""",
      s"""  "project": "${DevServerState.escape(project.toString)}",""",
      s"""  "port": "${DevServerState.escape(port)}",""",
      s"""  "runtimeVersion": "${DevServerState.escape(runtimeVersion)}",""",
      executionProfile.map(v => s"""  "executionProfile": "${DevServerState.escape(v)}",""").getOrElse("""  "executionProfile": null,"""),
      s"""  "startedAt": "${startedAt.toString}",""",
      processStartedAt.map(v => s"""  "processStartedAt": "${v.toString}",""").getOrElse("""  "processStartedAt": null,"""),
      commandLine.map(v => s"""  "commandLine": "${DevServerState.escape(v)}",""").getOrElse("""  "commandLine": null,"""),
      s"""  "command": "${DevServerState.escape(command)}"""",
      "}"
    ).mkString("\n")
}

object DevServerState {
  def parse(
    text: String,
    fallbackproject: Path
  ): Option[DevServerState] =
    for {
      pid <- _json_long(text, "pid")
    } yield {
      val project = _json_string(text, "project").map(Path.of(_)).getOrElse(fallbackproject)
      DevServerState(
        pid = pid,
        project = project.toAbsolutePath.normalize,
        port = _json_string(text, "port").getOrElse(""),
        runtimeVersion = _json_string(text, "runtimeVersion").getOrElse(""),
        executionProfile = _json_string(text, "executionProfile"),
        startedAt = _json_string(text, "startedAt").flatMap(v => Try(Instant.parse(v)).toOption).getOrElse(Instant.EPOCH),
        processStartedAt = _json_string(text, "processStartedAt").flatMap(v => Try(Instant.parse(v)).toOption),
        commandLine = _json_string(text, "commandLine"),
        command = _json_string(text, "command").getOrElse("cncf dev server")
      )
    }

  def escape(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

  private def _json_string(
    text: String,
    key: String
  ): Option[String] = {
    val pattern = ("\\\"" + key + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").r
    pattern.findFirstMatchIn(text).map(_.group(1).replace("\\\"", "\"").replace("\\\\", "\\"))
  }

  private def _json_long(
    text: String,
    key: String
  ): Option[Long] = {
    val pattern = ("\\\"" + key + "\\\"\\s*:\\s*([0-9]+)").r
    pattern.findFirstMatchIn(text).flatMap(m => Try(m.group(1).toLong).toOption)
  }
}

final case class DevCheckItem(
  status: String,
  name: String,
  detail: String
) {
  def isError: Boolean =
    status == "ERROR"

  def render: String =
    f"$status%-7s $name $detail"
}

object DevCheckItem {
  def ok(name: String, detail: String): DevCheckItem =
    DevCheckItem("OK", name, detail)

  def warning(name: String, detail: String): DevCheckItem =
    DevCheckItem("WARN", name, detail)

  def error(name: String, detail: String): DevCheckItem =
    DevCheckItem("ERROR", name, detail)
}
