package com.v2ray.ang.ui.main

import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.BrandGold
import com.v2ray.ang.ui.compose.OnBrandGold
import com.v2ray.ang.util.ShareQr

private val QrSize = 240.dp

/**
 * Cassanova: the "Bagikan" sheet of one config. A plain/locked switch changes the QR code and the
 * link together; one button copies the link shown. The QR card carries the config name and the
 * app, so a screenshot of it explains itself. The screen is at full brightness while it is open.
 */
@Composable
internal fun ShareSheet(sheet: ShareSheetUiState, onAction: (MainAction) -> Unit) {
    val dismiss = { onAction(MainAction.DismissShareSheet) }
    val hasQr = sheet.plainQr != null || sheet.lockedQr != null
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        // Full brightness for scanning from another phone; only this dialog window is changed.
        DisposableEffect(view, hasQr) {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (hasQr && window != null) {
                window.attributes = window.attributes.apply { screenBrightness = 1f }
            }
            onDispose {
                window?.let {
                    it.attributes = it.attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
                }
            }
        }
        val outside = remember { MutableInteractionSource() }
        val inside = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = outside, indication = null, onClick = dismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    // Taps inside the sheet must not reach the dismissing background.
                    .clickable(interactionSource = inside, indication = null, onClick = {})
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                SheetContent(sheet, hasQr, onAction)
            }
        }
    }
}

@Composable
private fun ColumnScope.SheetContent(sheet: ShareSheetUiState, hasQr: Boolean, onAction: (MainAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .align(Alignment.CenterHorizontally)
            .width(40.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.outline)
    )
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.share_sheet_title), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Text(typeParts(sheet.profile, locked = false).joinToString(" · "), fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (hasQr) {
            Text("☀ " + stringResource(R.string.share_brightness), fontSize = 11.sp, color = colors.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(12.dp))

    if (sheet.plainLink == null) {
        // Custom JSON and group configs: no link or QR, only the full JSON.
        Text(stringResource(R.string.share_complex_note), fontSize = 13.sp, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        GoldButton(stringResource(R.string.share_copy_json)) { onAction(MainAction.ShareFullContent(sheet.guid)) }
        Spacer(Modifier.height(8.dp))
        return
    }

    val locked = sheet.locked && sheet.lockedLink != null
    if (sheet.lockedLink != null) {
        ModeSwitch(locked) { onAction(MainAction.SetShareLocked(it)) }
        Spacer(Modifier.height(14.dp))
    }
    val link = if (locked) sheet.lockedLink!! else sheet.plainLink
    val qr = if (locked) sheet.lockedQr else sheet.plainQr
    if (qr != null) {
        QrCard(sheet.remarks, qr, locked)
        Spacer(Modifier.height(10.dp))
    }
    Text(
        text = stringResource(if (locked) R.string.share_note_locked else R.string.share_note_plain) +
            if (ShareQr.isDense(link)) "\n" + stringResource(R.string.share_note_dense) else "",
        fontSize = 12.sp,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = link,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
    Spacer(Modifier.height(12.dp))
    GoldButton(stringResource(if (locked) R.string.share_copy_locked else R.string.share_copy_link)) {
        onAction(if (locked) MainAction.ShareLocked(sheet.guid) else MainAction.ShareClipboard(sheet.guid))
    }
    Spacer(Modifier.height(10.dp))
    if (locked) {
        Text(
            stringResource(R.string.share_json_locked),
            fontSize = 12.sp,
            color = colors.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    } else {
        Text(
            stringResource(R.string.share_copy_json_advanced),
            fontSize = 12.sp,
            color = colors.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) { onAction(MainAction.ShareFullContent(sheet.guid)) }
                .padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun ModeSwitch(locked: Boolean, onSelect: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.background)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(false, true).forEach { option ->
            val selected = option == locked
            val bg = when {
                !selected -> Color.Transparent
                option -> BrandGold
                else -> colors.surfaceContainerHighest
            }
            val fg = when {
                !selected -> colors.onSurfaceVariant
                option -> OnBrandGold
                else -> colors.onSurface
            }
            Text(
                text = if (option) "🔒 " + stringResource(R.string.share_mode_locked) else stringResource(R.string.share_mode_plain),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(bg)
                    .selectable(selected = selected, role = Role.Tab) { onSelect(option) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun QrCard(remarks: String, qr: android.graphics.Bitmap, locked: Boolean) {
    val app = stringResource(R.string.app_name)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .then(if (locked) Modifier.border(BorderStroke(3.dp, BrandGold), RoundedCornerShape(22.dp)).padding(4.dp) else Modifier)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = stringResource(R.string.acc_qr_code),
                    modifier = Modifier.size(QrSize),
                )
                // Small centre badge; ShareQr uses error correction Q so the code stays readable.
                Box(
                    modifier = Modifier
                        .size(QrSize * ShareQr.BADGE_FRACTION)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.White)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF0E1220)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(if (locked) R.drawable.ic_lock_24dp else R.drawable.ic_stat_name),
                        contentDescription = null,
                        tint = BrandGold,
                        modifier = Modifier.size(QrSize * ShareQr.BADGE_FRACTION * 0.55f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.width(QrSize),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = remarks,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0E1220),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (locked) "🔒 " + stringResource(R.string.share_tag_locked, app) else app,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (locked) OnBrandGold else Color(0xFF2F3A5A),
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (locked) BrandGold else Color(0xFFE6EAF4))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun GoldButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold,
        color = OnBrandGold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BrandGold)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
    )
}
