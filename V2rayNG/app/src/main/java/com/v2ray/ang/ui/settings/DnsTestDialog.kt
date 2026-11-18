package com.v2ray.ang.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.handler.DnsPresets

/**
 * Cassanova: result of the DNS speed test. Tapping a finished preset row applies it via
 * [onPick]; the custom row only shows its result.
 */
@Composable
fun DnsTestDialog(
    state: DnsTestUiState,
    currentPresetId: String?,
    onPick: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dns_test_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        when (state.viaVpn) {
                            null -> R.string.dns_test_checking
                            true -> R.string.dns_test_via_vpn
                            false -> R.string.dns_test_direct
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(top = 8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(state.rows, key = { it.id }) { row ->
                        DnsTestRowItem(
                            row = row,
                            current = row.id == currentPresetId,
                            onPick = onPick,
                        )
                    }
                }
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    text = stringResource(R.string.dns_test_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRetry, enabled = !state.running) {
                Text(stringResource(R.string.dns_test_retry))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dns_test_close))
            }
        },
    )
}

@Composable
private fun DnsTestRowItem(row: DnsTestRow, current: Boolean, onPick: (String) -> Unit) {
    val preset = DnsPresets.byId(row.id)
    val name = if (preset != null) {
        stringResource(preset.nameRes)
    } else {
        stringResource(R.string.dns_preset_custom_host, row.host.orEmpty())
    }
    val time = when {
        !row.done -> stringResource(R.string.dns_test_pending)
        row.millis == null -> stringResource(R.string.dns_test_failed)
        else -> stringResource(R.string.dns_test_ms, row.millis)
    }
    val ads = when (row.adsBlocked) {
        true -> stringResource(R.string.dns_test_ads_blocked)
        false -> stringResource(R.string.dns_test_ads_not_blocked)
        null -> null
    }
    val pickable = preset != null && row.done && row.millis != null
    val pickLabel = stringResource(R.string.dns_test_use)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (pickable) Modifier.clickable(onClickLabel = pickLabel) { onPick(row.id) }
                else Modifier.semantics(mergeDescendants = true) {}
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (current) stringResource(R.string.dns_test_current, name) else name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (ads != null) {
                Text(
                    text = ads,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.adsBlocked == true) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = time,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (row.done && row.millis == null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}
