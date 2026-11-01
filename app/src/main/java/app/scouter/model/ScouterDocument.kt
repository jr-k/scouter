package app.scouter.model

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val DEFAULT_BACKGROUND = "#1C1C1E"
private const val DEFAULT_SURFACE = "#29292C"
private const val DEFAULT_TEXT = "#FAFAFC"
private const val DEFAULT_MUTED = "#A9A9B0"
private const val DEFAULT_ACCENT = "#5B9CFF"
private const val DEFAULT_DANGER = "#FF625C"

data class ScouterDocument(
    val version: Int,
    val id: String,
    val title: String?,
    val titleIcon: IconSpec?,
    val notification: NotificationContent,
    val theme: ScouterTheme,
    val presentation: Presentation,
    val content: UiNode,
)

data class IconSpec(
    val name: String,
    val position: String = "start",
    val color: String? = null,
    val size: Float = 20f,
    val spacing: Float = 8f,
)

data class NotificationContent(
    val title: String? = null,
    val message: String? = null,
    /** Explicit semantic icon, or null when the sender left it to Scouter. */
    val icon: String? = null,
)

data class ScouterTheme(
    val background: String = DEFAULT_BACKGROUND,
    val surface: String = DEFAULT_SURFACE,
    val text: String = DEFAULT_TEXT,
    val muted: String = DEFAULT_MUTED,
    val accent: String = DEFAULT_ACCENT,
    val danger: String = DEFAULT_DANGER,
    val cornerRadius: Float = 18f,
    val fontScale: Float = 1f,
)

data class Presentation(
    val dismissible: Boolean = true,
    val minimizable: Boolean = false,
    val showHandle: Boolean = true,
    val maxHeight: Float = .82f,
    val horizontalMargin: Float = 12f,
    val radius: Corners? = null,
)

data class Insets(
    val start: Float = 0f,
    val top: Float = 0f,
    val end: Float = 0f,
    val bottom: Float = 0f,
)

data class Corners(
    val topStart: Float = 0f,
    val topEnd: Float = 0f,
    val bottomEnd: Float = 0f,
    val bottomStart: Float = 0f,
)

data class UiStyle(
    val width: String? = null,
    val height: Float? = null,
    val minHeight: Float? = null,
    val maxHeight: Float? = null,
    val padding: Insets = Insets(),
    val margin: Insets = Insets(),
    val background: String? = null,
    val borderColor: String? = null,
    val borderWidth: Float = 0f,
    val radius: Corners? = null,
    val opacity: Float = 1f,
    val weight: Float? = null,
    val aspectRatio: Float? = null,
)

sealed interface UiNode {
    val id: String?
    val style: UiStyle

    data class Column(
        override val id: String?,
        override val style: UiStyle,
        val gap: Float,
        val align: String,
        val children: List<UiNode>,
    ) : UiNode

    data class Row(
        override val id: String?,
        override val style: UiStyle,
        val gap: Float,
        val align: String,
        val children: List<UiNode>,
    ) : UiNode

    data class Grid(
        override val id: String?,
        override val style: UiStyle,
        val columns: Int,
        val horizontalGap: Float,
        val verticalGap: Float,
        val children: List<UiNode>,
    ) : UiNode

    data class Box(
        override val id: String?,
        override val style: UiStyle,
        val align: String,
        val child: UiNode,
    ) : UiNode

    data class Card(
        override val id: String?,
        override val style: UiStyle,
        val tone: String,
        val padding: Float,
        val child: UiNode,
    ) : UiNode

    data class Text(
        override val id: String?,
        override val style: UiStyle,
        val value: String,
        val variant: String,
        val color: String?,
        val align: String,
        val maxLines: Int,
        val fontSize: Float?,
        val fontWeight: String?,
        val lineHeight: Float?,
        val letterSpacing: Float?,
        val fontFamily: String?,
        val italic: Boolean,
        val icon: IconSpec?,
    ) : UiNode

    data class Button(
        override val id: String,
        override val style: UiStyle,
        val label: String,
        val variant: String,
        val color: String?,
        val pressedColor: String?,
        val textColor: String?,
        val pressedScale: Float,
        val enabled: Boolean,
        val icon: IconSpec?,
        val action: ScouterAction,
    ) : UiNode

    data class HoldButton(
        override val id: String,
        override val style: UiStyle,
        val label: String,
        val completedLabel: String?,
        val durationMs: Int,
        val color: String?,
        val trackColor: String?,
        val textColor: String?,
        val enabled: Boolean,
        val icon: IconSpec?,
        val vibrateSequence: List<Long>,
        val action: ScouterAction,
    ) : UiNode

    data class SlideAction(
        override val id: String,
        override val style: UiStyle,
        val label: String,
        val completedLabel: String?,
        val threshold: Float,
        val color: String?,
        val trackColor: String?,
        val textColor: String?,
        val enabled: Boolean,
        val icon: IconSpec?,
        val vibrateSequence: List<Long>,
        val action: ScouterAction,
    ) : UiNode

    data class Badge(
        override val id: String?,
        override val style: UiStyle,
        val label: String,
        val tone: String,
        val icon: IconSpec?,
    ) : UiNode

