package cncf.launcher

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import io.circe.parser.decode
import io.circe.syntax.*
import LifecycleSupervisorProtocol.given

/*
 * @version Jul. 22, 2026
 */
final class LifecycleSupervisorHttpServer(
  supervisorid: String,
  token: String,
  profiles: LifecycleSupervisorProfileResolver = LifecycleSupervisorProfileResolver(LauncherPaths()),
  store: Option[LifecycleSupervisorStateStore] = None,
  children: LifecycleSupervisorChildFactory = new LifecycleSupervisorDevelopmentDirectoryChildFactory
) {
  private val _store = store.getOrElse(LifecycleSupervisorStateStore(LauncherPaths(), supervisorid))
  private val _loaded = _store.load()
  private val _state_available = new AtomicBoolean(_loaded.isRight)
  private val _state = new AtomicReference(_loaded.getOrElse(LifecycleSupervisorState(supervisorid)))
  private val _lock = new Object
  private var _children: Map[String, LifecycleSupervisorChild] = Map.empty

  def start(port: Int): HttpServer = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0)
    server.createContext("/v1/lifecycle-requests", _handler)
    server.start()
    server
  }

  private val _handler = new HttpHandler {
    def handle(exchange: HttpExchange): Unit = {
      val response =
        if (exchange.getRequestMethod == "GET") _lookup(exchange)
        else if (exchange.getRequestMethod != "POST") None
        else if (!Option(exchange.getRequestHeaders.getFirst("Authorization")).contains(s"Bearer $token")) None
        else decode[LifecycleSupervisorRequest](new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)).toOption.map { request =>
          val result = _submit(request)
          result.asJson.noSpaces
        }
      response match {
        case Some(body) => _write(exchange, 200, body)
        case None => _write(exchange, 400, "{\"state\":\"rejected\",\"diagnosticCode\":\"supervisor-request-invalid\"}")
      }
    }
  }

  private def _submit(request: LifecycleSupervisorRequest): LifecycleSupervisorResult = {
    if (!_state_available.get)
      LifecycleSupervisorProtocol.rejected(request, supervisorid, LifecycleSupervisorStateStore.STATE_UNAVAILABLE)
    else if (request.deadlineAt.isBefore(Instant.now()))
      _reject(request, "supervisor-request-timed-out")
    else {
      profiles.resolve(request.artifactId).fold(code => _reject(request, code), profile => _execute(request, profile))
    }
  }

  private def _execute(request: LifecycleSupervisorRequest, profile: LifecycleSupervisorLaunchProfile): LifecycleSupervisorResult =
    _lock.synchronized {
      _state.get.existing(request).getOrElse {
        request.action match {
          case LifecycleAction.Start if _state.get.ownedInstances.contains(request.artifactId) || _children.contains(request.artifactId) =>
            _reject(request, "supervisor-ownership-unavailable")
          case LifecycleAction.Start => _start(request, profile, None)
          case LifecycleAction.Stop => _children.get(request.artifactId).filter(_.isAlive).fold(_reject(request, "supervisor-ownership-unavailable"))(child => _stop(request, child))
          case LifecycleAction.Restart => _children.get(request.artifactId).filter(_.isAlive).fold(_reject(request, "supervisor-ownership-unavailable"))(child => _restart(request, profile, child))
        }
      }
    }

  private def _start(
    request: LifecycleSupervisorRequest,
    profile: LifecycleSupervisorLaunchProfile,
    owned: Option[LifecycleSupervisorChild]
  ): LifecycleSupervisorResult =
    children.preflight(profile, owned).fold(code => _reject(request, code), _ =>
      children.start(profile, LifecycleSupervisorChildCorrelation(UUID.randomUUID().toString)).fold(code => _reject(request, code), child => _accept_start(request, child))
    )

  private def _accept_start(request: LifecycleSupervisorRequest, child: LifecycleSupervisorChild, releaseownership: Boolean = false): LifecycleSupervisorResult = {
    if (!child.isAlive) {
      child.stop()
      _fail_start(request, "supervisor-execution-unavailable", releaseownership)
    } else {
      val (updated, result) = _state.get.submit(request, Some(child.instanceId), Instant.now())
      if (result.state != "accepted") {
        child.stop()
        _fail_start(request, result.diagnosticCode.getOrElse("supervisor-ownership-unavailable"), releaseownership)
      } else {
        _save(updated).fold(error => {
          child.stop()
          if (releaseownership) _fail_restart(request, error)
          else LifecycleSupervisorProtocol.rejected(request, supervisorid, error)
        }, _ => {
          _state.set(updated)
          _children = _children.updated(request.artifactId, child)
          result
        })
      }
    }
  }

  private def _fail_start(request: LifecycleSupervisorRequest, code: String, releaseownership: Boolean): LifecycleSupervisorResult =
    if (releaseownership) _fail_restart(request, code) else _reject(request, code)

  private def _stop(request: LifecycleSupervisorRequest, child: LifecycleSupervisorChild): LifecycleSupervisorResult =
    if (child.stop()) {
      val (updated, result) = _state.get.submit(request, None, Instant.now())
      _save(updated).fold(error => LifecycleSupervisorProtocol.rejected(request, supervisorid, error), _ => {
        _state.set(updated)
        _children = _children.removed(request.artifactId)
        result
      })
    } else _reject(request, "supervisor-execution-unavailable")

  private def _restart(request: LifecycleSupervisorRequest, profile: LifecycleSupervisorLaunchProfile, child: LifecycleSupervisorChild): LifecycleSupervisorResult =
    children.preflight(profile, Some(child)).fold(code => _reject(request, code), _ =>
      if (!child.stop()) _reject(request, "supervisor-execution-unavailable")
      else {
        _children = _children.removed(request.artifactId)
        children.start(profile).fold(code => _fail_restart(request, code), replacement => _accept_start(request, replacement, releaseownership = true))
      }
    )

  private def _fail_restart(request: LifecycleSupervisorRequest, code: String): LifecycleSupervisorResult = {
    val (updated, result) = _state.get.failRestart(request, code, Instant.now())
    _save(updated).fold(error => LifecycleSupervisorProtocol.rejected(request, supervisorid, error), _ => {
      _state.set(updated)
      _children = _children.removed(request.artifactId)
      result
    })
  }

  private def _reject(request: LifecycleSupervisorRequest, code: String): LifecycleSupervisorResult = {
    _lock.synchronized {
      _state.get.existing(request).getOrElse {
        val (updated, result) = _state.get.reject(request, code, Instant.now())
        _save(updated) match {
          case Right(_) =>
            _state.set(updated)
            result
          case Left(error) => LifecycleSupervisorProtocol.rejected(request, supervisorid, error)
        }
      }
    }
  }

  private def _save(state: LifecycleSupervisorState): Either[String, Unit] =
    _store.save(state).left.map { error =>
      _state_available.set(false)
      error
    }

  private def _lookup(exchange: HttpExchange): Option[String] =
    if (!Option(exchange.getRequestHeaders.getFirst("Authorization")).contains(s"Bearer $token"))
      None
    else {
      val prefix = "/v1/lifecycle-requests/"
      val path = exchange.getRequestURI.getPath
      Option.when(path.startsWith(prefix) && path.drop(prefix.length).nonEmpty)(path.drop(prefix.length)).flatMap { requestid =>
        _state.get.lookup(requestid).map(_.asJson.noSpaces)
      }
    }

  private def _write(exchange: HttpExchange, status: Int, body: String): Unit = {
    val bytes = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.length)
    val output = exchange.getResponseBody
    try output.write(bytes) finally output.close()
  }
}
