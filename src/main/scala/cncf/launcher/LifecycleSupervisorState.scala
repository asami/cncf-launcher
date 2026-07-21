package cncf.launcher

import java.time.Instant

/*
 * @version Jul. 22, 2026
 */
final case class LifecycleSupervisorState(
  supervisorId: String,
  requests: Map[(String, LifecycleAction, String), LifecycleSupervisorResult] = Map.empty,
  ownedInstances: Map[String, String] = Map.empty
) {
  def submit(request: LifecycleSupervisorRequest, instanceid: Option[String], now: Instant): (LifecycleSupervisorState, LifecycleSupervisorResult) = {
    val key = (request.artifactId, request.action, request.idempotencyKey)
    requests.get(key) match {
      case Some(result) => this -> result
      case None =>
        val result = request.action match {
          case LifecycleAction.Start => LifecycleSupervisorResult(request.requestId, "accepted", None, None, supervisorId, instanceid, Some(now), None)
          case LifecycleAction.Stop | LifecycleAction.Restart if ownedInstances.contains(request.artifactId) => LifecycleSupervisorResult(request.requestId, "accepted", None, None, supervisorId, ownedInstances.get(request.artifactId), Some(now), None)
          case _ => LifecycleSupervisorProtocol.rejected(request, supervisorId, "supervisor-ownership-unavailable")
        }
        val nextinstances = result.state match {
          case "accepted" if request.action == LifecycleAction.Start => instanceid.fold(ownedInstances)(value => ownedInstances.updated(request.artifactId, value))
          case _ => ownedInstances
        }
        copy(requests = requests.updated(key, result), ownedInstances = nextinstances) -> result
    }
  }
}
