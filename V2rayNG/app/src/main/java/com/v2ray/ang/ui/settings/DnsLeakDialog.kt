package com.v2ray.ang.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.handler.DnsLeakCheck

/** Cassanova: result of the in-app DNS leak check, with a link to the full browser test. */
@Composable
fun DnsLeakDialog(state: DnsLeakUiState, onFullTest: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dns_leak_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val result = state.result
                when {
                    state.running -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.dns_leak_checking))
                    }

                    state.needVpn -> Text(stringResource(R.string.dns_leak_need_vpn))

                    result != null -> LeakResult(result)
                }
                Text(
                    text = stringResource(R.string.dns_leak_limits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onFullTest) { Text(stringResource(R.string.dns_leak_full_test)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dns_test_close)) }
        },
    )
}

@Composable
private fun LeakResult(result: DnsLeakCheck.Result) {
    val headline = when {
        result.resolverIp == null -> stringResource(R.string.dns_leak_no_answer)
        DnsLeakCheck.isFakeDnsIp(result.resolverIp) -> stringResource(R.string.dns_leak_fakedns)
        result.verdict == DnsLeakCheck.Verdict.SAFE -> stringResource(R.string.dns_leak_safe)
        result.verdict == DnsLeakCheck.Verdict.LEAK -> stringResource(R.string.dns_leak_leak)
        else -> stringResource(R.string.dns_leak_unknown)
    }
    val color = when (result.verdict) {
        DnsLeakCheck.Verdict.SAFE -> MaterialTheme.colorScheme.primary
        DnsLeakCheck.Verdict.LEAK -> MaterialTheme.colorScheme.error
        DnsLeakCheck.Verdict.UNKNOWN -> MaterialTheme.colorScheme.onSurface
    }
    Text(text = headline, color = color, fontWeight = FontWeight.Bold)
    result.resolverIp?.takeIf { !DnsLeakCheck.isFakeDnsIp(it) }?.let { ip ->
        val r = result.resolver
        Text(
            stringResource(
                R.string.dns_leak_resolver,
                DnsLeakCheck.orgName(r?.org) ?: stringResource(R.string.dns_leak_unknown_org),
                listOfNotNull(r?.city, r?.country).joinToString(", ").ifEmpty { "-" },
                ip,
            )
        )
    }
    result.operator?.let { o ->
        Text(
            stringResource(
                R.string.dns_leak_operator,
                DnsLeakCheck.orgName(o.org) ?: stringResource(R.string.dns_leak_unknown_org),
            )
        )
    }
}
