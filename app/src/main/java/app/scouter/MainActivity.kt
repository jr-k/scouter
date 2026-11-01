package app.scouter

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import app.scouter.overlay.ScouterOverlayService
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var overlayGranted by mutableStateOf(false)
    private var notificationsGranted by mutableStateOf(false)
    private var batteryExempt by mutableStateOf(false)
    private var currentTab by mutableStateOf(AppTab.Home)
    private var historyEntries by mutableStateOf(emptyList<ScouterHistoryEntry>())
    private var networkAddress by mutableStateOf("")
    private var networkToken by mutableStateOf("")
    private var pullDownAction by mutableStateOf(PullDownAction.Minimize)
    private var outsideTapAction by mutableStateOf(OutsideTapAction.Minimize)
    private var launchAtBoot by mutableStateOf(true)
    private var minimizeOnAction by mutableStateOf(false)
    private var sheetControlsSide by mutableStateOf(SheetControlsSide.Right)
    private var networkSettings by mutableStateOf(
        ScouterNetworkConfig.NetworkSettings(
            enabled = true,
            bindHost = ScouterNetworkConfig.DEFAULT_BIND_HOST,
            port = ScouterNetworkConfig.DEFAULT_PORT,
        ),
    )
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshPermissionState() }
    private val historyChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshHistory()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        networkSettings = ScouterNetworkConfig.settings(this)
        networkAddress = ScouterNetworkConfig.localAddress(this)
        networkToken = ScouterNetworkConfig.token(this)
        pullDownAction = ScouterPreferences.pullDownAction(this)
        outsideTapAction = ScouterPreferences.outsideTapAction(this)
        launchAtBoot = ScouterPreferences.launchAtBoot(this)
        minimizeOnAction = ScouterPreferences.minimizeOnAction(this)
        sheetControlsSide = ScouterPreferences.sheetControlsSide(this)
        refreshHistory()
        ContextCompat.registerReceiver(
            this,
            historyChangedReceiver,
            IntentFilter(ScouterHistoryStore.ACTION_HISTORY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        startForegroundService(Intent(this, ScouterOverlayService::class.java))
        setContent {
            ScouterApp(
                currentTab = currentTab,
                onTabSelected = {
                    currentTab = it
                    if (it == AppTab.Home) refreshHistory()
                },
                history = historyEntries,
                overlayGranted = overlayGranted,
                notificationsGranted = notificationsGranted,
                batteryExempt = batteryExempt,
                networkAddress = networkAddress,
                networkToken = networkToken,
                networkSettings = networkSettings,
                pullDownAction = pullDownAction,
                outsideTapAction = outsideTapAction,
                launchAtBoot = launchAtBoot,
                minimizeOnAction = minimizeOnAction,
                sheetControlsSide = sheetControlsSide,
                requestOverlay = ::requestOverlay,
                requestNotifications = ::requestNotifications,
                requestBatteryExemption = ::requestBatteryExemption,
                openAppSettings = ::openAppSettings,
                onSaveNetwork = ::saveNetworkSettings,
                onRegenerateToken = ::regenerateNetworkToken,
                onPullDownActionChange = ::savePullDownAction,
                onOutsideTapActionChange = ::saveOutsideTapAction,
                onLaunchAtBootChange = ::saveLaunchAtBoot,
                onMinimizeOnActionChange = ::saveMinimizeOnAction,
                onSheetControlsSideChange = ::saveSheetControlsSide,
                reopenHistory = ::reopenHistory,
                clearHistory = ::clearHistory,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        refreshHistory()
        networkAddress = ScouterNetworkConfig.localAddress(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            !notificationsGranted
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(historyChangedReceiver)
        super.onDestroy()
    }

    private fun requestOverlay() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        }
    }

    private fun requestBatteryExemption() {
        val intent = if (batteryExempt) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName"),
            )
        }
        startActivity(intent)
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun saveNetworkSettings(enabled: Boolean, bindHost: String, port: String): String? =
        runCatching {
            val parsedPort = port.toIntOrNull() ?: error("Enter a valid numeric port")
            networkSettings = ScouterNetworkConfig.save(this, enabled, bindHost, parsedPort)
            networkAddress = ScouterNetworkConfig.localAddress(this)
            startForegroundService(
                Intent(this, ScouterOverlayService::class.java).apply {
                    action = ScouterOverlayService.ACTION_RESTART_NETWORK
                },
            )
        }.exceptionOrNull()?.message

    private fun regenerateNetworkToken() {
        networkToken = ScouterNetworkConfig.regenerateToken(this)
    }

    private fun savePullDownAction(action: PullDownAction) {
        ScouterPreferences.setPullDownAction(this, action)
        pullDownAction = action
        refreshOverlayPreferences()
    }

    private fun saveOutsideTapAction(action: OutsideTapAction) {
        ScouterPreferences.setOutsideTapAction(this, action)
        outsideTapAction = action
        refreshOverlayPreferences()
    }

    private fun saveLaunchAtBoot(enabled: Boolean) {
        ScouterPreferences.setLaunchAtBoot(this, enabled)
        launchAtBoot = enabled
    }

    private fun saveMinimizeOnAction(enabled: Boolean) {
        ScouterPreferences.setMinimizeOnAction(this, enabled)
        minimizeOnAction = enabled
    }

    private fun saveSheetControlsSide(side: SheetControlsSide) {
        ScouterPreferences.setSheetControlsSide(this, side)
        sheetControlsSide = side
        refreshOverlayPreferences()
    }

    private fun refreshOverlayPreferences() {
        startForegroundService(
            Intent(this, ScouterOverlayService::class.java).apply {
                action = ScouterOverlayService.ACTION_REFRESH_PREFERENCES
            },
        )
    }

    private fun refreshPermissionState() {
        overlayGranted = Settings.canDrawOverlays(this)
        notificationsGranted = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        batteryExempt = (getSystemService(POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(packageName)
    }

    private fun reopenHistory(entry: ScouterHistoryEntry) {
        startForegroundService(
            Intent(this, ScouterOverlayService::class.java).apply {
                action = ScouterReceiver.ACTION_SHOW
                putExtra(ScouterOverlayService.EXTRA_DOCUMENT, entry.documentJson)
                putExtra(ScouterOverlayService.EXTRA_ID, entry.documentId)
            },
        )
    }

    private fun clearHistory() {
        ScouterHistoryStore(this).clear()
        refreshHistory()
    }

    private fun refreshHistory() {
        historyEntries = ScouterHistoryStore(this).entries()
    }

}

private enum class AppTab(val label: String) {
    Home("Home"),
    Connect("Connect"),
    Preferences("Preferences"),
    Permissions("Access"),
}

private data class HistoryDay(
    val timestamp: Long,
    val entries: List<ScouterHistoryEntry>,
)

@Composable
private fun ScouterApp(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    history: List<ScouterHistoryEntry>,
    overlayGranted: Boolean,
    networkAddress: String,
    networkToken: String,
    networkSettings: ScouterNetworkConfig.NetworkSettings,
    pullDownAction: PullDownAction,
    outsideTapAction: OutsideTapAction,
    launchAtBoot: Boolean,
    minimizeOnAction: Boolean,
    sheetControlsSide: SheetControlsSide,
    notificationsGranted: Boolean,
    batteryExempt: Boolean,
    requestOverlay: () -> Unit,
    requestNotifications: () -> Unit,
    requestBatteryExemption: () -> Unit,
    openAppSettings: () -> Unit,
    onSaveNetwork: (Boolean, String, String) -> String?,
    onRegenerateToken: () -> Unit,
    onPullDownActionChange: (PullDownAction) -> Unit,
    onOutsideTapActionChange: (OutsideTapAction) -> Unit,
    onLaunchAtBootChange: (Boolean) -> Unit,
    onMinimizeOnActionChange: (Boolean) -> Unit,
    onSheetControlsSideChange: (SheetControlsSide) -> Unit,
    reopenHistory: (ScouterHistoryEntry) -> Unit,
    clearHistory: () -> Unit,
) {
    MaterialTheme {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                ScouterBottomBar(currentTab, onTabSelected)
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (currentTab) {
                    AppTab.Home -> HomeScreen(
                        history = history,
                        reopenHistory = reopenHistory,
                        clearHistory = clearHistory,
                    )
                    AppTab.Connect, AppTab.Preferences -> ConfigurationScreen(
                        page = currentTab,
                        settings = networkSettings,
                        address = networkAddress,
                        token = networkToken,
                        pullDownAction = pullDownAction,
                        outsideTapAction = outsideTapAction,
                        launchAtBoot = launchAtBoot,
                        minimizeOnAction = minimizeOnAction,
                        sheetControlsSide = sheetControlsSide,
                        onBack = { onTabSelected(AppTab.Home) },
                        onSaveNetwork = onSaveNetwork,
                        onRegenerateToken = onRegenerateToken,
                        onPullDownActionChange = onPullDownActionChange,
                        onOutsideTapActionChange = onOutsideTapActionChange,
                        onLaunchAtBootChange = onLaunchAtBootChange,
                        onMinimizeOnActionChange = onMinimizeOnActionChange,
                        onSheetControlsSideChange = onSheetControlsSideChange,
                    )
                    AppTab.Permissions -> PermissionsScreen(
                        overlayGranted = overlayGranted,
                        notificationsGranted = notificationsGranted,
                        batteryExempt = batteryExempt,
                        onBack = { onTabSelected(AppTab.Home) },
                        requestOverlay = requestOverlay,
                        requestNotifications = requestNotifications,
                        requestBatteryExemption = requestBatteryExemption,
                        openAppSettings = openAppSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScouterBottomBar(currentTab: AppTab, onTabSelected: (AppTab) -> Unit) {
    NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
        AppTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = when (tab) {
                            AppTab.Home -> Icons.Filled.Home
                            AppTab.Connect -> Icons.Filled.Link
                            AppTab.Preferences -> Icons.Filled.Settings
                            AppTab.Permissions -> Icons.Filled.Security
                        },
                        contentDescription = null,
                    )
                },
                label = { Text(tab.label) },
                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    selectedIconColor = Ink,
                    selectedTextColor = Acid,
                    indicatorColor = Acid,
                    unselectedIconColor = Sage,
                    unselectedTextColor = Sage,
                ),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
    history: List<ScouterHistoryEntry>,
    reopenHistory: (ScouterHistoryEntry) -> Unit,
    clearHistory: () -> Unit,
) {
    var showClearConfirmation by remember { mutableStateOf(false) }
    val historyDays = remember(history) {
        history
            .groupBy { entry ->
                Calendar.getInstance().apply {
                    timeInMillis = entry.receivedAt
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            .map { (timestamp, entries) -> HistoryDay(timestamp, entries) }
            .sortedByDescending(HistoryDay::timestamp)
    }
    Box(Modifier.fillMaxSize()) {
        Radar(Modifier.align(Alignment.TopEnd).size(220.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 22.dp,
                top = 28.dp,
                end = 22.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Scouter Cards",
                    color = Frost,
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "Recent cards stay here, ready to reopen.",
                    color = Sage,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(26.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("History", color = Frost, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (history.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearConfirmation = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = Sage),
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }
            if (history.isEmpty()) {
                item { EmptyHistoryCard() }
            } else {
                historyDays.forEach { day ->
                    stickyHeader(key = "day-${day.timestamp}") {
                        HistoryDayHeader(day.timestamp)
                    }
                    items(day.entries, key = { it.historyId }) { entry ->
                        HistoryCard(entry, onClick = { reopenHistory(entry) })
                    }
                }
            }
        }
    }
    if (showClearConfirmation) {
        ClearHistoryDialog(
            itemCount = history.size,
            onDismiss = { showClearConfirmation = false },
            onConfirm = {
                showClearConfirmation = false
                clearHistory()
            },
        )
    }
}

@Composable
private fun ClearHistoryDialog(
    itemCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Panel,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(22.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Ember.copy(alpha = .12f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = Ember,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Clear history?",
                    color = Frost,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "$itemCount ${if (itemCount == 1) "card" else "cards"} will be removed from this device. Active overlays won’t be closed.",
                    color = Sage,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = Ink,
                            contentColor = Frost,
                        ),
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Ember,
                            contentColor = Ink,
                        ),
                    ) {
                        Text("Clear", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryDayHeader(timestamp: Long) {
    val locale = Locale.getDefault()
    val date = Date(timestamp)
    val day = SimpleDateFormat("EEEE d MMMM", locale)
        .format(date)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    val year = SimpleDateFormat("yyyy", locale).format(date)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink)
            .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            day,
            color = Frost,
            fontSize = 23.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            year,
            color = Sage,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 3.dp),
        )
    }
}

@Composable
private fun HistoryCard(entry: ScouterHistoryEntry, onClick: () -> Unit) {
    val accent = runCatching { Color(android.graphics.Color.parseColor(entry.accent)) }.getOrDefault(Acid)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(18.dp))
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            entry.iconName?.historyIcon()?.let { icon ->
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(accent.copy(alpha = .12f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(entry.title, color = Frost, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                entry.summary?.takeIf { it != entry.title }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = Sage, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2)
                }
            }
        }
        Text(
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.receivedAt)),
            color = Sage.copy(alpha = .72f),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.End).padding(top = 5.dp, end = 4.dp),
        )
    }
}

