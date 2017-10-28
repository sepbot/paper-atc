package qualified.software.atc.paper

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.newSingleThreadContext
import org.jetbrains.ktor.application.ApplicationCallPipeline
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.content.default
import org.jetbrains.ktor.content.files
import org.jetbrains.ktor.content.static
import org.jetbrains.ktor.host.embeddedServer
import org.jetbrains.ktor.jetty.Jetty
import org.jetbrains.ktor.response.respondText
import org.jetbrains.ktor.routing.Routing
import org.jetbrains.ktor.sessions.Sessions
import org.jetbrains.ktor.sessions.cookie
import org.jetbrains.ktor.sessions.get
import org.jetbrains.ktor.sessions.sessions
import org.jetbrains.ktor.sessions.set
import org.jetbrains.ktor.util.nextNonce
import org.jetbrains.ktor.websocket.CloseReason
import org.jetbrains.ktor.websocket.Frame
import org.jetbrains.ktor.websocket.WebSocketSession
import org.jetbrains.ktor.websocket.WebSockets
import org.jetbrains.ktor.websocket.close
import org.jetbrains.ktor.websocket.webSocket
import java.io.File
import java.time.Duration
import java.util.Date
import java.util.UUID

internal data class ServerParameters(
  val state: State,
  val webPort: Int,
  val publicDir: File
)

internal fun setupServer(params: ServerParameters): Server {
  return _Server(params).start()
}

internal interface Server {
  fun simulate(env: SimulatedEnvironment)
}

internal data class SocketSession(val id: String)

private interface MessageMetadata {
  val type: String
  val time: Long
}

private sealed class IncomingMessage: MessageMetadata {
  data class Subscribe(override val time: Long): IncomingMessage() {
    override val type = "subscribe"
  }
}

private sealed class OutgoingMessage: MessageMetadata {
  data class Instruction(override val time: Long, val instructions: List<StateInstruction>): OutgoingMessage() {
    override val type = "instruction"
  }
}

private class _Server(private val params: ServerParameters): Server {
  private val mapper = jacksonObjectMapper()
  private val subscribers = mutableMapOf<WebSocketSession, UUID>()
  private val dispatchContext = newSingleThreadContext("paper-atc-web-server-dispatch")

  private val DEFAULT_ERROR_MESSAGE = "could not process last message"

  fun start(): Server {
    launch(newSingleThreadContext("paper-atc-web-server")) {
      val server = embeddedServer(Jetty, params.webPort) {
        install(WebSockets) {
          pingPeriod = Duration.ofMinutes(1)
        }
        install(Routing) {
          install(Sessions) {
            cookie<SocketSession>("SESSION")
          }
          intercept(ApplicationCallPipeline.Infrastructure) {
            if (call.sessions.get<SocketSession>() == null) {
              call.sessions.set(SocketSession(nextNonce()))
            }
          }
          webSocket("/a") {
            val session = call.sessions.get<SocketSession>()
            if (session == null) {
              close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Need that session"))
              return@webSocket
            }
            try {
              incoming.consumeEach { frame ->
                when(frame) {
                  is Frame.Text -> handleMessage(frame, this)
                  else -> println(frame) // TODO: Do we get anything else?
                }
              }
            } catch (e: Exception) {
              send(Frame.Text(DEFAULT_ERROR_MESSAGE))
            } finally {
              val id = subscribers[this@webSocket]
              if (id != null) {
                params.state.deregister(id)
              }
              subscribers.remove(this@webSocket)
            }
          }
          static {
            files(params.publicDir)
            default(params.publicDir.resolve("index.html"))
          }
        }
      }
      server.start(wait = true)
    }
    return this
  }

  override fun simulate(env: SimulatedEnvironment) {
    params.state.update(env.tracks.values.toList())
  }

  private fun handleMessage(message: Frame.Text, session: WebSocketSession) = async(dispatchContext) {
    val parsed: IncomingMessage = try {
      val buffer = message.buffer.getArray()
      val raw = mapper.readTree(buffer)
      val type = raw["type"] ?: throw RuntimeException()
      when (type.asText()) {
        "subscribe" -> mapper.readValue(buffer, IncomingMessage.Subscribe::class.java)
        else -> throw RuntimeException()
      }
    } catch (e: Exception) {
      session.send(Frame.Text(DEFAULT_ERROR_MESSAGE))
      return@async
    }
    when (parsed) {
      is IncomingMessage.Subscribe -> {
        val id = UUID.randomUUID()
        subscribers[session] = id
        params.state.register(id) { instructions ->
          launch(dispatchContext) {
            session.send(
              Frame.Text(
                mapper.writeValueAsString(
                  OutgoingMessage.Instruction(
                    time = Date().time,
                    instructions = instructions
                  )
                )
              )
            )
          }
        }
      }
    }
  }

}
