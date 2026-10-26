package app.scouter.network

import android.content.Context
import android.util.Log
import app.scouter.ScouterNetworkConfig
import app.scouter.mcp.ScouterMcpServer
import app.scouter.model.ScouterParser
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.authorization
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.server.mcpStatelessStreamableHttp
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class ScouterNetworkServer(context: Context) {
    private val applicationContext = context.applicationContext
    private val dispatcher = ScouterCommandDispatcher(applicationContext)
    private var stopServer: (() -> Unit)? = null

    @Synchronized
    fun start() {
        if (stopServer != null) return
        val settings = ScouterNetworkConfig.settings(applicationContext)
        if (!settings.enabled) return

        val schema = applicationContext.assets.open(SCHEMA_ASSET).bufferedReader().use { it.readText() }
        val mcp = ScouterMcpServer(dispatcher, schema) {
            "${ScouterNetworkConfig.localAddress(applicationContext)}/mcp"
        }
        val server = embeddedServer(CIO, host = settings.bindHost, port = settings.port) {
            install(RequestBodyLimit) {
                bodyLimit { ScouterParser.MAX_PAYLOAD_BYTES.toLong() }
            }
            install(StatusPages) {
                exception<PayloadTooLargeException> { call, _ ->
                    call.respondJson(HttpStatusCode.PayloadTooLarge, """{"error":"payload_too_large"}""")
                }
            }
            intercept(ApplicationCallPipeline.Plugins) {
                if (!isAuthorized(context.request.authorization())) {
                    context.respondJson(HttpStatusCode.Unauthorized, """{"error":"unauthorized"}""")
                    finish()
                } else {
                    proceed()
                }
            }
            routing {
                get("/health") {
                    call.respondJson(HttpStatusCode.OK, """{"ready":true,"mcp":"/mcp"}""")
                }
                post("/show") {
                    handleRest {
                        val body = call.receiveText()
                        dispatcher.show(body, call.request.headers["X-Scouter-Id"])
                        call.respondJson(HttpStatusCode.Accepted, """{"accepted":true}""")
                    }
                }
                post("/close/{id}") {
                    handleRest {
                        val encodedId = requireNotNull(call.parameters["id"]) { "Missing id" }
                        dispatcher.close(URLDecoder.decode(encodedId, StandardCharsets.UTF_8.name()))
                        call.respondJson(HttpStatusCode.Accepted, """{"accepted":true}""")
                    }
                }
                post("/close-all") {
                    dispatcher.closeAll()
                    call.respondJson(HttpStatusCode.Accepted, """{"accepted":true}""")
                }
            }
            mcpStatelessStreamableHttp(
                path = "/mcp",
                enableDnsRebindingProtection = false,
            ) {
                mcp.create()
            }
        }

        runCatching {
            server.start(wait = false)
            stopServer = {
                server.stop(gracePeriodMillis = 300, timeoutMillis = 1_500)
            }
            Log.i(TAG, "Network server listening on ${settings.bindHost}:${settings.port}")
        }.onFailure {
            Log.e(TAG, "Unable to start network server", it)
            runCatching { server.stop(0, 0) }
        }
    }

    @Synchronized
    fun restart() {
        stop()
        start()
    }

    @Synchronized
    fun stop() {
        val stop = stopServer ?: return
        stopServer = null
        runCatching(stop).onFailure { Log.w(TAG, "Unable to stop network server cleanly", it) }
    }

    private fun isAuthorized(header: String?): Boolean {
        val supplied = header
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?.toByteArray(StandardCharsets.UTF_8)
            ?: return false
        return MessageDigest.isEqual(
            supplied,
            ScouterNetworkConfig.token(applicationContext).toByteArray(StandardCharsets.UTF_8),
        )
    }

    private companion object {
        const val TAG = "ScouterNetwork"
        const val SCHEMA_ASSET = "scouter.schema.json"
        const val BEARER_PREFIX = "Bearer "
    }
}

private suspend inline fun io.ktor.server.routing.RoutingContext.handleRest(
    block: suspend () -> Unit,
) {
    try {
        block()
    } catch (error: Exception) {
        Log.w("ScouterNetwork", "Rejected network request: ${error.message}")
        call.respondJson(HttpStatusCode.BadRequest, """{"error":"bad_request"}""")
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondJson(
    status: HttpStatusCode,
    body: String,
) {
    respondText(body, ContentType.Application.Json, status)
}
