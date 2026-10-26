package app.scouter

import android.content.Context

class PendingDocumentStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun put(id: String, json: String) {
        val ids = ids().toMutableSet().apply { add(id) }
        preferences.edit()
            .putString(key(id), json)
            .putStringSet(KEY_IDS, ids)
            .apply()
    }

    fun get(id: String): String? = preferences.getString(key(id), null)

    fun remove(id: String) {
        val ids = ids().toMutableSet().apply { remove(id) }
        preferences.edit()
            .remove(key(id))
            .putStringSet(KEY_IDS, ids)
            .apply()
    }

    fun ids(): Set<String> = preferences.getStringSet(KEY_IDS, emptySet())?.toSet().orEmpty()

    fun clear(): Set<String> {
        val removed = ids()
        preferences.edit().clear().apply()
        return removed
    }

    private fun key(id: String) = "document.$id"

    companion object {
        private const val PREFERENCES = "scouter_pending_documents"
        private const val KEY_IDS = "document_ids"
    }
}
