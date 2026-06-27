package cncf.launcher

import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.jar.JarFile
import scala.sys.process.*
import scala.util.Try
import scala.util.Using
import org.goldenport.launcher.{LauncherDevInvoker => CoreLauncherDevInvoker}

/*
 * @since   May. 17, 2026
 *  version May. 18, 2026
 * @version Jun. 27, 2026
 * @author  ASAMI, Tomoharu
 */
trait CncfRuntimeResolver {
  def resolveVersion(version: String, config: LauncherConfig, paths: LauncherPaths): String =
    version

  def resolve(version: String, config: LauncherConfig, paths: LauncherPaths): Vector[Path]
}

final class CoursierCncfRuntimeResolver(
  coursiercommand: String = sys.env.getOrElse("CNCF_COURSIER_COMMAND", "cs")
) extends CncfRuntimeResolver {
  override def resolveVersion(version: String, config: LauncherConfig, paths: LauncherPaths): String =
    _catalog_version(version, config, paths) match {
      case Some(v) =>
        v.warnIfDeprecated()
        v.version
      case None =>
        _fallback_version(version, config)
    }

  def resolve(version: String, config: LauncherConfig, paths: LauncherPaths): Vector[Path] = {
    val catalog = RuntimeCatalogStore(paths).loadOrRefresh(config)
    val catalogversion = _catalog_version(version, config, paths, catalog)
    val concreteversion =
      catalogversion match {
        case Some(v) =>
          v.warnIfDeprecated()
          v.version
        case None =>
          _fallback_version(version, config)
      }
    val metadata = paths.runtimeRoot.resolve(concreteversion).resolve("classpath.txt")
    if (Files.isRegularFile(metadata)) {
      _read_classpath(metadata)
    } else {
      Files.createDirectories(metadata.getParent)
      Files.createDirectories(paths.coursierCache)
      val module = catalogversion.map(_.moduleCoordinate).getOrElse(s"org.goldenport:goldenport-cncf_3:$concreteversion")
      val effectiveconfig = catalog.map(config.withCatalog).getOrElse(config)
      val repositories = (effectiveconfig.coursierRepositories ++ effectiveconfig.mavenRepositories).distinct.flatMap(r => Vector("-r", r))
      val command =
        Vector(coursiercommand, "fetch", "--classpath", "--cache", paths.coursierCache.toString) ++
          repositories ++
          Vector(module)
      val out = new StringBuilder
      val err = new StringBuilder
      val code = Process(command).!(ProcessLogger(out append _, err append _))
      if (code != 0) {
        throw CncfException(
          s"failed to resolve CNCF runtime $concreteversion with Coursier: ${err.toString.trim}",
          2
        )
      }
      val classpath = out.toString.trim
      if (classpath.isEmpty)
        throw CncfException(s"Coursier returned an empty classpath for CNCF runtime $concreteversion", 2)
      Files.writeString(metadata, classpath + "\n", StandardCharsets.UTF_8)
      _classpath_to_paths(classpath)
    }
  }

  private def _catalog_version(
    version: String,
    config: LauncherConfig,
    paths: LauncherPaths
  ): Option[RuntimeCatalogVersion] =
    _catalog_version(version, config, paths, RuntimeCatalogStore(paths).loadOrRefresh(config))

  private def _catalog_version(
    version: String,
    config: LauncherConfig,
    paths: LauncherPaths,
    catalog: Option[RuntimeCatalog]
  ): Option[RuntimeCatalogVersion] =
    catalog.flatMap { c =>
      Try(c.resolve(version)).toOption.orElse {
        if (_is_dynamic_runtime_selector(version))
          Some(c.resolve(version))
        else
          None
      }
    }

  private def _fallback_version(version: String, config: LauncherConfig): String =
    version match {
      case "latest" | "latest-stable" | "latest.release" =>
        _latest_release(config)
      case "latest-snapshot" =>
        _latest_snapshot(config)
      case "newest" =>
        _newest(config)
      case "recommended" =>
        throw CncfException("failed to resolve recommended CNCF runtime version from runtime catalog")
      case x =>
        x
    }

  private def _is_dynamic_runtime_selector(selector: String): Boolean =
    selector match {
      case "recommended" | "latest" | "latest-stable" | "latest.release" | "latest-snapshot" | "newest" => true
      case _ => false
    }

  private def _newest(config: LauncherConfig): String =
    config.mavenRepositories.iterator.flatMap(_metadata_versions).nextOption()
      .getOrElse(throw CncfException("failed to resolve newest CNCF runtime version from Maven repositories"))

  private def _latest_release(config: LauncherConfig): String =
    config.mavenRepositories.iterator.flatMap(_metadata_versions).find(!_.endsWith("-SNAPSHOT"))
      .getOrElse(throw CncfException("failed to resolve latest CNCF runtime version from Maven repositories"))

  private def _latest_snapshot(config: LauncherConfig): String =
    config.mavenRepositories.iterator.flatMap(_metadata_versions).find(_.endsWith("-SNAPSHOT"))
      .getOrElse(throw CncfException("failed to resolve latest snapshot CNCF runtime version from Maven repositories"))

  private def _metadata_versions(repository: String): Vector[String] = {
    val url = _join(repository, "org", "goldenport", "goldenport-cncf_3", "maven-metadata.xml")
    try {
      val connection = URI.create(url).toURL.openConnection()
      connection.setConnectTimeout(2000)
      connection.setReadTimeout(5000)
      val text = Using.resource(scala.io.Source.fromInputStream(connection.getInputStream, "UTF-8"))(_.mkString)
      val latest = _first_tag(text, "latest").orElse(_first_tag(text, "release")).toVector
      val versions = "<version>([^<]+)</version>".r.findAllMatchIn(text).map(_.group(1).trim).filter(_.nonEmpty).toVector.reverse
      (latest ++ versions).distinct
    } catch {
      case _: Throwable => Vector.empty
    }
  }

  private def _read_classpath(path: Path): Vector[Path] =
    _classpath_to_paths(Files.readString(path, StandardCharsets.UTF_8).trim)

  private def _classpath_to_paths(value: String): Vector[Path] =
    value.split(File.pathSeparator).toVector.map(_.trim).filter(_.nonEmpty).map(Path.of(_))

  private def _join(parts: String*): String =
    parts.toVector.zipWithIndex.map { case (p, idx) =>
      if (idx == 0) p.reverse.dropWhile(_ == '/').reverse
      else p.dropWhile(_ == '/').reverse.dropWhile(_ == '/').reverse
    }.mkString("/")

  private def _first_tag(text: String, tag: String): Option[String] =
    s"<$tag>([^<]+)</$tag>".r.findFirstMatchIn(text).map(_.group(1).trim).filter(_.nonEmpty)
}

