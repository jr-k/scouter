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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ScouterMcpServer(
    private val dispatcher: ScouterCommandDispatcher,
    schemaJson: String,
    private val endpoint: () -> String,
) {
    private val schema = Json.parseToJsonElement(schemaJson) as JsonObject
    private val schemaDefinitions =
        requireNotNull(schema["\$defs"] as? JsonObject) {
            "Scouter schema must define \$defs"
        }

    fun create(): Server = Server(
        serverInfo = Implementation(name = "scouter", version = BuildConfig.VERSION_NAME),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            ),
        ),
        instructions = "Compose native Android overlays with scouter_show. " +
            "The complete protocol schema is embedded in scouter_show and is also available through scouter_get_schema. " +
            "To display a Minimize control, every document must explicitly set " +
            "presentation.minimizable=true; it defaults to false, while presentation.dismissible defaults to true. " +
            "Checklist items may combine a persistent checkbox with an independent action button by setting " +
            "item.action and optional item.buttonLabel. Set item.actionIcon to replace visible button text " +
            "with a semantic icon while retaining buttonLabel for accessibility. " +
            "For a global list action, compose the list and a regular button in one column. " +
            "Use navigate_route with ordered stops for a complete multi-stop itinerary.",
    ).apply {
        registerTools()
    }

    private fun Server.registerTools() {
        addTool(
            name = "scouter_show",
            description = "Validate and dispatch a Scouter UI document. A matching stable id replaces and raises it. " +
                "Dispatch may become immediately visible or be queued while the device is locked. " +
                "Set document.presentation.minimizable=true to display Minimize; omission means false and usually " +
                "leaves only Close because dismissible defaults to true. " +
                "For a checkbox plus per-item button, use checklist item.action, item.buttonLabel, and optionally " +
                "item.actionIcon for an icon-only action. Compose lists with regular buttons for global actions; " +
                "navigate_route accepts ordered address or coordinate stops.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("document", buildJsonObject {
                        schema.forEach { (key, value) ->
                            if (key !in setOf("\$schema", "\$id", "\$defs")) {
                                put(key, value)
                            }
                        }
                        put(
                            "description",
                            "Complete Scouter protocol v1 document. " +
                                (schema["description"]?.jsonPrimitive?.contentOrNull ?: ""),
                        )
                    })
                    put("id", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional stable id override.")
                        put("pattern", "^[A-Za-z0-9._-]{1,80}$")
                    })
                },
                required = listOf("document"),
                defs = schemaDefinitions,
            ),
        ) { request ->
            toolCall {
                val documentElement = requireNotNull(request.arguments?.get("document")) {
                    "document is required"
                }
                val documentObject = documentElement as? JsonObject
                val presentation = documentObject?.get("presentation") as? JsonObject
                val minimizable =
                    (presentation?.get("minimizable") as? JsonPrimitive)?.booleanOrNull ?: false
                val dismissible =
                    (presentation?.get("dismissible") as? JsonPrimitive)?.booleanOrNull ?: true
                val document = documentElement.toString()
                val id = request.arguments?.get("id")?.jsonPrimitive?.contentOrNull
                val acceptedId = dispatcher.show(document, id)
                success(
                    message = if (minimizable) {
                        "Scouter validated and dispatched '$acceptedId'. Minimize is configured."
                    } else {
                        "Scouter validated and dispatched '$acceptedId', but Minimize is disabled. " +
                            "Set document.presentation.minimizable=true to display the Minimize control."
                    },
                    data = buildJsonObject {
                        put("accepted", true)
                        put("dispatched", true)
                        put("deliveryState", "dispatched")
                        put("id", acceptedId)
                        put("minimizable", minimizable)
                        put("dismissible", dismissible)
                        put("configuredControls", buildJsonArray {
                            if (minimizable) add(JsonPrimitive("minimize"))
                            if (dismissible) add(JsonPrimitive("close"))
                        })
                        put("visibleControls", buildJsonArray {
                            if (minimizable) add(JsonPrimitive("minimize"))
                            if (dismissible) add(JsonPrimitive("close"))
                        })
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
            CallToolResult(
                content = listOf(
                    TextContent("Scouter protocol v1 schema:\n${schema}"),
                ),
                structuredContent = buildJsonObject { put("schema", schema) },
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
                    put("toolCapabilities", buildJsonArray {
                        add(JsonPrimitive("show"))
                        add(JsonPrimitive("close"))
                        add(JsonPrimitive("close-all"))
                        add(JsonPrimitive("get-schema"))
                        add(JsonPrimitive("status"))
                    })
                    put("uiCapabilities", buildJsonArray {
                        add(JsonPrimitive("replace"))
                        add(JsonPrimitive("horizontal-pages"))
                        add(JsonPrimitive("minimize"))
                        add(JsonPrimitive("notification-restore"))
                        add(JsonPrimitive("locked-screen-queue"))
                    })
                    put("capabilities", buildJsonArray {
                        add(JsonPrimitive("show"))
                        add(JsonPrimitive("replace"))
                        add(JsonPrimitive("stack"))
                        add(JsonPrimitive("close"))
                        add(JsonPrimitive("minimize"))
                        add(JsonPrimitive("notification-restore"))
                        add(JsonPrimitive("locked-screen-queue"))
                    })
                    put("presentationDefaults", buildJsonObject {
                        put("dismissible", true)
                        put("minimizable", false)
                        put("showHandle", true)
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
