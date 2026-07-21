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
  profiles: LifecycleSupervisorProfileResolver = LifecycleSupervisorProfileResolver(LauncherPaths())
) {
  private val _state = new AtomicReference(LifecycleSupervisorState(supervisorid))

  def start(port: Int): HttpServer = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0)
    server.createContext("/v1/lifecycle-requests", _handler)
    server.start()
    server
  }

  private val _handler = new HttpHandler {
    def handle(exchange: HttpExchange): Unit = {
      val response =
        if (exchange.getRequestMethod != "POST") None
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
    val code = profiles.resolve(request.artifactId).fold(identity, _ => "supervisor-execution-unavailable")
    _reject(request, code)
  }

  private def _reject(request: LifecycleSupervisorRequest, code: String): LifecycleSupervisorResult = {
    var next: LifecycleSupervisorResult = LifecycleSupervisorProtocol.rejected(request, supervisorid, code)
    _state.updateAndGet { state =>
      val (updated, result) = state.reject(request, code, Instant.now())
      next = result
      updated
    }
    next
  }

  private def _write(exchange: HttpExchange, status: Int, body: String): Unit = {
    val bytes = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.length)
    val output = exchange.getResponseBody
    try output.write(bytes) finally output.close()
  }
}
