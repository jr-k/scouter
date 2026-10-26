package app.scouter.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.scouter.MainActivity
import app.scouter.PendingDocumentStore
import app.scouter.R
import app.scouter.ScouterReceiver
import app.scouter.model.ScouterAction
import app.scouter.model.ScouterDocument
import app.scouter.model.ScouterParser
import app.scouter.network.ScouterNetworkServer
import app.scouter.ui.ScouterSheet

class ScouterOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var networkServer: ScouterNetworkServer
    private val entries = mutableListOf<OverlayEntry>()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        networkServer = ScouterNetworkServer(this)
        startForeground(NOTIFICATION_ID, notification())
        networkServer.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ScouterReceiver.ACTION_SHOW -> show(
                raw = intent.getStringExtra(EXTRA_DOCUMENT),
                idOverride = intent.getStringExtra(EXTRA_ID),
            )
            ScouterReceiver.ACTION_CLOSE -> intent.getStringExtra(EXTRA_ID)?.let(::dismiss)
            ScouterReceiver.ACTION_CLOSE_ALL -> entries.map { it.document.value.id }.forEach(::dismiss)
            ACTION_RESTART_NETWORK -> networkServer.restart()
        }
        return START_NOT_STICKY
    }

    private fun show(raw: String?, idOverride: String?) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission is missing")
            openPermissionScreen()
            stopSelf()
            return
        }
        val source = runCatching {
            requireNotNull(raw) { "Missing ${ScouterReceiver.EXTRA_JSON}" }
        }.onFailure { Log.e(TAG, "Invalid Scouter document: ${it.message}", it) }.getOrNull() ?: return
        val document = runCatching {
            ScouterParser.parse(source, idOverride)
        }.onFailure { Log.e(TAG, "Invalid Scouter document: ${it.message}", it) }.getOrNull() ?: return

        val existing = entries.firstOrNull { it.document.value.id == document.id }
        if (existing != null) {
            existing.document.value = document
            existing.raw.value = source
            entries.remove(existing)
            entries += existing
            updateStack()
            return
        }

        lateinit var entry: OverlayEntry
        val owner = OverlayLifecycleOwner()
        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                ScouterSheet(
                    document = entry.document.value,
                    depth = entry.depth.intValue,
                    visible = entry.visible.value,
                    onClose = { dismiss(entry.document.value.id) },
                    onMinimize = { minimize(entry) },
                    onAction = { elementId, action, value, event ->
                        execute(entry.document.value.id, elementId, action, value, event)
                    },
                )
            }
        }
        entry = OverlayEntry(
            document = mutableStateOf(document),
            raw = mutableStateOf(source),
            depth = mutableIntStateOf(0),
            visible = mutableStateOf(true),
            view = view,
            owner = owner,
            params = layoutParams(document),
        )
        entries += entry
        owner.start()
        windowManager.addView(view, entry.params)
        updateStack()
    }

    private fun minimize(entry: OverlayEntry) {
        val document = entry.document.value
        val store = PendingDocumentStore(this)
        store.put(document.id, entry.raw.value)
        if (!ScouterReceiver.postPendingNotification(
                context = this,
                document = document,
            )
        ) {
            store.remove(document.id)
            return
        }
        dismiss(document.id)
    }

    private fun dismiss(id: String) {
        val entry = entries.firstOrNull { it.document.value.id == id } ?: return
        if (!entry.visible.value) return
        entry.visible.value = false
        handler.postDelayed({
            if (entries.remove(entry)) {
                runCatching { windowManager.removeView(entry.view) }
                entry.owner.destroy()
                updateStack()
            }
        }, EXIT_ANIMATION_MS)
    }

    private fun updateStack() {
        entries.forEachIndexed { index, entry ->
            val depth = entries.lastIndex - index
            entry.depth.intValue = depth
            entry.params.y = dp(depth * entry.document.value.presentation.stackOffset)
            entry.params.width = screenWidth() - dp(entry.document.value.presentation.horizontalMargin * 2)
            runCatching { windowManager.updateViewLayout(entry.view, entry.params) }
        }
    }

    private fun execute(
        screenId: String,
        elementId: String,
        action: ScouterAction,
        value: String?,
        event: String,
    ) {
        when (action) {
            ScouterAction.Close -> dismiss(screenId)
            is ScouterAction.Broadcast -> {
                val intent = Intent(action.action).apply {
                    action.packageName?.let(::setPackage)
                    putExtra("scouter_id", screenId)
                    putExtra("element_id", elementId)
                    putExtra("event", event)
                    value?.let { putExtra("value", it) }
                    action.extras.forEach { (key, value) -> putExtra(key, value) }
                }
                sendBroadcast(intent)
                if (action.closeAfter) dismiss(screenId)
            }
            is ScouterAction.OpenUrl -> {
                val uri = runCatching { Uri.parse(action.url) }.getOrNull()
                if (uri?.scheme in setOf("https", "http")) {
                    startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    if (action.closeAfter) dismiss(screenId)
                }
            }
            is ScouterAction.Navigate -> {
                val location = if (action.latitude != null && action.longitude != null) {
                    "${action.latitude},${action.longitude}"
                } else {
                    "0,0"
                }
                val query = action.address ?: location
                val navigationIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:$location?q=${Uri.encode(query)}"),
                )
                val chooser = Intent.createChooser(
                    navigationIntent,
                    action.chooserTitle ?: "Choose navigation app",
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { startActivity(chooser) }.onSuccess {
                    if (action.closeAfter) dismiss(screenId)
                }
            }
            is ScouterAction.Dial -> {
                val dialIntent = Intent(
                    Intent.ACTION_DIAL,
                    Uri.fromParts("tel", action.phoneNumber, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { startActivity(dialIntent) }.onSuccess {
                    if (action.closeAfter) dismiss(screenId)
                }
            }
            ScouterAction.None -> Unit
        }
    }

    private fun layoutParams(document: ScouterDocument) = WindowManager.LayoutParams(
        screenWidth() - dp(document.presentation.horizontalMargin * 2),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        windowAnimations = 0
    }

    private fun screenWidth(): Int = resources.displayMetrics.widthPixels
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun openPermissionScreen() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        networkServer.stop()
        handler.removeCallbacksAndMessages(null)
        entries.toList().forEach {
            runCatching { windowManager.removeView(it.view) }
            it.owner.destroy()
        }
        entries.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private data class OverlayEntry(
        val document: MutableState<ScouterDocument>,
        val raw: MutableState<String>,
        val depth: androidx.compose.runtime.MutableIntState,
        val visible: MutableState<Boolean>,
        val view: ComposeView,
        val owner: OverlayLifecycleOwner,
        val params: WindowManager.LayoutParams,
    )

    companion object {
        const val EXTRA_DOCUMENT = "document"
        const val EXTRA_ID = "id"
        const val ACTION_RESTART_NETWORK = "app.scouter.internal.RESTART_NETWORK"
        private const val TAG = "ScouterOverlay"
        private const val CHANNEL_ID = "scouter_overlays"
        private const val NOTIFICATION_ID = 7301
        private const val EXIT_ANIMATION_MS = 260L
    }
}

private class OverlayLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    init {
        controller.performAttach()
        controller.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun start() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
