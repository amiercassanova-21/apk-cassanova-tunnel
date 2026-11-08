package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import com.v2ray.ang.ui.compose.ThemeManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.ui.compose.CassanovaCard
import com.v2ray.ang.util.Utils
import com.v2ray.ang.ui.checkupdate.CheckUpdateActivity

class AboutActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        AboutScreen(
            onBackClick = { finish() },
            onTranslatorsClick = {
                startActivity(Intent(this, TranslatorsActivity::class.java))
            }
        )
    }
}

@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
    onTranslatorsClick: () -> Unit
) {
    val context = LocalContext.current
    var showOssDialog by remember { mutableStateOf(false) }
    var versionTaps by remember { mutableIntStateOf(0) }
    var lastVersionTap by remember { mutableLongStateOf(0L) }
    var tapToast by remember { mutableStateOf<Toast?>(null) }

    val libVersion = CoreNativeManager.getLibVersion()
    val appIdText = BuildConfig.APPLICATION_ID

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_about),
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp)
        ) {
            // Identity card: logo, name, version and core.
            CassanovaCard {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // Tapping the version 7 times unlocks (or locks again) the experimental styles.
                        Text(
                            text = stringResource(R.string.about_build_line, BuildConfig.VERSION_NAME, BuildConfig.CASSANOVA_BUILD),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val now = System.currentTimeMillis()
                                versionTaps = VersionTaps.next(versionTaps, lastVersionTap, now)
                                lastVersionTap = now
                                tapToast?.cancel()
                                tapToast = null
                                if (VersionTaps.switches(versionTaps)) {
                                    versionTaps = 0
                                    lastVersionTap = 0L
                                    val on = ThemeManager.toggleExperimentalUi()
                                    tapToast = Toast.makeText(
                                        context,
                                        if (on) R.string.about_experimental_on else R.string.about_experimental_off,
                                        Toast.LENGTH_LONG
                                    ).also { it.show() }
                                } else {
                                    VersionTaps.remaining(versionTaps)?.let { left ->
                                        tapToast = Toast.makeText(
                                            context,
                                            context.resources.getQuantityString(R.plurals.about_taps_left, left, left),
                                            Toast.LENGTH_SHORT
                                        ).also { it.show() }
                                    }
                                }
                            }
                        )
                        Text(
                            text = stringResource(R.string.about_core_line, libVersion),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = appIdText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            CassanovaCard(title = stringResource(R.string.about_contact)) {
                SettingsMenuItem(
                    icon = painterResource(R.drawable.ic_telegram_24dp),
                    title = stringResource(R.string.about_telegram_title),
                    subtitle = stringResource(R.string.about_telegram_handle),
                    onClick = { Utils.openUri(context, AppConfig.TG_CONTACT_URL) }
                )
            }
            CassanovaCard(title = stringResource(R.string.about_info)) {
                // Cassanova: the update check's only entry in styles A and C (style B also has it
                // in the drawer).
                SettingsMenuItem(
                    icon = painterResource(R.drawable.ic_check_update_24dp),
                    title = stringResource(R.string.update_check_for_update),
                    onClick = { context.startActivity(Intent(context, CheckUpdateActivity::class.java)) }
                )
                SettingsMenuItem(
                    icon = painterResource(R.drawable.ic_source_code_24dp),
                    title = stringResource(R.string.title_source_code),
                    onClick = { Utils.openUri(context, AppConfig.APP_URL) }
                )
                SettingsMenuItem(
                    icon = painterResource(R.drawable.license_24px),
                    title = stringResource(R.string.title_oss_license),
                    onClick = { showOssDialog = true }
                )
            }
            CassanovaCard(title = stringResource(R.string.about_credits)) {
                SettingsMenuItem(
                    icon = painterResource(R.drawable.ic_about_24dp),
                    title = stringResource(R.string.about_based_on),
                    subtitle = stringResource(R.string.about_based_on_desc),
                    onClick = { Utils.openUri(context, AppConfig.UPSTREAM_URL) }
                )
                SettingsMenuItem(
                    icon = painterResource(R.drawable.ic_translate_24dp),
                    title = stringResource(R.string.title_translators),
                    onClick = onTranslatorsClick
                )
            }
            NavigationBarsSpacer(Modifier.padding(bottom = 16.dp))
        }
    }

    if (showOssDialog) {
        AlertDialog(
            onDismissRequest = { showOssDialog = false },
            title = { Text(stringResource(R.string.title_oss_license)) },
            text = {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            loadUrl("file:///android_asset/open_source_licenses.html")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { showOssDialog = false }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(bottom = 60.dp)
        )
    }
}
