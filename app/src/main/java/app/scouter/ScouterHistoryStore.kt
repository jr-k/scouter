package app.scouter

import android.content.Context
import app.scouter.model.ScouterDocument
import app.scouter.model.ScouterParser
import app.scouter.model.notificationMessage
import app.scouter.model.notificationTitle
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ScouterHistoryEntry(
    val historyId: String,
    val documentId: String,
    val title: String,
    val summary: String?,
    val accent: String,
    val iconName: String?,
    val receivedAt: Long,
    val documentJson: String,
)

class ScouterHistoryStore(context: Context) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    fun record(document: ScouterDocument, raw: String) {
        val entry = ScouterHistoryEntry(
            historyId = UUID.randomUUID().toString(),
            documentId = document.id,
            title = document.notificationTitle(),
            summary = document.notificationMessage(),
            accent = normalizedAccent(document.theme.accent),
            iconName = document.titleIcon?.name,
            receivedAt = System.currentTimeMillis(),
            documentJson = raw,
        )
        val updated = listOf(entry) + entries()
        write(updated.take(MAX_ENTRIES))
        context.sendBroadcast(
            android.content.Intent(ACTION_HISTORY_CHANGED).setPackage(context.packageName),
        )
    }

    @Synchronized
    fun entries(): List<ScouterHistoryEntry> {
        val values = runCatching {
            JSONArray(preferences.getString(KEY_ENTRIES, "[]"))
        }.getOrElse { JSONArray() }
        return (0 until values.length()).mapNotNull { index ->
            runCatching {
                val value = values.getJSONObject(index)
                val documentJson = value.getString("documentJson")
                ScouterHistoryEntry(
                    historyId = value.getString("historyId"),
                    documentId = value.getString("documentId"),
                    title = value.getString("title"),
                    summary = value.optString("summary").takeIf(String::isNotBlank),
                    accent = normalizedAccent(value.optString("accent", "#71C56E")),
                    iconName = value.optString("iconName").takeIf(String::isNotBlank)
                        ?: runCatching { ScouterParser.parse(documentJson).titleIcon?.name }.getOrNull(),
                    receivedAt = value.getLong("receivedAt"),
                    documentJson = documentJson,
                )
            }.getOrNull()
        }
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY_ENTRIES).apply()
    }

    private fun write(entries: List<ScouterHistoryEntry>) {
        val values = JSONArray()
        entries.forEach { entry ->
            values.put(
                JSONObject()
                    .put("historyId", entry.historyId)
                    .put("documentId", entry.documentId)
                    .put("title", entry.title)
                    .put("summary", entry.summary)
                    .put("accent", entry.accent)
                    .put("iconName", entry.iconName)
                    .put("receivedAt", entry.receivedAt)
                    .put("documentJson", entry.documentJson),
            )
        }
        preferences.edit().putString(KEY_ENTRIES, values.toString()).apply()
    }

    private fun normalizedAccent(accent: String): String =
        when (accent.uppercase()) {
            "#C9FF3F", "#C8FF3D" -> "#71C56E"
            else -> accent
        }

    companion object {
        const val ACTION_HISTORY_CHANGED = "app.scouter.internal.HISTORY_CHANGED"
        private const val PREFERENCES = "scouter_history"
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 100
    }
}
