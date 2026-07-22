package cncf.launcher

import java.net.{HttpURLConnection, URI}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.util.Try

/*
 * The authority process is deliberately Launcher-internal.  Operators use
 * cncf server and Control Center invokes the bounded lifecycle CLI; neither
 * caller needs a foreground supervisor command, endpoint, or credential.
 *
 * @version Jul. 22, 2026
 */
trait LifecycleSupervisorAuthority {
  def ensure(configuration: LifecycleSupervisorDaemonConfiguration, token: String, paths: LauncherPaths): Either[String, Unit]
}

trait LifecycleSupervisorAuthorityProbe {
  def available(configuration: LifecycleSupervisorDaemonConfiguration, token: String): Boolean
}

object LifecycleSupervisorAuthorityProbe {
  object System extends LifecycleSupervisorAuthorityProbe {
    def available(configuration: LifecycleSupervisorDaemonConfiguration, token: String): Boolean =
      LifecycleSupervisorHttpClient.available(configuration, token)
  }
}

trait LifecycleSupervisorAuthorityProcess {
  def start(configuration: LifecycleSupervisorDaemonConfiguration, token: String, paths: LauncherPaths): Either[String, Unit]
}

object LifecycleSupervisorAuthorityProcess {
  object System extends LifecycleSupervisorAuthorityProcess {
    def start(configuration: LifecycleSupervisorDaemonConfiguration, token: String, paths: LauncherPaths): Either[String, Unit] =
      Try {
        Files.createDirectories(paths.supervisorLog.getParent)
        val process = new ProcessBuilder("cncf", "launcher", "supervisor", "serve")
          .redirectOutput(ProcessBuilder.Redirect.appendTo(paths.supervisorLog.toFile))
          .redirectError(ProcessBuilder.Redirect.appendTo(paths.supervisorLog.toFile))
        process.environment.put(configuration.tokenEnv, token)
        process.start()
        ()
      }.toEither.left.map(_ => LifecycleSupervisorAuthority.AUTHORITY_UNAVAILABLE)
  }
}

class LocalLifecycleSupervisorAuthority(
  probe: LifecycleSupervisorAuthorityProbe = LifecycleSupervisorAuthorityProbe.System,
  process: LifecycleSupervisorAuthorityProcess = LifecycleSupervisorAuthorityProcess.System,
  attempts: Int = LocalLifecycleSupervisorAuthority.DEFAULT_ATTEMPTS,
  retrydelay: Long = LocalLifecycleSupervisorAuthority.RETRY_DELAY_MILLIS
) extends LifecycleSupervisorAuthority {
  import LifecycleSupervisorAuthority.*

  def ensure(configuration: LifecycleSupervisorDaemonConfiguration, token: String, paths: LauncherPaths): Either[String, Unit] =
    if (probe.available(configuration, token)) Right(())
    else process.start(configuration, token, paths).flatMap(_ => _await_available(configuration, token))

  private def _await_available(configuration: LifecycleSupervisorDaemonConfiguration, token: String): Either[String, Unit] = {
    var attempt = 0
    var ready = false
    while (!ready && attempt < attempts) {
      Thread.sleep(retrydelay)
      ready = probe.available(configuration, token)
      attempt += 1
    }
    Either.cond(ready, (), AUTHORITY_UNAVAILABLE)
  }
}

object LocalLifecycleSupervisorAuthority {
  val DEFAULT_ATTEMPTS = 100
  val RETRY_DELAY_MILLIS = 100L
}

object LifecycleSupervisorAuthority {
  val AUTHORITY_UNAVAILABLE = "supervisor-authority-unavailable"

  object System extends LocalLifecycleSupervisorAuthority
}

object LifecycleSupervisorHttpClient {
  private val _health_path = "/v1/lifecycle-health"
  private val _request_path = "/v1/lifecycle-requests"

  def available(configuration: LifecycleSupervisorDaemonConfiguration, token: String): Boolean =
    _connection(configuration, _health_path, token, "GET").exists { connection =>
      try connection.getResponseCode == 200
      catch { case _: Throwable => false }
      finally connection.disconnect()
    }

  def submit(configuration: LifecycleSupervisorDaemonConfiguration, token: String, request: LifecycleSupervisorRequest): Either[String, LifecycleSupervisorResult] =
    _connection(configuration, _request_path, token, "POST").toRight(LifecycleSupervisorAuthority.AUTHORITY_UNAVAILABLE).flatMap { connection =>
      try {
        import LifecycleSupervisorProtocol.given
        import io.circe.syntax.*
        connection.setDoOutput(true)
        connection.setRequestProperty("Content-Type", "application/json")
        val output = connection.getOutputStream
        try output.write(request.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)) finally output.close()
        if (connection.getResponseCode != 200) Left("supervisor-request-rejected")
        else _result(connection.getInputStream, request.requestId)
      } catch {
        case _: Throwable => Left(LifecycleSupervisorAuthority.AUTHORITY_UNAVAILABLE)
      } finally connection.disconnect()
    }

  def lookup(configuration: LifecycleSupervisorDaemonConfiguration, token: String, requestid: String): Option[LifecycleSupervisorResult] =
    _connection(configuration, s"$_request_path/$requestid", token, "GET").flatMap { connection =>
      try {
        if (connection.getResponseCode == 200) _result(connection.getInputStream, requestid).toOption else None
      } catch {
        case _: Throwable => None
      } finally connection.disconnect()
    }

  private def _connection(configuration: LifecycleSupervisorDaemonConfiguration, path: String, token: String, method: String): Option[HttpURLConnection] =
    Try {
      val connection = URI.create(s"http://127.0.0.1:${configuration.port}$path").toURL.openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod(method)
      connection.setConnectTimeout(1000)
      connection.setReadTimeout(3000)
      connection.setRequestProperty("Authorization", s"Bearer $token")
      connection
    }.toOption

  private def _result(stream: java.io.InputStream, requestid: String): Either[String, LifecycleSupervisorResult] =
    try {
      import LifecycleSupervisorProtocol.given
      io.circe.parser.decode[LifecycleSupervisorResult](new String(stream.readAllBytes(), StandardCharsets.UTF_8))
        .left.map(_ => "supervisor-response-invalid")
        .filterOrElse(_.requestId == requestid, "supervisor-response-request-mismatch")
    } finally stream.close()
}
