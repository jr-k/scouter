package app.scouter.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.KeyguardManager
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
import android.view.WindowManager
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
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
import app.scouter.ActiveDocumentStore
import app.scouter.PendingDocumentStore
import app.scouter.PullDownAction
import app.scouter.R
import app.scouter.ScouterReceiver
import app.scouter.ScouterPreferences
import app.scouter.SheetControlsSide
import app.scouter.model.ScouterAction
import app.scouter.model.ScouterDocument
import app.scouter.model.ScouterParser
import app.scouter.network.ScouterNetworkServer
import app.scouter.ui.ScouterOverlayHost
import java.util.concurrent.Executors

class ScouterOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var networkServer: ScouterNetworkServer
    private lateinit var activeStore: ActiveDocumentStore
    private val entries = mutableStateListOf<OverlayEntry>()
    private val selectedId = mutableStateOf<String?>(null)
    private val sheetVisible = mutableStateOf(true)
    private val sheetControlsSide = mutableStateOf(SheetControlsSide.Right)
    private var hostView: ComposeView? = null
    private var hostOwner: OverlayLifecycleOwner? = null
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val pendingDismissals = mutableMapOf<String, Runnable>()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        networkServer = ScouterNetworkServer(this)
        activeStore = ActiveDocumentStore(this)
        sheetControlsSide.value = ScouterPreferences.sheetControlsSide(this)
        startForeground(NOTIFICATION_ID, notification())
        networkExecutor.execute { networkServer.start() }
        restoreActiveDocuments()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ScouterReceiver.ACTION_SHOW -> show(
                raw = intent.getStringExtra(EXTRA_DOCUMENT),
                idOverride = intent.getStringExtra(EXTRA_ID),
            )
            ScouterReceiver.ACTION_CLOSE -> intent.getStringExtra(EXTRA_ID)?.let(::dismiss)
            ScouterReceiver.ACTION_CLOSE_ALL -> closeAll()
            ACTION_RESTART_NETWORK -> networkExecutor.execute { networkServer.restart() }
            ACTION_REFRESH_PREFERENCES ->
                sheetControlsSide.value = ScouterPreferences.sheetControlsSide(this)
        }
        return START_STICKY
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
        activeStore.put(document.id, source)

        val existing = entries.firstOrNull { it.document.value.id == document.id }
        if (existing != null) {
            pendingDismissals.remove(document.id)?.let(handler::removeCallbacks)
            existing.document.value = document
            existing.raw.value = source
            entries.remove(existing)
            entries += existing
            selectedId.value = document.id
            sheetVisible.value = true
            ensureOverlayHost()
            return
        }

        entries += OverlayEntry(
            document = mutableStateOf(document),
            raw = mutableStateOf(source),
        )
        selectedId.value = document.id
        sheetVisible.value = true
        ensureOverlayHost()
    }

    private fun ensureOverlayHost() {
        if (hostView != null) return
        val owner = OverlayLifecycleOwner()
        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                ScouterOverlayHost(
                    documents = entries.map { it.document.value },
                    selectedId = selectedId.value,
                    visible = sheetVisible.value,
                    controlsSide = sheetControlsSide.value,
                    onSelect = {
                        selectedId.value = it
                        sheetVisible.value = true
                    },
                    onClose = ::dismiss,
                    onMinimize = { id -> entries.firstOrNull { it.document.value.id == id }?.let(::minimize) },
                    onHandleDismiss = { id ->
                        val entry = entries.firstOrNull { it.document.value.id == id }
                        val presentation = entry?.document?.value?.presentation
                        when (ScouterPreferences.pullDownAction(this@ScouterOverlayService)) {
                            PullDownAction.Minimize -> if (presentation?.minimizable == true) {
                                minimize(entry)
                            } else {
                                false
                            }
                            PullDownAction.Close -> if (presentation?.dismissible == true) {
                                dismiss(id)
                                true
                            } else {
                                false
                            }
                        }
                    },
                    onMinimizeAll = ::minimizeAll,
                    onCloseAll = ::closeAll,
                    onAction = ::execute,
                )
            }
        }
        hostView = view
        hostOwner = owner
        owner.start()
        runCatching {
            windowManager.addView(view, layoutParams())
        }.onFailure { error ->
            Log.e(TAG, "Unable to attach the overlay host", error)
            hostView = null
            hostOwner = null
            runCatching { windowManager.removeView(view) }
            owner.destroy()
            if (error is SecurityException) openPermissionScreen()
        }
    }

    private fun minimize(entry: OverlayEntry): Boolean {
        val document = entry.document.value
        val store = PendingDocumentStore(this)
        store.put(document.id, entry.raw.value)
        if (!ScouterReceiver.postPendingNotification(
                context = this,
                document = document,
            )
        ) {
            store.remove(document.id)
            return false
        }
        dismiss(document.id)
        return true
    }

    private fun minimizeAll() {
        entries.toList().forEach { minimize(it) }
    }

    private fun closeAll() {
        activeStore.clear()
        pendingDismissals.values.forEach(handler::removeCallbacks)
        pendingDismissals.clear()
        if (entries.isEmpty()) return
        val closingEntries = entries.toList()
        sheetVisible.value = false
        handler.postDelayed({
            entries.removeAll(closingEntries)
            if (entries.isEmpty()) {
                selectedId.value = null
                detachOverlayHost()
            } else {
                selectedId.value = entries.last().document.value.id
                sheetVisible.value = true
            }
        }, EXIT_ANIMATION_MS)
    }

    private fun dismiss(id: String) {
        activeStore.remove(id)
        val entry = entries.firstOrNull { it.document.value.id == id } ?: return
        if (pendingDismissals.containsKey(id)) return
        val wasSelected = selectedId.value == id
        if (!wasSelected) {
            entries.remove(entry)
            return
        }
        sheetVisible.value = false
        val removal = Runnable {
            pendingDismissals.remove(id)
            val stillSelected = selectedId.value == id
            entries.remove(entry)
            if (stillSelected) selectedId.value = entries.lastOrNull()?.document?.value?.id
            if (entries.isEmpty()) {
                detachOverlayHost()
            } else if (stillSelected) {
                sheetVisible.value = true
            }
        }
        pendingDismissals[id] = removal
        handler.postDelayed(removal, EXIT_ANIMATION_MS)
    }

    private fun detachOverlayHost() {
        val view = hostView
        val owner = hostOwner
        hostView = null
        hostOwner = null
        if (view != null) runCatching { windowManager.removeView(view) }
        owner?.destroy()
    }

    private fun restoreActiveDocuments() {
        val stored = activeStore.documents()
        if (stored.isEmpty()) return
        val isLocked =
            (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
        if (!isLocked) {
            if (!Settings.canDrawOverlays(this)) {
                Log.w(TAG, "Active documents retained until overlay permission is granted")
                return
            }
            stored.forEach { (id, raw) ->
                val valid = runCatching { ScouterParser.parse(raw, id) }
                    .onFailure { Log.e(TAG, "Discarding invalid restored document $id", it) }
                    .isSuccess
                if (valid) {
                    show(raw, id)
                } else {
                    activeStore.remove(id)
                }
            }
            return
        }

        val pendingStore = PendingDocumentStore(this)
        stored.forEach { (id, raw) ->
            val document = runCatching { ScouterParser.parse(raw, id) }
                .onFailure { Log.e(TAG, "Unable to restore Scouter document $id", it) }
                .getOrNull()
                ?: run {
                    activeStore.remove(id)
                    return@forEach
                }
            pendingStore.put(id, raw)
            if (ScouterReceiver.postPendingNotification(this, document)) {
                activeStore.remove(id)
            } else {
                pendingStore.remove(id)
            }
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
                    completeExternalAction(screenId, action.closeAfter)
                }
            }
            is ScouterAction.Navigate -> {
                val coordinates = if (action.latitude != null && action.longitude != null) {
                    "${action.latitude},${action.longitude}"
                } else null
                val destination = coordinates ?: action.address ?: return
                val googleMapsIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("google.navigation:q=${Uri.encode(destination)}"),
                ).apply {
                    setPackage(GOOGLE_MAPS_PACKAGE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val fallbackIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:${coordinates ?: "0,0"}?q=${Uri.encode(destination)}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val launched = runCatching {
                    startActivity(googleMapsIntent)
                }.recoverCatching {
                    startActivity(
                        Intent.createChooser(
                            fallbackIntent,
                            action.chooserTitle ?: "Choose navigation app",
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.isSuccess
                if (launched) {
                    completeExternalAction(screenId, action.closeAfter)
                }
            }
            is ScouterAction.Dial -> {
                val dialIntent = Intent(
                    Intent.ACTION_DIAL,
                    Uri.fromParts("tel", action.phoneNumber, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { startActivity(dialIntent) }.onSuccess {
                    completeExternalAction(screenId, action.closeAfter)
                }
            }
            ScouterAction.None -> Unit
        }
    }

    private fun completeExternalAction(screenId: String, closeAfter: Boolean) {
        if (closeAfter) {
            dismiss(screenId)
        } else if (ScouterPreferences.minimizeOnAction(this)) {
            entries.firstOrNull { it.document.value.id == screenId }?.let(::minimize)
        }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        screenWidth(),
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
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_scouter_notification)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        networkExecutor.execute { networkServer.stop() }
        networkExecutor.shutdown()
        handler.removeCallbacksAndMessages(null)
        pendingDismissals.clear()
        hostView?.let { runCatching { windowManager.removeView(it) } }
        hostOwner?.destroy()
        hostView = null
        hostOwner = null
        entries.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private data class OverlayEntry(
        val document: MutableState<ScouterDocument>,
        val raw: MutableState<String>,
    )

    companion object {
        const val EXTRA_DOCUMENT = "document"
        const val EXTRA_ID = "id"
        const val ACTION_RESTART_NETWORK = "app.scouter.internal.RESTART_NETWORK"
        const val ACTION_REFRESH_PREFERENCES = "app.scouter.internal.REFRESH_PREFERENCES"
        private const val TAG = "ScouterOverlay"
        private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
        private const val CHANNEL_ID = "scouter_overlays"
        private const val NOTIFICATION_ID = 7301
        private const val EXIT_ANIMATION_MS = 190L
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
