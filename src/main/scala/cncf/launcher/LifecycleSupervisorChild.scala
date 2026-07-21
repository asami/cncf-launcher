package cncf.launcher

/*
 * @since   Jul. 22, 2026
 * @version Jul. 22, 2026
 * @author  ASAMI, Tomoharu
 */
trait LifecycleSupervisorChild {
  def instanceId: String
  def isAlive: Boolean
  def stop(): Boolean
}

trait LifecycleSupervisorChildFactory {
  def start(artifactId: String): Either[String, LifecycleSupervisorChild]
}

object LifecycleSupervisorChildFactory {
  object Unavailable extends LifecycleSupervisorChildFactory {
    def start(artifactId: String): Either[String, LifecycleSupervisorChild] =
      Left("supervisor-execution-unavailable")
  }
}
