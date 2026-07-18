package cncf.launcher

import java.net.{HttpURLConnection, URI, URLEncoder}
import java.nio.charset.StandardCharsets
import java.time.{Duration, Instant}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}

/*
 * @since   Jul. 18, 2026
 * @version Jul. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class CncfTextusAdminRegistrationConfig(
  endpoint: String,
  tokenEnv: String,
  timeout: Duration,
  heartbeatInterval: Duration,
  hostLabel: String,
  baseUrl: String
)

object CncfTextusAdminRegistrationConfig {
  def enabled(values: Map[String, Vector[String]]): Option[Boolean] =
    _first(values, "textus-admin.registration.enabled", "textus.admin.registration.enabled").map(_boolean)

  def fromParsed(values: Map[String, Vector[String]]): Option[CncfTextusAdminRegistrationConfig] = {
    def _first_(keys: String*): Option[String] =
      _first(values, keys*)

    if (!enabled(values).contains(true)) {
      None
    } else {
      Some(CncfTextusAdminRegistrationConfig(
        _first_("textus-admin.registration.endpoint", "textus.admin.registration.endpoint").getOrElse(throw CncfException("textus-admin.registration.endpoint is required when registration is enabled")),
        _first_("textus-admin.registration.token-env", "textus-admin.registration.tokenEnv", "textus.admin.registration.token-env", "textus.admin.registration.tokenEnv").getOrElse(throw CncfException("textus-admin.registration.token-env is required when registration is enabled")),
        _duration(_first_("textus-admin.registration.timeout", "textus.admin.registration.timeout").getOrElse("2s"), "textus-admin.registration.timeout"),
        _duration(_first_("textus-admin.registration.heartbeat-interval", "textus-admin.registration.heartbeatInterval", "textus.admin.registration.heartbeat-interval", "textus.admin.registration.heartbeatInterval").getOrElse("30s"), "textus-admin.registration.heartbeat-interval"),
        _first_("textus-admin.registration.host-label", "textus-admin.registration.hostLabel", "textus.admin.registration.host-label", "textus.admin.registration.hostLabel").getOrElse(throw CncfException("textus-admin.registration.host-label is required when registration is enabled")),
        _first_("textus-admin.registration.base-url", "textus-admin.registration.baseUrl", "textus.admin.registration.base-url", "textus.admin.registration.baseUrl").getOrElse(throw CncfException("textus-admin.registration.base-url is required when registration is enabled"))
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

final case class CncfTextusAdminRegistrationReport(
  instanceId: String,
  target: String,
  subsystemName: Option[String],
  subsystemVersion: Option[String],
  runtimeVersion: String,
  startedAt: Instant
)

trait CncfTextusAdminRegistrationSession {
  def close(): Unit
}

trait CncfTextusAdminRegistrationReporter {
  def start(
    config: CncfTextusAdminRegistrationConfig,
    report: CncfTextusAdminRegistrationReport,
    token: Option[String]
  ): CncfTextusAdminRegistrationSession
}

object CncfTextusAdminRegistrationReporter {
  val System: CncfTextusAdminRegistrationReporter = new SystemCncfTextusAdminRegistrationReporter
}

private final class SystemCncfTextusAdminRegistrationReporter extends CncfTextusAdminRegistrationReporter {
  def start(
    config: CncfTextusAdminRegistrationConfig,
    report: CncfTextusAdminRegistrationReport,
    token: Option[String]
  ): CncfTextusAdminRegistrationSession = {
    token match {
      case None =>
        _warning("credential is unavailable")
        CncfTextusAdminRegistrationSession.noop
      case Some(value) if value.trim.nonEmpty =>
        _request_best_effort(config, report, value, "register-subsystem", "starting")
        val executor = Executors.newSingleThreadScheduledExecutor(_daemon_thread_factory)
        val task = new Runnable {
          def run(): Unit =
            _request_best_effort(config, report, value, "heartbeat-subsystem", "running")
        }
        executor.scheduleAtFixedRate(task, config.heartbeatInterval.toMillis, config.heartbeatInterval.toMillis, TimeUnit.MILLISECONDS)
        ActiveCncfTextusAdminRegistrationSession(
          executor,
          () => _request_best_effort(config, report, value, "deregister-subsystem", "stopped")
        )
      case Some(_) =>
        _warning("credential is unavailable")
        CncfTextusAdminRegistrationSession.noop
    }
  }

  private def _request_best_effort(
    config: CncfTextusAdminRegistrationConfig,
    report: CncfTextusAdminRegistrationReport,
    token: String,
    operation: String,
    state: String
  ): Unit =
    try {
      val status = _request(config, report, token, operation, state)
      if (status < 200 || status >= 300)
        _warning(s"$operation request to ${_operation_endpoint(config, operation)} returned HTTP $status")
    } catch {
      case _: Throwable => _warning(s"$operation connection to ${_operation_endpoint(config, operation)} failed")
    }

  private def _request(
    config: CncfTextusAdminRegistrationConfig,
    report: CncfTextusAdminRegistrationReport,
    token: String,
    operation: String,
    state: String
  ): Int = {
    val endpoint = URI.create(_operation_endpoint(config, operation))
    val timeout = config.timeout.toMillis.min(Int.MaxValue.toLong).toInt
    val query = _parameters(config, report, state).map { case (key, value) => s"${_encode(key)}=${_encode(value)}" }.mkString("?", "&", "")
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
    config: CncfTextusAdminRegistrationConfig,
    report: CncfTextusAdminRegistrationReport,
    state: String
  ): Vector[(String, String)] =
    Vector(
      "protocolVersion" -> "1",
      "instanceId" -> report.instanceId,
      "launcherKind" -> "cncf",
      "target" -> report.target,
      "runtimeVersion" -> report.runtimeVersion,
      "baseUrl" -> config.baseUrl,
      "hostLabel" -> config.hostLabel,
      "startedAt" -> report.startedAt.toString,
      "launcherState" -> state
    ) ++ report.subsystemName.map("subsystemName" -> _).toVector ++ report.subsystemVersion.map("subsystemVersion" -> _).toVector

  private def _operation_endpoint(
    config: CncfTextusAdminRegistrationConfig,
    operation: String
  ): String =
    config.endpoint.stripSuffix("/") + "/" + operation

  private def _encode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)

  private def _daemon_thread_factory = new java.util.concurrent.ThreadFactory {
    def newThread(runnable: Runnable): Thread = {
      val thread = new Thread(runnable, "cncf-textus-admin-registration-heartbeat")
      thread.setDaemon(true)
      thread
    }
  }

  private def _warning(message: String): Unit =
    Console.err.println(s"warning: Textus Admin registration $message; continuing server startup.")
}

private final case class ActiveCncfTextusAdminRegistrationSession(
  executor: ScheduledExecutorService,
  closeAction: () => Unit,
  closed: AtomicBoolean = new AtomicBoolean(false)
) extends CncfTextusAdminRegistrationSession {
  def close(): Unit =
    if (closed.compareAndSet(false, true)) {
      executor.shutdownNow()
      closeAction()
    }
}

object CncfTextusAdminRegistrationSession {
  val noop: CncfTextusAdminRegistrationSession = new CncfTextusAdminRegistrationSession {
    def close(): Unit = ()
  }
}
