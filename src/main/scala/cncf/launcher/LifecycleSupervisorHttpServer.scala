package cncf.launcher

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

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
  children: LifecycleSupervisorChildFactory = LifecycleSupervisorChildFactory.Unavailable
) {
  private val _store = store.getOrElse(LifecycleSupervisorStateStore(LauncherPaths(), supervisorid))
  private val _loaded = _store.load()
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
    if (_loaded.isLeft)
      LifecycleSupervisorProtocol.rejected(request, supervisorid, LifecycleSupervisorStateStore.STATE_UNAVAILABLE)
    else if (request.deadlineAt.isBefore(Instant.now()))
      _reject(request, "supervisor-request-timed-out")
    else {
      profiles.resolve(request.artifactId).fold(code => _reject(request, code), _ => _execute(request))
    }
  }

  private def _execute(request: LifecycleSupervisorRequest): LifecycleSupervisorResult =
    _lock.synchronized {
      _state.get.existing(request).getOrElse {
        request.action match {
          case LifecycleAction.Start if _state.get.ownedInstances.contains(request.artifactId) || _children.contains(request.artifactId) =>
            _reject(request, "supervisor-ownership-unavailable")
          case LifecycleAction.Start => children.start(request.artifactId).fold(code => _reject(request, code), child => _accept_start(request, child))
          case LifecycleAction.Stop => _children.get(request.artifactId).filter(_.isAlive).fold(_reject(request, "supervisor-ownership-unavailable"))(child => _stop(request, child))
          case LifecycleAction.Restart => _children.get(request.artifactId).filter(_.isAlive).fold(_reject(request, "supervisor-ownership-unavailable"))(child => _restart(request, child))
        }
      }
    }

  private def _accept_start(request: LifecycleSupervisorRequest, child: LifecycleSupervisorChild): LifecycleSupervisorResult = {
    if (!child.isAlive) {
      child.stop()
      _reject(request, "supervisor-execution-unavailable")
    } else {
      val (updated, result) = _state.get.submit(request, Some(child.instanceId), Instant.now())
      if (result.state != "accepted") {
        child.stop()
        _reject(request, result.diagnosticCode.getOrElse("supervisor-ownership-unavailable"))
      } else {
        _store.save(updated).fold(error => {
          child.stop()
          LifecycleSupervisorProtocol.rejected(request, supervisorid, error)
        }, _ => {
          _state.set(updated)
          _children = _children.updated(request.artifactId, child)
          result
        })
      }
    }
  }

  private def _stop(request: LifecycleSupervisorRequest, child: LifecycleSupervisorChild): LifecycleSupervisorResult =
    if (child.stop()) {
      val (updated, result) = _state.get.submit(request, None, Instant.now())
      _store.save(updated).fold(error => LifecycleSupervisorProtocol.rejected(request, supervisorid, error), _ => {
        _state.set(updated)
        _children = _children.removed(request.artifactId)
        result
      })
    } else _reject(request, "supervisor-execution-unavailable")

  private def _restart(request: LifecycleSupervisorRequest, child: LifecycleSupervisorChild): LifecycleSupervisorResult =
    if (!child.stop()) _reject(request, "supervisor-execution-unavailable")
    else {
      _children = _children.removed(request.artifactId)
      children.start(request.artifactId).fold(code => _reject(request, code), replacement =>
        if (replacement.isAlive) _accept_start(request, replacement)
        else _reject(request, "supervisor-execution-unavailable")
      )
    }

  private def _reject(request: LifecycleSupervisorRequest, code: String): LifecycleSupervisorResult = {
    _lock.synchronized {
      _state.get.existing(request).getOrElse {
        val (updated, result) = _state.get.reject(request, code, Instant.now())
        _store.save(updated) match {
          case Right(_) =>
            _state.set(updated)
            result
          case Left(error) => LifecycleSupervisorProtocol.rejected(request, supervisorid, error)
        }
      }
    }
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
