package cncf.launcher

import java.time.Instant

/*
 * @version Jul. 22, 2026
 */
final case class LifecycleSupervisorState(
  supervisorId: String,
  requests: Map[(String, LifecycleAction, String), LifecycleSupervisorRequestRecord] = Map.empty,
  ownedInstances: Map[String, String] = Map.empty
) {
  def existing(request: LifecycleSupervisorRequest): Option[LifecycleSupervisorResult] =
    requests.get(_key(request)).map(_.result)

  def lookup(requestid: String): Option[LifecycleSupervisorResult] =
    requests.valuesIterator.map(_.result).find(_.requestId == requestid)

  def records: Vector[LifecycleSupervisorRequestRecord] =
    requests.values.toVector.sortBy(_.request.requestId)

  def reject(request: LifecycleSupervisorRequest, code: String, now: Instant): (LifecycleSupervisorState, LifecycleSupervisorResult) = {
    val key = _key(request)
    requests.get(key) match {
      case Some(record) => this -> record.result
      case None =>
        val result = LifecycleSupervisorProtocol.rejected(request, supervisorId, code, now)
        copy(requests = requests.updated(key, LifecycleSupervisorRequestRecord(request, result))) -> result
    }
  }

  def failRestart(request: LifecycleSupervisorRequest, code: String, now: Instant): (LifecycleSupervisorState, LifecycleSupervisorResult) = {
    val key = _key(request)
    requests.get(key) match {
      case Some(record) => this -> record.result
      case None =>
        val result = LifecycleSupervisorResult(request.requestId, "failed", Some(code), Some(code), supervisorId, None, Some(now), Some(now))
        copy(
          requests = requests.updated(key, LifecycleSupervisorRequestRecord(request, result)),
          ownedInstances = ownedInstances.removed(request.artifactId)
        ) -> result
    }
  }

  def submit(request: LifecycleSupervisorRequest, instanceid: Option[String], now: Instant): (LifecycleSupervisorState, LifecycleSupervisorResult) = {
    val key = _key(request)
    requests.get(key) match {
      case Some(record) => this -> record.result
      case None =>
        val result = request.action match {
          case LifecycleAction.Start if instanceid.isDefined && !ownedInstances.contains(request.artifactId) => LifecycleSupervisorResult(request.requestId, "accepted", None, None, supervisorId, instanceid, Some(now), None)
          case LifecycleAction.Stop if ownedInstances.contains(request.artifactId) => LifecycleSupervisorResult(request.requestId, "stopped", None, None, supervisorId, ownedInstances.get(request.artifactId), Some(now), Some(now))
          case LifecycleAction.Restart if ownedInstances.contains(request.artifactId) && instanceid.isDefined => LifecycleSupervisorResult(request.requestId, "accepted", None, None, supervisorId, instanceid, Some(now), None)
          case _ => LifecycleSupervisorProtocol.rejected(request, supervisorId, "supervisor-ownership-unavailable")
        }
        val nextinstances = result.state match {
          case "accepted" if request.action == LifecycleAction.Start || request.action == LifecycleAction.Restart => instanceid.fold(ownedInstances)(value => ownedInstances.updated(request.artifactId, value))
          case "stopped" => ownedInstances.removed(request.artifactId)
          case _ => ownedInstances
        }
        copy(requests = requests.updated(key, LifecycleSupervisorRequestRecord(request, result)), ownedInstances = nextinstances) -> result
    }
  }

  private def _key(request: LifecycleSupervisorRequest): (String, LifecycleAction, String) =
    (request.artifactId, request.action, request.idempotencyKey)
}
