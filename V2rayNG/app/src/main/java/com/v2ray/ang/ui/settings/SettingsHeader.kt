package com.v2ray.ang.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.BrandBlue
import com.v2ray.ang.ui.compose.BrandBlueDeep
import com.v2ray.ang.ui.compose.BrandBlueMid
import com.v2ray.ang.ui.compose.BrandGold
import com.v2ray.ang.ui.compose.BrandGoldDeep
import com.v2ray.ang.ui.compose.BrandNavy
import com.v2ray.ang.ui.compose.OnBrandGold
import com.v2ray.ang.ui.compose.ThemeManager

/**
 * Cassanova settings header for styles A (gold-to-blue gradient with an identity card) and
 * C (blue header with the light/dark/system switch). Style B keeps the shared top bar.
 */
@Composable
fun CassanovaSettingsHeader(
    style: String,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    if (style == ThemeManager.STYLE_C) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(BrandBlue)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TitleRow(onBackClick = onBackClick, color = Color.White)
            ThemeModeSwitch(
                selected = themeMode,
                onSelect = onThemeModeChange,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    } else {
        val styleLabels = stringArrayResource(R.array.ui_style).toList()
        val styleValues = stringArrayResource(R.array.ui_style_value).toList()
        val styleName = styleLabels.getOrNull(styleValues.indexOf(style)).orEmpty()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                .background(
                    Brush.linearGradient(listOf(BrandGold, BrandGoldDeep, BrandBlue, BrandNavy))
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TitleRow(onBackClick = onBackClick, color = OnBrandGold)
            Row(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_version_style, BuildConfig.VERSION_NAME, styleName),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TitleRow(onBackClick: () -> Unit, color: Color) {
    val backLabel = stringResource(R.string.acc_back)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClickLabel = backLabel, role = Role.Button, onClick = onBackClick)
                .semantics { contentDescription = backLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back_24dp),
                contentDescription = null,
                tint = color,
            )
        }
        Text(
            text = stringResource(R.string.title_settings),
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
        )
    }
}

/** Light / Dark / Follow system, in that order (ThemeManager values "1", "2", "0"). */
@Composable
private fun ThemeModeSwitch(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val labels = stringArrayResource(R.array.ui_mode_night).toList()
    val values = stringArrayResource(R.array.ui_mode_night_value).toList()
    val order = listOf("1", "2", "0").filter { it in values }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BrandBlueMid)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        order.forEach { value ->
            ModeOption(
                label = labels.getOrNull(values.indexOf(value)).orEmpty(),
                selected = value == selected,
                onClick = { onSelect(value) },
            )
        }
    }
}

@Composable
private fun RowScope.ModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) BrandBlueDeep else Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Routing / Backup / About shortcuts shown under the A and C headers. */
@Composable
fun SettingsShortcutRow(
    onRouting: () -> Unit,
    onBackup: () -> Unit,
    onAbout: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ShortcutTile(stringResource(R.string.tool_routing), R.drawable.ic_routing_24dp, onRouting)
        ShortcutTile(stringResource(R.string.tool_backup), R.drawable.ic_backup_24dp, onBackup)
        ShortcutTile(stringResource(R.string.title_about), R.drawable.ic_about_24dp, onAbout)
    }
}

@Composable
private fun RowScope.ShortcutTile(label: String, iconRes: Int, onClick: () -> Unit) {
    val style = ThemeManager.uiStyle.value
    Column(
        modifier = Modifier
            .weight(1f)
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (style == ThemeManager.STYLE_C) MaterialTheme.colorScheme.primary else BrandGold,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
