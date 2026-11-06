package com.v2ray.ang.ui.tools

import android.app.Application
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.handler.HostCheck
import com.v2ray.ang.handler.HostLookup
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.BrandGold
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val OnGold = Color(0xFF1B1608)

/** "Host to IP" and "Host Checker" tools from the Tools tab (one screen, two modes). */
class HostToIpActivity : BaseComponentActivity() {
    private val viewModel: HostToIpViewModel by viewModels()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_CHECKER, false)) {
            viewModel.setMode(ToolMode.CHECK)
        }
    }

    companion object {
        const val EXTRA_CHECKER = "host_checker"
    }

    @Composable
    override fun ScreenContent() {
        HostToIpScreen(viewModel = viewModel, onBackClick = { finish() })
    }
}

enum class ToolMode { LOOKUP, CHECK }

data class HostToIpState(
    val mode: ToolMode = ToolMode.LOOKUP,
    val source: HostLookup.Source = HostLookup.Source.SYSTEM,
    val history: List<String> = emptyList(),
    val running: Boolean = false,
    val results: List<HostLookup.Result> = emptyList(),
    val checks: List<HostCheck.Result> = emptyList(),
)

class HostToIpViewModel(application: Application) : BaseViewModel(application) {
    private val _state = MutableStateFlow(HostToIpState())
    val state: StateFlow<HostToIpState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val source = HostLookup.source()
            val history = HostLookup.history()
            _state.update { it.copy(source = source, history = history) }
        }
    }

    fun setMode(mode: ToolMode) {
        if (_state.value.mode == mode) return
        job?.cancel()
        _state.update { it.copy(mode = mode, results = emptyList(), checks = emptyList()) }
    }

    fun setSource(source: HostLookup.Source) {
        _state.update { it.copy(source = source) }
        viewModelScope.launch(Dispatchers.IO) { HostLookup.saveSource(source) }
    }

    fun lookup(text: String) {
        val hosts = HostLookup.parseHosts(text)
        if (hosts.isEmpty()) {
            toast(R.string.host_to_ip_empty)
            return
        }
        val previous = job
        previous?.cancel()
        val source = _state.value.source
        val history = HostLookup.pushHistory(_state.value.history, hosts)
        val mode = _state.value.mode
        _state.update { it.copy(running = true, results = emptyList(), checks = emptyList(), history = history) }
        job = viewModelScope.launch(Dispatchers.IO) {
            // A newer search replaces the previous one; wait so its cleanup cannot clear our state.
            previous?.join()
            _state.update { it.copy(running = true, results = emptyList(), checks = emptyList()) }
            HostLookup.saveHistory(history)
            try {
                for (host in hosts) {
                    if (mode == ToolMode.CHECK) {
                        val check = HostCheck.check(host)
                        _state.update { it.copy(checks = it.checks + check) }
                    } else {
                        val result = HostLookup.lookup(host, source)
                        _state.update { it.copy(results = it.results + result) }
                    }
                }
            } finally {
                _state.update { it.copy(running = false) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun clearHistory() {
        _state.update { it.copy(history = emptyList()) }
        viewModelScope.launch(Dispatchers.IO) { HostLookup.saveHistory(emptyList()) }
    }

    fun copy(text: String) {
        Utils.setClipboard(app, text)
        toast(R.string.host_to_ip_copied)
    }

    fun copyAll() {
        val state = _state.value
        val text = if (state.mode == ToolMode.CHECK) {
            state.checks.joinToString("\n") { HostCheck.summary(it) }
        } else {
            state.results.joinToString("\n") { r ->
                if (r.ips.isEmpty()) "${r.host} -" else "${r.host} ${r.ips.joinToString(" ")}"
            }
        }
        copy(text)
    }
}

@Composable
fun HostToIpScreen(viewModel: HostToIpViewModel, onBackClick: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(if (state.mode == ToolMode.CHECK) R.string.title_host_checker else R.string.title_host_to_ip),
                onBackClick = onBackClick,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.mode == ToolMode.LOOKUP,
                    onClick = { viewModel.setMode(ToolMode.LOOKUP) },
                    label = { Text(stringResource(R.string.title_host_to_ip)) },
                )
                FilterChip(
                    selected = state.mode == ToolMode.CHECK,
                    onClick = { viewModel.setMode(ToolMode.CHECK) },
                    label = { Text(stringResource(R.string.title_host_checker)) },
                )
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.host_to_ip_input)) },
                placeholder = { Text(stringResource(R.string.host_to_ip_hint)) },
                minLines = 3,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.mode == ToolMode.LOOKUP) {
                Text(
                    text = stringResource(R.string.host_to_ip_dns),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SourceChip(HostLookup.Source.SYSTEM, R.string.host_to_ip_dns_system, state.source, viewModel::setSource)
                    SourceChip(HostLookup.Source.CLOUDFLARE, R.string.host_to_ip_dns_cloudflare, state.source, viewModel::setSource)
                    SourceChip(HostLookup.Source.GOOGLE, R.string.host_to_ip_dns_google, state.source, viewModel::setSource)
                }
                Text(
                    text = stringResource(
                        if (state.source == HostLookup.Source.SYSTEM) R.string.host_to_ip_dns_system_note else R.string.host_to_ip_dns_doh_note
                    ),
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )

            } else {
                Text(
                    text = stringResource(R.string.host_checker_note),
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }

            Button(
                onClick = { if (state.running) viewModel.cancel() else viewModel.lookup(input) },
                colors = ButtonDefaults.buttonColors(containerColor = BrandGold, contentColor = OnGold),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                if (state.running) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = OnGold)
                    Spacer(Modifier.size(10.dp))
                }
                Text(
                    text = stringResource(
                        when {
                            state.running -> R.string.host_to_ip_cancel
                            state.mode == ToolMode.CHECK -> R.string.host_checker_run
                            else -> R.string.host_to_ip_run
                        }
                    ),
                    fontWeight = FontWeight.Bold,
                )
            }

            val hasResults = state.results.isNotEmpty() || state.checks.isNotEmpty()
            if (state.history.isNotEmpty() && !hasResults && !state.running) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.host_to_ip_history),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::clearHistory) {
                        Text(stringResource(R.string.host_to_ip_history_clear), color = BrandGold)
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.history.forEach { host ->
                        SuggestionChip(onClick = { input = host }, label = { Text(host, maxLines = 1) })
                    }
                }
            }

            if (hasResults) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.host_to_ip_results),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::copyAll) {
                        Text(stringResource(R.string.host_to_ip_copy_all), color = BrandGold, fontWeight = FontWeight.Bold)
                    }
                }
                state.results.forEach { result -> ResultCard(result, viewModel::copy) }
                state.checks.forEach { check -> CheckCard(check, viewModel::copy) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SourceChip(
    source: HostLookup.Source,
    labelRes: Int,
    selected: HostLookup.Source,
    onSelect: (HostLookup.Source) -> Unit,
) {
    FilterChip(
        selected = source == selected,
        onClick = { onSelect(source) },
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun ResultCard(result: HostLookup.Result, onCopy: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainer)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = result.host,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.host_to_ip_ms, result.millis),
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(end = 10.dp),
            )
        }
        when (result.failure) {
            HostLookup.Failure.NOT_FOUND -> FailureText(stringResource(R.string.host_to_ip_not_found))
            HostLookup.Failure.NETWORK -> FailureText(stringResource(R.string.host_to_ip_network_error))
            null -> result.ips.forEach { ip -> IpRow(ip, onCopy) }
        }
    }
}

