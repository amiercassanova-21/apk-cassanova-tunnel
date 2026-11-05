package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.ui.compose.AppDivider

/**
 * Style B status bar: connection line, today's data, and the gold power button. Tapping the
 * text tests the current server, as before.
 */
@Composable
fun MainBottomBar(
    statusLine: String,
    dataLine: String,
    isRunning: Boolean,
    onAction: (MainAction) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val palette = configPalette()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        AppDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button) { onAction(MainAction.TestCurrentServer) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = statusLine,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRunning) palette.green else colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dataLine,
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PowerTile(running = isRunning, size = 60.dp, corner = 18.dp) { onAction(MainAction.ToggleService) }
        }
    }
}
