package com.v2ray.ang.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SwitchColors
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

@Composable
fun PreferenceGroupHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        letterSpacing = 0.8.sp,
        color = sectionTitleColor(),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
    )
}

/** Section title color per Cassanova style: gold for A, brand blue for B and C. */
@Composable
fun sectionTitleColor(): Color {
    val style by ThemeManager.uiStyle.collectAsState()
    val dark = LocalDarkTheme.current
    return if (style == ThemeManager.STYLE_A) {
        if (dark) BrandGold else Color(0xFF8A5E00)
    } else {
        MaterialTheme.colorScheme.primary
    }
}

/**
 * A settings group as a Cassanova card. Style A and C show the title above the card,
 * style B shows it as a header bar inside the card. [content] is shown while [expanded].
 */
@Composable
fun SettingsSection(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val style by ThemeManager.uiStyle.collectAsState()
    val dark = LocalDarkTheme.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (style == ThemeManager.STYLE_B) 16.dp else 18.dp)
    val cardBorder = if (style == ThemeManager.STYLE_C && !dark) {
        Modifier
    } else {
        Modifier.border(1.dp, colors.outlineVariant, shape)
    }
    val titleColor = sectionTitleColor()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        if (style == ThemeManager.STYLE_B) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surfaceContainer)
                    .then(cardBorder)
            ) {
                SectionHeaderRow(
                    title = title,
                    expanded = expanded,
                    color = titleColor,
                    onExpandedChange = onExpandedChange,
                    modifier = Modifier
                        .background(colors.primaryContainer)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                )
                if (expanded) content()
            }
        } else {
            SectionHeaderRow(
                title = title,
                expanded = expanded,
                color = titleColor,
                onExpandedChange = onExpandedChange,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(colors.surfaceContainer)
                        .then(cardBorder),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    expanded: Boolean,
    color: Color,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandedChange(!expanded) }
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            letterSpacing = 0.8.sp,
            color = color,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(R.drawable.ic_expand_more_24dp),
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(22.dp)
                .rotate(if (expanded) 180f else 0f)
        )
    }
}

@Composable
fun CollapsiblePreferenceGroupHeader(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onExpandedChange(!expanded) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(R.drawable.ic_expand_more_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .size(24.dp)
                .rotate(if (expanded) 180f else 0f)
        )
    }
}

@Composable
private fun SettingsItemRow(
    icon: Painter?,
    title: String,
    description: String?,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    showChevron: Boolean = false
) {
    val titleColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val descriptionColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = titleColor
            )
            if (!description.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = descriptionColor
                )
            }
        }
        trailing?.invoke()
        if (showChevron && trailing == null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.ic_expand_more_24dp),
                contentDescription = null,
                tint = descriptionColor,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(-90f)
            )
        }
    }
}

@Composable
fun SettingsEditItem(
    icon: Painter? = null,
    title: String,
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    keyboardNumber: Boolean = false
) {
    var showDialog by remember { mutableStateOf(false) }
    val description = if (isPassword) {
        if (value.isEmpty()) null else "******"
    } else {
        value.ifEmpty { null }
    }

    SettingsItemRow(
        icon = icon,
        title = title,
        description = description,
        enabled = enabled,
        onClick = if (enabled) {
            { showDialog = true }
        } else null,
        modifier = modifier,
        showChevron = true
    )

    if (showDialog) {
        var text by remember { mutableStateOf(value) }
        InputDialog(
            title = title,
            fields = listOf(
                InputField(
                    label = title,
                    value = text,
                    visualTransformation = VisualTransformation.None
                )
            ),
            onFieldChange = { _, v -> text = v },
            confirmText = stringResource(R.string.action_ok),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = { showDialog = false; onValueChanged(text) },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun SettingsListItem(
    icon: Painter? = null,
    title: String,
    entries: List<String>,
    values: List<String>,
    selectedValue: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }
    val options = entries.zip(values)
    val selectedOption = options.find { it.second == selectedValue } ?: options.firstOrNull()
    val summary = selectedOption?.first.orEmpty()

    SettingsItemRow(
        icon = icon,
        title = title,
        description = summary.ifEmpty { null },
        enabled = enabled,
        onClick = if (enabled) {
            { showDialog = true }
        } else null,
        modifier = modifier,
        showChevron = true
    )

    if (showDialog) {
        SelectListDialog(
            title = title,
            options = options,
            optionText = { it.first },
            selectedOption = selectedOption,
            onSelected = { option ->
                showDialog = false
                onSelected(option.second)
            },
            onDismiss = { showDialog = false },
            showRadio = true
        )
    }
}

@Composable
fun SettingsMenuItem(
    icon: Painter? = null,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    SettingsItemRow(
        icon = icon,
        title = title,
        description = subtitle,
        enabled = true,
        onClick = onClick,
        modifier = modifier,
        showChevron = true
    )
}

@Composable
fun SettingsSwitchItem(
    icon: Painter? = null,
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    SettingsItemRow(
        icon = icon,
        title = title,
        description = summary,
        enabled = enabled,
        onClick = if (enabled) {
            { onCheckedChange(!checked) }
        } else null,
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                modifier = Modifier.scale(0.8f),
                colors = cassanovaSwitchColors(),
                enabled = enabled
            )
        }
    )
}

/** Gold switches for styles A and B, brand blue for style C. */
@Composable
fun cassanovaSwitchColors(): SwitchColors {
    val style by ThemeManager.uiStyle.collectAsState()
    return if (style == ThemeManager.STYLE_C) {
        SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary
        )
    } else {
        SwitchDefaults.colors(
            checkedThumbColor = OnBrandGold,
            checkedTrackColor = BrandGold
        )
    }
}
