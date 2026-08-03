package cncf.launcher

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  3, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class Gcf08BindingEnvelopeTransportSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-gcf08j-launcher-binding-envelope-transport, example:E1, rules:GCF08J-R1,R2,R3, phase:55, slice:GCF-08J"
  )

  "CNCF launcher binding-envelope transport" should {
    "E1 forward opaque binding-looking tokens unchanged for packaged and development targets" must _e1 {
      "when launcher configuration and target activation are also present" in {
        Given("a launcher fixture and runtime tokens that only the CNCF runtime may interpret")
        val fixture = _fixture()
        try {
          When("a packaged and a development target cross the launcher boundary")
          val results = fixture.run()

          Then("only target activation is added and every opaque token keeps its exact spelling and order")
          results.packagedstatus shouldBe 0
          results.developmentstatus shouldBe 0
          results.packaged shouldBe Vector(
            "command",
            "--textus.component=textus-sanpomap",
            "--textus.component.version=1"
          ) ++ fixture.envelopes
          results.development shouldBe Vector(
            "server",
            s"--component-dev-dir=${fixture.developmenttarget}"
          ) ++ fixture.envelopes
        } finally {
          fixture.close()
        }
      }
    }
  }

  private def _fixture(): Fixture = {
    val root = Files.createTempDirectory("gcf08j-cncf-launcher")
    val paths = LauncherPaths(home = root.resolve("home"), cwd = root.resolve("cwd"))
    val config = paths.cwd.resolve("transport-launcher.yaml")
    _write(config, "runtime:\n  version: 0.4.12\n")
    val developmenttarget = paths.cwd.resolve("transport-car")
    _write(developmenttarget.resolve("project.yaml"), "project:\n  kind: car\n")
    _write(developmenttarget.resolve("build.sbt"), "version := \"1.0.0-SNAPSHOT\"\n")
    val invoker = FakeInvoker()
    val envelopes = Vector(
      "--textus.binding=@s/platform/default:textus.subsystem.user-mode=standalone=with=equals",
      "--textus.binding=@s/platform/default:textus.subsystem.user-mode=",
      "--textus.binding=not-a-canonical-envelope",
      "--",
      "--textus.binding=@s/platform/default:textus.subsystem.user-mode=command-domain"
    )
    new Fixture(
      root,
      new CncfLauncher(paths, FakeResolver(), invoker),
      invoker,
      config,
      developmenttarget.toAbsolutePath.normalize.toString,
      envelopes
    )
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

  private final class Fixture(
    private val _root: Path,
    private val _launcher: CncfLauncher,
    private val _invoker: FakeInvoker,
    private val _config: Path,
    val developmenttarget: String,
    val envelopes: Vector[String]
  ) {
    def run(): Results = {
      val prefix = Vector("--config", _config.toString)
      val packagedstatus = _launcher.run(prefix ++ Vector("textus-sanpomap:1", "command") ++ envelopes)
      val packaged = _invoker.lastArgs
      val developmentstatus = _launcher.run(prefix ++ Vector(developmenttarget, "server") ++ envelopes)
      Results(packagedstatus, packaged, developmentstatus, _invoker.lastArgs)
    }

    def close(): Unit = _delete(_root)
  }

  private final case class Results(
    packagedstatus: Int,
    packaged: Vector[String],
    developmentstatus: Int,
    development: Vector[String]
  )
}
