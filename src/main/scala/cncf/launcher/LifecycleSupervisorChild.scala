package cncf.launcher

import java.net.{InetAddress, InetSocketAddress, ServerSocket}
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.util.Try

/*
 * @since   Jul. 22, 2026
 * @version Jul. 22, 2026
 * @author  ASAMI, Tomoharu
 */
trait LifecycleSupervisorChild {
  def instanceId: String
  def port: Int = -1
  def isAlive: Boolean
  def stop(): Boolean
}

trait LifecycleSupervisorChildFactory {
  def preflight(profile: LifecycleSupervisorLaunchProfile, owned: Option[LifecycleSupervisorChild]): Either[String, Unit] = Right(())
  def start(profile: LifecycleSupervisorLaunchProfile): Either[String, LifecycleSupervisorChild]
  def start(profile: LifecycleSupervisorLaunchProfile, correlation: LifecycleSupervisorChildCorrelation): Either[String, LifecycleSupervisorChild] = start(profile)
}

final case class LifecycleSupervisorChildCorrelation(
  instanceId: String
)

object LifecycleSupervisorChildFactory {
  object Unavailable extends LifecycleSupervisorChildFactory {
    override def preflight(profile: LifecycleSupervisorLaunchProfile, owned: Option[LifecycleSupervisorChild]): Either[String, Unit] =
      Left("supervisor-execution-unavailable")
    def start(profile: LifecycleSupervisorLaunchProfile): Either[String, LifecycleSupervisorChild] =
      Left("supervisor-execution-unavailable")
  }
}

trait LifecycleSupervisorPortProbe {
  def isAvailable(port: Int): Boolean
}

object LifecycleSupervisorPortProbe {
  object System extends LifecycleSupervisorPortProbe {
    def isAvailable(port: Int): Boolean =
      Try {
        val socket = new ServerSocket()
        try {
          socket.setReuseAddress(false)
          socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress, port))
          true
        } finally socket.close()
      }.getOrElse(false)
  }
}

trait LifecycleSupervisorCommandRunner {
  def start(command: Vector[String], directory: Path, port: Int): Either[String, LifecycleSupervisorChild]
}

object LifecycleSupervisorCommandRunner {
  object System extends LifecycleSupervisorCommandRunner {
    def start(command: Vector[String], directory: Path, port: Int): Either[String, LifecycleSupervisorChild] =
      Try {
        val process = new ProcessBuilder(command*)
          .directory(directory.toFile)
          .redirectOutput(ProcessBuilder.Redirect.INHERIT)
          .redirectError(ProcessBuilder.Redirect.INHERIT)
          .start()
        new SystemLifecycleSupervisorChild(process, port)
      }.toEither.left.map(_ => "supervisor-execution-unavailable")
  }

  private final class SystemLifecycleSupervisorChild(process: Process, override val port: Int) extends LifecycleSupervisorChild {
    val instanceId: String = UUID.randomUUID().toString

    def isAlive: Boolean = process.isAlive

    def stop(): Boolean = {
      if (process.isAlive) {
        process.destroy()
        try process.waitFor(5, TimeUnit.SECONDS)
        catch {
          case _: InterruptedException => Thread.currentThread.interrupt()
        }
        if (process.isAlive) {
          process.destroyForcibly()
          try process.waitFor(5, TimeUnit.SECONDS)
          catch {
            case _: InterruptedException => Thread.currentThread.interrupt()
          }
        }
      }
      !process.isAlive
    }
  }
}

final class LifecycleSupervisorDevelopmentDirectoryChildFactory(
  probe: LifecycleSupervisorPortProbe = LifecycleSupervisorPortProbe.System,
  runner: LifecycleSupervisorCommandRunner = LifecycleSupervisorCommandRunner.System
) extends LifecycleSupervisorChildFactory {
  import LifecycleSupervisorDevelopmentDirectoryChildFactory.*

  override def preflight(profile: LifecycleSupervisorLaunchProfile, owned: Option[LifecycleSupervisorChild]): Either[String, Unit] =
    if (owned.exists(child => child.isAlive && child.port == profile.defaultPort) || probe.isAvailable(profile.defaultPort))
      Right(())
    else
      Left(PORT_UNAVAILABLE)

  def start(profile: LifecycleSupervisorLaunchProfile): Either[String, LifecycleSupervisorChild] =
    runner.start(_command(profile), profile.developmentDirectory, profile.defaultPort)

  override def start(profile: LifecycleSupervisorLaunchProfile, correlation: LifecycleSupervisorChildCorrelation): Either[String, LifecycleSupervisorChild] =
    runner.start(_command(profile, Some(correlation)), profile.developmentDirectory, profile.defaultPort).map { child =>
      LifecycleSupervisorCorrelatedChild(correlation.instanceId, child)
    }

  private def _command(profile: LifecycleSupervisorLaunchProfile, correlation: Option[LifecycleSupervisorChildCorrelation] = None): Vector[String] =
    Vector("cncf", profile.developmentDirectory.toString, "server", s"--textus.server.port=${profile.defaultPort}") ++
      correlation.map(value => s"--textus.control-center.registration-instance-id=${value.instanceId}").toVector
}

private final case class LifecycleSupervisorCorrelatedChild(
  instanceId: String,
  child: LifecycleSupervisorChild
) extends LifecycleSupervisorChild {
  override val port: Int = child.port
  def isAlive: Boolean = child.isAlive
  def stop(): Boolean = child.stop()
}

object LifecycleSupervisorDevelopmentDirectoryChildFactory {
  val PORT_UNAVAILABLE = "supervisor-port-unavailable"
}