class CncfInvoker {
  def invoke(classpath: Vector[Path], args: Vector[String]): Int = {
    val urls = classpath.map(_.toUri.toURL).toArray
    val parent = ClassLoader.getPlatformClassLoader
    val loader = new java.net.URLClassLoader(urls, parent)
    val old = Thread.currentThread().getContextClassLoader
    val oldclasspath = sys.props.get("java.class.path")
    try {
      Thread.currentThread().setContextClassLoader(loader)
      sys.props.update("java.class.path", classpath.map(_.toString).mkString(java.io.File.pathSeparator))
      val mainclass = Class.forName("org.goldenport.cncf.CncfMain", true, loader)
      val main = mainclass.getMethod("main", classOf[Array[String]])
      main.invoke(null, args.toArray)
      0
    } catch {
      case e: java.lang.reflect.InvocationTargetException =>
        val cause = Option(e.getCause).getOrElse(e)
        val clifailed =
          cause.getClass.getName == "org.goldenport.cncf.CncfMain$CliFailed"
        if (clifailed) {
          val method = cause.getClass.getMethod("code")
          method.invoke(cause).asInstanceOf[Int]
        } else {
          throw cause
        }
    } finally {
      oldclasspath match {
        case Some(value) => sys.props.update("java.class.path", value)
        case None => sys.props.remove("java.class.path")
      }
      Thread.currentThread().setContextClassLoader(old)
      loader.close()
    }
  }
}

trait LauncherDevInvoker {
  def invoke(devdir: Path, args: Vector[String], cwd: Path): Int
}

object LauncherDevInvoker {
  object System extends LauncherDevInvoker {
    def invoke(devdir: Path, args: Vector[String], cwd: Path): Int = {
      if (!Files.isDirectory(devdir))
        throw CncfException(s"cncf launcher development directory not found: ${devdir}")
      val classpath = _launcher_classpath(devdir)
      CoreLauncherDevInvoker.invokeJavaMain(
        productname = "cncf",
        devdir = devdir,
        classpath = classpath,
        mainclass = "cncf.launcher.CncfLauncherMain",
        args = args,
        cwd = cwd,
        environment = Map("CNCF_LAUNCHER_DEV_DELEGATED" -> "1"),
        argsFileEnvironmentKey = "CNCF_LAUNCHER_ARGS_FILE"
      )
    }

    private def _launcher_classpath(devdir: Path): String = {
      val file = devdir.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt")
      if (!Files.isRegularFile(file))
        throw CncfException(s"cncf launcher development classpath not found: ${file}; run cncf dev classpath in ${devdir}")
      val value = Files.readString(file, StandardCharsets.UTF_8).trim
      if (value.isEmpty)
        throw CncfException(s"cncf launcher development classpath is empty: ${file}")
      if (!_contains_launcher_main(value))
        throw CncfException(s"cncf launcher development classpath does not contain cncf.launcher.CncfLauncherMain: ${file}; run sbt --batch compile and cncf dev classpath in ${devdir}")
      value
    }

    private def _contains_launcher_main(classpath: String): Boolean =
      classpath.split(File.pathSeparator).toVector.map(_.trim).filter(_.nonEmpty).exists { entry =>
        val path = Path.of(entry)
        if (Files.isDirectory(path))
          Files.isRegularFile(path.resolve("cncf").resolve("launcher").resolve("CncfLauncherMain.class"))
        else if (Files.isRegularFile(path) && entry.endsWith(".jar"))
          _jar_contains_launcher_main(path)
        else
          false
      }

    private def _jar_contains_launcher_main(path: Path): Boolean =
      Try {
        Using.resource(new JarFile(path.toFile)) { jar =>
          jar.getEntry("cncf/launcher/CncfLauncherMain.class") != null
        }
      }.getOrElse(false)

  }
}
