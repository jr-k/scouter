package app.scouter

import android.content.Context

class ChecklistStateStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun checked(documentId: String, listId: String, itemId: String, default: Boolean): Boolean =
        preferences.getBoolean(key(documentId, listId, itemId), default)

    fun setChecked(documentId: String, listId: String, itemId: String, checked: Boolean) {
        preferences.edit().putBoolean(key(documentId, listId, itemId), checked).apply()
    }

    private fun key(documentId: String, listId: String, itemId: String): String =
        "$documentId::$listId::$itemId"

    private companion object {
        const val PREFERENCES = "scouter_check_states"
    }
}
