package cncf.launcher

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.sys.process.*

/*
 * @since   May. 17, 2026
 * @version May. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final case class DevContext(
  project: Path,
  port: String,
  componentDevDirs: Vector[Path],
  runtimeVersion: String,
  passthrough: Vector[String]
) {
  def classpathFile: Path =
    project.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt")
}

final class DevSupport(paths: LauncherPaths) {
  def context(
    options: CncfCommand.DevOptions,
    config: LauncherConfig,
    store: RuntimeVersionStore
  ): DevContext = {
    val project = _resolve_project(options, config)
    val devdirs = (Vector(project.toString) ++ config.devComponentDevDirs ++ options.componentDevDirs)
      .map(p => project.resolve(p).normalize)
      .map(_.toAbsolutePath.normalize)
      .distinct
    DevContext(
      project = project,
      port = options.port.orElse(config.devPort).getOrElse(LauncherConfig.DEFAULT_DEV_PORT),
      componentDevDirs = devdirs,
      runtimeVersion = store.current(options.runtimeVersion, config),
      passthrough = options.passthrough
    )
  }

  def writeRuntimeClasspath(context: DevContext): Path = {
    val classpath = exportRuntimeClasspath(context.project)
    val entries = _classpath_entries(classpath)
    if (entries.isEmpty)
      throw CncfException(s"Runtime / fullClasspath was empty for ${context.project}")
    if (!entries.exists(Files.isDirectory(_)))
      throw CncfException(s"Runtime / fullClasspath contains no class directories for ${context.project}; run sbt --batch compile first")
    Files.createDirectories(context.classpathFile.getParent)
    Files.writeString(context.classpathFile, classpath + "\n", StandardCharsets.UTF_8)
    context.classpathFile
  }

  def exportRuntimeClasspath(project: Path): String = {
    val out = new StringBuilder
    val err = new StringBuilder
    val code = Process(Vector("sbt", "--batch", "export Runtime / fullClasspath"), project.toFile)
      .!(ProcessLogger(out append _, err append _))
    if (code != 0)
      throw CncfException(s"failed to resolve Runtime / fullClasspath for ${project}: ${err.toString.trim}", 2)
    out.toString.linesIterator
      .map(_.trim)
      .find(line => line.startsWith("/") && line.contains(File.pathSeparator))
      .orElse(out.toString.linesIterator.map(_.trim).find(_.startsWith("/")))
      .getOrElse(throw CncfException(s"failed to find classpath in sbt output for ${project}", 2))
  }

  def check(context: DevContext): Vector[DevCheckItem] = {
    val classpath = _check_classpath(context)
    val descriptors = _check_descriptors(context.project)
    val webroots = _check_web_roots(context.project)
    val devdirs = context.componentDevDirs.map { dir =>
      val file = dir.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt")
      if (Files.isRegularFile(file) && Files.size(file) > 0L)
        DevCheckItem.ok("component-dev-dir", s"${dir} (${file})")
      else
        DevCheckItem.error("component-dev-dir", s"${dir} missing ${file}; run cncf dev classpath --project ${dir}")
    }
    Vector(
      DevCheckItem.ok("project", context.project.toString),
      DevCheckItem.ok("runtime", context.runtimeVersion),
      DevCheckItem.ok("port", context.port)
    ) ++ classpath ++ descriptors ++ webroots ++ devdirs
  }

  def cncfArgs(
    context: DevContext,
    mode: String,
    args: Vector[String]
  ): Vector[String] =
    context.componentDevDirs.flatMap(dir => Vector("--component-dev-dir", dir.toString)) ++
      Vector(
        "--no-exit",
        s"--cncf.server.port=${context.port}",
        s"--cncf.http.baseurl=http://127.0.0.1:${context.port}"
      ) ++
      Vector(mode) ++
      args ++
      context.passthrough

  def runtimeClasspath(context: DevContext): Vector[Path] = {
    if (!Files.isRegularFile(context.classpathFile) || Files.size(context.classpathFile) == 0L)
      throw CncfException(
        s"runtime classpath is not prepared: ${context.classpathFile}. Run: cncf dev classpath --project ${context.project}"
      )
    _classpath_entries(Files.readString(context.classpathFile, StandardCharsets.UTF_8).trim)
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

  private def _check_classpath(context: DevContext): Vector[DevCheckItem] =
    if (!Files.isRegularFile(context.classpathFile)) {
      Vector(DevCheckItem.error("runtime-classpath", s"missing ${context.classpathFile}; run cncf dev classpath"))
    } else if (Files.size(context.classpathFile) == 0L) {
      Vector(DevCheckItem.error("runtime-classpath", s"empty ${context.classpathFile}; run cncf dev classpath"))
    } else {
      val entries = runtimeClasspath(context)
      val directories = entries.filter(Files.isDirectory(_))
      if (directories.isEmpty)
        Vector(DevCheckItem.error("runtime-classpath", s"no class directories in ${context.classpathFile}; run sbt --batch compile"))
      else
        Vector(DevCheckItem.ok("runtime-classpath", s"${context.classpathFile} (${entries.size} entries)"))
    }

  private def _check_descriptors(project: Path): Vector[DevCheckItem] = {
    val dirs = Vector(project.resolve("car.d"), project.resolve("src").resolve("main").resolve("car"))
    val found = dirs.filter(Files.isDirectory(_))
    if (found.isEmpty)
      Vector(DevCheckItem.warning("descriptor", "no car.d or src/main/car directory found"))
    else
      found.map(dir => DevCheckItem.ok("descriptor", dir.toString))
  }

  private def _check_web_roots(project: Path): Vector[DevCheckItem] = {
    val roots = Vector(project.resolve("car.d").resolve("web"), project.resolve("src").resolve("main").resolve("web"), project.resolve("web"))
    val found = roots.filter(Files.isDirectory(_))
    if (found.isEmpty)
      Vector(DevCheckItem.warning("web-root", "no car.d/web, src/main/web, or web directory found"))
    else
      found.map(dir => DevCheckItem.ok("web-root", dir.toString))
  }

  private def _classpath_entries(value: String): Vector[Path] =
    value
      .split(File.pathSeparator)
      .toVector
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Path.of(_))
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
