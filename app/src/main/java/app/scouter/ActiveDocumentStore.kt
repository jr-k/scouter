package app.scouter

import android.content.Context

class ActiveDocumentStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun put(id: String, json: String) {
        val ids = ids().toMutableSet().apply { add(id) }
        preferences.edit()
            .putString(key(id), json)
            .putStringSet(KEY_IDS, ids)
            .apply()
    }

    fun remove(id: String) {
        val ids = ids().toMutableSet().apply { remove(id) }
        preferences.edit()
            .remove(key(id))
            .putStringSet(KEY_IDS, ids)
            .apply()
    }

    fun documents(): List<Pair<String, String>> =
        ids().mapNotNull { id -> preferences.getString(key(id), null)?.let { id to it } }

    fun hasDocuments(): Boolean = ids().isNotEmpty()

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun ids(): Set<String> =
        preferences.getStringSet(KEY_IDS, emptySet())?.toSet().orEmpty()

    private fun key(id: String) = "document.$id"

    private companion object {
        const val PREFERENCES = "scouter_active_documents"
        const val KEY_IDS = "document_ids"
    }
}
