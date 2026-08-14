package cncf.launcher

import java.net.{HttpURLConnection, URI, URLEncoder}
import java.nio.charset.StandardCharsets
import java.time.{Duration, Instant}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}

/*
 * @since   Jul. 18, 2026
 *  version Jul. 24, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final case class CncfTextusControlCenterRegistrationConfig(
  endpoint: String,
  tokenEnv: String,
  timeout: Duration,
  heartbeatInterval: Duration,
  hostLabel: String,
  baseUrl: String
)

object CncfTextusControlCenterRegistrationConfig {
  def enabled(values: Map[String, Vector[String]]): Option[Boolean] =
    _first(values, "textus-control-center.registration.enabled", "textus-admin.registration.enabled", "textus.admin.registration.enabled").map(_boolean)

  def fromParsed(values: Map[String, Vector[String]]): Option[CncfTextusControlCenterRegistrationConfig] = {
    def _first_(keys: String*): Option[String] =
      _first(values, keys*)

    if (!enabled(values).contains(true)) {
      None
    } else {
      Some(CncfTextusControlCenterRegistrationConfig(
        _first_("textus-control-center.registration.endpoint", "textus-admin.registration.endpoint", "textus.admin.registration.endpoint").getOrElse(throw CncfException("textus-control-center.registration.endpoint is required when registration is enabled")),
        _first_("textus-control-center.registration.token-env", "textus-control-center.registration.tokenEnv", "textus-admin.registration.token-env", "textus-admin.registration.tokenEnv", "textus.admin.registration.token-env", "textus.admin.registration.tokenEnv").getOrElse(throw CncfException("textus-control-center.registration.token-env is required when registration is enabled")),
        _duration(_first_("textus-control-center.registration.timeout", "textus-admin.registration.timeout", "textus.admin.registration.timeout").getOrElse("2s"), "textus-control-center.registration.timeout"),
        _duration(_first_("textus-control-center.registration.heartbeat-interval", "textus-control-center.registration.heartbeatInterval", "textus-admin.registration.heartbeat-interval", "textus-admin.registration.heartbeatInterval", "textus.admin.registration.heartbeat-interval", "textus.admin.registration.heartbeatInterval").getOrElse("30s"), "textus-control-center.registration.heartbeat-interval"),
        _first_("textus-control-center.registration.host-label", "textus-control-center.registration.hostLabel", "textus-admin.registration.host-label", "textus-admin.registration.hostLabel", "textus.admin.registration.host-label", "textus.admin.registration.hostLabel").getOrElse(throw CncfException("textus-control-center.registration.host-label is required when registration is enabled")),
        _first_("textus-control-center.registration.base-url", "textus-control-center.registration.baseUrl", "textus-admin.registration.base-url", "textus-admin.registration.baseUrl", "textus.admin.registration.base-url", "textus.admin.registration.baseUrl").getOrElse("")
      ))
    }
  }

  private def _first(values: Map[String, Vector[String]], keys: String*): Option[String] =
    keys.toVector.flatMap(key => values.getOrElse(key, Vector.empty)).headOption.map(_.trim).filter(_.nonEmpty)

  private def _boolean(value: String): Boolean =
    Set("true", "yes", "on", "1").contains(value.toLowerCase)

  private def _duration(value: String, name: String): Duration = {
    val normalized = value.trim
    val duration =
      if (normalized.matches("[0-9]+ms")) Duration.ofMillis(normalized.stripSuffix("ms").toLong)
      else if (normalized.matches("[0-9]+s")) Duration.ofSeconds(normalized.stripSuffix("s").toLong)
      else if (normalized.matches("[0-9]+m")) Duration.ofMinutes(normalized.stripSuffix("m").toLong)
      else scala.util.Try(Duration.parse(normalized)).getOrElse(throw CncfException(s"$name must be a positive duration"))
    if (duration.isZero || duration.isNegative)
      throw CncfException(s"$name must be a positive duration")
    duration
  }
}

final case class CncfTextusControlCenterRegistrationReport(
  instanceId: String,
  target: String,
  artifactId: Option[String],
  executionMode: String,
  developmentDirectory: Option[String],
  subsystemName: Option[String],
  subsystemVersion: Option[String],
  runtimeVersion: String,
  startedAt: Instant
)

trait CncfTextusControlCenterRegistrationSession {
  def close(): Unit
}

trait CncfTextusControlCenterRegistrationReporter {
  def start(
    config: CncfTextusControlCenterRegistrationConfig,
    report: CncfTextusControlCenterRegistrationReport,
    token: Option[String]
  ): CncfTextusControlCenterRegistrationSession
}

object CncfTextusControlCenterRegistrationReporter {
  val System: CncfTextusControlCenterRegistrationReporter = new SystemCncfTextusControlCenterRegistrationReporter
}

private final class SystemCncfTextusControlCenterRegistrationReporter extends CncfTextusControlCenterRegistrationReporter {
  def start(
    config: CncfTextusControlCenterRegistrationConfig,
    report: CncfTextusControlCenterRegistrationReport,
    token: Option[String]
  ): CncfTextusControlCenterRegistrationSession = {
    token match {
      case None =>
        _warning("credential is unavailable")
        CncfTextusControlCenterRegistrationSession.noop
      case Some(value) if value.trim.nonEmpty =>
        _start_pending(config, report, value)
      case Some(_) =>
        _warning("credential is unavailable")
        CncfTextusControlCenterRegistrationSession.noop
    }
  }

  private def _start_active(
    config: CncfTextusControlCenterRegistrationConfig,
    report: CncfTextusControlCenterRegistrationReport,
    token: String,
    snapshot: RegistrationSnapshot
  ): CncfTextusControlCenterRegistrationSession = {
    val registered = new AtomicBoolean(_request_best_effort(config, report, token, snapshot, "register-subsystem", "starting"))
    val executor = Executors.newSingleThreadScheduledExecutor(_daemon_thread_factory)
    val task = new Runnable {
      def run(): Unit =
        if (registered.get) {
          _request_best_effort(config, report, token, snapshot, "heartbeat-subsystem", "running")
          ()
        } else {
          registered.set(_request_best_effort(config, report, token, snapshot, "register-subsystem", "starting"))
        }
    }
    executor.scheduleAtFixedRate(task, config.heartbeatInterval.toMillis, config.heartbeatInterval.toMillis, TimeUnit.MILLISECONDS)
    ActiveCncfTextusControlCenterRegistrationSession(
      executor,
      () => if (registered.get) _request_best_effort(config, report, token, snapshot, "deregister-subsystem", "stopped")
    )
  }

  private def _start_pending(
    config: CncfTextusControlCenterRegistrationConfig,
    report: CncfTextusControlCenterRegistrationReport,
    token: String
  ): CncfTextusControlCenterRegistrationSession =
    _snapshot(config) match {
      case Some(snapshot) => _start_active(config, report, token, snapshot)
      case None => _wait_for_snapshot(config, report, token)
    }

  private def _wait_for_snapshot(
    config: CncfTextusControlCenterRegistrationConfig,
    report: CncfTextusControlCenterRegistrationReport,
    token: String
  ): CncfTextusControlCenterRegistrationSession = {
    val executor = Executors.newSingleThreadScheduledExecutor(_daemon_thread_factory)
    val closed = new AtomicBoolean(false)
    val activated = new AtomicBoolean(false)
    val active = new AtomicReference[CncfTextusControlCenterRegistrationSession](CncfTextusControlCenterRegistrationSession.noop)
    val task = new Runnable {
      def run(): Unit =
        _snapshot(config).foreach { snapshot =>
          if (!closed.get && activated.compareAndSet(false, true)) {
            val session = _start_active(config, report, token, snapshot)
            active.set(session)
            executor.shutdown()
            if (closed.get) session.close()
          }
        }
    }
    executor.scheduleWithFixedDelay(task, 0L, 50L, TimeUnit.MILLISECONDS)
    new CncfTextusControlCenterRegistrationSession {
      def close(): Unit =
        if (closed.compareAndSet(false, true)) {
          executor.shutdownNow()
          active.get.close()
        }
    }
  }

  private def _request_best_effort(
    config: CncfTextusControlCenterRegistrationConfig,
    report: CncfTextusControlCenterRegistrationReport,
    token: String,
    snapshot: RegistrationSnapshot,
    operation: String,
    state: String
  ): Boolean =
    try {
      val status = _request(config, report, token, snapshot, operation, state)
      if (status < 200 || status >= 300) {
        _warning(s"$operation request to ${_operation_endpoint(config, operation)} returned HTTP $status")
        false
      } else {
        true
      }
    } catch {
      case _: Throwable =>
        _warning(s"$operation connection to ${_operation_endpoint(config, operation)} failed")
        false
    }

  private def _request(
    config: CncfTextusControlCenterRegistrationConfig,
    report: CncfTextusControlCenterRegistrationReport,
    token: String,
    snapshot: RegistrationSnapshot,
    operation: String,
    state: String
  ): Int = {
    val endpoint = URI.create(_operation_endpoint(config, operation))
    val timeout = config.timeout.toMillis.min(Int.MaxValue.toLong).toInt
    val query = _parameters(config, report, snapshot, state).map { case (key, value) => s"${_encode(key)}=${_encode(value)}" }.mkString("?", "&", "")
    val connection = URI.create(endpoint.toString + query).toURL.openConnection().asInstanceOf[HttpURLConnection]
    try {
      connection.setRequestMethod("GET")
      connection.setConnectTimeout(timeout)
      connection.setReadTimeout(timeout)
      connection.setRequestProperty("Authorization", s"Bearer $token")
      connection.getResponseCode
    } finally {
      connection.disconnect()
    }
  }

  private def _parameters(
    config: CncfTextusControlCenterRegistrationConfig,
    report: CncfTextusControlCenterRegistrationReport,
    snapshot: RegistrationSnapshot,
    state: String
  ): Vector[(String, String)] =
    Vector(
      "protocolVersion" -> "1",
      "instanceId" -> report.instanceId,
      "launcherKind" -> "cncf",
      "target" -> report.target,
      "executionMode" -> report.executionMode,
      "runtimeVersion" -> report.runtimeVersion,
      "baseUrl" -> snapshot.baseUrl,
      "hostLabel" -> config.hostLabel,
      "startedAt" -> report.startedAt.toString,
      "launcherState" -> state
    ) ++ snapshot.applicationUrl.map("applicationUrl" -> _).toVector ++ report.artifactId.map("artifactId" -> _).toVector ++ report.developmentDirectory.map("developmentDirectory" -> _).toVector ++ report.subsystemName.map("subsystemName" -> _).toVector ++ report.subsystemVersion.map("subsystemVersion" -> _).toVector

  private def _snapshot(config: CncfTextusControlCenterRegistrationConfig): Option[RegistrationSnapshot] =
    sys.props.get(_bound_snapshot_property_key).flatMap(_parse_bound_snapshot).flatMap { bound =>
      val publicbase = Option(config.baseUrl).map(_.trim).filter(_.nonEmpty).getOrElse(bound.baseUrl)
      _valid_public_base(publicbase).map { baseurl =>
        val applicationurl = bound.applicationPath.flatMap(path => _application_url(baseurl, path))
        RegistrationSnapshot(baseurl, applicationurl)
      }
    }

  private def _parse_bound_snapshot(value: String): Option[BoundServerSnapshot] =
    value.split("\\n", -1).toVector match {
      case Vector("v1", baseurl, "") =>
        _valid_public_base(baseurl).map(value => BoundServerSnapshot(value, None))
      case Vector("v1", baseurl, path) =>
        for {
          validbase <- _valid_public_base(baseurl)
          validpath <- _valid_application_path(path)
        } yield BoundServerSnapshot(validbase, Some(validpath))
      case _ => None
    }

  private def _valid_public_base(value: String): Option[String] =
    scala.util.Try(URI.create(value)).toOption.filter { uri =>
      uri.isAbsolute &&
        Option(uri.getScheme).exists(scheme => Set("http", "https").contains(scheme.toLowerCase)) &&
        Option(uri.getHost).exists(_.nonEmpty) &&
        uri.getUserInfo == null && uri.getQuery == null && uri.getFragment == null &&
        Set("", "/").contains(Option(uri.getPath).getOrElse(""))
    }.map(_ => value.stripSuffix("/"))

  private def _application_url(baseurl: String, path: String): Option[String] =
    _valid_application_path(path).map { validpath =>
      s"${baseurl.stripSuffix("/")}$validpath"
    }

  private def _valid_application_path(path: String): Option[String] =
    Option.when(
      path == "/web" ||
        (path.startsWith("/web/") &&
          path != "/web/system" &&
          !path.startsWith("/web/system/") &&
          !path.contains("//") &&
          !path.contains("?") &&
          !path.contains("#") &&
          !path.split("/", -1).drop(1).exists(segment => segment.isEmpty || segment == "." || segment == ".."))
    ) {
      path
    }

  private def _operation_endpoint(
    config: CncfTextusControlCenterRegistrationConfig,
    operation: String
  ): String =
    config.endpoint.stripSuffix("/") + "/" + operation

  private def _encode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)

  private def _daemon_thread_factory = new java.util.concurrent.ThreadFactory {
    def newThread(runnable: Runnable): Thread = {
      val thread = new Thread(runnable, "cncf-textus-control-center-registration-heartbeat")
      thread.setDaemon(true)
      thread
    }
  }

  private def _warning(message: String): Unit =
    Console.err.println(s"warning: Textus Control Center registration $message; continuing server startup.")

  private val _bound_snapshot_property_key = "textus.server.bound-snapshot"
}

private final case class BoundServerSnapshot(
  baseUrl: String,
  applicationPath: Option[String]
)

private final case class RegistrationSnapshot(
  baseUrl: String,
  applicationUrl: Option[String]
)

private final case class ActiveCncfTextusControlCenterRegistrationSession(
  executor: ScheduledExecutorService,
  closeAction: () => Unit,
  closed: AtomicBoolean = new AtomicBoolean(false)
) extends CncfTextusControlCenterRegistrationSession {
  def close(): Unit =
    if (closed.compareAndSet(false, true)) {
      executor.shutdownNow()
      closeAction()
    }
}

object CncfTextusControlCenterRegistrationSession {
  val noop: CncfTextusControlCenterRegistrationSession = new CncfTextusControlCenterRegistrationSession {
    def close(): Unit = ()
  }
}
