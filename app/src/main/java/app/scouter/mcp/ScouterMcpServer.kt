package app.scouter.mcp

import app.scouter.BuildConfig
import app.scouter.network.ScouterCommandDispatcher
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ScouterMcpServer(
    private val dispatcher: ScouterCommandDispatcher,
    schemaJson: String,
    private val endpoint: () -> String,
) {
    private val schema = Json.parseToJsonElement(schemaJson) as JsonObject

    fun create(): Server = Server(
        serverInfo = Implementation(name = "scouter", version = BuildConfig.VERSION_NAME),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            ),
        ),
        instructions = "Compose native Android overlays with scouter_show. " +
            "Call scouter_get_schema before generating a document when the contract is not already known.",
    ).apply {
        registerTools()
    }

    private fun Server.registerTools() {
        addTool(
            name = "scouter_show",
            description = "Validate and show a Scouter UI document. A matching stable id replaces and raises it.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("document", buildJsonObject {
                        put("type", "object")
                        put("description", "Complete Scouter protocol v1 document.")
                    })
                    put("id", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional stable id override.")
                        put("pattern", "^[A-Za-z0-9._-]{1,80}$")
                    })
                },
                required = listOf("document"),
            ),
        ) { request ->
            toolCall {
                val document = requireNotNull(request.arguments?.get("document")) {
                    "document is required"
                }.toString()
                val id = request.arguments?.get("id")?.jsonPrimitive?.contentOrNull
                val acceptedId = dispatcher.show(document, id)
                success(
                    message = "Scouter accepted the view '$acceptedId'.",
                    data = buildJsonObject {
                        put("accepted", true)
                        put("id", acceptedId)
                    },
                )
            }
        }

        addTool(
            name = "scouter_close",
            description = "Close one visible or queued Scouter view and cancel its local notification.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "string")
                        put("pattern", "^[A-Za-z0-9._-]{1,80}$")
                    })
                },
                required = listOf("id"),
            ),
        ) { request ->
            toolCall {
                val id = requireNotNull(request.arguments?.get("id")?.jsonPrimitive?.contentOrNull) {
                    "id is required"
                }
                dispatcher.close(id)
                success("Scouter closed '$id'.", buildJsonObject {
                    put("closed", true)
                    put("id", id)
                })
            }
        }

        addTool(
            name = "scouter_close_all",
            description = "Close every visible and queued Scouter view.",
        ) {
            toolCall {
                dispatcher.closeAll()
                success("Scouter closed all views.", buildJsonObject { put("closedAll", true) })
            }
        }

        addTool(
            name = "scouter_get_schema",
            description = "Return the complete JSON Schema for Scouter protocol v1.",
        ) {
            success(
                message = "Scouter protocol v1 schema.",
                data = buildJsonObject { put("schema", schema) },
            )
        }

        addTool(
            name = "scouter_status",
            description = "Return the Scouter server version, MCP endpoint, and supported capabilities.",
        ) {
            success(
                message = "Scouter ${BuildConfig.VERSION_NAME} is ready.",
                data = buildJsonObject {
                    put("ready", true)
                    put("version", BuildConfig.VERSION_NAME)
                    put("protocolVersion", 1)
                    put("mcpEndpoint", endpoint())
                    put("capabilities", buildJsonArray {
                        add(JsonPrimitive("show"))
                        add(JsonPrimitive("replace"))
                        add(JsonPrimitive("stack"))
                        add(JsonPrimitive("close"))
                        add(JsonPrimitive("locked-screen-queue"))
                    })
                },
            )
        }
    }

    private inline fun toolCall(block: () -> CallToolResult): CallToolResult =
        runCatching(block).getOrElse { error(it.message ?: "Scouter rejected the request.") }

    private fun success(message: String, data: JsonObject): CallToolResult = CallToolResult(
        content = listOf(TextContent(message)),
        structuredContent = data,
    )

    private fun error(message: String): CallToolResult = CallToolResult(
        content = listOf(TextContent(message)),
        isError = true,
        structuredContent = buildJsonObject {
            put("accepted", false)
            put("error", message)
        },
    )
}
