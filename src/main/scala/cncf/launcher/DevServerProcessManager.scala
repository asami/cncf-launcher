package cncf.launcher

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.jdk.OptionConverters.*

/*
 * @since   May. 24, 2026
 * @version May. 25, 2026
 * @author  ASAMI, Tomoharu
 */
trait DevServerProcessManager {
  def currentPid: Long
  def processStartedAt(pid: Long): Option[Instant]
  def commandLine(pid: Long): Option[String]
  def isAlive(pid: Long): Boolean
  def stopGracefully(pid: Long): Boolean
  def stopForcibly(pid: Long): Boolean
}

object DevServerProcessManager {
  object System extends DevServerProcessManager {
    def currentPid: Long =
      ProcessHandle.current().pid()

    def processStartedAt(pid: Long): Option[Instant] =
      ProcessHandle.of(pid).toScala.flatMap(_.info().startInstant().toScala)

    def commandLine(pid: Long): Option[String] =
      ProcessHandle.of(pid).toScala.flatMap(_.info().commandLine().toScala)

    def isAlive(pid: Long): Boolean =
      ProcessHandle.of(pid).map(_.isAlive).orElse(false)

    def stopGracefully(pid: Long): Boolean =
      ProcessHandle.of(pid).map { handle =>
        if (!handle.isAlive) {
          true
        } else {
          handle.destroy()
          _wait_for_exit(handle)
        }
      }.orElse(true)

    def stopForcibly(pid: Long): Boolean =
      ProcessHandle.of(pid).map { handle =>
        if (!handle.isAlive) {
          true
        } else {
          handle.destroyForcibly()
          _wait_for_exit(handle)
        }
      }.orElse(true)

    private def _wait_for_exit(handle: ProcessHandle): Boolean =
      try {
        handle.onExit().get(5L, TimeUnit.SECONDS)
        !handle.isAlive
      } catch {
        case _: Throwable => !handle.isAlive
      }
  }
}