private fun String.historyIcon(): ImageVector? = when (this) {
    "calendar", "time" -> Icons.Filled.CalendarToday
    "call" -> Icons.Filled.Call
    "directions", "navigation" -> Icons.Filled.Directions
    "home" -> Icons.Filled.Home
    "info" -> Icons.Filled.Info
    "link", "open" -> Icons.Filled.Link
    "list" -> Icons.Filled.List
    "location" -> Icons.Filled.LocationOn
    "lock", "unlock" -> Icons.Filled.Lock
    "message", "notification" -> Icons.Filled.Message
    "phone" -> Icons.Filled.Phone
    "play", "pause" -> Icons.Filled.PlayArrow
    "settings" -> Icons.Filled.Settings
    "star", "favorite" -> Icons.Filled.Star
    "task", "check" -> Icons.Filled.TaskAlt
    "warning" -> Icons.Filled.Warning
    else -> null
}

@Composable
private fun EmptyHistoryCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(18.dp))
            .padding(20.dp),
    ) {
        Text("No cards yet", color = Frost, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(
            "Cards received through intents, REST, or MCP will appear here.",
            color = Sage,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
    }
}

@Composable
private fun ConfigurationScreen(
    page: AppTab,
    settings: ScouterNetworkConfig.NetworkSettings,
    address: String,
    token: String,
    pullDownAction: PullDownAction,
    outsideTapAction: OutsideTapAction,
    launchAtBoot: Boolean,
    minimizeOnAction: Boolean,
    sheetControlsSide: SheetControlsSide,
    onBack: () -> Unit,
    onSaveNetwork: (Boolean, String, String) -> String?,
    onRegenerateToken: () -> Unit,
    onPullDownActionChange: (PullDownAction) -> Unit,
    onOutsideTapActionChange: (OutsideTapAction) -> Unit,
    onLaunchAtBootChange: (Boolean) -> Unit,
    onMinimizeOnActionChange: (Boolean) -> Unit,
    onSheetControlsSideChange: (SheetControlsSide) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var enabled by remember(settings) { mutableStateOf(settings.enabled) }
    var bindHost by remember(settings) { mutableStateOf(settings.bindHost) }
    var port by remember(settings) { mutableStateOf(settings.port.toString()) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var confirmTokenReset by remember { mutableStateOf(false) }
    var tokenVisible by remember { mutableStateOf(false) }
    MaterialTheme {
        Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
            ) {
                Text(
                    if (page == AppTab.Connect) "Connect" else "Preferences",
                    color = Frost,
                    fontSize = 36.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(28.dp))
                if (page == AppTab.Connect) {
                    Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(20.dp))
                        .padding(18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Network server", color = Frost, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (enabled) "Accepting authenticated requests" else "Listener disabled",
                                color = Sage,
                                fontSize = 13.sp,
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Ink,
                                checkedTrackColor = Acid,
                                uncheckedThumbColor = Sage,
                                uncheckedTrackColor = Ink,
                            ),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    ScouterTextField(
                        value = bindHost,
                        onValueChange = { bindHost = it },
                        label = "Bind host",
                        supportingText = "0.0.0.0 exposes the bridge on every network interface. Use 127.0.0.1 for device-only access.",
                    )
                    Spacer(Modifier.height(12.dp))
                    ScouterTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        label = "Listen port",
                        supportingText = "Allowed range: 1024–65535",
                        keyboardType = KeyboardType.Number,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Advertised address", color = Sage, fontSize = 12.sp)
                    Text(address, color = Frost, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("MCP endpoint", color = Sage, fontSize = 12.sp)
                    Text("$address/mcp", color = Frost, fontSize = 14.sp)
                    TextButton(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Scouter MCP endpoint", "$address/mcp"),
                            )
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Acid),
                    ) {
                        Text("Copy MCP endpoint")
                    }
                    feedback?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            it,
                            color = if (it == "Network server restarted") Acid else Ember,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            feedback = onSaveNetwork(enabled, bindHost, port)
                                ?: "Network server restarted"
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Acid, contentColor = Ink),
                    ) {
                        Text("Save and restart", fontWeight = FontWeight.Bold)
                    }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (page == AppTab.Preferences) {
                    Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(20.dp))
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Launch at boot",
                            color = Frost,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Start the background service after a restart to restore cards and accept requests.",
                            color = Sage,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                    }
                    Switch(
                        checked = launchAtBoot,
                        onCheckedChange = onLaunchAtBootChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Ink,
                            checkedTrackColor = Acid,
                            uncheckedThumbColor = Sage,
                            uncheckedTrackColor = Ink,
                        ),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(20.dp))
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Minimize on action",
                            color = Frost,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Minimize the card after opening navigation, a link, or the dialer.",
                            color = Sage,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                    }
                    Switch(
                        checked = minimizeOnAction,
                        onCheckedChange = onMinimizeOnActionChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Ink,
                            checkedTrackColor = Acid,
                            uncheckedThumbColor = Sage,
                            uncheckedTrackColor = Ink,
                        ),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(20.dp))
                        .padding(18.dp),
                ) {
                    Text(
                        "Tap outside card",
                        color = Frost,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Choose what happens when the darkened area around the active card is tapped.",
                        color = Sage,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutsideTapAction.entries.forEach { action ->
                            val selected = action == outsideTapAction
                            TextButton(
                                onClick = { onOutsideTapActionChange(action) },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (selected) Acid else Ink,
                                    contentColor = if (selected) Ink else Frost,
                                ),
                            ) {
                                Text(
                                    when (action) {
                                        OutsideTapAction.Minimize -> "Minimize"
                                        OutsideTapAction.Close -> "Close"
                                        OutsideTapAction.Nothing -> "Ignore"
                                    },
                                    maxLines = 1,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(20.dp))
                        .padding(18.dp),
                ) {
                    Text(
                        "Pull-down gesture",
                        color = Frost,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Choose what happens when the grey sheet handle is dragged down.",
                        color = Sage,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PullDownAction.entries.forEach { action ->
                            val selected = action == pullDownAction
                            TextButton(
                                onClick = { onPullDownActionChange(action) },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (selected) Acid else Ink,
                                    contentColor = if (selected) Ink else Frost,
                                ),
                            ) {
                                Text(
                                    when (action) {
                                        PullDownAction.Minimize -> "Minimize"
                                        PullDownAction.Close -> "Close"
                                    },
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                }
                if (page == AppTab.Connect) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Panel, RoundedCornerShape(20.dp))
                            .padding(18.dp),
                    ) {
                        Text("Access token", color = Frost, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (tokenVisible) token else "${token.take(8)}…${token.takeLast(4)}",
                        color = if (tokenVisible) Frost else Sage,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Row {
                        TextButton(
                            onClick = { tokenVisible = !tokenVisible },
                            colors = ButtonDefaults.textButtonColors(contentColor = Acid),
                        ) {
                            Text(if (tokenVisible) "Hide token" else "Show token")
                        }
                        TextButton(
                            onClick = {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("Scouter access token", token),
                                )
                                feedback = "Access token copied"
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Acid),
                        ) {
                            Text("Copy token")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Regenerating the token immediately disconnects every configured desktop client.",
                        color = Sage,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    TextButton(
                        onClick = {
                            if (confirmTokenReset) {
                                onRegenerateToken()
                                confirmTokenReset = false
                                tokenVisible = false
                                feedback = "Access token regenerated"
                            } else {
                                confirmTokenReset = true
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (confirmTokenReset) Ember else Acid,
                        ),
                    ) {
                        Text(if (confirmTokenReset) "Confirm token regeneration" else "Regenerate token")
                    }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (page == AppTab.Preferences) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Panel, RoundedCornerShape(20.dp))
                            .padding(18.dp),
                    ) {
                        Text("Renderer", color = Frost, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text("Protocol version 1 · 256 KiB payload limit", color = Sage, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (BuildConfig.GOOGLE_MAPS_CONFIGURED) {
                            "Google Maps SDK configured"
                        } else {
                            "Google Maps API key missing at build time"
                        },
                        color = if (BuildConfig.GOOGLE_MAPS_CONFIGURED) Acid else Ember,
                        fontSize = 13.sp,
                    )
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Panel, RoundedCornerShape(20.dp))
                            .padding(18.dp),
                    ) {
                        Text(
                            "Control layout",
                            color = Frost,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Place the Close action on your preferred side.",
                            color = Sage,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SheetControlsSide.entries.forEach { side ->
                                val selected = side == sheetControlsSide
                                TextButton(
                                    onClick = { onSheetControlsSideChange(side) },
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = if (selected) Acid else Ink,
                                        contentColor = if (selected) Ink else Frost,
                                    ),
                                ) {
                                    Text(
                                        when (side) {
                                            SheetControlsSide.Left -> "Left-handed"
                                            SheetControlsSide.Right -> "Right-handed"
                                        },
                                        maxLines = 1,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ScouterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = { Text(supportingText) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Frost,
            unfocusedTextColor = Frost,
            focusedBorderColor = Acid,
            unfocusedBorderColor = Sage.copy(alpha = .45f),
            focusedLabelColor = Acid,
            unfocusedLabelColor = Sage,
            cursorColor = Acid,
            focusedSupportingTextColor = Sage,
            unfocusedSupportingTextColor = Sage,
        ),
    )
}

@Composable
private fun PermissionsScreen(
    overlayGranted: Boolean,
    notificationsGranted: Boolean,
    batteryExempt: Boolean,
    onBack: () -> Unit,
    requestOverlay: () -> Unit,
    requestNotifications: () -> Unit,
    requestBatteryExemption: () -> Unit,
    openAppSettings: () -> Unit,
) {
    BackHandler(onBack = onBack)
    MaterialTheme {
        Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
            ) {
                Text(
                    "Permissions",
                    color = Frost,
                    fontSize = 36.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Android keeps these controls under your authority. Scouter can check their status and take you to the official system prompt.",
                    color = Sage,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.height(28.dp))
                PermissionCard(
                    title = "Display over other apps",
                    description = "Required to render bottom sheets above the current application.",
                    granted = overlayGranted,
                    button = if (overlayGranted) "Review" else "Allow",
                    onClick = requestOverlay,
                )
                Spacer(Modifier.height(12.dp))
                PermissionCard(
                    title = "Notifications",
                    description = "Required for pending views received while the phone is locked.",
                    granted = notificationsGranted,
                    button = if (notificationsGranted) "Review" else "Allow",
                    onClick = requestNotifications,
                )
                Spacer(Modifier.height(12.dp))
                PermissionCard(
                    title = "Unrestricted battery",
                    description = "Recommended so the local network bridge remains reachable in the background.",
                    granted = batteryExempt,
                    button = if (batteryExempt) "Review" else "Request",
                    onClick = requestBatteryExemption,
                )
                Spacer(Modifier.height(12.dp))
                PermissionCard(
                    title = "Foreground service",
                    description = "Declared in the application manifest and active while the network bridge is running.",
                    granted = true,
                    button = null,
                    onClick = null,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = openAppSettings,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Frost),
                ) {
                    Text("Open all app settings", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    button: String?,
    onClick: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(if (granted) Acid else Ember, RoundedCornerShape(50)),
            )
            Text(
                title,
                color = Frost,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (granted) "Allowed" else "Action needed",
                color = if (granted) Acid else Ember,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(description, color = Sage, fontSize = 14.sp, lineHeight = 20.sp)
        if (button != null && onClick != null) {
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = onClick,
                colors = ButtonDefaults.textButtonColors(contentColor = Acid),
            ) {
                Text(button, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatusCard(granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(if (granted) Acid else Ember, RoundedCornerShape(50)),
        )
        Column {
            Text(
                if (granted) "Ready" else "Overlay access required",
                color = Frost,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (granted) "Overlay receiver is armed" else "Grant “display over other apps”",
                color = Sage,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun NetworkCard(address: String, token: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text("Network bridge", color = Frost, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(address, color = Sage, fontSize = 13.sp)
        Text("Token ${token.take(8)}…", color = Sage, fontSize = 13.sp)
        TextButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("Scouter connection", "$address\n$token"),
                )
            },
            colors = ButtonDefaults.textButtonColors(contentColor = Acid),
        ) {
            Text("Copy connection details")
        }
    }
}

@Composable
private fun Radar(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width * .72f, size.height * .28f)
        repeat(4) { index ->
            drawCircle(
                color = Acid.copy(alpha = .11f - index * .018f),
                radius = 42f + index * 43f,
                center = center,
                style = Stroke(width = 2f),
            )
        }
        drawLine(
            color = Acid.copy(alpha = .2f),
            start = Offset(center.x, 0f),
            end = Offset(center.x, size.height),
            strokeWidth = 2f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Acid.copy(alpha = .2f),
            start = Offset(0f, center.y),
            end = Offset(size.width, center.y),
            strokeWidth = 2f,
            cap = StrokeCap.Round,
        )
        drawCircle(Acid, 5f, center)
    }
}

private val Ink = Color(0xFF07100D)
private val Panel = Color(0xFF121D18)
private val Frost = Color(0xFFF1F8F3)
private val Sage = Color(0xFF9EB0A5)
private val Acid = Color(0xFF71C56E)
private val Ember = Color(0xFFFF765E)
