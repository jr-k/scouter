package app.scouter.network

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.scouter.ScouterReceiver
import app.scouter.model.ScouterParser

class ScouterCommandDispatcher(context: Context) {
    private val applicationContext = context.applicationContext

    fun show(document: String, idOverride: String?): String {
        val acceptedId = ScouterParser.parse(document, idOverride).id
        dispatch(ScouterReceiver.ACTION_SHOW, document, acceptedId)
        return acceptedId
    }

    fun close(id: String) {
        require(id.matches(ID_PATTERN)) { "Invalid id" }
        dispatch(ScouterReceiver.ACTION_CLOSE, null, id)
    }

    fun closeAll() {
        dispatch(ScouterReceiver.ACTION_CLOSE_ALL, null, null)
    }

    private fun dispatch(action: String, document: String?, id: String?) {
        applicationContext.sendBroadcast(
            Intent(action).apply {
                component = ComponentName(applicationContext, ScouterReceiver::class.java)
                document?.let { putExtra(ScouterReceiver.EXTRA_JSON, it) }
                id?.let { putExtra(ScouterReceiver.EXTRA_ID, it) }
            },
        )
    }

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z0-9._-]{1,80}")
    }
}
