package app.scouter

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.scouter.overlay.ScouterOverlayService

class MainActivity : ComponentActivity() {
    private var overlayGranted by mutableStateOf(false)
    private var notificationsGranted by mutableStateOf(false)
    private var batteryExempt by mutableStateOf(false)
    private var showPermissions by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var networkAddress by mutableStateOf("")
    private var networkToken by mutableStateOf("")
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        networkSettings = ScouterNetworkConfig.settings(this)
        networkAddress = ScouterNetworkConfig.localAddress(this)
        networkToken = ScouterNetworkConfig.token(this)
        startForegroundService(Intent(this, ScouterOverlayService::class.java))
        setContent {
            if (showSettings) {
                SettingsScreen(
                    settings = networkSettings,
                    address = networkAddress,
                    token = networkToken,
                    onBack = { showSettings = false },
                    onSaveNetwork = ::saveNetworkSettings,
                    onRegenerateToken = ::regenerateNetworkToken,
                )
            } else if (showPermissions) {
                PermissionsScreen(
                    overlayGranted = overlayGranted,
                    notificationsGranted = notificationsGranted,
                    batteryExempt = batteryExempt,
                    onBack = { showPermissions = false },
                    requestOverlay = ::requestOverlay,
                    requestNotifications = ::requestNotifications,
                    requestBatteryExemption = ::requestBatteryExemption,
                    openAppSettings = ::openAppSettings,
                )
            } else {
                ScouterHome(
                    overlayGranted = overlayGranted,
                    networkAddress = networkAddress,
                    networkToken = networkToken,
                    requestOverlay = ::requestOverlay,
                    showPermissions = { showPermissions = true },
                    showSettings = { showSettings = true },
                    showDemo = ::showDemo,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        networkAddress = ScouterNetworkConfig.localAddress(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            !notificationsGranted
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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

    private fun refreshPermissionState() {
        overlayGranted = Settings.canDrawOverlays(this)
        notificationsGranted = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        batteryExempt = (getSystemService(POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(packageName)
    }

    private fun showDemo() {
        sendBroadcast(
            Intent(ScouterReceiver.ACTION_SHOW).apply {
                component = ComponentName(this@MainActivity, ScouterReceiver::class.java)
                putExtra(ScouterReceiver.EXTRA_JSON, DEMO_DOCUMENT)
            },
        )
    }

}

@Composable
private fun ScouterHome(
    overlayGranted: Boolean,
    networkAddress: String,
    networkToken: String,
    requestOverlay: () -> Unit,
    showPermissions: () -> Unit,
    showSettings: () -> Unit,
    showDemo: () -> Unit,
) {
    val context = LocalContext.current
    MaterialTheme {
        Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Radar(Modifier.align(Alignment.TopEnd).size(280.dp))
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 36.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Scouter",
                                color = Acid,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Row {
                                TextButton(
                                    onClick = showSettings,
                                    colors = ButtonDefaults.textButtonColors(contentColor = Frost),
                                ) {
                                    Text("Settings")
                                }
                                TextButton(
                                    onClick = showPermissions,
                                    colors = ButtonDefaults.textButtonColors(contentColor = Frost),
                                ) {
                                    Text("Permissions")
                                }
                            }
                        }
                        Spacer(Modifier.height(40.dp))
                        Text(
                            "Information,\nright on target.",
                            color = Frost,
                            fontSize = 43.sp,
                            lineHeight = 46.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(
                            "Compose native Android overlays from a single JSON intent. No screen templates. No rebuild.",
                            color = Sage,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.fillMaxWidth(.88f),
                        )
                    }

                    Column {
                        StatusCard(overlayGranted)
                        Spacer(Modifier.height(10.dp))
                        NetworkCard(networkAddress, networkToken)
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = if (overlayGranted) showDemo else requestOverlay,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Acid,
                                contentColor = Ink,
                            ),
                        ) {
                            Text(
                                if (overlayGranted) "Launch demo overlay" else "Allow overlay access",
                                fontWeight = FontWeight.Black,
                            )
                        }
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Scouter JSON", DEMO_DOCUMENT))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = Sage),
                        ) {
                            Text("Copy demo JSON")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: ScouterNetworkConfig.NetworkSettings,
    address: String,
    token: String,
    onBack: () -> Unit,
    onSaveNetwork: (Boolean, String, String) -> String?,
    onRegenerateToken: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var enabled by remember(settings) { mutableStateOf(settings.enabled) }
    var bindHost by remember(settings) { mutableStateOf(settings.bindHost) }
    var port by remember(settings) { mutableStateOf(settings.port.toString()) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var confirmTokenReset by remember { mutableStateOf(false) }
    MaterialTheme {
        Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
            ) {
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(contentColor = Sage),
                ) {
                    Text("Back")
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Settings",
                    color = Frost,
                    fontSize = 36.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(28.dp))
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(20.dp))
                        .padding(18.dp),
                ) {
                    Text("Access token", color = Frost, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text("${token.take(8)}…${token.takeLast(4)}", color = Sage, fontSize = 14.sp)
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
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(contentColor = Sage),
                ) {
                    Text("Back")
                }
                Spacer(Modifier.height(18.dp))
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
private val Acid = Color(0xFFC9FF3F)
private val Ember = Color(0xFFFF765E)

private const val DEMO_DOCUMENT = """
{
  "version": 1,
  "id": "demo-target",
  "title": "Live scan",
  "theme": {
    "background": "#0B120F",
    "surface": "#152019",
    "text": "#F4F8F5",
    "muted": "#93A49A",
    "accent": "#C9FF3F",
    "cornerRadius": 30
  },
  "presentation": {
    "dismissible": true,
    "minimizable": true,
    "showHandle": true,
    "maxHeight": 0.82
  },
  "content": {
    "type": "column",
    "gap": 14,
    "children": [
      {"type": "badge", "label": "Target acquired", "tone": "accent"},
      {"type": "text", "value": "Unknown signature", "variant": "title"},
      {"type": "text", "value": "18,004", "variant": "display", "color": "#C9FF3F"},
      {"type": "progress", "label": "Signal confidence", "value": 0.87, "showValue": true},
      {
        "type": "card",
        "tone": "surface",
        "child": {
          "type": "text",
          "value": "Fast-moving energy source detected 240 m north-east.",
          "variant": "body"
        }
      },
      {
        "type": "row",
        "children": [
          {
            "type": "button",
            "id": "dismiss_target",
            "label": "Dismiss",
            "variant": "secondary",
            "action": {"type": "close"}
          },
          {
            "type": "button",
            "id": "track_target",
            "label": "Track target",
            "action": {
              "type": "broadcast",
              "action": "app.scouter.demo.TRACK",
              "closeAfter": true
            }
          }
        ]
      }
    ]
  }
}
"""
