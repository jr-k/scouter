package app.scouter

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import app.scouter.model.ScouterParser
import app.scouter.model.notificationMessage
import app.scouter.model.notificationTitle
import app.scouter.overlay.ScouterOverlayService

class ScouterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = PendingDocumentStore(context)
        when (intent.action) {
            ACTION_SHOW -> {
                val raw = intent.getStringExtra(EXTRA_JSON)
                val document = runCatching {
                    ScouterParser.parse(requireNotNull(raw) { "Missing $EXTRA_JSON" }, intent.getStringExtra(EXTRA_ID))
                }.onFailure {
                    Log.e(TAG, "Rejected Scouter document: ${it.message}", it)
                }.getOrNull() ?: return

                if (isLocked(context)) {
                    store.put(document.id, raw!!)
                    postPendingNotification(context, document)
                } else {
                    store.remove(document.id)
                    cancelNotification(context, document.id)
                    dispatchService(context, ACTION_SHOW, raw, document.id)
                }
            }
            ACTION_CLOSE -> {
                intent.getStringExtra(EXTRA_ID)?.let {
                    store.remove(it)
                    cancelNotification(context, it)
                }
                dispatchService(context, ACTION_CLOSE, null, intent.getStringExtra(EXTRA_ID))
            }
            ACTION_CLOSE_ALL -> {
                store.clear().forEach { cancelNotification(context, it) }
                dispatchService(context, ACTION_CLOSE_ALL, null, null)
            }
            ACTION_OPEN_PENDING -> {
                val id = intent.getStringExtra(EXTRA_ID) ?: return
                val raw = store.get(id) ?: return
                store.remove(id)
                cancelNotification(context, id)
                dispatchService(context, ACTION_SHOW, raw, id)
            }
        }
    }

    private fun dispatchService(context: Context, action: String, document: String?, id: String?) {
        val command = Intent(context, ScouterOverlayService::class.java).apply {
            this.action = action
            putExtra(ScouterOverlayService.EXTRA_DOCUMENT, document)
            putExtra(ScouterOverlayService.EXTRA_ID, id)
        }
        runCatching { context.startForegroundService(command) }
            .onFailure { Log.e(TAG, "Unable to dispatch Scouter command", it) }
    }

    private fun isLocked(context: Context): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked

    companion object {
        const val ACTION_SHOW = "app.scouter.SHOW"
        const val ACTION_CLOSE = "app.scouter.CLOSE"
        const val ACTION_CLOSE_ALL = "app.scouter.CLOSE_ALL"
        const val ACTION_OPEN_PENDING = "app.scouter.OPEN_PENDING"
        const val EXTRA_JSON = "scouter_json"
        const val EXTRA_ID = "scouter_id"
        private const val PENDING_CHANNEL_ID = "scouter_pending"
        private const val TAG = "ScouterReceiver"

        fun postPendingNotification(
            context: Context,
            document: app.scouter.model.ScouterDocument,
        ): Boolean {
            val id = document.id
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Document $id was queued, but notification permission is missing")
                return false
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    PENDING_CHANNEL_ID,
                    context.getString(R.string.pending_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
            val openIntent = Intent(context, ScouterReceiver::class.java).apply {
                action = ACTION_OPEN_PENDING
                data = Uri.parse("scouter://pending/${Uri.encode(id)}")
                putExtra(EXTRA_ID, id)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = Notification.Builder(context, PENDING_CHANNEL_ID)
                .setSmallIcon(notificationIcon(document.notification.icon))
                .setContentTitle(document.notificationTitle())
                .setContentText(
                    document.notificationMessage() ?: context.getString(R.string.pending_notification_text),
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .build()
            manager.notify(notificationId(id), notification)
            return true
        }

        fun cancelNotification(context: Context, id: String) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notificationId(id))
        }

        private fun notificationId(id: String): Int = 0x53000000 xor id.hashCode()

        private fun notificationIcon(icon: String): Int = when (icon) {
            "info" -> android.R.drawable.ic_dialog_info
            "warning" -> android.R.drawable.ic_dialog_alert
            "message" -> android.R.drawable.ic_dialog_email
            "navigation" -> android.R.drawable.ic_menu_mylocation
            "phone" -> android.R.drawable.ic_menu_call
            "task" -> android.R.drawable.checkbox_on_background
            else -> R.drawable.ic_scouter
        }
    }
}
