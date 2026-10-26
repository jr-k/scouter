package app.scouter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.scouter.overlay.ScouterOverlayService

class ScouterBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        val shouldRun = ScouterNetworkConfig.settings(context).enabled ||
            ActiveDocumentStore(context).hasDocuments()
        if (shouldRun) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ScouterOverlayService::class.java),
            )
        }
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
