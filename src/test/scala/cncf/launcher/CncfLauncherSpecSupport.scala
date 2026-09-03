/*
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
package cncf.launcher

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
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


trait CncfLauncherSpecSupport extends org.scalatest.GivenWhenThen with org.scalatest.matchers.should.Matchers {
  self: org.scalatest.Informing =>
  private[launcher] def _capture_stdout(f: => Int): (Int, String) = {
    val out = new java.io.ByteArrayOutputStream()
    val code = Console.withOut(new java.io.PrintStream(out)) {
      f
    }
    (code, out.toString)
  }

  private[launcher] def _post_lifecycle_request(endpoint: String, artifactid: String, requestid: String, idempotencykey: String, action: String = "start", deadline: java.time.Instant = java.time.Instant.now().plusSeconds(60)): String = {
    val connection = URI(endpoint).toURL.openConnection().asInstanceOf[HttpURLConnection]
    val body =
      s"""{"requestId":"$requestid","idempotencyKey":"$idempotencykey","artifactId":"$artifactid","action":"$action","operatorSubjectId":"test-operator","deadlineAt":"$deadline"}"""
    connection.setRequestMethod("POST")
    connection.setRequestProperty("Authorization", "Bearer test-token")
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setDoOutput(true)
    connection.getOutputStream.write(body.getBytes(StandardCharsets.UTF_8))
    try {
      new String(connection.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
    } finally {
      connection.disconnect()
    }
  }

  private[launcher] def _get_lifecycle_request(endpoint: String, requestid: String): String = {
    val connection = URI(s"$endpoint/$requestid").toURL.openConnection().asInstanceOf[HttpURLConnection]
    connection.setRequestMethod("GET")
    connection.setRequestProperty("Authorization", "Bearer test-token")
    try {
      new String(connection.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
    } finally {
      connection.disconnect()
    }
  }

  private[launcher] def _capture_stdout_stderr(f: => Int): (Int, String, String) = {
    val out = new java.io.ByteArrayOutputStream()
    val err = new java.io.ByteArrayOutputStream()
    val code = Console.withOut(new java.io.PrintStream(out)) {
      Console.withErr(new java.io.PrintStream(err)) {
        f
      }
    }
    (code, out.toString, err.toString)
  }

  private[launcher] def _with_temp_paths(f: LauncherPaths => Unit): Unit = {
    val root = Files.createTempDirectory("cncf-launcher-spec-")
    val home = root.resolve("home")
    val cwd = root.resolve("work")
    Files.createDirectories(home)
    Files.createDirectories(cwd)
    f(LauncherPaths(home, cwd))
  }

  private[launcher] def _write(path: Path, value: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value)
  }

  private[launcher] def _write_bytes(path: Path, value: Array[Byte]): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, value)
  }

  private[launcher] def _tree(root: Path): Vector[String] = {
    val stream = Files.walk(root)
    try stream.iterator.asScala.map(path => root.relativize(path).toString).toVector.sorted
    finally stream.close()
  }

  private[launcher] def _assert_component_repository_index_rejection(
    paths: LauncherPaths,
    indexpath: Path,
    label: String,
    listfailure: CncfException,
    showfailure: CncfException,
    beforebytes: Vector[Byte],
    beforetree: Vector[String]
  ): Unit = withClue(label) {
    Vector(listfailure, showfailure).foreach { failure =>
      failure.getMessage should include(paths.localRepository.toString)
      failure.getMessage should include(indexpath.toString)
      failure.getMessage should include("1. Stop processes using the active warehouse.")
      failure.getMessage should include("2. Copy or move the whole active warehouse to a timestamped forensic sibling.")
      failure.getMessage should include("3. Create an empty active warehouse.")
      failure.getMessage should include("4. Republish canonical CAR/SAR artifacts from source with cozyPublishLocalCar/cozyPublishLocalSar as applicable.")
      failure.getMessage should include("5. Retry the original launcher operation.")
      failure.getMessage should include("6. Verify with `cncf repository list` that only canonical v2 entries are active.")
      failure.getMessage should include("The launcher performed no backup, merge, migration, deletion, or rebuild mutation.")
    }
    Files.readAllBytes(indexpath).toVector shouldBe beforebytes
    _tree(paths.localRepository) shouldBe beforetree
  }

  private[launcher] def _write_zip(
    path: Path,
    entry: String,
    value: String
  ): Path = {
    Files.createDirectories(path.getParent)
    val out = new ZipOutputStream(Files.newOutputStream(path))
    try {
      out.putNextEntry(new ZipEntry(entry))
      out.write(value.getBytes(StandardCharsets.UTF_8))
      out.closeEntry()
    } finally {
      out.close()
    }
    path
  }

  private[launcher] def _write_dev_server_state(
    project: Path,
    pid: Long,
    port: String,
    processstartedat: java.time.Instant = java.time.Instant.parse("2026-05-24T00:00:00Z")
  ): Unit = {
    val state = DevServerState(
      pid = pid,
      project = project.toAbsolutePath.normalize,
      port = port,
      runtimeVersion = "0.9.0",
      executionProfile = None,
      startedAt = java.time.Instant.parse("2026-05-24T00:00:00Z"),
      processStartedAt = Some(processstartedat),
      commandLine = Some("cncf dev server"),
      command = "cncf dev server"
    )
    _write(DevSupport.devServerPidFile(project), s"${pid}\n")
    _write(DevSupport.devServerJsonFile(project), state.renderJson)
  }

  private[launcher] def _assert_equals[A](actual: A, expected: A): Unit =
    actual shouldBe expected

  private[launcher] val _catalog_text: String =
    """schemaVersion: 1
      |generatedAt: 2026-05-17T00:00:00Z
      |recommended: 0.2.0
      |latestStable: 0.2.0
      |latestSnapshot: 0.3.0-SNAPSHOT
      |mavenRepositories:
      |  - https://repo.example/maven
      |carRepositories:
      |  - https://repo.example/car
      |sarRepositories:
      |  - https://repo.example/sar
      |coursierRepositories:
      |  - https://repo.example/coursier
      |baseProvided:
      |  - org.goldenport:goldenport-cncf_3
      |  - org.typelevel:cats-core_3
      |versions:
      |  - version: 0.1.0
      |    channel: stable
      |    status: disabled
      |    scalaBinaryVersion: "3"
      |    module: org.goldenport:goldenport-cncf_3:0.1.0
      |  - version: 0.2.0
      |    channel: stable
      |    status: active
      |    scalaBinaryVersion: "3"
      |    module: org.goldenport:goldenport-cncf_3:0.2.0
      |    publishedAt: 2026-05-17T01:00:00Z
      |  - version: 0.3.0-SNAPSHOT
      |    channel: snapshot
      |    status: active
      |    scalaBinaryVersion: "3"
      |    module: org.goldenport:goldenport-cncf_3:0.3.0-SNAPSHOT
      |    publishedAt: 2026-05-17T02:00:00Z
      |""".stripMargin

  private[launcher] val _runtime_dev_catalog_text: String =
    """schemaVersion: 1
      |generatedAt: 2026-05-25T00:00:00Z
      |recommended: 0.4.9
      |latestStable: 0.4.9
      |latestSnapshot: 0.4.10-SNAPSHOT
      |mavenRepositories:
      |  - https://repo.example/maven
      |baseProvided:
      |  - org.goldenport:goldenport-cncf_3
      |versions:
      |  - version: 0.4.9
      |    channel: stable
      |    status: active
      |    scalaBinaryVersion: "3"
      |    module: org.goldenport:goldenport-cncf_3:0.4.9
      |    publishedAt: 2026-05-24T01:00:00Z
      |  - version: 0.4.10-SNAPSHOT
      |    channel: snapshot
      |    status: active
      |    scalaBinaryVersion: "3"
      |    module: org.goldenport:goldenport-cncf_3:0.4.10-SNAPSHOT
      |    publishedAt: 2026-05-25T01:00:00Z
      |""".stripMargin

  private[launcher] def _project_yaml(
    minimum: String,
    tested: Vector[String]
  ): String =
    s"""packaging:
       |  kind: car
       |  car:
       |    runtime:
       |      cncf:
       |        minimum: $minimum
       |        excluded: []
       |        tested:
       |${tested.map(v => s"          - $v").mkString("\n")}
       |""".stripMargin

  private[launcher] def _component_project_yaml(
    artifactid: String,
    kind: String,
    version: String,
    port: Int = 18013,
    canonicalnamespace: Option[String] = None,
    canonicalid: Option[String] = None
  ): String = {
    val identityfields = kind match {
      case "car" =>
        val (derivednamespace, derivedid) = _fixture_car_identity(artifactid)
        s"       |  namespace: ${canonicalnamespace.getOrElse(derivednamespace)}\n" +
          s"       |  id: ${canonicalid.getOrElse(derivedid)}\n"
      case "sar" => ""
      case other => throw new IllegalArgumentException(s"unsupported fixture component kind: $other")
    }
    s"""project:
       |  name: $artifactid
       |  kind: $kind
$identityfields
       |  component:
       |    name: component-name-that-does-not-define-artifact-identity
       |    version: $version
       |    config:
       |      textus.server.default-port: "$port"
       |packaging:
       |  kind: $kind
       |""".stripMargin
  }

  private[launcher] def _supervisor_yaml(supervisorid: String, port: Int, tokenenv: String): String =
    s"""schema: cncf.launcher.supervisor.v1
       |supervisor:
       |  id: $supervisorid
       |  port: "$port"
       |  token-env: $tokenenv
       |""".stripMargin

  private[launcher] def _record_development_evidence(
    paths: LauncherPaths,
    project: Path,
    artifactid: String,
    launcherkind: String = "cncf",
    startedat: java.time.Instant = java.time.Instant.now()
  ): Unit =
    CncfLocalServerEvidenceStore(paths).started(
      CncfTextusControlCenterRegistrationReport(
        instanceId = java.util.UUID.randomUUID().toString,
        target = artifactid,
        artifactId = Some(artifactid),
        executionMode = "development",
        developmentDirectory = Some(project.toAbsolutePath.normalize.toString),
        subsystemName = Some(artifactid),
        subsystemVersion = Some("1.0.0-SNAPSHOT"),
        runtimeVersion = "0.5.0-SNAPSHOT",
        startedAt = startedat
      ),
      launcherkind
    )

  private[launcher] def _local_evidence_report(instanceid: String): CncfTextusControlCenterRegistrationReport =
    CncfTextusControlCenterRegistrationReport(
      instanceId = instanceid,
      target = instanceid,
      artifactId = Some(instanceid),
      executionMode = "development",
      developmentDirectory = None,
      subsystemName = None,
      subsystemVersion = None,
      runtimeVersion = "0.5.0-SNAPSHOT",
      startedAt = java.time.Instant.now()
    )

  private[launcher] def _component_repository_index(kind: String, artifactid: String, version: String): String = {
    val (namespace, id, catalog) = kind match {
      case "car" =>
        val (canonicalnamespace, localid) = _fixture_car_identity(artifactid)
        (Some(canonicalnamespace), Some(localid), s"car/${canonicalnamespace.replace('.', '/')}/$artifactid.yaml")
      case "sar" => (None, None, s"sar/$artifactid.yaml")
      case other => throw new IllegalArgumentException(s"unsupported fixture component kind: $other")
    }
    val identityfields = namespace.map(value => s"    \"namespace\": \"$value\",\n").getOrElse("") +
      id.map(value => s"    \"id\": \"$value\",\n").getOrElse("")
    s"""{
       |  "schemaVersion": "cncf.component-repository-index.v2",
       |  "generatedAt": "2026-07-21T00:00:00Z",
       |  "artifacts": [{
       |    "kind": "$kind",
       |$identityfields
       |    "artifactId": "$artifactid",
       |    "catalog": "$catalog",
       |    "status": "active",
       |    "recommended": "$version",
       |    "latestStable": "$version"
       |  }]
       |}
       |""".stripMargin
  }

  private[launcher] def _fixture_car_identity(artifactid: String): (String, String) = {
    val separator = artifactid.indexOf('-')
    require(separator > 0 && separator + 1 < artifactid.length, s"CAR fixture requires an artifactId with namespace and local-id projections: $artifactid")
    val localid = artifactid.drop(separator + 1).split("-").map(part => part.head.toUpper.toString + part.tail).mkString
    s"org.example.${artifactid.take(separator)}" -> localid
  }

  private[launcher] val _component_repository_v2_index: String =
    """{
      |  "schemaVersion": "cncf.component-repository-index.v2",
      |  "generatedAt": "2026-08-07T00:00:00Z",
      |  "artifacts": [
      |    {
      |      "kind": "sar",
      |      "artifactId": "textus-platform",
      |      "catalog": "sar/textus-platform.yaml",
      |      "status": "active",
      |      "recommended": "1.0.0",
      |      "latestStable": "1.0.0"
      |    },
      |    {
      |      "kind": "car",
      |      "namespace": "org.alpha.textus",
      |      "id": "Shared",
      |      "artifactId": "textus-shared",
      |      "catalog": "car/org/alpha/textus/textus-shared.yaml",
      |      "status": "active",
      |      "recommended": "1.2.0",
      |      "latestStable": "1.2.0",
      |      "latestSnapshot": "1.3.0-SNAPSHOT"
      |    },
      |    {
      |      "kind": "car",
      |      "namespace": "org.beta.textus",
      |      "id": "Shared",
      |      "artifactId": "textus-shared",
      |      "catalog": "car/org/beta/textus/textus-shared.yaml",
      |      "status": "active"
      |    }
      |  ]
      |}
      |""".stripMargin

  private[launcher] def _legacy_component_repository_index: String =
    """{
      |  "schemaVersion": "cncf.component-repository-index.v1",
      |  "generatedAt": "2026-07-21T00:00:00Z",
      |  "artifacts": [{
      |    "kind": "car",
      |    "artifactId": "legacy-component",
      |    "catalog": "car/legacy-component.yaml",
      |    "status": "active",
      |    "recommended": "1.0.0",
      |    "latestStable": "1.0.0"
      |  }]
      |}
      |""".stripMargin
}

final class CncfLauncherSpecManualHarness extends AnyWordSpec with CncfLauncherCommandSpecSupport with CncfLauncherLifecycleSpecSupport with CncfLauncherRuntimeSpecSupport with CncfLauncherDevelopmentSpecSupport

object CncfLauncherSpecManualMain {
  def main(args: Array[String]): Unit = {
      val spec = new CncfLauncherSpecManualHarness
      spec.parser()
      spec.runtimeVersion()
      spec.launcherVersion()
      spec.runtimeHelp()
      spec.configMerge()
      spec.launcherDevDirDelegatesToDevelopmentLauncher()
      spec.launcherDevDirRejectsStaleDevelopmentClasspath()
      spec.configSupportsAdditionalRdfNamespaces()
      spec.configFileOptionOverridesProjectConfig()
      spec.workspaceRootConfigAppliesToNestedCwd()
      spec.launcherConfigControlsDevelopmentRuntime()
      spec.launcherConfigSupportsPropertiesAndConfFiles()
      spec.defaultRuntimeConfigFilesAreForwarded()
      spec.configFileProjectDevSurvivesTargetCwdSwitch()
      spec.cncfConfigOptionIsForwardedToRuntime()
      spec.configFileOptionRequiresExistingFile()
      spec.runtimeVersionPrecedence()
      spec.runtimeUseWritesExpectedFiles()
      spec.runtimeUseSelectsExactLocalSnapshot()
      spec.snapshotRuntimeRefreshesCachedClasspath()
      spec.runtimeUseAutoSelectsProjectWhenCncfDirectoryExists()
      spec.installCliWritesDevelopmentCommand()
      spec.installCliPinsExplicitRuntimeVersion()
      spec.installCliPinsDevelopmentRuntimeWithoutCatalog()
      spec.installCliRejectsIncompatibleDevelopmentRuntime()
      spec.textusControlCenterRegistrationLifecycle()
      spec.controlCenterConfigAppliesOnlyToCurrentProject()
      spec.localServerEvidenceProjection()
      spec.localServerEvidenceRetentionAndRecovery()
      spec.localServerEvidenceRetainsSupervisorProcessIdentity()
      spec.standaloneControlCenterLocatorLifecycle()
      spec.textusControlCenterRegistrationHttpLifecycle()
      spec.textusControlCenterRegistrationHttpFailureIsolation()
      spec.lifecycleSupervisorFailurePreservesRegistrationHeartbeat()
      spec.executeTargetFirstDelegatesToRuntime()
      spec.launcherHomeEnvironmentPropagatesToRuntime()
      spec.canonicalDevelopmentTargetsUseConfiguredRuntime()
      spec.canonicalDevelopmentTargetSelectsCompatibleInstalledRuntime()
      spec.canonicalDevelopmentTargetRefreshesStaleRuntimeCatalog()
      spec.canonicalDevelopmentTargetRetainsStaleRuntimeCatalogWhenRefreshFails()
      spec.canonicalDevelopmentTargetUsesExplicitRuntimeVersion()
      spec.canonicalDevelopmentTargetUsesExplicitDevelopmentRuntime()
      spec.canonicalServerRejectsRuntimeCheckoutTarget()
      spec.deprecatedDevSeparatesDevelopmentTargetFromRuntime()
      spec.serverExecutionDelegatesDefaultPortResolutionToRuntime()
      spec.runtimeCatalogParseAndSelectorResolution()
      spec.runtimeCatalogCommands()
      spec.runtimeCurrentWarnsWhenCachedRecommendedIsStale()
      spec.runtimeDescriptorCommands()
      spec.runtimeDescriptorPrefersRuntimeJarDescriptor()
      spec.devParser()
      spec.devServerRewritesToCncfArgs()
      spec.devServerParserSupportsProcessManagementOptions()
      spec.devServerWritesStateDuringInvocation()
      spec.devServerRejectsAliveExistingState()
      spec.devServerStopExistingBeforeInvocation()
      spec.devStopStopsExistingWithoutInvocation()
      spec.devStopUsesRecordedPortWhenPortIsOmitted()
      spec.devServerForceStopsAfterGracefulFailure()
      spec.devServerRequiresForceForAmbiguousAliveState()
      spec.devServerRejectsPidReuseWithoutForce()
      spec.devServerProfileAddsLocalPersistentSqliteArgs()
      spec.devConfigCanSelectExecutionProfile()
      spec.devServerUsesRuntimeDevelopmentDirectory()
      spec.devServerUsesRuntimeDevelopmentCatalogForSelection()
      spec.devCommandPassesRuntimeLeadingArgs()
      spec.devCommandKeepsSampleMainClassValueAsRuntimeArg()
      spec.devCommandCanDisableProjectClasspath()
      spec.devCommandCanDisableProjectComponentDevDir()
      spec.devCommandDoesNotAutoActivateComponentDirArtifacts()
      spec.devTargetOptionsAreMutuallyExclusive()
      spec.devNameTargetUsesLocalSnapshotOnly()
      spec.devNameTargetSnapshotBypassesReleaseCatalog()
      spec.devNameTargetUsesReleaseRepositories()
      spec.devNameTargetUsesSelectedArtifactRepository()
      spec.devArtifactRepositoryRejectsInvalidTarget()
      spec.devServerEmulationRewritesToCncfArgs()
      spec.devProjectLoadsTargetProjectConfig()
      spec.devHelpExplainsResolutionModel()
      spec.devCheckReportsMainTargetAndDependencyResolution()
      spec.devCheckReportsDevServerState()
      spec.devCheckTreatsMissingMainTargetClasspathAsError()
      spec.devCheckTreatsMissingDependencyClasspathAsError()
      spec.devServerRequiresPreparedMainTargetClasspath()
      spec.runtimeDevelopmentRequiresPreparedClasspath()
      spec.runtimeDevelopmentReadsNewlineDelimitedClasspath()
      spec.devUsesCurrentCompatibleRuntimeByDefault()
      spec.devCanSelectLatestTestedRuntime()
      spec.devCanSelectLatestCompatibleRuntime()
      spec.devCanSelectNewestCompatibleRuntime()
      spec.devParsesInlineRuntimeRequirementLists()
      spec.devSelectsCommonRuntimeAcrossProjectAndDependency()
      spec.devRuntimeConflictDefaultsToError()
      spec.devRuntimeConflictCanUseNewestPolicy()
      spec.runtimeCommandDoesNotLoadCncf()
      spec.componentRepositoryCommandParser()
      spec.componentRepositoryDevelopmentOverridesLocalIdentity()
      spec.componentRepositoryPreservesNamespaceQualifiedCarIdentity()
      spec.componentRepositoryRejectsImplicitDevelopmentDirectory()
      spec.componentRepositoryShowUsesDescriptorIdentity()
      spec.componentRepositoryOutputMatchesTextusIdentityColumns()
      spec.componentRepositoryRejectsInvalidLocalIndex()
      spec.componentRepositoryCommandDoesNotLoadCncfRuntime()
      spec.lifecycleSupervisorResolvesRetainedDevelopmentProfile()
      spec.lifecycleSupervisorUsesRetainedMutationOrder()
      spec.lifecycleSupervisorRejectsInvalidDevelopmentEvidence()
      spec.canonicalServerRetainsDevelopmentProfile()
      spec.lifecycleSupervisorProjectsProfileResolutionToHttp()
      spec.lifecycleSupervisorRetainsRejectedRequestAcrossRestart()
      spec.lifecycleSupervisorRejectsExpiredRequest()
      spec.lifecycleSupervisorRejectsCorruptRequestIdentity()
      spec.lifecycleSupervisorControlsOwnedChildOnly()
      spec.lifecycleSupervisorRejectsPersistedOwnershipWithoutChildHandle()
      spec.lifecycleSupervisorRejectsDuplicateOrDeadChildStart()
      spec.lifecycleSupervisorStopsChildWhenStatePersistenceFails()
      spec.lifecycleSupervisorUsesDescriptorPortAndCanonicalDevelopmentCommand()
      spec.lifecycleSupervisorRejectsUnavailablePortBeforeStartOrRestart()
      spec.lifecycleSupervisorReleasesOwnershipWhenRestartReplacementFails()
      spec.lifecycleSupervisorFailsClosedWhenRestartFailureCannotBePersisted()
      spec.lifecycleSupervisorDaemonUsesPrivateLoopbackConfiguration()
      spec.lifecycleSupervisorDaemonRejectsUnsafeConfigurationOrCredential()
      spec.lifecycleSupervisorLifecycleCommandUsesInternalAuthority()
      spec.lifecycleSupervisorAuthorityWaitsForColdStart()
      spec.lifecycleSupervisorDaemonHostBindsLoopbackUntilInterrupted()
      spec.lifecycleSupervisorDaemonClosesListenerWhenShutdownHookRegistrationFails()
      spec.latestRuntimeIsConcrete()
      spec.noCncfRuntimeLibraryDependencies()
      println("CncfLauncherSpec: OK")
  }
}

final class FakeResolver(
  classpath: Option[Vector[Path]] = None
) extends CncfRuntimeResolver {
  var resolvedVersions: Vector[String] = Vector.empty
  var resolvedClasspaths: Vector[String] = Vector.empty
  override def resolveVersion(version: String, config: LauncherConfig, paths: LauncherPaths): String = {
    resolvedVersions :+= version
    if (version == LauncherConfig.DEFAULT_RUNTIME_VERSION) "0.9.0" else version
  }
  def resolve(version: String, config: LauncherConfig, paths: LauncherPaths): Vector[Path] = {
    val concreteversion = resolveVersion(version, config, paths)
    resolvedClasspaths :+= concreteversion
    classpath.getOrElse(Vector(paths.cwd.resolve(s"fake-cncf-$concreteversion.jar")))
  }
}

object FakeResolver {
  def apply(): FakeResolver = new FakeResolver()
  def apply(classpath: Option[Vector[Path]]): FakeResolver = new FakeResolver(classpath)
}

final class FakeClasspathExporter(
  response: Either[CncfException, String]
) extends RuntimeClasspathExporter {
  var projects: Vector[Path] = Vector.empty

  def exportRuntimeClasspath(project: Path): String = {
    projects :+= project
    response match {
      case Right(value) => value
      case Left(error) => throw error
    }
  }
}

object FakeClasspathExporter {
  def success(value: String): FakeClasspathExporter =
    new FakeClasspathExporter(Right(value))

  def failure(message: String): FakeClasspathExporter =
    new FakeClasspathExporter(Left(CncfException(message)))
}

final class FakeInvoker extends CncfInvoker {
  var lastClasspath: Vector[Path] = Vector.empty
  var lastArgs: Vector[String] = Vector.empty
  var onInvoke: () => Unit = () => ()

  override def invoke(classpath: Vector[Path], args: Vector[String]): Int = {
    lastClasspath = classpath
    lastArgs = args
    onInvoke()
    0
  }
}

object FakeInvoker {
  def apply(): FakeInvoker = new FakeInvoker()
}

final class FakeLifecycleSupervisorDaemonHost extends LifecycleSupervisorDaemonHost {
  var configurations: Vector[LifecycleSupervisorDaemonConfiguration] = Vector.empty
  var tokens: Vector[String] = Vector.empty
  var paths: Vector[LauncherPaths] = Vector.empty

  def serve(configuration: LifecycleSupervisorDaemonConfiguration, token: String, launcherpaths: LauncherPaths): Int = {
    configurations :+= configuration
    tokens :+= token
    paths :+= launcherpaths
    0
  }
}

object FakeLifecycleSupervisorDaemonHost {
  def apply(): FakeLifecycleSupervisorDaemonHost = new FakeLifecycleSupervisorDaemonHost()
}

final class FakeCncfTextusControlCenterRegistrationReporter extends CncfTextusControlCenterRegistrationReporter {
  var starts: Vector[(CncfTextusControlCenterRegistrationReport, Option[String])] = Vector.empty
  var configs: Vector[CncfTextusControlCenterRegistrationConfig] = Vector.empty
  var closes: Int = 0

  def start(
    config: CncfTextusControlCenterRegistrationConfig,
    report: CncfTextusControlCenterRegistrationReport,
    token: Option[String]
  ): CncfTextusControlCenterRegistrationSession = {
    starts :+= report -> token
    configs :+= config
    new CncfTextusControlCenterRegistrationSession {
      def close(): Unit = closes += 1
    }
  }
}

object FakeCncfTextusControlCenterRegistrationReporter {
  def apply(): FakeCncfTextusControlCenterRegistrationReporter = new FakeCncfTextusControlCenterRegistrationReporter()
}

final class CncfTextusControlCenterRegistrationOutageReporter extends CncfTextusControlCenterRegistrationReporter {
  def start(
    config: CncfTextusControlCenterRegistrationConfig,
    report: CncfTextusControlCenterRegistrationReport,
    token: Option[String]
  ): CncfTextusControlCenterRegistrationSession =
    throw CncfException("simulated Textus Control Center outage")
}

final class FakeLauncherDevInvoker extends LauncherDevInvoker {
  var devDir: Option[Path] = None
  var args: Vector[String] = Vector.empty
  var cwd: Option[Path] = None

  def invoke(devdir: Path, args: Vector[String], cwd: Path): Int = {
    this.devDir = Some(devdir)
    this.args = args
    this.cwd = Some(cwd)
    0
  }
}

object FakeLauncherDevInvoker {
  def apply(): FakeLauncherDevInvoker = new FakeLauncherDevInvoker()
}

final class FakeDevServerProcessManager(
  currentpid: Long
) extends DevServerProcessManager {
  var alive: Set[Long] = Set.empty
  var defaultProcessStartedAt: Option[java.time.Instant] =
    Some(java.time.Instant.parse("2026-05-24T00:00:00Z"))
  var processStarts: Map[Long, java.time.Instant] =
    Map(currentpid -> java.time.Instant.parse("2026-05-24T00:00:00Z"))
  var commandLines: Map[Long, String] =
    Map(currentpid -> "cncf dev server")
  var gracefulStopped: Vector[Long] = Vector.empty
  var forceStopped: Vector[Long] = Vector.empty
  var gracefulSucceeds: Boolean = true

  def currentPid: Long =
    currentpid

  def processStartedAt(pid: Long): Option[java.time.Instant] =
    processStarts.get(pid).orElse(defaultProcessStartedAt)

  def commandLine(pid: Long): Option[String] =
    commandLines.get(pid)

  def isAlive(pid: Long): Boolean =
    alive.contains(pid)

  def stopGracefully(pid: Long): Boolean = {
    gracefulStopped :+= pid
    if (gracefulSucceeds) {
      alive -= pid
      true
    } else {
      false
    }
  }

  def stopForcibly(pid: Long): Boolean = {
    forceStopped :+= pid
    alive -= pid
    true
  }
}

object FakeDevServerProcessManager {
  def apply(currentpid: Long): FakeDevServerProcessManager =
    new FakeDevServerProcessManager(currentpid)
}
