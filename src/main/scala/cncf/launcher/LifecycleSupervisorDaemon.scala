package cncf.launcher

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import scala.util.Try
import scala.util.control.NonFatal

/*
 * @since   Jul. 22, 2026
 * @version Jul. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final case class LifecycleSupervisorDaemonConfiguration(
  supervisorId: String,
  port: Int,
  tokenEnv: String
)

object LifecycleSupervisorDaemonConfiguration {
  val CONFIGURATION_UNAVAILABLE = "supervisor-configuration-unavailable"
  val CREDENTIAL_UNAVAILABLE = "supervisor-credential-unavailable"

  def resolve(paths: LauncherPaths): Either[String, LifecycleSupervisorDaemonConfiguration] =
    if (!Files.isRegularFile(paths.supervisorConfig))
      Left(CONFIGURATION_UNAVAILABLE)
    else
      Try {
        val values = LauncherConfigParser.parse(paths.supervisorConfig, Files.readString(paths.supervisorConfig, StandardCharsets.UTF_8))
        _parse(values)
      }.toEither.left.map(_ => CONFIGURATION_UNAVAILABLE).flatten

  private def _parse(values: Map[String, Vector[String]]): Either[String, LifecycleSupervisorDaemonConfiguration] = {
    val allowed = values.keys.forall(key => key == "schema" || LifecycleSupervisorProfileResolver.SUPERVISOR_KEYS.contains(key) || key.startsWith("profiles.development-directory."))
    if (!allowed || values.getOrElse("schema", Vector.empty) != Vector(LifecycleSupervisorProfileResolver.SCHEMA_VERSION))
      Left(CONFIGURATION_UNAVAILABLE)
    else {
      for {
        supervisorid <- _required(values, "supervisor.id").filterOrElse(_.matches("[A-Za-z0-9][A-Za-z0-9._-]*"), CONFIGURATION_UNAVAILABLE)
        porttext <- _required(values, "supervisor.port")
        port <- Try(porttext.toInt).toOption.filter(value => value >= 1 && value <= 65535).toRight(CONFIGURATION_UNAVAILABLE)
        tokenenv <- _required(values, "supervisor.token-env").filterOrElse(_.matches("[A-Za-z_][A-Za-z0-9_]*"), CONFIGURATION_UNAVAILABLE)
      } yield LifecycleSupervisorDaemonConfiguration(supervisorid, port, tokenenv)
    }
  }

  private def _required(values: Map[String, Vector[String]], key: String): Either[String, String] =
    values.get(key).collect { case Vector(value) if value.trim.nonEmpty => value.trim }.toRight(CONFIGURATION_UNAVAILABLE)
}

trait LifecycleSupervisorDaemonHost {
  def serve(configuration: LifecycleSupervisorDaemonConfiguration, token: String, paths: LauncherPaths): Int
}

trait LifecycleSupervisorShutdownHooks {
  def add(hook: Thread): Unit
  def remove(hook: Thread): Boolean
}

object LifecycleSupervisorShutdownHooks {
  object System extends LifecycleSupervisorShutdownHooks {
    def add(hook: Thread): Unit = Runtime.getRuntime.addShutdownHook(hook)
    def remove(hook: Thread): Boolean = Runtime.getRuntime.removeShutdownHook(hook)
  }
}

class LifecycleSupervisorForegroundDaemonHost(
  shutdownhooks: LifecycleSupervisorShutdownHooks = LifecycleSupervisorShutdownHooks.System
) extends LifecycleSupervisorDaemonHost {
  def serve(configuration: LifecycleSupervisorDaemonConfiguration, token: String, paths: LauncherPaths): Int = {
    val server = try {
      new LifecycleSupervisorHttpServer(
        configuration.supervisorId,
        token,
        LifecycleSupervisorProfileResolver(paths),
        Some(LifecycleSupervisorStateStore(paths, configuration.supervisorId))
      ).start(configuration.port)
    } catch {
      case NonFatal(_) => throw CncfException("supervisor-loopback-bind-unavailable")
    }
    val stopped = new CountDownLatch(1)
    val shutdown = new Thread(() => {
      server.stop(0)
      stopped.countDown()
    }, "cncf-lifecycle-supervisor-shutdown")
    try shutdownhooks.add(shutdown)
    catch {
      case NonFatal(error) =>
        server.stop(0)
        throw error
    }
    println(s"supervisor listening on http://127.0.0.1:${server.getAddress.getPort}")
    try {
      stopped.await()
      0
    } catch {
      case _: InterruptedException =>
        Thread.currentThread.interrupt()
        0
    } finally {
      scala.util.Try(shutdownhooks.remove(shutdown))
      server.stop(0)
    }
  }
}

object LifecycleSupervisorDaemonHost {
  object System extends LifecycleSupervisorForegroundDaemonHost
}