    data class Progress(
        override val id: String?,
        override val style: UiStyle,
        val value: Float,
        val label: String?,
        val showValue: Boolean,
        val color: String?,
        val trackColor: String?,
    ) : UiNode

    data class Image(
        override val id: String?,
        override val style: UiStyle,
        val url: String,
        val description: String?,
        val height: Float,
        val cornerRadius: Float,
        val contentScale: String,
    ) : UiNode

    data class Checklist(
        override val id: String,
        override val style: UiStyle,
        val items: List<ChecklistItem>,
        val action: ScouterAction,
    ) : UiNode

    data class Link(
        override val id: String,
        override val style: UiStyle,
        val label: String,
        val url: String,
        val description: String?,
        val icon: IconSpec?,
    ) : UiNode

    data class LinkList(
        override val id: String?,
        override val style: UiStyle,
        val links: List<LinkItem>,
        val checkable: Boolean,
    ) : UiNode

    data class ActionList(
        override val id: String?,
        override val style: UiStyle,
        val items: List<ActionItem>,
        val checkable: Boolean,
    ) : UiNode

    data class Map(
        override val id: String?,
        override val style: UiStyle,
        val latitude: Double,
        val longitude: Double,
        val zoom: Int,
        val markerLabel: String?,
        val height: Float,
        val mapType: String,
        val gestures: Boolean,
        val controls: Boolean,
    ) : UiNode

    data class Video(
        override val id: String?,
        override val style: UiStyle,
        val url: String,
        val autoplay: Boolean,
        val controls: Boolean,
        val height: Float,
    ) : UiNode

    data class Web(
        override val id: String?,
        override val style: UiStyle,
        val html: String,
        val javaScriptEnabled: Boolean,
        val height: Float,
    ) : UiNode

    data class Divider(override val id: String?, override val style: UiStyle, val color: String?) : UiNode
    data class Spacer(override val id: String?, override val style: UiStyle, val height: Float) : UiNode
}

data class ChecklistItem(
    val id: String,
    val label: String,
    val checked: Boolean,
    val enabled: Boolean,
    val action: ScouterAction?,
    val buttonLabel: String?,
    val actionIcon: IconSpec?,
)

data class LinkItem(
    val id: String,
    val label: String,
    val url: String,
    val description: String?,
    val icon: IconSpec?,
    val checked: Boolean,
)

data class ActionItem(
    val id: String,
    val label: String,
    val description: String?,
    val detail: String?,
    val buttonLabel: String,
    val icon: IconSpec?,
    val action: ScouterAction,
    val checked: Boolean,
)

data class RouteLocation(
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
) {
    fun queryValue(): String = address ?: "$latitude,$longitude"
}

/**
 * Semantic icon name for the minimized/lock-screen notification: an explicit
 * `notification.icon` wins, then the sheet's `titleIcon`, else null for the
 * default Scouter glyph. The literal "scouter" also maps to the default.
 */
fun ScouterDocument.notificationIconName(): String? =
    (notification.icon ?: titleIcon?.name)?.takeIf { it != "scouter" }

fun ScouterDocument.notificationTitle(): String =
    notification.title ?: title ?: content.notificationTextCandidates().firstOrNull() ?: "Scouter"

fun ScouterDocument.notificationMessage(): String? {
    notification.message?.let { return it }
    val excluded = setOfNotNull(title, notification.title)
    return content.notificationTextCandidates()
        .firstOrNull { candidate -> candidate !in excluded }
        ?.take(240)
}

private fun UiNode.notificationTextCandidates(): Sequence<String> = sequence {
    when (this@notificationTextCandidates) {
        is UiNode.Column -> children.forEach { yieldAll(it.notificationTextCandidates()) }
        is UiNode.Row -> children.forEach { yieldAll(it.notificationTextCandidates()) }
        is UiNode.Grid -> children.forEach { yieldAll(it.notificationTextCandidates()) }
        is UiNode.Box -> yieldAll(child.notificationTextCandidates())
        is UiNode.Card -> yieldAll(child.notificationTextCandidates())
        is UiNode.Text -> yield(value)
        is UiNode.Button -> yield(label)
        is UiNode.HoldButton -> yield(label)
        is UiNode.SlideAction -> yield(label)
        is UiNode.Badge -> yield(label)
        is UiNode.Progress -> label?.let { yield(it) }
        is UiNode.Image -> description?.let { yield(it) }
        is UiNode.Checklist -> items.firstOrNull { !it.checked }?.let { yield(it.label) }
        is UiNode.Link -> yield(description ?: label)
        is UiNode.LinkList -> links.firstOrNull()?.let { yield(it.description ?: it.label) }
        is UiNode.ActionList -> items.firstOrNull()?.let {
            yield(it.description ?: it.detail ?: it.label)
        }
        is UiNode.Map -> markerLabel?.let { yield(it) }
        is UiNode.Video, is UiNode.Web, is UiNode.Divider, is UiNode.Spacer -> Unit
    }
}

sealed interface ScouterAction {
    data object Close : ScouterAction
    data class Broadcast(
        val action: String,
        val packageName: String?,
        val extras: Map<String, String>,
        val closeAfter: Boolean,
    ) : ScouterAction

