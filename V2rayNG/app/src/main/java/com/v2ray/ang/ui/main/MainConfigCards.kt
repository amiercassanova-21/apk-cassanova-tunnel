package com.v2ray.ang.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.core.ssh.SshMode
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.ConfigLockManager
import com.v2ray.ang.ui.compose.BrandBlueDeep
import com.v2ray.ang.ui.compose.BrandGold
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.OnBrandGold

/** Theme-aware accent colors of the Cassanova config screens (dark and light variants). */
internal class ConfigPalette(dark: Boolean) {
    val green = if (dark) Color(0xFF6EE7A0) else Color(0xFF1F8A4C)
    val greenBg = if (dark) Color(0xFF13301F) else Color(0xFFCDEFD9)
    val warn = if (dark) Color(0xFFF5C451) else Color(0xFF6B4A00)
    val warnBg = if (dark) Color(0xFF3A2C08) else Color(0xFFFFEDC2)
    val lock = if (dark) Color(0xFFE3A6FF) else Color(0xFF7A3A9A)
    val lockBg = if (dark) Color(0xFF2A1A33) else Color(0xFFF3E3FA)
    val blue = if (dark) Color(0xFF9DB4FF) else BrandBlueDeep
    val blueBg = if (dark) Color(0xFF1A2550) else Color(0xFFDCE4FF)
    val sky = if (dark) Color(0xFF7CC8FF) else Color(0xFF1F6FB0)
    val skyBg = if (dark) Color(0xFF14304A) else Color(0xFFD8EEFF)
    val red = if (dark) Color(0xFFFF8A8A) else Color(0xFFC62828)
    val redBg = if (dark) Color(0xFF3A1414) else Color(0xFFFFE0E0)

    fun familyColor(family: ProtocolFilter): Color = when (family) {
        ProtocolFilter.SSH, ProtocolFilter.SLOWDNS -> warn
        ProtocolFilter.HY2 -> sky
        else -> blue
    }

    fun familyBg(family: ProtocolFilter): Color = when (family) {
        ProtocolFilter.SSH, ProtocolFilter.SLOWDNS -> warnBg
        ProtocolFilter.HY2 -> skyBg
        else -> blueBg
    }
}

@Composable
internal fun configPalette(): ConfigPalette = ConfigPalette(LocalDarkTheme.current)

/** Short SSH mode name for chips and type lines. */
@Composable
internal fun sshModeShort(mode: SshMode): String = stringResource(
    when (mode) {
        SshMode.DIRECT -> R.string.ssh_mode_short_direct
        SshMode.PAYLOAD -> R.string.ssh_mode_short_payload
        SshMode.SSL -> R.string.ssh_mode_short_ssl
        SshMode.SSL_PAYLOAD -> R.string.ssh_mode_short_ssl_payload
        SshMode.SLOWDNS -> R.string.ssh_mode_short_slowdns
    }
)

/** Protocol name as shown on chips: SSH, VLESS, Hysteria2… */
internal fun protocolName(profile: ProfileItem): String = when (profile.configType) {
    EConfigType.HYSTERIA2 -> "Hysteria2"
    EConfigType.HYSTERIA -> "Hysteria"
    else -> profile.configType.name
}

/**
 * Parts of the type line, e.g. `SSH · SlowDNS · NULL` or `VLESS · ws · tls`. A locked SSH
 * config keeps no mode in the clear, so it shows just `SSH`.
 */
@Composable
internal fun typeParts(profile: ProfileItem, locked: Boolean): List<String> {
    if (profile.configType != EConfigType.SSH) return listOf(protocolName(profile)) + transportParts(profile)
    if (locked) return listOf("SSH")
    val mode = SshMode.from(profile.sshMode)
    val parts = mutableListOf("SSH", sshModeShort(mode))
    if (mode == SshMode.SLOWDNS) parts.add(profile.dnsRecordType?.ifBlank { null }?.uppercase() ?: "TXT")
    return parts
}

@Composable
internal fun pingText(delayMillis: Long): String = when (pingLevel(delayMillis)) {
    PingLevel.NONE -> stringResource(R.string.config_ping_none)
    PingLevel.FAILED -> stringResource(R.string.home_ping_failed)
    else -> stringResource(R.string.server_test_delay_value, delayMillis)
}

private fun ConfigPalette.pingColors(delayMillis: Long, muted: Color, mutedBg: Color): Pair<Color, Color> =
    when (pingLevel(delayMillis)) {
        PingLevel.GOOD -> green to greenBg
        PingLevel.SLOW -> warn to warnBg
        PingLevel.FAILED -> red to redBg
        PingLevel.NONE -> muted to mutedBg
    }

/** Card actions; selection and identity always go through the server GUID. */
internal class ConfigCardActions(
    val select: (String) -> Unit,
    val edit: (String, ProfileItem) -> Unit,
    val share: (String, ProfileItem) -> Unit,
    val more: (String, ProfileItem) -> Unit,
    val remove: (String, String) -> Unit,
)

@Composable
private fun Modifier.selectedState(isSelected: Boolean): Modifier {
    val label = stringResource(R.string.acc_selected_server)
    return semantics { if (isSelected) stateDescription = label }
}

// ---------- Style A: hexagon badge, gold selection ----------

