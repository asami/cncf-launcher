package cncf.launcher

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.sys.process.*

/*
 * @since   May. 17, 2026
 * @version May. 21, 2026
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
  classpathexporter: RuntimeClasspathExporter = SbtRuntimeClasspathExporter
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
    val target = Vector(
      DevCheckItem.ok("main-target", s"source=local-project project=${context.project}"),
      DevCheckItem.ok("main-target-repository-lookup", "disabled in dev mode")
    )
    val dependencyresolution = Vector(
      DevCheckItem.ok("dependency-components", "local dev overrides from --component-dev-dir/.cncf config; otherwise resolved by CNCF component repositories")
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
    ) ++ target ++ dependencyresolution ++ runtimerequirements ++ runtimedevdir ++ classpath ++ descriptors ++ webapproots ++ webdescriptorsources ++ webdescriptors ++ componentdirs ++ devdirs
  }

  def cncfArgs(
    context: DevContext,
    mode: String,
    args: Vector[String]
  ): Vector[String] =
    context.componentDevDirs.flatMap(dir => Vector("--component-dev-dir", dir.toString)) ++
      context.componentDirs.flatMap(dir => Vector("--component-dir", dir.toString)) ++
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
