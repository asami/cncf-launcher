/*
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
package cncf.launcher

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import java.net.{HttpURLConnection, InetAddress, InetSocketAddress, ServerSocket, URI}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.jdk.CollectionConverters.*
import scala.util.Try
import io.circe.syntax.*
import LifecycleSupervisorProtocol.given
import LifecycleSupervisorStateStore.given

trait CncfLauncherCommandSpecSupport extends CncfLauncherSpecSupport {
  self: org.scalatest.Informing =>
  def parser(): Unit = {
    val autouse = CncfCommandParser.parse(Vector("runtime", "use", "latest"))
      .asInstanceOf[CncfCommand.Runtime.Use]
    _assert_equals(autouse.version, "latest")
    _assert_equals(autouse.target, CncfCommand.RuntimeUseTarget.Auto)

    val install = CncfCommandParser.parse(Vector(
      "install-cli",
      "sanpomap",
      "--project-dev",
      ".",
      "--component-dev-dir",
      "../textus-georesolver",
      "--overwrite"
    )).asInstanceOf[CncfCommand.InstallCli]
    _assert_equals(install.name, "sanpomap")
    _assert_equals(install.installedName, "sanpomap-dev")
    _assert_equals(install.projectDev, Some("."))
    _assert_equals(install.operationPrefix, None)
    _assert_equals(install.fileParams, Vector.empty)
    _assert_equals(install.componentDevDirs, Vector("../textus-georesolver"))
    _assert_equals(install.binDir, None)
    _assert_equals(install.runtimeVersion, None)
    _assert_equals(install.runtimeDevDir, None)
    _assert_equals(install.launcherDevDir, None)
    install.overwrite shouldBe true

    val installruntime = CncfCommandParser.parse(Vector(
      "--runtime", "0.4.13-SNAPSHOT",
      "--runtime-dev-dir", "/tmp/cncf-runtime",
      "install-cli",
      "sanpomap"
    )).asInstanceOf[CncfCommand.InstallCli]
    _assert_equals(installruntime.runtimeVersion, Some("0.4.13-SNAPSHOT"))
    _assert_equals(installruntime.runtimeDevDir, Some("/tmp/cncf-runtime"))

    val currenttarget = CncfCommandParser.parse(Vector(
      "command",
      "validate-presentation"
    )).asInstanceOf[CncfCommand.Execute]
    _assert_equals(currenttarget.args, Vector("--component-dev-dir=.", "command", "validate-presentation"))
    _assert_equals(currenttarget.developmentTarget, Some(CncfCommand.ExecuteDevelopmentTarget(".", explicit = false)))

    val explicitcurrenttarget = CncfCommandParser.parse(Vector(
      ".",
      "command",
      "validate-presentation"
    )).asInstanceOf[CncfCommand.Execute]
    _assert_equals(explicitcurrenttarget.args, Vector("--component-dev-dir=.", "command", "validate-presentation"))
    _assert_equals(explicitcurrenttarget.developmentTarget, Some(CncfCommand.ExecuteDevelopmentTarget(".", explicit = true)))

    val packagedtarget = CncfCommandParser.parse(Vector(
      "command",
      "--no-project-classpath",
      "--component-car-dir",
      "car.d",
      "testcomp.main.hello"
    )).asInstanceOf[CncfCommand.Execute]
    _assert_equals(packagedtarget.args, Vector("command", "--no-project-classpath", "--component-car-dir", "car.d", "testcomp.main.hello"))

    val namedtarget = CncfCommandParser.parse(Vector(
      "textus-sanpomap:1",
      "command",
      "validate-presentation"
    )).asInstanceOf[CncfCommand.Execute]
    _assert_equals(namedtarget.args, Vector("--textus.component=textus-sanpomap", "--textus.component.version=1", "command", "validate-presentation"))
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
    disabled shouldBe true
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

    val selected = CncfCommandParser.parse(Vector("dev", "server", "--name=textus-art-scene", "--artifact-repository=https://example.com/repository/car"))
      .asInstanceOf[CncfCommand.Dev.Server]
    _assert_equals(selected.options.artifactRepository, Some("https://example.com/repository/car"))

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

  def componentRepositoryCommandParser(): Unit = {
    Given("repository list and show commands with explicit development admission")
    When("the CNCF command parser reads their options")
    val list = CncfCommandParser.parse(Vector("repository", "list", "--kind", "car", "--include-development", "--development-dir", "../dependency"))
      .asInstanceOf[CncfCommand.Repository.ListArtifacts]
    val show = CncfCommandParser.parse(Vector("repository", "show", "textus-blog", "--kind=sar"))
      .asInstanceOf[CncfCommand.Repository.Show]
    val missing = intercept[CncfException] {
      CncfCommandParser.parse(Vector("repository", "show"))
    }

    Then("kind, current project admission, and extra directories remain explicit")
    list.kind shouldBe Some("car")
    list.includeDevelopment shouldBe true
    list.developmentDirs shouldBe Vector("../dependency")
    show.target shouldBe "textus-blog"
    show.kind shouldBe Some("sar")
    missing.getMessage should include("qualified component id, artifact id, or component directory")
    CncfCommandParser.helpText should include("<qualified-component-id|artifact-id|component-dir>")
  }

  def componentRepositoryDevelopmentOverridesLocalIdentity(): Unit = _with_temp_paths { paths =>
    Given("namespace-distinct local CARs sharing one artifact ID and an admitted checkout for one exact canonical identity")
    _write(paths.localRepository.resolve("repository/catalog/index.json"), _component_repository_v2_index)
    _write(
      paths.cwd.resolve("checkout/project.yaml"),
      _component_project_yaml("textus-shared", "car", "1.4.0-SNAPSHOT", canonicalnamespace = Some("org.alpha.textus"), canonicalid = Some("Shared"))
    )
    val discovery = CncfComponentRepositoryDiscovery(paths)

    When("CNCF lists component repository entries")
    val result = discovery.list(None, false, Vector("checkout"))

    Then("development precedence applies only to the exact namespace-qualified CAR identity")
    result.artifacts should have size 3
    result.artifacts.find(_.qualifiedComponentId.contains("org.alpha.textus.Shared")).map(_.origin) shouldBe Some("development")
    result.artifacts.find(_.qualifiedComponentId.contains("org.alpha.textus.Shared")).flatMap(_.recommended) shouldBe Some("1.4.0-SNAPSHOT")
    result.artifacts.find(_.qualifiedComponentId.contains("org.beta.textus.Shared")).map(_.origin) shouldBe Some("local")
    result.artifacts.find(_.qualifiedComponentId.contains("org.alpha.textus.Shared")).map(_.render) should not contain paths.cwd.toString
  }

  def componentRepositoryPreservesNamespaceQualifiedCarIdentity(): Unit = _with_temp_paths { paths =>
    Given("a v2 index with a SAR and namespace-distinct CARs sharing one derived artifact ID")
    _write(paths.localRepository.resolve("repository/catalog/index.json"), _component_repository_v2_index)
    val discovery = CncfComponentRepositoryDiscovery(paths)

    When("CNCF lists entries and shows CARs by qualified Component IDs or a unique artifact ID")
    val listed = discovery.list(None, false, Vector.empty)
    val alpha = discovery.show("org.alpha.textus.Shared", Some("car"), false, Vector.empty)
    val beta = discovery.show("org.beta.textus.Shared", Some("car"), false, Vector.empty)
    val platform = discovery.show("textus-platform", Some("sar"), false, Vector.empty)
    val ambiguous = intercept[CncfException] {
      discovery.show("textus-shared", Some("car"), false, Vector.empty)
    }

    Then("both CARs survive with their canonical identities, artifact fallback is unique-only, and the six-column rendering remains stable")
    listed.artifacts.map(_.identity) shouldBe Vector(
      ("car", "org.alpha.textus", "Shared"),
      ("car", "org.beta.textus", "Shared"),
      ("sar", "", "textus-platform")
    )
    alpha.artifact.qualifiedComponentId shouldBe Some("org.alpha.textus.Shared")
    beta.artifact.qualifiedComponentId shouldBe Some("org.beta.textus.Shared")
    platform.artifact.identity shouldBe ("sar", "", "textus-platform")
    alpha.artifact.render.split("\\t").toVector should have size 6
    alpha.artifact.renderDetailed should include("namespace: org.alpha.textus")
    alpha.artifact.renderDetailed should include("local-id: Shared")
    alpha.artifact.renderDetailed should include("qualified-component-id: org.alpha.textus.Shared")
    ambiguous.getMessage should include("artifact is ambiguous")
    ambiguous.getMessage should include("org.alpha.textus.Shared")
    ambiguous.getMessage should include("org.beta.textus.Shared")
  }

  def componentRepositoryRejectsImplicitDevelopmentDirectory(): Unit = _with_temp_paths { paths =>
    Given("a current checkout with project metadata but no development admission option")
    _write(paths.cwd.resolve("project.yaml"), _component_project_yaml("current-component", "car", "1.0.0-SNAPSHOT"))
    val discovery = CncfComponentRepositoryDiscovery(paths)

    When("CNCF lists component repository entries with and without explicit admission")
    val hidden = discovery.list(None, false, Vector.empty)
    val admitted = discovery.list(None, true, Vector.empty)
    val admittedtwice = discovery.list(None, true, Vector("."))

    Then("the current checkout is not included implicitly or duplicated by equivalent admissions")
    hidden.artifacts shouldBe empty
    admitted.artifacts.map(_.artifactId) shouldBe Vector("current-component")
    admittedtwice.artifacts.map(_.artifactId) shouldBe Vector("current-component")
    admittedtwice.diagnostics shouldBe empty
  }

  def componentRepositoryShowUsesDescriptorIdentity(): Unit = _with_temp_paths { paths =>
    Given("a development directory whose name differs from project.name")
    _write(paths.cwd.resolve("misleading-directory/project.yaml"), _component_project_yaml("canonical-component", "car", "2.0.0-SNAPSHOT"))
    val discovery = CncfComponentRepositoryDiscovery(paths)

    When("CNCF shows the explicitly selected directory")
    val result = discovery.show("misleading-directory", None, false, Vector.empty)

    Then("project.yaml defines the CAR identity and version")
    result.artifact.artifactId shouldBe "canonical-component"
    result.artifact.recommended shouldBe Some("2.0.0-SNAPSHOT")
    result.artifact.origin shouldBe "development"
  }

  def componentRepositoryOutputMatchesTextusIdentityColumns(): Unit = _with_temp_paths { paths =>
    Given("the shared CNCF component repository index fixture")
    _write(paths.localRepository.resolve("repository/catalog/index.json"), _component_repository_index("sar", "sample-app", "1.2.0"))
    When("CNCF renders its normalized local entry")
    val result = CncfComponentRepositoryDiscovery(paths).list(None, false, Vector.empty)

    val columns = result.artifacts.head.render.split("\\t").toVector

    Then("kind, artifact id, lifecycle, selector, origin, and source columns match the Textus contract")
    columns.take(5) shouldBe Vector("sar", "sample-app", "active", "1.2.0", "local")
    columns should have size 6
  }

  def componentRepositoryRejectsInvalidLocalIndex(): Unit = _with_temp_paths { paths =>
    Given("a valid v2 machine-local index and every invalid present-index contract variant")
    val indexpath = paths.localRepository.resolve("repository/catalog/index.json")
    val source = _component_repository_v2_index
    val invalidindextexts = Vector(
      "malformed JSON" -> "{not-json",
      "legacy v1 schema" -> _legacy_component_repository_index,
      "unsupported non-v1 schema" -> source.replace("cncf.component-repository-index.v2", "cncf.component-repository-index.v3"),
      "missing CAR namespace" -> source.replaceFirst("      \"namespace\": \"org.alpha.textus\",\n", ""),
      "null CAR id" -> source.replaceFirst("\"id\": \"Shared\"", "\"id\": null"),
      "wrong-type CAR namespace" -> source.replaceFirst("\"namespace\": \"org.alpha.textus\"", "\"namespace\": 7"),
      "wrong-type CAR id" -> source.replaceFirst("\"id\": \"Shared\"", "\"id\": 7"),
      "unknown root field" -> source.replaceFirst("  \"generatedAt\": \"2026-08-07T00:00:00Z\",", "  \"generatedAt\": \"2026-08-07T00:00:00Z\",\n  \"unexpected\": true,"),
      "unknown entry field" -> source.replaceFirst("      \"catalog\": \"sar/textus-platform.yaml\",", "      \"unexpected\": true,\n      \"catalog\": \"sar/textus-platform.yaml\","),
      "duplicate JSON object field" -> source.replaceFirst("  \"generatedAt\": \"2026-08-07T00:00:00Z\",", "  \"generatedAt\": \"2026-08-07T00:00:00Z\",\n  \"generatedAt\": \"2026-08-07T00:00:00Z\","),
      "invalid generatedAt" -> source.replace("2026-08-07T00:00:00Z", "not-an-instant"),
      "invalid status" -> source.replaceFirst("\"status\": \"active\"", "\"status\": \"unknown\""),
      "empty selector" -> source.replaceFirst("\"recommended\": \"1.0.0\"", "\"recommended\": \"\""),
      "null selector" -> source.replaceFirst("\"recommended\": \"1.0.0\"", "\"recommended\": null"),
      "invalid namespace" -> source.replaceFirst("\"namespace\": \"org.alpha.textus\"", "\"namespace\": \"Org.alpha.textus\""),
      "invalid local ID" -> source.replaceFirst("\"id\": \"Shared\"", "\"id\": \"shared\""),
      "wrong CAR artifact projection" -> source.replaceFirst("\"artifactId\": \"textus-shared\"", "\"artifactId\": \"other-artifact\""),
      "wrong CAR catalog" -> source.replace("car/org/alpha/textus/textus-shared.yaml", "car/textus-shared.yaml"),
      "CAR catalog traversal" -> source.replace("car/org/alpha/textus/textus-shared.yaml", "../car/org/alpha/textus/textus-shared.yaml"),
      "CAR catalog backslash" -> source.replace("car/org/alpha/textus/textus-shared.yaml", "car\\\\org\\\\alpha\\\\textus\\\\textus-shared.yaml"),
      "CAR catalog absolute path" -> source.replace("car/org/alpha/textus/textus-shared.yaml", "/car/org/alpha/textus/textus-shared.yaml"),
      "SAR namespace and id fields" -> source.replaceFirst("      \"kind\": \"sar\",", "      \"kind\": \"sar\",\n      \"namespace\": \"org.alpha.textus\",\n      \"id\": \"Shared\","),
      "wrong SAR catalog" -> source.replace("sar/textus-platform.yaml", "sar/textus-platform.txt"),
      "SAR catalog traversal" -> source.replace("sar/textus-platform.yaml", "../sar/textus-platform.yaml"),
      "duplicate canonical identity" -> source.replace("org.beta.textus", "org.alpha.textus").replace("car/org/beta/textus", "car/org/alpha/textus")
    )
    val invalidindexes = invalidindextexts.map { case (label, contents) =>
      label -> contents.getBytes(StandardCharsets.UTF_8)
    } :+ ("malformed UTF-8 bytes" -> Array[Byte](0x7b.toByte, 0x22.toByte, 0xc3.toByte, 0x28.toByte))

    invalidindexes.foreach { case (label, contents) =>
      Given(s"an invalid present machine-local component repository index: $label")
      _write_bytes(indexpath, contents)
      val beforebytes = Files.readAllBytes(indexpath).toVector
      val beforetree = _tree(paths.localRepository)

      When("CNCF lists or shows the invalid local repository state")
      val listfailure = intercept[CncfException] {
        CncfComponentRepositoryDiscovery(paths).list(None, false, Vector.empty)
      }
      val showfailure = intercept[CncfException] {
        CncfComponentRepositoryDiscovery(paths).show("missing", None, false, Vector.empty)
      }

      Then("both operations fail closed with the complete operator-owned recovery procedure and no warehouse mutation")
      _assert_component_repository_index_rejection(paths, indexpath, label, listfailure, showfailure, beforebytes, beforetree)
    }
  }

  def componentRepositoryCommandDoesNotLoadCncfRuntime(): Unit = _with_temp_paths { paths =>
    Given("a valid machine-local component repository index")
    _write(paths.localRepository.resolve("repository/catalog/index.json"), _component_repository_index("car", "sample-component", "1.2.0"))
    val resolver = FakeResolver()
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, resolver, invoker)

    When("the launcher executes repository list")
    val (code, output) = _capture_stdout(launcher.run(Vector("repository", "list")))

    Then("the launcher renders repository entries without resolving or invoking CNCF runtime")
    code shouldBe 0
    output should include("car\tsample-component\tactive\t1.2.0\tlocal\t")
    resolver.resolvedClasspaths shouldBe empty
    invoker.lastArgs shouldBe empty
  }
}