@Composable
internal fun ConfigCardA(
    row: ServerRowUiModel,
    isSelected: Boolean,
    actions: ConfigCardActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val palette = configPalette()
    val locked = ConfigLockManager.isLocked(row.profile)
    val family = ProtocolFilter.of(row.profile)
    val parts = typeParts(row.profile, locked).let { if (row.profile.configType == EConfigType.SSH && it.size > 1) it.drop(1) else it }
    val typeLine = if (locked) (listOf(stringResource(R.string.config_locked)) + parts).joinToString(" · ") else parts.joinToString(" · ")
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) BrandGold else colors.outlineVariant, shape)
            .selectedState(isSelected)
            .clickable(role = Role.Button) { actions.select(row.guid) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HexBadge(protocolBadge(row.profile), palette.familyBg(family), palette.familyColor(family))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = row.remarks,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isSelected) {
                    Text(
                        text = stringResource(R.string.config_in_use),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = OnBrandGold,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BrandGold)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                } else if (locked) {
                    Icon(painterResource(R.drawable.ic_lock_24dp), contentDescription = null, tint = palette.lock, modifier = Modifier.size(14.dp))
                }
            }
            val address = maskedAddress(row.profile.server, row.profile.serverPort)
            if (address.isNotEmpty()) {
                Text(text = address, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = typeLine,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    locked -> palette.lock
                    family == ProtocolFilter.HY2 -> palette.sky
                    else -> palette.blue
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val (pingColor, _) = palette.pingColors(row.testDelayMillis, colors.onSurfaceVariant, Color.Transparent)
            Text(text = pingText(row.testDelayMillis), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = pingColor, maxLines = 1)
            MoreButton(
                label = stringResource(R.string.config_options_for, row.remarks),
                width = 44.dp,
                height = 32.dp,
                background = colors.surfaceContainerHighest,
                onClick = { actions.more(row.guid, row.profile) },
            )
        }
    }
}

@Composable
private fun HexBadge(text: String, background: Color, foreground: Color) {
    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawPath(hexagon(center, size.minDimension / 2f * 0.95f), color = background)
        }
        Text(text = text, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = foreground, maxLines = 1)
    }
}

@Composable
private fun MoreButton(label: String, width: Dp, height: Dp, background: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_more_vert_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .rotate(90f),
        )
    }
}

// ---------- Style B: chips and inline actions ----------

@Composable
internal fun ConfigCardB(
    row: ServerRowUiModel,
    isSelected: Boolean,
    compact: Boolean,
    actions: ConfigCardActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val palette = configPalette()
    val locked = ConfigLockManager.isLocked(row.profile)
    val family = ProtocolFilter.of(row.profile)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) BrandGold else colors.outlineVariant, shape)
            .selectedState(isSelected)
            .clickable(role = Role.Button) { actions.select(row.guid) }
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.remarks,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val (pingColor, _) = palette.pingColors(row.testDelayMillis, colors.onSurfaceVariant, Color.Transparent)
            Text(text = pingText(row.testDelayMillis), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = pingColor, maxLines = 1)
        }
        val address = maskedAddress(row.profile.server, row.profile.serverPort)
        if (address.isNotEmpty()) {
            Text(text = address, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(protocolName(row.profile), palette.familyBg(family), palette.familyColor(family))
                when {
                    locked -> Chip(stringResource(R.string.config_locked), palette.lockBg, palette.lock)
                    row.profile.configType == EConfigType.SSH ->
                        Chip(sshModeShort(SshMode.from(row.profile.sshMode)), palette.blueBg, palette.blue)
                    else -> transportParts(row.profile).firstOrNull()?.let {
                        Chip(it, colors.surfaceContainerHighest, colors.onSurfaceVariant)
                    }
                }
            }
            if (compact) {
                MoreButton(
                    label = stringResource(R.string.config_options_for, row.remarks),
                    width = 40.dp,
                    height = 36.dp,
                    background = Color.Transparent,
                    onClick = { actions.more(row.guid, row.profile) },
                )
            } else {
                if (!locked) {
                    ActionIcon(R.drawable.ic_share_24dp, stringResource(R.string.title_configuration_share)) { actions.share(row.guid, row.profile) }
                    ActionIcon(R.drawable.ic_edit_24dp, stringResource(R.string.acc_edit)) { actions.edit(row.guid, row.profile) }
                }
                ActionIcon(R.drawable.ic_delete_24dp, stringResource(R.string.acc_delete)) { actions.remove(row.guid, row.remarks) }
            }
        }
    }
}

@Composable
private fun Chip(text: String, background: Color, foreground: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = foreground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun ActionIcon(iconRes: Int, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

// ---------- Style C: radio card with a ping badge ----------

@Composable
internal fun ConfigCardC(
    row: ServerRowUiModel,
    isSelected: Boolean,
    actions: ConfigCardActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val palette = configPalette()
    val dark = LocalDarkTheme.current
    val accent = if (dark) BrandGold else colors.primary
    val locked = ConfigLockManager.isLocked(row.profile)
    val parts = typeParts(row.profile, locked).toMutableList()
    if (row.profile.configType != EConfigType.SSH) {
        row.profile.serverPort?.takeIf { it.isNotBlank() }?.let { parts.add(":$it") }
    }
    val subtitle = (if (locked) listOf(stringResource(R.string.config_locked)) + parts else parts).joinToString(" · ")
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) accent else colors.outlineVariant, shape)
            .selectedState(isSelected)
            .clickable(role = Role.RadioButton) { actions.select(row.guid) }
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(2.dp, if (isSelected) accent else colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = row.remarks,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = if (locked) palette.lock else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val (fg, bg) = palette.pingColors(row.testDelayMillis, colors.onSurfaceVariant, colors.surfaceContainerHighest)
        Text(
            text = pingText(row.testDelayMillis),
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = fg,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        MoreButton(
            label = stringResource(R.string.config_options_for, row.remarks),
            width = 36.dp,
            height = 44.dp,
            background = Color.Transparent,
            onClick = { actions.more(row.guid, row.profile) },
        )
    }
}
