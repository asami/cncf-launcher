package cncf.launcher

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  1, 2026
 * @version Aug.  1, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfLauncherTransportSpec {
  def main(args: Array[String]): Unit = {
    val spec = new CncfLauncherTransportSpec
    spec.verifyTransportRuntimeArgumentsWithoutInterpretation()
    println("CncfLauncherTransportSpec: OK")
  }
}

final class CncfLauncherTransportSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(example: String, rules: String) =
    afterWord(s"in spec:phase-53-cs05-launcher-transport, example:$example, rules:$rules, phase:53, slice:CS-05J")

  "CNCF launcher runtime transport" should {
    "E1 retain canonical and noncanonical Web strings plus fixed-user-shaped arguments for packaged and development targets" must _metadata("E1", "CS05J-R1,CS05J-R2") {
      "when target-first execution crosses the launcher boundary" in {
        Given("Spec: phase-53-cs05-launcher-transport; Rules: CS05J-R1, CS05J-R2; Example: E1; packaged and development CAR targets with runtime-owned arguments")
        val fixture = _prepare_fixture()
        try {
          When("the launcher selects each target and forwards the runtime command")
          val results = fixture.run()

          Then("target activation is the only launcher addition, runtime selection remains independent, and every runtime argument retains its spelling and order")
          _assert_transport(results)
        } finally {
          fixture.close()
        }
      }
    }
  }

  def verifyTransportRuntimeArgumentsWithoutInterpretation(): Unit = {
    val fixture = _prepare_fixture()
    try _assert_transport(fixture.run())
    finally fixture.close()
  }

  private def _prepare_fixture(): Fixture = {
    val root = Files.createTempDirectory("cncf-launcher-transport")
    val paths = LauncherPaths(home = root.resolve("home"), cwd = root.resolve("cwd"))
    _write(paths.cwd.resolve(".cncf/launcher.yaml"), "runtime:\n  version: 0.4.12\n")
    val developmenttarget = paths.cwd.resolve("transport-car")
    _write(developmenttarget.resolve("project.yaml"), "project:\n  kind: car\n")
    _write(developmenttarget.resolve("build.sbt"), "version := \"1.0.0-SNAPSHOT\"\n")
    val invoker = FakeInvoker()
    val resolver = FakeResolver()
    new Fixture(
      root,
      new CncfLauncher(paths, resolver, invoker),
      invoker,
      resolver,
      paths.cwd.resolve("transport-car").toAbsolutePath.normalize.toString,
      Vector(
        "--textus.web.application-mode=multi-user",
        "--cncf.web.application-mode=standalone",
        "--textus.user.fixed-id=alice",
        "--",
        "opaque-runtime-value"
      )
    )
  }

  private def _assert_transport(results: Results): Unit = {
    results.packagedstatus shouldBe 0
    results.developmentstatus shouldBe 0
    results.packaged shouldBe Vector(
      "command",
      "--textus.component=textus-sanpomap",
      "--textus.component.version=1",
      "--textus.web.application-mode=multi-user",
      "--cncf.web.application-mode=standalone",
      "--textus.user.fixed-id=alice",
      "--",
      "opaque-runtime-value"
    )
    results.development shouldBe Vector(
      "server",
      s"--component-dev-dir=${results.developmenttarget}",
      "--textus.web.application-mode=multi-user",
      "--cncf.web.application-mode=standalone",
      "--textus.user.fixed-id=alice",
      "--",
      "opaque-runtime-value"
    )
    results.runtimeclasspaths shouldBe Vector("0.4.12", "0.4.12")
  }

  private def _write(path: Path, value: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value, StandardCharsets.UTF_8)
  }

  private def _delete(path: Path): Unit = {
    if (Files.isDirectory(path)) {
      val stream = Files.list(path)
      try stream.forEach(child => _delete(child))
      finally stream.close()
    }
    Files.deleteIfExists(path)
  }

  private final case class Results(
    packagedstatus: Int,
    packaged: Vector[String],
    developmentstatus: Int,
    development: Vector[String],
    developmenttarget: String,
    runtimeclasspaths: Vector[String]
  )

  private final class Fixture(
    private val _root: Path,
    private val _launcher: CncfLauncher,
    private val _invoker: FakeInvoker,
    private val _resolver: FakeResolver,
    private val _development_target: String,
    private val _runtime_args: Vector[String]
  ) {
    def run(): Results = {
      val packagedstatus = _launcher.run(Vector("textus-sanpomap:1", "command") ++ _runtime_args)
      val packaged = _invoker.lastArgs
      val developmentstatus = _launcher.run(Vector(_development_target, "server") ++ _runtime_args)
      Results(
        packagedstatus,
        packaged,
        developmentstatus,
        _invoker.lastArgs,
        _development_target,
        _resolver.resolvedClasspaths
      )
    }

    def close(): Unit = _delete(_root)
  }
}