@Composable
private fun FailureText(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun IpRow(ip: String, onCopy: (String) -> Unit) {
    val copyLabel = stringResource(R.string.host_to_ip_copy_ip, ip)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClickLabel = copyLabel) { onCopy(ip) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = ip,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onCopy(ip) }) {
            Icon(
                painter = painterResource(R.drawable.ic_copy),
                contentDescription = copyLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CheckCard(result: HostCheck.Result, onCopy: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainer)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 8.dp),
    ) {
        Text(
            text = result.host,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.onSurface,
        )
        val ip = result.ip
        if (ip == null) {
            FailureText(stringResource(R.string.host_to_ip_not_found))
            return@Column
        }
        IpRow(ip, onCopy)
        result.probes.forEach { probe -> ProbeRow(probe) }
    }
}

@Composable
private fun ProbeRow(probe: HostCheck.Probe) {
    val colors = MaterialTheme.colorScheme
    val good = probe.state == HostCheck.State.RESPONDED
    val label = if (probe.port == 443) "HTTPS 443" else "HTTP 80"
    val detail = when (probe.state) {
        HostCheck.State.RESPONDED -> listOfNotNull(probe.code?.toString(), probe.server).joinToString(" · ")
        HostCheck.State.OPEN_NO_HTTP -> stringResource(R.string.host_checker_open_no_http)
        HostCheck.State.TLS_ERROR -> stringResource(R.string.host_checker_tls_error)
        HostCheck.State.CLOSED -> stringResource(R.string.host_checker_closed)
        HostCheck.State.TIMEOUT -> stringResource(R.string.host_checker_timeout)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(
            text = detail,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = if (good) colors.onSurface else colors.error,
            modifier = Modifier.weight(1f),
        )
        if (probe.state != HostCheck.State.CLOSED && probe.state != HostCheck.State.TIMEOUT) {
            Text(
                text = stringResource(R.string.host_to_ip_ms, probe.millis),
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
