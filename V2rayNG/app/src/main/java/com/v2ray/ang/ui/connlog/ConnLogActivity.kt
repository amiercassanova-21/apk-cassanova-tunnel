package com.v2ray.ang.ui.connlog

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.handler.ConnLog
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.ui.logcat.LogcatActivity
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "Log Koneksi": the connection log written by the core (see [ConnLog]), HTTP Custom style. */
class ConnLogActivity : BaseComponentActivity() {
    private val viewModel: ConnLogViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        ConnLogScreen(
            viewModel = viewModel,
            onBackClick = { finish() },
            onOpenLogcat = { startActivity(Intent(this, LogcatActivity::class.java)) },
        )
    }
}

class ConnLogViewModel(application: Application) : BaseViewModel(application) {
    private val _lines = MutableStateFlow<List<ConnLog.Line>>(emptyList())
    val lines: StateFlow<List<ConnLog.Line>> = _lines.asStateFlow()

    init {
        // The core process appends to the file; re-read it while this screen is alive.
        viewModelScope.launch(Dispatchers.IO) {
            var modified = -1L
            while (isActive) {
                val file = ConnLog.file(app)
                val m = if (file.exists()) file.lastModified() + file.length() else 0L
                if (m != modified) {
                    modified = m
                    _lines.value = ConnLog.read(app)
                }
                delay(REFRESH_MS)
            }
        }
    }

    fun copyAll() {
        val format = SimpleDateFormat("HH:mm:ss", Locale.US)
        val text = _lines.value.filter { it.kind != ConnLog.Kind.SESSION }
            .joinToString("\n") { "[${format.format(Date(it.time))}] ${if (it.kind == ConnLog.Kind.BANNER) htmlToPlain(it.text) else it.text}" }
        Utils.setClipboard(app, text)
        toast(R.string.connlog_copied)
    }

    fun clear() {
        viewModelScope.launch(Dispatchers.IO) {
            ConnLog.clear(app)
            _lines.value = emptyList()
        }
    }

    private companion object {
        const val REFRESH_MS = 1000L
    }
}

/** Banner text without tags, for copying. */
internal fun htmlToPlain(html: String): String =
    html.replace(Regex("(?i)<br\\s*/?>"), "\n").replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")

/** True when the banner is HTML; plain-text banners keep their own line breaks. */
internal fun looksLikeHtml(text: String): Boolean = Regex("<\\s*/?\\s*[a-zA-Z][^>]*>").containsMatchIn(text)

@Composable
fun ConnLogScreen(
    viewModel: ConnLogViewModel,
    onBackClick: () -> Unit,
    onOpenLogcat: () -> Unit,
) {
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val visible = remember(lines) { lines.filter { it.kind != ConnLog.Kind.SESSION } }

    LaunchedEffect(visible.size) {
        if (visible.isNotEmpty()) listState.animateScrollToItem(visible.lastIndex)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_conn_log),
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = { viewModel.copyAll() }) {
                        Icon(painterResource(R.drawable.ic_copy), contentDescription = stringResource(R.string.acc_copy_log))
                    }
                    IconButton(onClick = onOpenLogcat) {
                        Icon(painterResource(R.drawable.ic_logcat_24dp), contentDescription = stringResource(R.string.connlog_open_logcat))
                    }
                    IconButton(onClick = { viewModel.clear() }) {
                        Icon(painterResource(R.drawable.ic_delete_24dp), contentDescription = stringResource(R.string.acc_clear_log))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (visible.isEmpty()) {
            Box(Modifier.padding(innerPadding).padding(24.dp)) {
                Text(
                    text = stringResource(R.string.connlog_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScrollbar(listState),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
        ) {
            itemsIndexed(visible, key = { index, line -> "${line.time}-$index" }) { _, line ->
                if (line.kind == ConnLog.Kind.BANNER) BannerLine(line.text) else LogLine(line)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }
        }
    }
}

@Composable
private fun LogLine(line: ConnLog.Line) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val color = when (line.kind) {
        ConnLog.Kind.OK -> if (dark) Color(0xFF6EE7A0) else Color(0xFF1F8A4C)
        ConnLog.Kind.WARN -> if (dark) Color(0xFFF5C451) else Color(0xFF8A5E00)
        ConnLog.Kind.ERROR -> if (dark) Color(0xFFFF8A8A) else Color(0xFFC62828)
        else -> colors.onSurface
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    Row(modifier = Modifier.padding(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "[${timeFormat.format(Date(line.time))}]",
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = if (dark) Color(0xFF7C9BFF) else Color(0xFF1E3FB0),
        )
        Text(text = line.text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = color)
    }
}

/** The server banner rendered from its HTML: colors, sizes, line breaks and tappable links. */
@Composable
internal fun BannerLine(html: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
    ) {
        Text(text = bannerText(html), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun bannerText(html: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(html, linkColor) {
        if (looksLikeHtml(html)) {
            AnnotatedString.fromHtml(
                html.trim(),
                linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
            )
        } else {
            AnnotatedString(html.trim())
        }
    }
}
