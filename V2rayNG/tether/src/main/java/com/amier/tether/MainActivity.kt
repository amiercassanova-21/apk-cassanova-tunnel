package com.amier.tether

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private val Navy = Color(0xFF0E1430)
private val Card = Color(0xFF182046)
private val Blue = Color(0xFF3F5BD8)
private val Gold = Color(0xFFF5B530)
private val Muted = Color(0xFFA9B1D6)
private val Green = Color(0xFF6EE7A0)

class MainActivity : ComponentActivity() {

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (VpnService.prepare(this) == null) TetherVpnService.start(this)
        else Toast.makeText(this, R.string.error_vpn_denied, Toast.LENGTH_SHORT).show()
    }

    private val runtimePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Connecting continues whatever the answer; without local-network access the proxy is
        // simply not found, and without notifications only the status bar text is missing.
        connectWithVpnPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = Navy, surface = Card)) {
                TetherScreen(onConnect = ::connect, onDisconnect = { TetherVpnService.stop(this) })
            }
        }
    }

    private fun connect(settings: TetherSettings) {
        if (!settings.auto && (settings.host.isBlank() || ProxyFinder.parsePort(settings.port) == null)) {
            Toast.makeText(this, R.string.error_manual_missing, Toast.LENGTH_SHORT).show()
            return
        }
        TetherPrefs.save(this, settings)
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            // Android 17+: reaching the hotspot phone (a local address) needs this permission.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) add(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) connectWithVpnPermission() else runtimePermissions.launch(needed.toTypedArray())
    }

    private fun connectWithVpnPermission() {
        val request = VpnService.prepare(this)
        if (request == null) TetherVpnService.start(this) else vpnPermission.launch(request)
    }
}

@Composable
private fun TetherScreen(onConnect: (TetherSettings) -> Unit, onDisconnect: () -> Unit) {
    val context = LocalContext.current
    val status by TetherState.status.collectAsStateWithLifecycle()
    val saved = remember { TetherPrefs.load(context) }
    var auto by rememberSaveable { mutableStateOf(saved.auto) }
    var host by rememberSaveable { mutableStateOf(saved.host) }
    var port by rememberSaveable { mutableStateOf(saved.port) }
    val on = status != TetherStatus.Off

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
            Column {
                Row {
                    Text("Amier ", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Tether", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(stringResource(R.string.subtitle), color = Muted, fontSize = 12.sp)
            }
        }

        PowerButton(on = on, onClick = { if (on) onDisconnect() else onConnect(TetherSettings(auto, host, port)) })
        StatusText(status)
        if (status is TetherStatus.Connected) ConnectionCard(status as TetherStatus.Connected)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Card)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = auto, enabled = !on, role = Role.Switch, onValueChange = { auto = it }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.label_auto), color = Color.White, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.summary_auto), color = Muted, fontSize = 12.sp)
                }
                Switch(checked = auto, onCheckedChange = null, enabled = !on)
            }
            if (!auto) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.label_host)) },
                    singleLine = true,
                    enabled = !on,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                label = { Text(stringResource(R.string.label_port)) },
                supportingText = { if (auto) Text(stringResource(R.string.hint_port_auto)) },
                singleLine = true,
                enabled = !on,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Card)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.help_title), color = Gold, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.help_text), color = Muted, fontSize = 13.sp)
        }
        Text("v${BuildConfig.VERSION_NAME} (build ${BuildConfig.CASSANOVA_BUILD})", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PowerButton(on: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.acc_toggle)
    Box(
        modifier = Modifier
            .size(150.dp)
            .clip(CircleShape)
            .background(
                if (on) Brush.radialGradient(listOf(Gold, Color(0xFFB07A10)))
                else Brush.radialGradient(listOf(Blue, Color(0xFF1C2882)))
            )
            .border(4.dp, if (on) Color(0x66F5B530) else Color(0x663F5BD8), CircleShape)
            .toggleable(value = on, role = Role.Switch, onValueChange = { onClick() })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(if (on) R.string.action_stop else R.string.action_connect),
            color = if (on) Navy else Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun StatusText(status: TetherStatus) {
    val (text, color) = when (status) {
        TetherStatus.Off -> stringResource(R.string.status_off) to Muted
        is TetherStatus.Searching -> stringResource(if (status.lost) R.string.status_lost else R.string.status_searching) to Gold
        is TetherStatus.Connected -> stringResource(R.string.status_connected) to Green
    }
    Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun ConnectionCard(status: TetherStatus.Connected) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status.sinceMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .border(1.dp, Color(0x55F5B530), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InfoRow(stringResource(R.string.label_proxy), "${status.target.host}:${status.target.port}", mono = true)
        InfoRow(
            stringResource(R.string.label_source),
            stringResource(
                when (status.target.source) {
                    ProxySource.AMIER_VPN -> R.string.source_amier
                    ProxySource.GATEWAY -> R.string.source_gateway
                    ProxySource.MANUAL -> R.string.source_manual
                }
            ),
        )
        InfoRow(stringResource(R.string.label_duration), formatDuration(now - status.sinceMillis), mono = true)
        InfoRow(stringResource(R.string.label_traffic), formatBytes(status.bytes), mono = true)
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 14.sp, fontFamily = if (mono) FontFamily.Monospace else null)
    }
}

internal fun formatDuration(millis: Long): String {
    val s = (millis / 1000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / 1073741824.0)
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
