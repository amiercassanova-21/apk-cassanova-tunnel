package com.v2ray.ang.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.BrandGold
import com.v2ray.ang.ui.compose.sectionTitleColor
import com.v2ray.ang.ui.compose.verticalScrollbar

enum class MainDestination(@DrawableRes val iconRes: Int, @StringRes val labelRes: Int) {
    Subscriptions(R.drawable.ic_subscriptions_24dp, R.string.title_sub_setting),
    PerAppProxy(R.drawable.ic_per_apps_24dp, R.string.per_app_proxy_settings),
    Routing(R.drawable.ic_routing_24dp, R.string.routing_settings_title),
    UserAssets(R.drawable.ic_file_24dp, R.string.title_user_asset_setting),
    Settings(R.drawable.ic_settings_24dp, R.string.title_settings),
    Logcat(R.drawable.ic_logcat_24dp, R.string.title_logcat),
    CheckUpdate(R.drawable.ic_check_update_24dp, R.string.update_check_for_update),
    BackupRestore(R.drawable.ic_restore_24dp, R.string.title_configuration_backup_restore),
    About(R.drawable.ic_about_24dp, R.string.title_about)
}

private val primaryDrawerItems = listOf(
    MainDestination.Subscriptions,
    MainDestination.PerAppProxy,
    MainDestination.Routing,
    MainDestination.UserAssets,
    MainDestination.Settings
)

private val drawerItems = primaryDrawerItems + listOf(
    MainDestination.Logcat,
    MainDestination.CheckUpdate,
    MainDestination.BackupRestore,
    MainDestination.About
)

@Composable
fun MainDrawerContent(drawerState: DrawerState, onNavigate: (MainDestination) -> Unit) {
    val drawerScrollState = rememberScrollState()
    val colors = MaterialTheme.colorScheme

    ModalDrawerSheet(
        drawerState = drawerState,
        modifier = Modifier.fillMaxWidth(0.8f),
        drawerContainerColor = colors.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(drawerScrollState)
                .verticalScrollbar(drawerScrollState)
        ) {
            // Header: the logo centred on the plain drawer background.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 24.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp)
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface
                )
            }
            Spacer(Modifier.height(12.dp))
            DrawerSectionLabel(stringResource(R.string.drawer_section_main))
            primaryDrawerItems.forEach { item -> DrawerRow(item, onNavigate) }
            Spacer(Modifier.height(8.dp))
            DrawerSectionLabel(stringResource(R.string.drawer_section_other))
            drawerItems.drop(primaryDrawerItems.size).forEach { item -> DrawerRow(item, onNavigate) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        letterSpacing = 0.8.sp,
        color = sectionTitleColor(),
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 6.dp)
    )
}

@Composable
private fun DrawerRow(item: MainDestination, onNavigate: (MainDestination) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(item.labelRes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = { onNavigate(item) })
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = null,
                tint = BrandGold,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
