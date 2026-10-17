/*
 * @since   Aug. 28, 2026
 * @version Oct. 17, 2026
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

trait CncfLauncherLifecycleSpecSupport extends CncfLauncherSpecSupport {
  self: org.scalatest.Informing =>
  def lifecycleSupervisorFailurePreservesRegistrationHeartbeat(): Unit = _with_temp_paths { paths =>
    Given("a lifecycle request rejected before launch and an independently reachable Control Center registration endpoint")
    val supervisor = LifecycleSupervisorHttpServer("local-supervisor", "test-token", LifecycleSupervisorProfileResolver(paths), Some(LifecycleSupervisorStateStore(paths, "local-supervisor"))).start(0)
    val registrations = new ConcurrentLinkedQueue[String]()
    val controlcenter = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    controlcenter.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        registrations.add(exchange.getRequestURI.toString)
        exchange.sendResponseHeaders(204, -1)
        exchange.close()
      }
    })
    controlcenter.start()
    val snapshotpropertykey = "textus.server.bound-snapshot"
    val previoussnapshot = sys.props.get(snapshotpropertykey)
    try {
      sys.props.update(snapshotpropertykey, "v1\nhttp://127.0.0.1:18013\n/web")
      val supervisorendpoint = s"http://127.0.0.1:${supervisor.getAddress.getPort}/v1/lifecycle-requests"
      val configuration = CncfTextusControlCenterRegistrationConfig(
        endpoint = s"http://127.0.0.1:${controlcenter.getAddress.getPort}/rest/v1/org-simplemodeling-textus-control-center/subsystem-inventory",
        tokenEnv = "TEXTUS_ADMIN_REGISTRATION_TOKEN",
        timeout = java.time.Duration.ofSeconds(1),
        heartbeatInterval = java.time.Duration.ofMillis(10),
        hostLabel = "failure-isolation",
        baseUrl = "http://127.0.0.1:18013"
      )
      val report = CncfTextusControlCenterRegistrationReport(
        instanceId = "40404040-4040-4040-8040-404040404040",
        target = "textus-control-center",
        artifactId = Some("textus-control-center"),
        executionMode = "development",
        developmentDirectory = None,
        subsystemName = Some("textus-control-center"),
        subsystemVersion = None,
        runtimeVersion = "spec-runtime",
        startedAt = java.time.Instant.parse("2026-07-22T00:00:00Z")
      )

      When("the supervisor rejects the unresolved component and the running launcher sends registration heartbeats")
      val rejected = _post_lifecycle_request(supervisorendpoint, "missing-component", "failure-isolation-request", "failure-isolation-key")
      val session = CncfTextusControlCenterRegistrationReporter.System.start(configuration, report, Some("registration-token"))
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
      while (registrations.size < 2 && System.nanoTime() < deadline)
        Thread.sleep(10L)
      session.close()
      _await_registration(registrations.iterator.asScala.exists(_.contains("/deregister-subsystem?")))

      Then("the failed lifecycle request creates no process authority and does not suppress registration, heartbeat, or deregistration")
      rejected should include(LifecycleSupervisorProfileResolver.PROFILE_UNAVAILABLE)
      registrations.iterator.asScala.exists(_.contains("register-subsystem")) shouldBe true
      registrations.iterator.asScala.exists(_.contains("heartbeat-subsystem")) shouldBe true
      registrations.iterator.asScala.exists(_.contains("deregister-subsystem")) shouldBe true
      registrations.iterator.asScala.forall(_.contains("instanceId=40404040-4040-4040-8040-404040404040")) shouldBe true
    } finally {
      previoussnapshot match {
        case Some(value) => sys.props.update("textus.server.bound-snapshot", value)
        case None => sys.props.remove("textus.server.bound-snapshot")
      }
      controlcenter.stop(0)
      supervisor.stop(0)
    }
  }

  def lifecycleSupervisorResolvesRetainedDevelopmentProfile(): Unit = _with_temp_paths { paths =>
    Given("retained shared Launcher evidence naming an absolute CAR checkout")
    val project = paths.cwd.resolve("checkout-with-different-directory-name")
    _write(project.resolve("project.yaml"), _component_project_yaml("textus-control-center", "car", "1.0.0-SNAPSHOT"))
    _record_development_evidence(paths, project, "textus-control-center", "textus")

    When("the supervisor resolves the component identity")
    val result = LifecycleSupervisorProfileResolver(paths).resolve("textus-control-center")

    Then("the descriptor identity selects the retained directory rather than its filesystem name")
    result.map(_.artifactId) shouldBe Right("textus-control-center")
    result.map(_.developmentDirectory) shouldBe Right(project.toAbsolutePath.normalize)
    result.map(_.defaultPort) shouldBe Right(18013)
  }

  def lifecycleSupervisorUsesRetainedMutationOrder(): Unit = _with_temp_paths { paths =>
    Given("two retained development records whose first launcher timestamp is ahead of the local clock")
    val first = paths.cwd.resolve("first")
    val latest = paths.cwd.resolve("latest")
    _write(first.resolve("project.yaml"), _component_project_yaml("textus-control-center", "car", "1.0.0-SNAPSHOT", 18013))
    _write(latest.resolve("project.yaml"), _component_project_yaml("textus-control-center", "car", "1.0.0-SNAPSHOT", 18014))
    _record_development_evidence(paths, first, "textus-control-center", startedat = java.time.Instant.now().plusSeconds(3600))
    _record_development_evidence(paths, latest, "textus-control-center", startedat = java.time.Instant.now())

    When("the supervisor resolves the retained profile")
    val result = LifecycleSupervisorProfileResolver(paths).resolve("textus-control-center")

    Then("local mutation order wins without trusting the skewed timestamp as ordering authority")
    result.map(_.developmentDirectory) shouldBe Right(latest.toAbsolutePath.normalize)
    result.map(_.defaultPort) shouldBe Right(18014)
  }

  def lifecycleSupervisorRejectsInvalidDevelopmentEvidence(): Unit = _with_temp_paths { paths =>
    Given("missing, portless, and descriptor-mismatched retained development evidence")
    val mismatched = paths.cwd.resolve("mismatched")
    val portless = paths.cwd.resolve("portless")
    _write(mismatched.resolve("project.yaml"), _component_project_yaml("other-component", "car", "1.0.0-SNAPSHOT"))
    _write(portless.resolve("project.yaml"), _component_project_yaml("textus-control-center", "car", "1.0.0-SNAPSHOT", 0))

    When("the supervisor resolves each unavailable operational component")
    val missing = LifecycleSupervisorProfileResolver(paths).resolve("textus-control-center")
    _record_development_evidence(paths, mismatched, "textus-control-center")
    val mismatch = LifecycleSupervisorProfileResolver(paths).resolve("textus-control-center")
    _record_development_evidence(paths, portless, "textus-control-center")
    val invalidport = LifecycleSupervisorProfileResolver(paths).resolve("textus-control-center")

    Then("each unavailable profile returns one stable safe diagnostic without filesystem discovery")
    missing shouldBe Left(LifecycleSupervisorProfileResolver.PROFILE_UNAVAILABLE)
    mismatch shouldBe Left(LifecycleSupervisorProfileResolver.PROFILE_UNAVAILABLE)
    invalidport shouldBe Left(LifecycleSupervisorProfileResolver.PROFILE_UNAVAILABLE)
  }

  def canonicalServerRetainsDevelopmentProfile(): Unit = _with_temp_paths { paths =>
    Given("a CAR project started through the canonical current-directory command")
    _write(paths.cwd.resolve("project.yaml"), _component_project_yaml("textus-control-center", "car", "1.0.0-SNAPSHOT"))
    _write(paths.cwd.resolve("build.sbt"), """version := "1.0.0-SNAPSHOT"
      |""".stripMargin)
    val runtimejar = paths.cwd.resolve("runtime-cache").resolve("0.5.0-SNAPSHOT").resolve("jars").resolve("goldenport-cncf_3.jar")
    _write(DevSupport.runtimeClasspathFile(paths.cwd), runtimejar.toString)
    val invoker = FakeInvoker()
    val launcher = new CncfLauncher(paths, FakeResolver(), invoker)

    When("cncf server adds its development assembly arguments and completes without a foreground authority command")
    launcher.run(Vector("server")) shouldBe 0
    val profile = LifecycleSupervisorProfileResolver(paths).resolve("textus-control-center")
    val evidence = CncfLocalServerEvidenceStore(paths).listProjection().toOption.get.entries

    Then("the original server intent retains evidence while only the runtime receives the additional arguments")
    Files.exists(paths.supervisorConfig) shouldBe false
    invoker.lastArgs.indexOf("server") should be > 0
    evidence.map(_.launcherKind) should contain ("cncf")
    evidence.map(_.stoppedAt.isDefined) should contain (true)
    profile.map(_.developmentDirectory) shouldBe Right(paths.cwd.toAbsolutePath.normalize)
    profile.map(_.defaultPort) shouldBe Right(18013)
  }

  def lifecycleSupervisorProjectsProfileResolutionToHttp(): Unit = _with_temp_paths { paths =>
    Given("an authenticated supervisor with retained CAR development evidence and an occupied declared port")
    val project = paths.cwd.resolve("registered")
    val listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress)
    _write(project.resolve("project.yaml"), _component_project_yaml("textus-control-center", "car", "1.0.0-SNAPSHOT", listener.getLocalPort))
    _record_development_evidence(paths, project, "textus-control-center")
    val server = LifecycleSupervisorHttpServer("local-supervisor", "test-token", LifecycleSupervisorProfileResolver(paths), Some(LifecycleSupervisorStateStore(paths, "local-supervisor"))).start(0)

    try {
      When("the lifecycle endpoint receives requests for a retained and an unregistered artifact")
      val endpoint = s"http://127.0.0.1:${server.getAddress.getPort}/v1/lifecycle-requests"
      val registered = _post_lifecycle_request(endpoint, "textus-control-center", "registered-request", "registered-key")
      val unavailable = _post_lifecycle_request(endpoint, "other-component", "unavailable-request", "unavailable-key")

      Then("the default supervisor factory performs private port preflight without leaking the directory")
      registered should include(LifecycleSupervisorDevelopmentDirectoryChildFactory.PORT_UNAVAILABLE)
      registered should not include project.toString
      unavailable should include(LifecycleSupervisorProfileResolver.PROFILE_UNAVAILABLE)
    } finally {
      server.stop(0)
      listener.close()
    }
  }

  def lifecycleSupervisorRetainsRejectedRequestAcrossRestart(): Unit = _with_temp_paths { paths =>
    Given("a supervisor request that cannot resolve a configured launch profile")
    val requestid = "retained-request"
    val first = LifecycleSupervisorHttpServer("local-supervisor", "test-token", LifecycleSupervisorProfileResolver(paths), Some(LifecycleSupervisorStateStore(paths, "local-supervisor"))).start(0)

    try {
      When("the supervisor restarts after recording its safe rejection")
      val firstendpoint = s"http://127.0.0.1:${first.getAddress.getPort}/v1/lifecycle-requests"
      val response = _post_lifecycle_request(firstendpoint, "missing-component", requestid, "retained-key")
      first.stop(0)
      val restarted = LifecycleSupervisorHttpServer("local-supervisor", "test-token", LifecycleSupervisorProfileResolver(paths), Some(LifecycleSupervisorStateStore(paths, "local-supervisor"))).start(0)
      try {
        val endpoint = s"http://127.0.0.1:${restarted.getAddress.getPort}/v1/lifecycle-requests"
        val lookup = _get_lifecycle_request(endpoint, requestid)
        val retry = _post_lifecycle_request(endpoint, "missing-component", "different-request", "retained-key")

        Then("lookup and retry retain the original safe result without inferring process ownership")
        response should include(LifecycleSupervisorProfileResolver.PROFILE_UNAVAILABLE)
        lookup should include(requestid)
        retry should include(requestid)
        retry should include(LifecycleSupervisorProfileResolver.PROFILE_UNAVAILABLE)
      } finally {
        restarted.stop(0)
      }
    } finally {
      first.stop(0)
    }
  }

  def lifecycleSupervisorRejectsExpiredRequest(): Unit = _with_temp_paths { paths =>
    Given("an authenticated lifecycle request whose absolute deadline has already elapsed")
    val server = LifecycleSupervisorHttpServer("local-supervisor", "test-token", LifecycleSupervisorProfileResolver(paths), Some(LifecycleSupervisorStateStore(paths, "local-supervisor"))).start(0)
    try {
      val endpoint = s"http://127.0.0.1:${server.getAddress.getPort}/v1/lifecycle-requests"

      When("the local supervisor receives the expired request")
      val response = _post_lifecycle_request(endpoint, "missing-component", "expired-request", "expired-key", deadline = java.time.Instant.now().minusSeconds(1))

      Then("it retains a safe timed-out result without resolving a profile or creating a child")
      response should include("supervisor-request-timed-out")
      response should include("\"state\":\"rejected\"")
    } finally server.stop(0)
  }

  def lifecycleSupervisorRejectsCorruptRequestIdentity(): Unit = _with_temp_paths { paths =>
    Given("a persisted supervisor ledger with duplicated and mismatched request identity")
    val request = LifecycleSupervisorRequest("request-a", "key-a", "component-a", LifecycleAction.Start, "operator", java.time.Instant.parse("2026-07-22T00:00:30Z"))
    val result = LifecycleSupervisorProtocol.rejected(request, "local-supervisor", "supervisor-launch-profile-unavailable")
    val mismatched = LifecycleSupervisorRequestRecord(request, result.copy(requestId = "other-request"))
    val duplicate = LifecycleSupervisorRequestRecord(request.copy(idempotencyKey = "key-b"), result)
    val snapshot = LifecycleSupervisorStateSnapshot(LifecycleSupervisorStateStore.SCHEMA_VERSION, "local-supervisor", Vector(mismatched, duplicate), Map.empty)
    _write(paths.supervisorState, snapshot.asJson.noSpaces)

    When("the supervisor loads the private state ledger")
    val loaded = LifecycleSupervisorStateStore(paths, "local-supervisor").load()

    Then("it refuses ambiguous request correlation before serving lookup or retry")
    loaded shouldBe Left(LifecycleSupervisorStateStore.STATE_UNAVAILABLE)
  }

  def lifecycleSupervisorControlsOwnedChildOnly(): Unit = _with_temp_paths { paths =>
    Given("a configured profile and a supervisor-local child factory")
    val project = paths.cwd.resolve("component")
    _write(project.resolve("project.yaml"), _component_project_yaml("textus-control-center", "car", "1.0.0-SNAPSHOT"))
    _write(paths.supervisorConfig, s"""schema: cncf.launcher.supervisor.v1
      |profiles:
      |  development-directory:
      |    textus-control-center: ${project.toAbsolutePath}
      |""".stripMargin)
    var started = Vector.empty[LifecycleSupervisorChild]
    val factory = new LifecycleSupervisorChildFactory {
      def start(profile: LifecycleSupervisorLaunchProfile) = {
        val child = new LifecycleSupervisorChild {
          val instanceId = s"instance-${started.size + 1}"
          var alive = true
          def isAlive = alive
          def stop() = { alive = false; true }
        }
        started = started :+ child
        Right(child)
      }
    }
    val server = LifecycleSupervisorHttpServer("local-supervisor", "test-token", LifecycleSupervisorProfileResolver(paths), Some(LifecycleSupervisorStateStore(paths, "local-supervisor")), factory).start(0)
    try {
      When("start, restart, and stop requests address the supervisor-created child")
      val endpoint = s"http://127.0.0.1:${server.getAddress.getPort}/v1/lifecycle-requests"
      val startresponse = _post_lifecycle_request(endpoint, "textus-control-center", "start-request", "start-key")
      val restarted = _post_lifecycle_request(endpoint, "textus-control-center", "restart-request", "restart-key", "restart")
      val stopped = _post_lifecycle_request(endpoint, "textus-control-center", "stop-request", "stop-key", "stop")

      Then("the supervisor replaces and stops only its retained child handles")
      startresponse should include("instance-1")
      restarted should include("instance-2")
      stopped should include("stopped")
      started.map(_.isAlive) shouldBe Vector(false, false)
    } finally server.stop(0)
  }

  def lifecycleSupervisorRejectsPersistedOwnershipWithoutChildHandle(): Unit = _with_temp_paths { paths =>
    Given("a persisted owned-instance record from a prior supervisor process")
    val project = paths.cwd.resolve("component")
    _write(project.resolve("project.yaml"), _component_project_yaml("textus-control-center", "car", "1.0.0-SNAPSHOT"))
    _write(paths.supervisorConfig, s"""schema: cncf.launcher.supervisor.v1
      |profiles:
      |  development-directory:
      |    textus-control-center: ${project.toAbsolutePath}
      |""".stripMargin)
    val child = new LifecycleSupervisorChild {
      val instanceId = "prior-instance"
      var alive = true
      def isAlive = alive
      def stop() = { alive = false; true }
    }
    val factory = new LifecycleSupervisorChildFactory {
      def start(profile: LifecycleSupervisorLaunchProfile) = Right(child)
    }
    val store = LifecycleSupervisorStateStore(paths, "local-supervisor")
    val first = LifecycleSupervisorHttpServer("local-supervisor", "test-token", LifecycleSupervisorProfileResolver(paths), Some(store), factory).start(0)
    try {
      When("a replacement supervisor receives stop for the persisted instance")
      val firstendpoint = s"http://127.0.0.1:${first.getAddress.getPort}/v1/lifecycle-requests"
      _post_lifecycle_request(firstendpoint, "textus-control-center", "start-request", "start-key") should include("prior-instance")
      first.stop(0)
      val replacement = LifecycleSupervisorHttpServer("local-supervisor", "test-token", LifecycleSupervisorProfileResolver(paths), Some(store), factory).start(0)
      try {
        val endpoint = s"http://127.0.0.1:${replacement.getAddress.getPort}/v1/lifecycle-requests"
        val result = _post_lifecycle_request(endpoint, "textus-control-center", "stop-request", "stop-key", "stop")

        Then("it refuses to signal a process for which it retained no local handle")
        result should include("supervisor-ownership-unavailable")
        child.isAlive shouldBe true
      } finally replacement.stop(0)
    } finally first.stop(0)
  }

  def lifecycleSupervisorRejectsDuplicateOrDeadChildStart(): Unit = _with_temp_paths { paths =>
    Given("a configured profile and child factories for running and already-dead children")
    val project = paths.cwd.resolve("component")
    _write(project.resolve("project.yaml"), _component_project_yaml("textus-control-center", "car", "1.0.0-SNAPSHOT"))
    _write(paths.supervisorConfig, s"""schema: cncf.launcher.supervisor.v1
      |profiles:
      |  development-directory:
      |    textus-control-center: ${project.toAbsolutePath}
      |""".stripMargin)
    var starts = 0
    val runningchild = new LifecycleSupervisorChild {
      val instanceId = "running-instance"
      var alive = true
      def isAlive = alive
      def stop() = { alive = false; true }
    }
    var deadchildstopped = false
    val deadchild = new LifecycleSupervisorChild {
      val instanceId = "dead-instance"
      def isAlive = false
      def stop() = { deadchildstopped = true; true }
    }
    val factory = new LifecycleSupervisorChildFactory {
      def start(profile: LifecycleSupervisorLaunchProfile) = {
        starts += 1
        Right(if (starts == 1) runningchild else deadchild)
      }
    }
    val server = LifecycleSupervisorHttpServer("local-supervisor", "test-token", LifecycleSupervisorProfileResolver(paths), Some(LifecycleSupervisorStateStore(paths, "local-supervisor")), factory).start(0)
    try {
      When("a different start request arrives while an owned child is retained")
      val endpoint = s"http://127.0.0.1:${server.getAddress.getPort}/v1/lifecycle-requests"
      _post_lifecycle_request(endpoint, "textus-control-center", "first-start", "first-key") should include("running-instance")
      val duplicate = _post_lifecycle_request(endpoint, "textus-control-center", "second-start", "second-key")

      Then("it does not create a replacement child")
      duplicate should include("supervisor-ownership-unavailable")
      starts shouldBe 1
      runningchild.isAlive shouldBe true

      When("the retained child stops and a subsequent factory result is already dead")
      _post_lifecycle_request(endpoint, "textus-control-center", "stop-request", "stop-key", "stop") should include("stopped")
      val dead = _post_lifecycle_request(endpoint, "textus-control-center", "dead-start", "dead-key")

      Then("it rejects and cleans up the non-running child")
      dead should include("supervisor-execution-unavailable")
      deadchildstopped shouldBe true
    } finally server.stop(0)
  }

  def lifecycleSupervisorStopsChildWhenStatePersistenceFails(): Unit = _with_temp_paths { paths =>
    Given("a configured profile whose state-file target is an existing directory")
    val project = paths.cwd.resolve("component")
    _write(project.resolve("project.yaml"), _component_project_yaml("textus-control-center", "car", "1.0.0-SNAPSHOT"))
    _write(paths.supervisorConfig, s"""schema: cncf.launcher.supervisor.v1
      |profiles:
      |  development-directory:
      |    textus-control-center: ${project.toAbsolutePath}
      |""".stripMargin)
    Files.createDirectories(paths.supervisorState)
    val child = new LifecycleSupervisorChild {
      val instanceId = "unpersisted-instance"
      var alive = true
      def isAlive = alive
      def stop() = { alive = false; true }
    }
    val factory = new LifecycleSupervisorChildFactory {
      def start(profile: LifecycleSupervisorLaunchProfile) = Right(child)
    }
    val server = LifecycleSupervisorHttpServer("local-supervisor", "test-token", LifecycleSupervisorProfileResolver(paths), Some(LifecycleSupervisorStateStore(paths, "local-supervisor")), factory).start(0)
    try {
      When("a start request cannot durably record its new child handle")
      val endpoint = s"http://127.0.0.1:${server.getAddress.getPort}/v1/lifecycle-requests"
      val response = _post_lifecycle_request(endpoint, "textus-control-center", "unpersisted-start", "unpersisted-key")

      Then("the request is rejected and the child is compensatingly stopped")
      response should include(LifecycleSupervisorStateStore.STATE_UNAVAILABLE)
      child.isAlive shouldBe false
    } finally server.stop(0)
  }

  def lifecycleSupervisorUsesDescriptorPortAndCanonicalDevelopmentCommand(): Unit = _with_temp_paths { paths =>
    Given("a resolved development profile with a declared default port")
    val project = paths.cwd.resolve("component")
    _write(project.resolve("project.yaml"), _component_project_yaml("textus-control-center", "car", "1.0.0-SNAPSHOT", 18013))
    _write(paths.supervisorConfig, s"""schema: cncf.launcher.supervisor.v1
      |profiles:
      |  development-directory:
      |    textus-control-center: ${project.toAbsolutePath}
      |""".stripMargin)
    val profile = LifecycleSupervisorProfileResolver(paths).resolve("textus-control-center").toOption.get
    var command = Vector.empty[String]
    var directory = Option.empty[Path]
    val child = new LifecycleSupervisorChild {
      val instanceId = "instance-1"
      override val port = 18013
      var alive = true
      def isAlive = alive
      def stop() = { alive = false; true }
    }
    val runner = new LifecycleSupervisorCommandRunner {
      def start(value: Vector[String], cwd: Path, port: Int) = {
        command = value
        directory = Some(cwd)
        Right(child)
      }
    }
    val factory = LifecycleSupervisorDevelopmentDirectoryChildFactory(new LifecycleSupervisorPortProbe {
      def isAvailable(port: Int) = true
    }, runner)

    When("the supervisor starts the resolved profile with its newly allocated registration identity")
    val correlation = LifecycleSupervisorChildCorrelation("10101010-1010-4010-8010-101010101010")
    val result = factory.start(profile, correlation)

    Then("it uses a fixed target-first cncf server command, the descriptor port, and the same registration identity returned to Control Center")
    result.map(_.port) shouldBe Right(18013)
    result.map(_.instanceId) shouldBe Right(correlation.instanceId)
    command shouldBe Vector("cncf", project.toAbsolutePath.normalize.toString, "server", "--textus.server.port=18013", s"--textus.control-center.registration-instance-id=${correlation.instanceId}")
    directory shouldBe Some(project.toAbsolutePath.normalize)
  }

  def lifecycleSupervisorRejectsUnavailablePortBeforeStartOrRestart(): Unit = _with_temp_paths { paths =>
    Given("a declared port already occupied on loopback and factories that fail preflight before process creation")
    val project = paths.cwd.resolve("component")
    val listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress)
    val declaredport = listener.getLocalPort
    _write(project.resolve("project.yaml"), _component_project_yaml("textus-control-center", "car", "1.0.0-SNAPSHOT", declaredport))
    _write(paths.supervisorConfig, s"""schema: cncf.launcher.supervisor.v1
      |profiles:
      |  development-directory:
      |    textus-control-center: ${project.toAbsolutePath}
      |""".stripMargin)
    var starts = 0
    val child = new LifecycleSupervisorChild {
      val instanceId = "instance-1"
      override val port = declaredport
      var alive = true
      def isAlive = alive
      def stop() = { alive = false; true }
    }
    val runner = new LifecycleSupervisorCommandRunner {
      def start(command: Vector[String], directory: Path, port: Int) = {
        starts += 1
        Right(child)
      }
    }
    val blocked = LifecycleSupervisorDevelopmentDirectoryChildFactory(LifecycleSupervisorPortProbe.System, runner)
    val blockedserver = LifecycleSupervisorHttpServer("local-supervisor", "test-token", LifecycleSupervisorProfileResolver(paths), Some(LifecycleSupervisorStateStore(paths, "local-supervisor")), blocked).start(0)
    try {
      When("a start request encounters the occupied declared port")
      val endpoint = s"http://127.0.0.1:${blockedserver.getAddress.getPort}/v1/lifecycle-requests"
      val response = _post_lifecycle_request(endpoint, "textus-control-center", "blocked-start", "blocked-key")

      Then("it rejects before spawning a child")
      response should include(LifecycleSupervisorDevelopmentDirectoryChildFactory.PORT_UNAVAILABLE)
      starts shouldBe 0
    } finally {
      blockedserver.stop(0)
      listener.close()
    }

    val restartfactory = new LifecycleSupervisorChildFactory {
      override def preflight(profile: LifecycleSupervisorLaunchProfile, owned: Option[LifecycleSupervisorChild]) =
        if (owned.isDefined) Left(LifecycleSupervisorDevelopmentDirectoryChildFactory.PORT_UNAVAILABLE) else Right(())
      def start(profile: LifecycleSupervisorLaunchProfile) = {
        starts += 1
        Right(child)
      }
    }
    val restartserver = LifecycleSupervisorHttpServer("local-supervisor", "test-token", LifecycleSupervisorProfileResolver(paths), Some(LifecycleSupervisorStateStore(paths, "local-supervisor")), restartfactory).start(0)
    try {
      When("a restart preflight fails before stopping the owned child")
      val endpoint = s"http://127.0.0.1:${restartserver.getAddress.getPort}/v1/lifecycle-requests"
      _post_lifecycle_request(endpoint, "textus-control-center", "restart-start", "restart-start-key") should include("instance-1")
      val response = _post_lifecycle_request(endpoint, "textus-control-center", "blocked-restart", "blocked-restart-key", "restart")

      Then("the preflight rejection leaves the currently owned child running")
      response should include(LifecycleSupervisorDevelopmentDirectoryChildFactory.PORT_UNAVAILABLE)
      child.isAlive shouldBe true
      starts shouldBe 1
    } finally restartserver.stop(0)
  }

  def lifecycleSupervisorReleasesOwnershipWhenRestartReplacementFails(): Unit = _with_temp_paths { paths =>
    Given("a supervisor child whose restart replacement cannot be created")
    val project = paths.cwd.resolve("component")
    _write(project.resolve("project.yaml"), _component_project_yaml("textus-control-center", "car", "1.0.0-SNAPSHOT", 18013))
    _write(paths.supervisorConfig, s"""schema: cncf.launcher.supervisor.v1
      |profiles:
      |  development-directory:
      |    textus-control-center: ${project.toAbsolutePath}
      |""".stripMargin)
    val first = new LifecycleSupervisorChild {
      val instanceId = "first-instance"
      override val port = 18013
      var alive = true
      def isAlive = alive
      def stop() = { alive = false; true }
    }
    val recovered = new LifecycleSupervisorChild {
      val instanceId = "recovered-instance"
      override val port = 18013
      var alive = true
      def isAlive = alive
      def stop() = { alive = false; true }
    }
    var starts = 0
    val factory = new LifecycleSupervisorChildFactory {
      def start(profile: LifecycleSupervisorLaunchProfile) = {
        starts += 1
        starts match {
          case 1 => Right(first)
          case 2 => Left("supervisor-execution-unavailable")
          case _ => Right(recovered)
        }
      }
    }
    val server = LifecycleSupervisorHttpServer("local-supervisor", "test-token", LifecycleSupervisorProfileResolver(paths), Some(LifecycleSupervisorStateStore(paths, "local-supervisor")), factory).start(0)
    try {
      When("Restart stops the owned child and its replacement startup fails")
      val endpoint = s"http://127.0.0.1:${server.getAddress.getPort}/v1/lifecycle-requests"
      _post_lifecycle_request(endpoint, "textus-control-center", "first-start", "first-key") should include("first-instance")
      val failed = _post_lifecycle_request(endpoint, "textus-control-center", "failed-restart", "restart-key", "restart")
      val recoveredstart = _post_lifecycle_request(endpoint, "textus-control-center", "recovered-start", "recovered-key")

      Then("the failed result releases the stopped ownership for a later Start")
      failed should include("\"state\":\"failed\"")
      failed should include("supervisor-execution-unavailable")
      first.isAlive shouldBe false
      recoveredstart should include("recovered-instance")
      starts shouldBe 3
    } finally server.stop(0)
  }

  def lifecycleSupervisorFailsClosedWhenRestartFailureCannotBePersisted(): Unit = _with_temp_paths { paths =>
    Given("a running supervisor child whose restart failure cannot be written to the state ledger")
    val project = paths.cwd.resolve("component")
    _write(project.resolve("project.yaml"), _component_project_yaml("textus-control-center", "car", "1.0.0-SNAPSHOT", 18013))
    _write(paths.supervisorConfig, s"""schema: cncf.launcher.supervisor.v1
      |profiles:
      |  development-directory:
      |    textus-control-center: ${project.toAbsolutePath}
      |""".stripMargin)
    val child = new LifecycleSupervisorChild {
      val instanceId = "first-instance"
      override val port = 18013
      var alive = true
      def isAlive = alive
      def stop() = { alive = false; true }
    }
    var starts = 0
    val factory = new LifecycleSupervisorChildFactory {
      def start(profile: LifecycleSupervisorLaunchProfile) = {
        starts += 1
        if (starts == 1) Right(child) else Left("supervisor-execution-unavailable")
      }
    }
    val server = LifecycleSupervisorHttpServer("local-supervisor", "test-token", LifecycleSupervisorProfileResolver(paths), Some(LifecycleSupervisorStateStore(paths, "local-supervisor")), factory).start(0)
    try {
      When("Restart stops the child but the state target becomes unwritable before its failure record is saved")
      val endpoint = s"http://127.0.0.1:${server.getAddress.getPort}/v1/lifecycle-requests"
      _post_lifecycle_request(endpoint, "textus-control-center", "first-start", "first-key") should include("first-instance")
      Files.delete(paths.supervisorState)
      Files.createDirectories(paths.supervisorState)
      val restart = _post_lifecycle_request(endpoint, "textus-control-center", "failed-restart", "restart-key", "restart")
      val laterstart = _post_lifecycle_request(endpoint, "textus-control-center", "later-start", "later-key")

      Then("the supervisor rejects both the undiscoverable transition and future operations as state-unavailable")
      restart should include(LifecycleSupervisorStateStore.STATE_UNAVAILABLE)
      laterstart should include(LifecycleSupervisorStateStore.STATE_UNAVAILABLE)
      laterstart should not include "supervisor-ownership-unavailable"
      child.isAlive shouldBe false
      starts shouldBe 2
    } finally server.stop(0)
  }

  def lifecycleSupervisorDaemonUsesPrivateLoopbackConfiguration(): Unit = _with_temp_paths { paths =>
    Given("a private historical supervisor configuration and a host spy")
    _write(paths.supervisorConfig, _supervisor_yaml("local-supervisor", 18014, "CNCF_LIFECYCLE_SUPERVISOR_TOKEN"))
    val host = FakeLifecycleSupervisorDaemonHost()
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), supervisorhost = host)

    When("the compatibility foreground command is invoked")
    val error = intercept[CncfException] { launcher.run(Vector("launcher", "supervisor", "serve")) }

    Then("the parser shape remains but its execution is retired before hosting")
    error.getMessage shouldBe "launcher-lifecycle-authority-retired"
    CncfCommandParser.parse(Vector("launcher", "supervisor", "serve")) shouldBe CncfCommand.Supervisor.Serve
    host.configurations shouldBe empty
    host.tokens shouldBe empty
    host.paths shouldBe empty
  }

  def lifecycleSupervisorDaemonRejectsUnsafeConfigurationOrCredential(): Unit = _with_temp_paths { paths =>
    Given("a malformed supervisor configuration and a valid configuration without its credential")
    _write(paths.supervisorConfig, _supervisor_yaml("local-supervisor", 0, "not-safe") + "supervisor.token: private-token\n")
    val invalid = LifecycleSupervisorDaemonConfiguration.resolve(paths)
    val host = FakeLifecycleSupervisorDaemonHost()
    val invalidlauncher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), supervisorhost = host)

    When("the compatibility command is invoked with both historical configuration states")
    val invaliderror = intercept[CncfException] {
      invalidlauncher.run(Vector("launcher", "supervisor", "serve"))
    }
    _write(paths.supervisorConfig, _supervisor_yaml("local-supervisor", 18014, "CNCF_LIFECYCLE_SUPERVISOR_TOKEN"))
    val missingcredential = intercept[CncfException] {
      invalidlauncher.run(Vector("launcher", "supervisor", "serve"))
    }

    Then("retirement is configuration-independent and the historical configuration parser still rejects unsafe files")
    invalid shouldBe Left(LifecycleSupervisorDaemonConfiguration.CONFIGURATION_UNAVAILABLE)
    invaliderror.getMessage shouldBe "launcher-lifecycle-authority-retired"
    missingcredential.getMessage shouldBe "launcher-lifecycle-authority-retired"
    host.configurations shouldBe empty
  }

  def lifecycleSupervisorDaemonHostBindsLoopbackUntilInterrupted(): Unit = _with_temp_paths { paths =>
    Given("a foreground daemon host with an available loopback port")
    val listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress)
    val port = listener.getLocalPort
    listener.close()
    val configuration = LifecycleSupervisorDaemonConfiguration("local-supervisor", port, "CNCF_LIFECYCLE_SUPERVISOR_TOKEN")
    var result = Option.empty[Either[Throwable, Int]]
    val daemon = new Thread(() => {
      result = Some(Try(LifecycleSupervisorDaemonHost.System.serve(configuration, "private-token", paths)).toEither)
    })

    When("the daemon host starts and is then interrupted")
    daemon.start()
    var status = Option.empty[Int]
    var attempts = 0
    while (status.isEmpty && attempts < 50) {
      status = Try {
        val connection = URI(s"http://127.0.0.1:$port/v1/lifecycle-requests").toURL.openConnection().asInstanceOf[HttpURLConnection]
        connection.setConnectTimeout(100)
        connection.setReadTimeout(100)
        try connection.getResponseCode finally connection.disconnect()
      }.toOption
      if (status.isEmpty) Thread.sleep(20)
      attempts += 1
    }
    daemon.interrupt()
    daemon.join(5000)

    Then("the private HTTP listener is reachable only through loopback and shuts down with its foreground thread")
    status shouldBe Some(400)
    daemon.isAlive shouldBe false
    result shouldBe Some(Right(0))
  }

  def lifecycleSupervisorDaemonClosesListenerWhenShutdownHookRegistrationFails(): Unit = _with_temp_paths { paths =>
    Given("a foreground host whose shutdown hook cannot be registered")
    val listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress)
    val port = listener.getLocalPort
    listener.close()
    val configuration = LifecycleSupervisorDaemonConfiguration("local-supervisor", port, "CNCF_LIFECYCLE_SUPERVISOR_TOKEN")
    val host = new LifecycleSupervisorForegroundDaemonHost(new LifecycleSupervisorShutdownHooks {
      def add(hook: Thread): Unit = throw new IllegalStateException("shutdown unavailable")
      def remove(hook: Thread): Boolean = false
    })

    When("the host fails after binding but before its foreground loop is available")
    val error = intercept[IllegalStateException] {
      host.serve(configuration, "private-token", paths)
    }
    val rebound = new ServerSocket()
    try {
      rebound.bind(new InetSocketAddress(InetAddress.getLoopbackAddress, port))
    } finally rebound.close()

    Then("it releases the loopback port and preserves the unrecoverable hook failure")
    error.getMessage shouldBe "shutdown unavailable"
  }

  def lifecycleSupervisorLifecycleCommandUsesInternalAuthority(): Unit = _with_temp_paths { paths =>
    Given("a compatibility lifecycle submit command and an internal authority spy")
    var ensured = false
    val authority = new LifecycleSupervisorAuthority {
      def ensure(configuration: LifecycleSupervisorDaemonConfiguration, token: String, launcherpaths: LauncherPaths) = {
        ensured = true
        Right(())
      }
    }
    val output = new java.io.ByteArrayOutputStream()
    val launcher = new CncfLauncher(paths, FakeResolver(), FakeInvoker(), supervisorauthority = authority)

    When("the compatibility path submits a request")
    val stream = new java.io.PrintStream(output)
    val code = try Console.withOut(stream) {
      launcher.run(Vector(
        "launcher", "lifecycle", "submit",
        "--request-id", "request-1", "--idempotency-key", "key-1",
        "--artifact-id", "textus-control-center", "--action", "start",
        "--operator-subject-id", "operator-1",
        "--deadline-at", java.time.Instant.now().plusSeconds(60).toString
      ))
    } finally stream.close()

    Then("it echoes safe rejected identity without ensuring authority")
    code shouldBe 0
    ensured shouldBe false
    output.toString(StandardCharsets.UTF_8) should include("\"requestId\":\"request-1\"")
    output.toString(StandardCharsets.UTF_8) should include("launcher-lifecycle-authority-retired")
    output.toString(StandardCharsets.UTF_8) should include("\"state\":\"rejected\"")
    CncfCommandParser.parse(Vector("launcher", "lifecycle", "lookup", "request-1")) shouldBe CncfCommand.Lifecycle.Lookup("request-1")
  }

  def lifecycleSupervisorAuthorityWaitsForColdStart(): Unit = _with_temp_paths { paths =>
    Given("a local authority that becomes reachable only after its process has been started")
    val configuration = LifecycleSupervisorDaemonConfiguration("local-supervisor", 18014, "CNCF_LIFECYCLE_SUPERVISOR_TOKEN")
    var probes = 0
    var starts = 0
    val authority = new LocalLifecycleSupervisorAuthority(
      new LifecycleSupervisorAuthorityProbe {
        def available(value: LifecycleSupervisorDaemonConfiguration, token: String) = {
          probes += 1
          probes >= 4
        }
      },
      new LifecycleSupervisorAuthorityProcess {
        def start(value: LifecycleSupervisorDaemonConfiguration, token: String, launcherpaths: LauncherPaths) = {
          starts += 1
          Right(())
        }
      },
      attempts = 5,
      retrydelay = 0
    )

    When("the bounded lifecycle path ensures the cold authority")
    val result = authority.ensure(configuration, "private-token", paths)

    Then("it starts once and waits through readiness probes instead of rejecting immediately")
    result shouldBe Right(())
    starts shouldBe 1
    probes shouldBe 4
  }
  private def _await_registration(condition: => Boolean): Unit = {
    val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3)
    while (!condition && System.nanoTime() < deadline) Thread.sleep(5L)
    condition shouldBe true
  }


}