    data class OpenUrl(val url: String, val closeAfter: Boolean) : ScouterAction
    data class Navigate(
        val address: String?,
        val latitude: Double?,
        val longitude: Double?,
        val chooserTitle: String?,
        val closeAfter: Boolean,
    ) : ScouterAction

    data class NavigateRoute(
        val origin: RouteLocation?,
        val stops: List<RouteLocation>,
        val travelMode: String,
        val chooserTitle: String?,
        val closeAfter: Boolean,
    ) : ScouterAction

    data class Dial(val phoneNumber: String, val closeAfter: Boolean) : ScouterAction
    data object None : ScouterAction
}

fun ScouterAction.NavigateRoute.mapsUrl(): String {
    fun String.encoded(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
    val destination = stops.last().queryValue().encoded()
    val waypoints = stops.dropLast(1).joinToString("%7C") { it.queryValue().encoded() }
    return buildString {
        append("https://www.google.com/maps/dir/?api=1")
        origin?.let { append("&origin=").append(it.queryValue().encoded()) }
        append("&destination=").append(destination)
        if (waypoints.isNotEmpty()) append("&waypoints=").append(waypoints)
        append("&travelmode=").append(travelMode)
        append("&dir_action=navigate")
    }
}

private fun ScouterAction.defaultButtonLabel(): String = when (this) {
    is ScouterAction.Navigate -> "Navigate"
    is ScouterAction.NavigateRoute -> "Route"
    is ScouterAction.OpenUrl -> "Open"
    is ScouterAction.Dial -> "Dial"
    is ScouterAction.Broadcast -> "Run"
    ScouterAction.Close -> "Close"
    ScouterAction.None -> ""
}

object ScouterParser {
    const val MAX_PAYLOAD_BYTES = 256 * 1024
    private const val MAX_DEPTH = 12
    private const val MAX_NODES = 120
    private val NOTIFICATION_ICONS =
        setOf("scouter", "info", "warning", "message", "navigation", "phone", "task")
    private val UI_ICON_NAMES = setOf(
        "add", "back", "calendar", "call", "check", "close", "delete", "directions",
        "edit", "email", "favorite", "home", "info", "link", "list", "location",
        "lock", "menu", "message", "more", "notification", "open", "pause", "person",
        "phone", "play", "search", "settings", "share", "star", "task", "time", "unlock",
        "warning",
    )

    fun parse(raw: String, idOverride: String? = null): ScouterDocument {
        require(raw.toByteArray().size <= MAX_PAYLOAD_BYTES) { "Payload exceeds 256 KiB" }
        val json = JSONObject(raw)
        var nodeCount = 0

        fun parseNode(value: JSONObject, depth: Int): UiNode {
            require(depth <= MAX_DEPTH) { "UI tree exceeds $MAX_DEPTH levels" }
            require(++nodeCount <= MAX_NODES) { "UI tree exceeds $MAX_NODES nodes" }
            val id = value.optString("id").takeIf(String::isNotBlank)
            val style = parseStyle(value.optJSONObject("style"))
            return when (value.requireString("type").lowercase()) {
                "column" -> UiNode.Column(
                    id = id,
                    style = style,
                    gap = value.float("gap", 12f, 0f, 64f),
                    align = value.optString("align", "start"),
                    children = value.children().map { parseNode(it, depth + 1) },
                )
                "row" -> UiNode.Row(
                    id = id,
                    style = style,
                    gap = value.float("gap", 10f, 0f, 64f),
                    align = value.optString("align", "center"),
                    children = value.children().map { parseNode(it, depth + 1) },
                )
                "grid" -> UiNode.Grid(
                    id = id,
                    style = style,
                    columns = value.int("columns", 2, 1, 6),
                    horizontalGap = value.float("horizontalGap", value.optDouble("gap", 12.0).toFloat(), 0f, 64f),
                    verticalGap = value.float("verticalGap", value.optDouble("gap", 12.0).toFloat(), 0f, 64f),
                    children = value.children().map { parseNode(it, depth + 1) },
                )
                "box" -> UiNode.Box(
                    id = id,
                    style = style,
                    align = value.optString("align", "center"),
                    child = parseNode(value.getJSONObject("child"), depth + 1),
                )
                "card" -> UiNode.Card(
                    id = id,
                    style = style,
                    tone = value.optString("tone", "surface"),
                    padding = value.float("padding", 16f, 0f, 48f),
                    child = parseNode(value.getJSONObject("child"), depth + 1),
                )
                "text" -> UiNode.Text(
                    id = id,
                    style = style,
                    value = value.requireString("value").take(8_000),
                    variant = value.optString(
                        "variant",
                        value.optString("textStyle", value.opt("style").takeIf { it is String } as? String ?: "body"),
                    ),
                    color = value.optionalColor("color")
                        ?: value.optionalColor("fontColor")
                        ?: value.optionalColor("textColor"),
                    align = value.optString("align", "start"),
                    maxLines = value.int("maxLines", 20, 1, 100),
                    fontSize = value.optionalFloat("fontSize", 8f, 96f),
                    fontWeight = value.optString("fontWeight").takeIf(String::isNotBlank),
                    lineHeight = value.optionalFloat("lineHeight", 8f, 120f),
                    letterSpacing = value.optionalFloat("letterSpacing", -8f, 32f),
                    fontFamily = value.optString("fontFamily").takeIf(String::isNotBlank),
                    italic = value.boolean("italic", false),
                    icon = parseIcon(value.opt("icon")),
                )
                "button" -> UiNode.Button(
                    id = (id ?: error("A button requires a stable id")).validElementId(),
                    style = style,
                    label = value.requireString("label").take(120),
                    variant = value.optString(
                        "variant",
                        value.optString("buttonStyle", value.opt("style").takeIf { it is String } as? String ?: "primary"),
                    ),
                    color = value.optionalColor("color"),
                    pressedColor = value.optionalColor("pressedColor"),
                    textColor = value.optionalColor("textColor"),
                    pressedScale = value.float("pressedScale", .975f, .9f, 1f),
                    enabled = value.boolean("enabled", true),
                    icon = parseIcon(value.opt("icon")),
                    action = parseAction(value.optionalObject("action")),
                )
                "holdbutton", "hold_button" -> UiNode.HoldButton(
                    id = (id ?: error("A hold button requires a stable id")).validElementId(),
                    style = style,
                    label = value.requireString("label").take(120),
                    completedLabel = value.optString("completedLabel").takeIf(String::isNotBlank)?.take(120),
                    durationMs = value.int("durationMs", 1_200, 250, 15_000),
                    color = value.optionalColor("color"),
                    trackColor = value.optionalColor("trackColor"),
                    textColor = value.optionalColor("textColor"),
                    enabled = value.boolean("enabled", true),
                    icon = parseIcon(value.opt("icon")),
                    vibrateSequence = value.vibrateSequence(),
                    action = parseAction(value.optionalObject("action")),
                )
                "slideaction", "slide_action", "slide_to_action" -> UiNode.SlideAction(
                    id = (id ?: error("A slide action requires a stable id")).validElementId(),
                    style = style,
                    label = value.requireString("label").take(120),
                    completedLabel = value.optString("completedLabel").takeIf(String::isNotBlank)?.take(120),
                    threshold = value.float("threshold", .82f, .5f, 1f),
                    color = value.optionalColor("color"),
                    trackColor = value.optionalColor("trackColor"),
                    textColor = value.optionalColor("textColor"),
                    enabled = value.boolean("enabled", true),
                    icon = parseIcon(value.opt("icon")),
                    vibrateSequence = value.vibrateSequence(),
                    action = parseAction(value.optionalObject("action")),
                )
                "badge" -> UiNode.Badge(
                    id,
                    style,
                    value.requireString("label").take(80),
                    value.optString("tone", "accent"),
                    parseIcon(value.opt("icon")),
                )
                "progress" -> UiNode.Progress(
                    id,
                    style,
                    value.float("value", 0f, 0f, 1f),
                    value.optString("label").takeIf(String::isNotBlank),
                    value.boolean("showValue", false),
                    value.optionalColor("color"),
                    value.optionalColor("trackColor"),
                )
                "image" -> {
                    val url = value.httpUrl("url")
                    UiNode.Image(
                        id,
                        style,
                        url,
                        value.optString("description").takeIf(String::isNotBlank),
                        value.float("height", 180f, 48f, 480f),
                        value.float("cornerRadius", 18f, 0f, 64f),
                        value.optString("contentScale", "crop"),
                    )
                }
                "checklist" -> {
                    val items = value.optJSONArray("items") ?: JSONArray()
                    require(items.length() in 1..40) { "Checklist requires 1–40 items" }
                    UiNode.Checklist(
                        id = (id ?: error("A checklist requires a stable id")).validElementId(),
                        style = style,
                        items = (0 until items.length()).map { index ->
                            val item = items.getJSONObject(index)
                            val itemActionJson = item.optionalObject("action")
                            val itemAction = itemActionJson
                                ?.let(::parseAction)
                                ?.takeUnless { it == ScouterAction.None }
                            ChecklistItem(
                                id = item.requireString("id").also {
                                    require(it.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "Invalid checklist item id" }
                                },
                                label = item.requireString("label").take(240),
                                checked = item.boolean("checked", false),
                                enabled = item.boolean("enabled", true),
                                action = itemAction,
                                buttonLabel = itemAction?.let {
                                    item.optString("buttonLabel").takeIf(String::isNotBlank)?.take(40)
                                        ?: itemActionJson.optString("label")
                                            ?.takeIf(String::isNotBlank)
                                            ?.take(40)
                                        ?: it.defaultButtonLabel()
                                },
                                actionIcon = itemAction?.let { parseIcon(item.opt("actionIcon")) },
                            )
                        },
                        action = parseAction(value.optionalObject("action")),
                    )
                }
                "link" -> UiNode.Link(
                    id = (id ?: error("A link requires a stable id")).validElementId(),
                    style = style,
                    label = value.requireString("label").take(240),
                    url = value.httpUrl("url"),
                    description = value.optString("description").takeIf(String::isNotBlank)?.take(500),
                    icon = parseIcon(value.opt("icon")),
                )
                "linklist", "link_list" -> {
                    val links = value.optJSONArray("links") ?: JSONArray()
                    require(links.length() in 1..30) { "Link list requires 1–30 links" }
                    val checkable = value.boolean("checkable", false)
                    UiNode.LinkList(
                        id = id.also {
                            require(!checkable || it != null) { "A checkable link list requires a stable id" }
                        },
                        style = style,
                        links = (0 until links.length()).map { index ->
                            val link = links.getJSONObject(index)
                            LinkItem(
                                id = link.requireString("id").validElementId(),
                                label = link.requireString("label").take(240),
                                url = link.httpUrl("url"),
                                description = link.optString("description").takeIf(String::isNotBlank)?.take(500),
                                icon = parseIcon(link.opt("icon")),
                                checked = link.boolean("checked", false),
                            )
                        },
                        checkable = checkable,
                    )
                }
                "address" -> UiNode.ActionList(
                    id = id,
                    style = style,
                    items = listOf(parseAddressItem(value, id ?: error("An address requires a stable id"))),
                    checkable = false,
                )
                "addresslist", "address_list" -> {
                    val checkable = value.boolean("checkable", false)
                    UiNode.ActionList(
                        id = id.also {
                            require(!checkable || it != null) { "A checkable address list requires a stable id" }
                        },
                        style = style,
                        items = value.actionItems("addresses", 30, ::parseAddressItem),
                        checkable = checkable,
                    )
                }
                "phone" -> UiNode.ActionList(
                    id = id,
                    style = style,
                    items = listOf(parsePhoneItem(value, id ?: error("A phone requires a stable id"))),
                    checkable = false,
                )
                "phonelist", "phone_list" -> {
                    val checkable = value.boolean("checkable", false)
                    UiNode.ActionList(
                        id = id.also {
                            require(!checkable || it != null) { "A checkable phone list requires a stable id" }
                        },
                        style = style,
                        items = value.actionItems("phones", 30, ::parsePhoneItem),
                        checkable = checkable,
                    )
                }
                "actionlist", "action_list" -> {
                    val items = value.optJSONArray("items") ?: JSONArray()
                    require(items.length() in 1..40) { "Action list requires 1–40 items" }
                    val checkable = value.boolean("checkable", false)
                    UiNode.ActionList(
                        id = id.also {
                            require(!checkable || it != null) { "A checkable action list requires a stable id" }
                        },
                        style = style,
                        items = (0 until items.length()).map { index ->
                            val item = items.getJSONObject(index)
                            val itemId = item.requireString("id").validElementId()
                            when (item.requireString("type").lowercase()) {
                                "address" -> parseAddressItem(item, itemId)
                                "phone" -> parsePhoneItem(item, itemId)
                                "link" -> parseLinkActionItem(item, itemId)
                                else -> error("Unknown action list item type: ${item.optString("type")}")
                            }
                        },
                        checkable = checkable,
                    )
                }
                "map" -> UiNode.Map(
                    id = id,
                    style = style,
                    latitude = value.getDouble("latitude").also { require(it in -90.0..90.0) { "Invalid latitude" } },
                    longitude = value.getDouble("longitude").also { require(it in -180.0..180.0) { "Invalid longitude" } },
                    zoom = value.int("zoom", 14, 1, 19),
                    markerLabel = value.optString("markerLabel").takeIf(String::isNotBlank)?.take(120),
                    height = value.float("height", 220f, 120f, 520f),
                    mapType = value.optString("mapType", "normal").also {
                        require(it.lowercase() in setOf("normal", "satellite", "terrain", "hybrid")) {
                            "Invalid mapType"
                        }
                    },
                    gestures = value.boolean("gestures", true),
                    controls = value.boolean("controls", false),
                )
                "video" -> UiNode.Video(
                    id = id,
                    style = style,
                    url = value.httpUrl("url"),
                    autoplay = value.boolean("autoplay", false),
                    controls = value.boolean("controls", true),
                    height = value.float("height", 220f, 120f, 520f),
                )
                "web" -> UiNode.Web(
                    id = id,
                    style = style,
                    html = value.requireString("html").also {
                        require(it.toByteArray().size <= 64 * 1024) { "Web HTML exceeds 64 KiB" }
                    },
                    javaScriptEnabled = value.boolean("javaScriptEnabled", false),
                    height = value.float("height", 220f, 80f, 600f),
                )
                "divider" -> UiNode.Divider(id, style, value.optionalColor("color"))
                "spacer" -> UiNode.Spacer(id, style, value.float("height", 12f, 0f, 240f))
                else -> error("Unknown component type: ${value.optString("type")}")
            }
        }

        val themeJson = json.optionalObject("theme") ?: JSONObject()
        val presentationJson = json.optionalObject("presentation") ?: JSONObject()
        val notificationJson = json.optionalObject("notification") ?: JSONObject()
        val id = (idOverride ?: json.optString("id")).trim()
        require(id.matches(Regex("[A-Za-z0-9._-]{1,80}"))) {
            "Document id must contain 1–80 letters, digits, dots, underscores or hyphens"
        }
        require(json.has("version") && !json.isNull("version")) { "version is required" }

        return ScouterDocument(
            version = json.int("version", 1, 1, 1),
            id = id,
            title = json.optString("title").takeIf(String::isNotBlank)?.take(120),
            titleIcon = parseIcon(json.opt("titleIcon")),
            notification = NotificationContent(
                title = notificationJson.optString("title").takeIf(String::isNotBlank)?.take(120),
                message = notificationJson.optString("message").takeIf(String::isNotBlank)?.take(240),
                icon = notificationJson.optString("icon").takeIf(String::isNotBlank)?.lowercase()?.also {
                    require(it in NOTIFICATION_ICONS) { "Unknown notification icon: $it" }
                },
            ),
            theme = ScouterTheme(
                background = themeJson.color("background", DEFAULT_BACKGROUND),
                surface = themeJson.color("surface", DEFAULT_SURFACE),
                text = themeJson.color("text", DEFAULT_TEXT),
                muted = themeJson.color("muted", DEFAULT_MUTED),
                accent = themeJson.color("accent", DEFAULT_ACCENT),
                danger = themeJson.color("danger", DEFAULT_DANGER),
                cornerRadius = themeJson.float("cornerRadius", 18f, 12f, 48f),
                fontScale = themeJson.float("fontScale", 1f, .8f, 1.5f),
            ),
            presentation = Presentation(
                dismissible = presentationJson.boolean("dismissible", true),
                minimizable = presentationJson.boolean("minimizable", false),
                showHandle = presentationJson.boolean("showHandle", true),
                maxHeight = presentationJson.float("maxHeight", .82f, .25f, .96f),
                horizontalMargin = presentationJson.float("horizontalMargin", 12f, 0f, 40f),
                radius = presentationJson.opt("radius")
                    ?.takeUnless { it == JSONObject.NULL }
                    ?.let(::parseCorners),
            ),
            content = parseNode(json.getJSONObject("content"), 0),
        )
    }

    private fun parseStyle(json: JSONObject?): UiStyle {
        if (json == null) return UiStyle()
        val border = json.optionalObject("border")
        val width = json.opt("width")?.takeUnless { it == JSONObject.NULL }?.toString()
        require(
            width == null ||
                width in setOf("fill", "wrap") ||
                width.toFloatOrNull()?.let { it in 0f..2_000f } == true,
        ) {
            "style.width must be fill, wrap, or a dp value"
        }
        return UiStyle(
            width = width,
            height = json.optionalFloat("height", 0f, 2_000f),
            minHeight = json.optionalFloat("minHeight", 0f, 2_000f),
            maxHeight = json.optionalFloat("maxHeight", 0f, 2_000f),
            padding = parseInsets(json.opt("padding")),
            margin = parseInsets(json.opt("margin")),
            background = json.optionalColor("background"),
            borderColor = (border?.optionalColor("color") ?: json.optionalColor("borderColor")),
            borderWidth = border?.float("width", 1f, 0f, 24f)
                ?: json.float("borderWidth", 0f, 0f, 24f),
            radius = json.opt("radius")
                ?.takeUnless { it == JSONObject.NULL }
                ?.let(::parseCorners),
            opacity = json.float("opacity", 1f, 0f, 1f),
            weight = json.optionalFloat("weight", .01f, 100f),
            aspectRatio = json.optionalFloat("aspectRatio", .1f, 10f),
        )
    }

    private fun parseIcon(value: Any?): IconSpec? {
        if (value == null || value == JSONObject.NULL) return null
        val json = when (value) {
            is String -> JSONObject().put("name", value)
            is JSONObject -> value
            else -> error("icon must be null, a name, or an object")
        }
        val name = json.requireString("name").lowercase()
        require(name in UI_ICON_NAMES) { "Unknown UI icon: $name" }
        val position = json.optString("position", "start").lowercase()
        require(position in setOf("start", "end")) { "Icon position must be start or end" }
        return IconSpec(
            name = name,
            position = position,
            color = json.optionalColor("color"),
            size = json.float("size", 20f, 8f, 48f),
            spacing = json.float("spacing", 8f, 0f, 24f),
        )
    }

    private fun parseInsets(value: Any?): Insets {
        if (value == null || value == JSONObject.NULL) return Insets()
        if (value is Number) {
            val inset = value.toFloat()
            require(inset.isFinite() && inset in 0f..240f) { "Insets must be between 0 and 240" }
            return Insets(inset, inset, inset, inset)
        }
        require(value is JSONObject) { "Insets must be a number or object" }
        val horizontal = value.float("horizontal", 0f, 0f, 240f)
        val vertical = value.float("vertical", 0f, 0f, 240f)
        return Insets(
            start = value.float("start", horizontal, 0f, 240f),
            top = value.float("top", vertical, 0f, 240f),
            end = value.float("end", horizontal, 0f, 240f),
            bottom = value.float("bottom", vertical, 0f, 240f),
        )
    }

    private fun parseCorners(value: Any?): Corners {
        if (value == null || value == JSONObject.NULL) return Corners()
        if (value is Number) {
            val radius = value.toFloat()
            require(radius.isFinite() && radius in 0f..160f) { "Radius must be between 0 and 160" }
            return Corners(radius, radius, radius, radius)
        }
        require(value is JSONObject) { "Radius must be a number or object" }
        val all = value.float("all", 0f, 0f, 160f)
        return Corners(
            topStart = value.float("topStart", all, 0f, 160f),
            topEnd = value.float("topEnd", all, 0f, 160f),
            bottomEnd = value.float("bottomEnd", all, 0f, 160f),
            bottomStart = value.float("bottomStart", all, 0f, 160f),
        )
    }

    private fun parseAction(json: JSONObject?): ScouterAction {
        if (json == null) return ScouterAction.None
        return when (json.optString("type", "none").lowercase()) {
            "close" -> ScouterAction.Close
            "broadcast" -> {
                val extrasJson = json.optJSONObject("extras") ?: JSONObject()
                val extras = extrasJson.keys().asSequence().associateWith { extrasJson.optString(it) }
                ScouterAction.Broadcast(
                    action = json.requireString("action"),
                    packageName = json.optString("package").takeIf(String::isNotBlank),
                    extras = extras,
                    closeAfter = json.boolean("closeAfter", false),
                )
            }
            "openurl", "open_url" -> ScouterAction.OpenUrl(
                url = json.httpUrl("url"),
                closeAfter = json.boolean("closeAfter", false),
            )
            "navigate" -> {
                val address = json.optString("address").takeIf(String::isNotBlank)?.take(500)
                val latitude = json.optionalDouble("latitude", -90.0, 90.0)
                val longitude = json.optionalDouble("longitude", -180.0, 180.0)
                require(address != null || (latitude != null && longitude != null)) {
                    "navigate requires an address or latitude and longitude"
                }
                require((latitude == null) == (longitude == null)) {
                    "navigate latitude and longitude must be provided together"
                }
                ScouterAction.Navigate(
                    address = address,
                    latitude = latitude,
                    longitude = longitude,
                    chooserTitle = json.optString("chooserTitle").takeIf(String::isNotBlank)?.take(80),
                    closeAfter = json.boolean("closeAfter", false),
                )
            }
            "route", "navigateroute", "navigate_route" -> {
                val values = json.optJSONArray("stops") ?: JSONArray()
                require(values.length() in 1..10) { "navigate_route requires 1–10 ordered stops" }
                val route = ScouterAction.NavigateRoute(
                    origin = json.opt("origin")
                        ?.takeUnless { it == JSONObject.NULL }
                        ?.let(::parseRouteLocation),
                    stops = (0 until values.length()).map { parseRouteLocation(values.get(it)) },
                    travelMode = json.optString("travelMode", "driving").lowercase().also {
                        require(it in setOf("driving", "walking", "bicycling", "two-wheeler", "transit")) {
                            "Invalid route travelMode"
                        }
                    },
                    chooserTitle = json.optString("chooserTitle").takeIf(String::isNotBlank)?.take(80),
                    closeAfter = json.boolean("closeAfter", false),
                )
                require(route.mapsUrl().length <= 2_048) {
                    "navigate_route exceeds the Google Maps URL limit of 2048 characters"
                }
                route
            }
            "dial" -> ScouterAction.Dial(
                phoneNumber = json.requireString("phoneNumber").validPhoneNumber(),
                closeAfter = json.boolean("closeAfter", false),
            )
            "none" -> ScouterAction.None
            else -> error("Unknown action type: ${json.optString("type")}")
        }
    }

    private fun JSONObject.vibrateSequence(): List<Long> {
        val values = optJSONArray("vibrateSequence") ?: return emptyList()
        require(values.length() <= 20) { "vibrateSequence accepts at most 20 durations" }
        val sequence = (0 until values.length()).map { index ->
            values.getLong(index).also {
                require(it in 0L..5_000L) { "Vibration durations must be between 0 and 5000 ms" }
            }
        }
        require(sequence.sum() <= 15_000L) { "vibrateSequence cannot exceed 15000 ms" }
        return sequence
    }

    private fun parseAddressItem(value: JSONObject, id: String): ActionItem {
        val address = value.requireString("address").take(500)
        val latitude = value.optionalDouble("latitude", -90.0, 90.0)
        val longitude = value.optionalDouble("longitude", -180.0, 180.0)
        require((latitude == null) == (longitude == null)) {
            "Address latitude and longitude must be provided together"
        }
        return ActionItem(
            id = id.validElementId(),
            label = value.optString("label").takeIf(String::isNotBlank)?.take(240) ?: address,
            description = value.optString("description").takeIf(String::isNotBlank)?.take(500),
            detail = address,
            buttonLabel = value.optString("buttonLabel", "Navigate").take(40),
            icon = parseIcon(value.opt("icon")),
            action = ScouterAction.Navigate(
                address = address,
                latitude = latitude,
                longitude = longitude,
                chooserTitle = value.optString("chooserTitle").takeIf(String::isNotBlank)?.take(80),
                closeAfter = value.boolean("closeAfter", false),
            ),
            checked = value.boolean("checked", false),
        )
    }

    private fun parseRouteLocation(value: Any): RouteLocation {
        if (value is String) {
            require(value.isNotBlank()) { "Route address cannot be blank" }
            return RouteLocation(value.take(500), null, null)
        }
        require(value is JSONObject) { "Route locations must be an address string or object" }
        val address = value.optString("address").takeIf(String::isNotBlank)?.take(500)
        val latitude = value.optionalDouble("latitude", -90.0, 90.0)
        val longitude = value.optionalDouble("longitude", -180.0, 180.0)
        require((latitude == null) == (longitude == null)) {
            "Route latitude and longitude must be provided together"
        }
        require((address != null) xor (latitude != null)) {
            "Route locations require either address or latitude and longitude"
        }
        return RouteLocation(address, latitude, longitude)
    }

    private fun parsePhoneItem(value: JSONObject, id: String): ActionItem {
        val number = value.requireString("phoneNumber").validPhoneNumber()
        return ActionItem(
            id = id.validElementId(),
            label = value.optString("label").takeIf(String::isNotBlank)?.take(240) ?: number,
            description = value.optString("description").takeIf(String::isNotBlank)?.take(500),
            detail = number,
            buttonLabel = value.optString("buttonLabel", "Dial").take(40),
            icon = parseIcon(value.opt("icon")),
            action = ScouterAction.Dial(number, value.boolean("closeAfter", false)),
            checked = value.boolean("checked", false),
        )
    }

    private fun parseLinkActionItem(value: JSONObject, id: String): ActionItem {
        val url = value.httpUrl("url")
        return ActionItem(
            id = id.validElementId(),
            label = value.requireString("label").take(240),
            description = value.optString("description").takeIf(String::isNotBlank)?.take(500),
            detail = null,
            buttonLabel = value.optString("buttonLabel", "Open").take(40),
            icon = parseIcon(value.opt("icon")),
            action = ScouterAction.OpenUrl(url, value.boolean("closeAfter", false)),
            checked = value.boolean("checked", false),
        )
    }

    private fun JSONObject.actionItems(
        field: String,
        maximum: Int,
        parser: (JSONObject, String) -> ActionItem,
    ): List<ActionItem> {
        val values = optJSONArray(field) ?: JSONArray()
        require(values.length() in 1..maximum) { "$field requires 1–$maximum items" }
        return (0 until values.length()).map { index ->
            val item = values.getJSONObject(index)
            parser(item, item.requireString("id"))
        }
    }

    private fun String.validElementId(): String = also {
        require(matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "Invalid element id" }
    }

    private fun String.validPhoneNumber(): String = take(40).also {
        require(it.matches(Regex("[+0-9*#(). -]{3,40}"))) { "Invalid phone number" }
    }

    private fun JSONObject.children(): List<JSONObject> {
        require(has("children") && !isNull("children")) { "children is required" }
        val array = getJSONArray("children")
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun JSONObject.requireString(name: String): String =
        requireNotNull(get(name) as? String) { "$name must be a string" }
            .also { require(it.isNotBlank()) { "$name cannot be blank" } }

    private fun JSONObject.optionalObject(name: String): JSONObject? {
        if (!has(name)) return null
        require(!isNull(name)) { "$name cannot be null" }
        return requireNotNull(get(name) as? JSONObject) { "$name must be an object" }
    }

    private fun JSONObject.boolean(name: String, fallback: Boolean): Boolean {
        if (!has(name)) return fallback
        require(!isNull(name)) { "$name cannot be null" }
        return requireNotNull(get(name) as? Boolean) { "$name must be a boolean" }
    }

    private fun JSONObject.optionalDouble(name: String, minimum: Double, maximum: Double): Double? =
        if (!has(name)) {
            null
        } else {
            require(!isNull(name)) { "$name cannot be null" }
            val value = requireNotNull(get(name) as? Number) { "$name must be a number" }.toDouble()
            require(value.isFinite() && value in minimum..maximum) {
                "$name must be between $minimum and $maximum"
            }
            value
        }

    private fun JSONObject.httpUrl(name: String): String =
        requireString(name).also {
            require(it.startsWith("https://") || it.startsWith("http://")) {
                "$name must use HTTP(S)"
            }
        }

    private fun JSONObject.float(name: String, fallback: Float, min: Float, max: Float): Float {
        if (!has(name)) return fallback
        require(!isNull(name)) { "$name cannot be null" }
        val value = requireNotNull(get(name) as? Number) { "$name must be a number" }.toFloat()
        require(value.isFinite() && value in min..max) { "$name must be between $min and $max" }
        return value
    }

    private fun JSONObject.int(name: String, fallback: Int, min: Int, max: Int): Int {
        if (!has(name)) return fallback
        require(!isNull(name)) { "$name cannot be null" }
        val number = requireNotNull(get(name) as? Number) { "$name must be an integer" }
        val value = number.toDouble()
        require(value.isFinite() && value % 1.0 == 0.0 && value in min.toDouble()..max.toDouble()) {
            "$name must be an integer between $min and $max"
        }
        return value.toInt()
    }

    private fun JSONObject.optionalFloat(name: String, min: Float, max: Float): Float? =
        if (!has(name)) {
            null
        } else {
            require(!isNull(name)) { "$name cannot be null" }
            val value = requireNotNull(get(name) as? Number) { "$name must be a number" }.toFloat()
            require(value.isFinite() && value in min..max) { "$name must be between $min and $max" }
            value
        }

    private fun JSONObject.optionalColor(name: String): String? =
        optString(name).takeIf(String::isNotBlank)?.also {
            require(it.matches(Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?"))) { "Invalid color for $name" }
        }

    private fun JSONObject.color(name: String, fallback: String): String =
        optString(name, fallback).also {
            require(it.matches(Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?"))) { "Invalid color for $name" }
        }
}
