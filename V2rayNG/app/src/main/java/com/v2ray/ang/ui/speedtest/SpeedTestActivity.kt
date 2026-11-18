package com.v2ray.ang.ui.speedtest

import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import java.util.Locale

/** Cassanova: internet speed test (ping, download, upload) from the Tools tab. */
class SpeedTestActivity : BaseComponentActivity() {
    private val viewModel: SpeedTestViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        SpeedTestScreen(viewModel = viewModel, onBackClick = { finish() })
    }

    override fun onStop() {
        super.onStop()
        // Leaving the screen ends the test, so it does not keep using quota in the background.
        if (!isChangingConfigurations) viewModel.stop()
    }
}

private fun formatMbps(value: Double): String = String.format(Locale.US, "%.1f", value)

@Composable
fun SpeedTestScreen(viewModel: SpeedTestViewModel, onBackClick: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { AppTopBar(title = stringResource(R.string.speedtest_title), onBackClick = onBackClick) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(
                    when (state.viaVpn) {
                        true -> R.string.speedtest_via_vpn
                        false -> R.string.speedtest_direct
                        null -> R.string.speedtest_intro
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )

            // Big live number while a transfer runs.
            val phaseLabel = when (state.phase) {
                SpeedPhase.IDLE -> stringResource(R.string.speedtest_ready)
                SpeedPhase.PING -> stringResource(R.string.speedtest_phase_ping)
                SpeedPhase.DOWNLOAD -> stringResource(R.string.speedtest_phase_download)
                SpeedPhase.UPLOAD -> stringResource(R.string.speedtest_phase_upload)
                SpeedPhase.DONE -> stringResource(R.string.speedtest_done)
                SpeedPhase.FAILED -> stringResource(R.string.speedtest_failed)
            }
            val live = if (state.phase == SpeedPhase.DOWNLOAD || state.phase == SpeedPhase.UPLOAD) {
                stringResource(R.string.speedtest_mbps, formatMbps(state.liveMbps))
            } else {
                null
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceContainer)
                    .padding(vertical = 24.dp, horizontal = 16.dp)
                    .semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = phaseLabel, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                if (live != null) {
                    Text(text = live, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = colors.primary)
                }
            }

            ResultRow(
                label = stringResource(R.string.speedtest_ping),
                value = state.pingMs?.let { stringResource(R.string.speedtest_ms, it) }
                    ?: placeholder(state, SpeedPhase.PING),
            )
            ResultRow(
                label = stringResource(R.string.speedtest_download),
                value = state.downMbps?.let { stringResource(R.string.speedtest_mbps, formatMbps(it)) }
                    ?: placeholder(state, SpeedPhase.DOWNLOAD),
            )
            ResultRow(
                label = stringResource(R.string.speedtest_upload),
                value = state.upMbps?.let { stringResource(R.string.speedtest_mbps, formatMbps(it)) }
                    ?: placeholder(state, SpeedPhase.UPLOAD),
            )
            if (state.usedBytes > 0) {
                Text(
                    text = stringResource(R.string.speedtest_used, formatMbps(state.usedBytes / 1024.0 / 1024.0)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }

            if (state.running) {
                OutlinedButton(onClick = { viewModel.stop() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.speedtest_stop))
                }
            } else {
                Button(onClick = { viewModel.start() }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (state.phase == SpeedPhase.IDLE) R.string.speedtest_start else R.string.speedtest_again
                        )
                    )
                }
            }
            Text(
                text = stringResource(R.string.speedtest_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
            NavigationBarsSpacer()
        }
    }
}

/** "…" while [phase] is still to come or running, "failed" once the test went past it. */
@Composable
private fun placeholder(state: SpeedTestUiState, phase: SpeedPhase): String {
    val order = listOf(SpeedPhase.PING, SpeedPhase.DOWNLOAD, SpeedPhase.UPLOAD, SpeedPhase.DONE)
    val past = state.phase == SpeedPhase.FAILED ||
        (state.phase in order && order.indexOf(state.phase) > order.indexOf(phase))
    return when {
        state.phase == SpeedPhase.IDLE -> stringResource(R.string.speedtest_none)
        past -> stringResource(R.string.speedtest_value_failed)
        else -> stringResource(R.string.speedtest_none)
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}
