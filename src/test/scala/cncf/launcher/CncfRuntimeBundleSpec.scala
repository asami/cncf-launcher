package cncf.launcher

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.util.jar.{JarEntry, JarOutputStream}

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep.  2, 2026
 * @version Sep.  2, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfRuntimeBundleSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "cncf launcher prebuilt runtime bundle" should {
    "parse a direct execute bundle option and invoke its sealed JAR classpath without resolver access" in {
      Given("a sealed runtime bundle and a resolver that fails if consulted")
      val root = Files.createTempDirectory("cncf-runtime-bundle-success")
      try {
        val bundle = _bundle(root, "1.2.3")
        val resolver = new _NeverResolver
        val invoker = new _RecordingInvoker
        val paths = LauncherPaths(home = root.resolve("home"), cwd = root)

        When("a direct server command selects the runtime bundle")
        val command = CncfCommandParser.parse(Vector("--runtime-bundle-dir", bundle.toString, "server")) match {
          case execute: CncfCommand.Execute => execute
          case _ => fail("expected a direct execute command")
        }
        val status = new CncfLauncher(paths, resolver, invoker).run(Vector("--runtime-bundle-dir", bundle.toString, "server"))

        Then("the parser records the bundle and only its sealed JAR reaches the invoker")
        command.runtimeBundleDir shouldBe Some(bundle.toString)
        status shouldBe 0
        resolver.called shouldBe false
        invoker.classpath shouldBe Vector(bundle.resolve("lib/runtime.jar").toRealPath())
      } finally {
        _delete(root)
      }
    }

    "reject a bundle option outside direct execute and conflicting runtime selection" in {
      Given("bundle arguments combined with install-cli and explicit runtime selection")

      When("the command parser receives each invalid invocation")
      val install = scala.util.Try(CncfCommandParser.parse(Vector("--runtime-bundle-dir", "bundle", "install-cli", "sample")))
      val conflict = scala.util.Try(CncfCommandParser.parse(Vector("--runtime-bundle-dir", "bundle", "--runtime", "1.2.3", "server")))

      Then("the parser rejects non-execute use and ambiguous runtime selection")
      install.failed.get.getMessage should include("direct execute")
      conflict.failed.get.getMessage should include("cannot be combined")
    }

    "reject external, source-output, symbolic-link, and non-JAR classpath substitutions" in {
      Given("a bundle classpath file with paths that do not name sealed JARs beneath lib")
      val root = Files.createTempDirectory("cncf-runtime-bundle-classpath")
      try {
        val external = root.resolve("external.jar")
        _jar(external, _descriptor("1.2.3"))
        val bundle = _bundle(root.resolve("bundle"), "1.2.3")
        val sourceoutput = bundle.resolve("target/scala-3.3.0/classes")
        Files.createDirectories(sourceoutput)
        val nonjar = bundle.resolve("lib/not-a-jar.txt")
        Files.writeString(nonjar, "not a jar", StandardCharsets.UTF_8)
        val escaped = bundle.resolve("lib/escaped.jar")
        Files.createSymbolicLink(escaped, external)

        When("each substitution is named by the bundle classpath")
        val outcomes = Vector(external.toString, sourceoutput.toString, escaped.toString, nonjar.toString).map { entry =>
          Files.writeString(bundle.resolve("target/cncf.d/runtime-classpath.txt"), entry, StandardCharsets.UTF_8)
          scala.util.Try(CncfRuntimeBundle.read(bundle))
        }

        Then("the bundle reader rejects every path before runtime invocation")
        outcomes.foreach { outcome =>
          outcome.isFailure shouldBe true
        }
      } finally {
        _delete(root)
      }
    }

    "reject an outbound top-level lib symbolic link before runtime invocation" in {
      Given("a runtime bundle whose top-level lib directory resolves outside the bundle")
      val root = Files.createTempDirectory("cncf-runtime-bundle-lib-link")
      try {
        val external = root.resolve("external-lib")
        val bundle = _bundle(root.resolve("bundle"), "1.2.3")
        Files.createDirectories(external)
        _jar(external.resolve("runtime.jar"), _descriptor("1.2.3"))
        Files.delete(bundle.resolve("lib/runtime.jar"))
        Files.delete(bundle.resolve("lib"))
        Files.createSymbolicLink(bundle.resolve("lib"), external)

        When("the bundle reader canonicalizes the selected runtime lib directory")
        val outcome = scala.util.Try(CncfRuntimeBundle.read(bundle))

        Then("it rejects the outbound lib link before a runtime classpath can be invoked")
        outcome.failed.get.getMessage should include("lib directory escapes bundle")
      } finally {
        _delete(root)
      }
    }

    "reject an invalid or duplicate runtime descriptor" in {
      Given("sealed JAR sets with an invalid schema and more than one descriptor")
      val root = Files.createTempDirectory("cncf-runtime-bundle-descriptor")
      try {
        val malformed = _bundle(root.resolve("malformed"), "1.2.3", "schemaVersion: 2\nruntime: cncf\nversion: 1.2.3\n")
        val duplicate = _bundle(root.resolve("duplicate"), "1.2.3")
        _jar(duplicate.resolve("lib/second.jar"), _descriptor("1.2.3"))
        Files.writeString(
          duplicate.resolve("target/cncf.d/runtime-classpath.txt"),
          Vector("lib/runtime.jar", "lib/second.jar").mkString(File.pathSeparator),
          StandardCharsets.UTF_8
        )

        When("the runtime bundle reader discovers its descriptor")
        val malformedoutcome = scala.util.Try(CncfRuntimeBundle.read(malformed))
        val duplicateoutcome = scala.util.Try(CncfRuntimeBundle.read(duplicate))

        Then("it rejects an unsupported schema and duplicate descriptor before invocation")
        malformedoutcome.failed.get.getMessage should include("schemaVersion")
        duplicateoutcome.failed.get.getMessage should include("exactly one")
      } finally {
        _delete(root)
      }
    }

    "reject a bundle whose descriptor version violates component runtime requirements" in {
      Given("a CAR development target requiring a newer runtime than the sealed bundle")
      val root = Files.createTempDirectory("cncf-runtime-bundle-requirement")
      try {
        val bundle = _bundle(root.resolve("bundle"), "1.2.3")
        val target = root.resolve("component")
        Files.createDirectories(target)
        Files.writeString(
          target.resolve("project.yaml"),
          "project:\n  kind: car\npackaging:\n  car:\n    runtime:\n      cncf:\n        minimum: 2.0.0\n",
          StandardCharsets.UTF_8
        )
        val invoker = new _RecordingInvoker
        val launcher = new CncfLauncher(LauncherPaths(home = root.resolve("home"), cwd = root), new _NeverResolver, invoker)

        When("the direct target execution selects the incompatible runtime bundle")
        val outcome = scala.util.Try(launcher.run(Vector("--runtime-bundle-dir", bundle.toString, target.toString, "server")))

        Then("the launcher rejects compatibility before the classloader invoker runs")
        outcome.failed.get.getMessage should include("not compatible")
        invoker.classpath shouldBe Vector.empty
      } finally {
        _delete(root)
      }
    }
  }

  private def _bundle(root: Path, version: String, descriptor: String = ""): Path = {
    val jar = root.resolve("lib/runtime.jar")
    _jar(jar, Option(descriptor).filter(_.nonEmpty).getOrElse(_descriptor(version)))
    val classpath = root.resolve("target/cncf.d/runtime-classpath.txt")
    Files.createDirectories(classpath.getParent)
    Files.writeString(classpath, "lib/runtime.jar", StandardCharsets.UTF_8)
    root
  }

  private def _descriptor(version: String): String =
    s"schemaVersion: 1\nruntime: cncf\nversion: ${version}\n"

  private def _jar(path: Path, descriptor: String): Unit = {
    Files.createDirectories(path.getParent)
    val out = new JarOutputStream(Files.newOutputStream(path))
    try {
      out.putNextEntry(new JarEntry("META-INF/cncf/runtime.yaml"))
      out.write(descriptor.getBytes(StandardCharsets.UTF_8))
      out.closeEntry()
    } finally {
      out.close()
    }
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
        val stream = Files.list(path)
        val children = try {
          val iterator = stream.iterator()
          val result = Vector.newBuilder[Path]
          while (iterator.hasNext) result += iterator.next()
          result.result()
        }
        finally stream.close()
        children.foreach(child => _delete(child))
      }
      Files.deleteIfExists(path)
    }

  private final class _NeverResolver extends CncfRuntimeResolver {
    var called: Boolean = false

    override def resolveVersion(version: String, config: LauncherConfig, paths: LauncherPaths): String = {
      called = true
      throw CncfException("runtime resolver must not be called for a prebuilt bundle")
    }

    def resolve(version: String, config: LauncherConfig, paths: LauncherPaths): Vector[Path] = {
      called = true
      throw CncfException("runtime resolver must not be called for a prebuilt bundle")
    }
  }

  private final class _RecordingInvoker extends CncfInvoker {
    var classpath: Vector[Path] = Vector.empty

    override def invoke(entries: Vector[Path], args: Vector[String]): Int = {
      classpath = entries
      0
    }
  }
}
