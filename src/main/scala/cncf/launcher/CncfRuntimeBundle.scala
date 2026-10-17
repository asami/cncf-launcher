package cncf.launcher

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.ZipFile

import scala.util.Try

/*
 * @since   Sep.  2, 2026
 * @version Sep.  2, 2026
 * @author  ASAMI, Tomoharu
 */
private[launcher] final case class CncfRuntimeBundle(
  directory: Path,
  classpath: Vector[Path],
  version: String
)

private[launcher] object CncfRuntimeBundle {
  private val _descriptor_path = "META-INF/cncf/runtime.yaml"

  def read(selecteddir: Path): CncfRuntimeBundle = {
    val directory = _canonical_directory(selecteddir, "runtime bundle directory")
    val libdirectory = _canonical_directory(directory.resolve("lib"), "runtime bundle lib directory")
    if (!libdirectory.startsWith(directory))
      throw CncfException(s"runtime bundle lib directory escapes bundle: ${libdirectory}")
    val classpathfile = directory.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt")
    if (!Files.isRegularFile(classpathfile))
      throw CncfException(s"runtime bundle classpath not found: ${classpathfile}")
    val classpath = _classpath(classpathfile, directory, libdirectory)
    val version = _runtime_version(classpath)
    CncfRuntimeBundle(directory, classpath, version)
  }

  private def _canonical_directory(path: Path, label: String): Path =
    Try(path.toRealPath()).toOption.filter(path => Files.isDirectory(path)).getOrElse(
      throw CncfException(s"${label} is not a directory: ${path}")
    )

  private def _classpath(classpathfile: Path, directory: Path, libdirectory: Path): Vector[Path] = {
    val text = Try(Files.readString(classpathfile, StandardCharsets.UTF_8)).getOrElse(
      throw CncfException(s"runtime bundle classpath cannot be read: ${classpathfile}")
    ).trim
    if (text.isEmpty)
      throw CncfException(s"runtime bundle classpath is empty: ${classpathfile}")
    val entries = text.split(java.util.regex.Pattern.quote(File.pathSeparator), -1).toVector
    if (entries.exists(_.trim.isEmpty))
      throw CncfException(s"runtime bundle classpath contains an empty entry: ${classpathfile}")
    entries.map { entry =>
      val candidate = Try(Path.of(entry.trim)).toOption.getOrElse(
        throw CncfException(s"runtime bundle classpath contains a malformed path: ${entry}")
      )
      val resolved = if (candidate.isAbsolute) candidate else directory.resolve(candidate)
      val jar = Try(resolved.toRealPath()).toOption.getOrElse(
        throw CncfException(s"runtime bundle classpath entry does not exist: ${entry}")
      )
      if (!Files.isRegularFile(jar) || !jar.getFileName.toString.toLowerCase.endsWith(".jar"))
        throw CncfException(s"runtime bundle classpath entry is not a regular JAR: ${entry}")
      if (!jar.startsWith(libdirectory))
        throw CncfException(s"runtime bundle classpath entry escapes lib: ${entry}")
      jar
    }
  }

  private def _runtime_version(classpath: Vector[Path]): String = {
    val descriptors = classpath.flatMap(_runtime_descriptor)
    if (descriptors.size != 1)
      throw CncfException(s"runtime bundle must contain exactly one ${_descriptor_path} descriptor")
    val values = Try(SimpleYaml.parse(descriptors.head)).toOption.getOrElse(
      throw CncfException("runtime bundle descriptor is malformed")
    )
    val schema = _required(values, "schemaVersion")
    val runtime = _required(values, "runtime")
    val version = _required(values, "version")
    if (schema != "1") throw CncfException(s"unsupported runtime bundle descriptor schemaVersion: ${schema}")
    if (runtime != "cncf") throw CncfException(s"runtime bundle descriptor runtime must be cncf: ${runtime}")
    version
  }

  private def _runtime_descriptor(jar: Path): Option[String] =
    Try {
      val zip = new ZipFile(jar.toFile)
      try {
        Option(zip.getEntry(_descriptor_path)).map { entry =>
          val in = zip.getInputStream(entry)
          try new String(in.readAllBytes(), StandardCharsets.UTF_8)
          finally in.close()
        }
      } finally {
        zip.close()
      }
    }.getOrElse(throw CncfException(s"runtime bundle JAR cannot be read: ${jar}"))

  private def _required(values: Map[String, Vector[String]], key: String): String =
    values.getOrElse(key, Vector.empty).headOption.map(_.trim).filter(_.nonEmpty).getOrElse(
      throw CncfException(s"runtime bundle descriptor requires ${key}")
    )
}
