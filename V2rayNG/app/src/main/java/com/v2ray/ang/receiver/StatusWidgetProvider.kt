package com.v2ray.ang.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.TrafficTracker
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil

/**
 * Cassanova status widget (4×1): connection state, server, data today and ping, with a power
 * button. Lives in the :daemon process like [WidgetProvider], so [CoreServiceManager.isRunning]
 * is authoritative. Refreshed on state broadcasts and by the speed loop while connected.
 */
class StatusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        render(context, appWidgetManager, appWidgetIds, CoreServiceManager.isRunning())
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> {
                if (CoreServiceManager.isRunning()) {
                    LauncherManager.stopService(context)
                } else {
                    LauncherManager.startServiceFromToggle(context)
                }
            }

            AppConfig.BROADCAST_ACTION_ACTIVITY -> when (intent.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING, AppConfig.MSG_STATE_START_SUCCESS -> refresh(context, running = true)
                AppConfig.MSG_STATE_NOT_RUNNING, AppConfig.MSG_STATE_START_FAILURE,
                AppConfig.MSG_STATE_STOP_SUCCESS -> refresh(context, running = false)
            }
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "${AppConfig.ANG_PACKAGE}.action.status_widget.toggle"

        /** Redraws every placed status widget; cheap no-op when none is placed. */
        fun refresh(context: Context, running: Boolean = CoreServiceManager.isRunning()) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(context, StatusWidgetProvider::class.java))
                if (ids.isEmpty()) return
                render(context, manager, ids, running)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Status widget refresh failed", e)
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray, running: Boolean) {
            val views = RemoteViews(context.packageName, R.layout.widget_status)
            val snapshot = TrafficTracker.snapshot()
            val serverName = MmkvManager.getSelectServer()
                ?.let { MmkvManager.decodeServerConfig(it)?.remarks }
                .orEmpty()
                .ifBlank { context.getString(R.string.home_no_server) }
            val ping = TrafficTracker.lastPing()

            views.setTextViewText(
                R.id.widget_status_state,
                context.getString(if (running) R.string.home_connected else R.string.home_disconnected)
            )
            views.setTextColor(R.id.widget_status_state, if (running) COLOR_GREEN else COLOR_MUTED)
            views.setTextViewText(R.id.widget_status_server, serverName)
            val detail = buildString {
                append(context.getString(R.string.home_status_data_today, snapshot.todayBytes.toTrafficString()))
                if (running && ping > 0) {
                    append(" · ")
                    append(context.getString(R.string.server_test_delay_value, ping))
                }
            }
            views.setTextViewText(R.id.widget_status_detail, detail)
            views.setInt(
                R.id.widget_status_power, "setBackgroundResource",
                if (running) R.drawable.widget_power_on else R.drawable.widget_power_off
            )
            views.setInt(R.id.widget_status_power_icon, "setColorFilter", if (running) COLOR_ON_GOLD else COLOR_WHITE)
            views.setContentDescription(
                R.id.widget_status_power,
                context.getString(if (running) R.string.acc_stop else R.string.acc_start)
            )

            val toggle = Intent(context, StatusWidgetProvider::class.java).setAction(ACTION_TOGGLE)
            views.setOnClickPendingIntent(
                R.id.widget_status_power,
                PendingIntent.getBroadcast(
                    context, R.id.widget_status_power, toggle,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(
                R.id.widget_status_root,
                PendingIntent.getActivity(
                    context, R.id.widget_status_root, open,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            manager.updateAppWidget(ids, views)
        }

        private const val COLOR_GREEN = 0xFF6EE7A0.toInt()
        private const val COLOR_MUTED = 0xFFC9CFE0.toInt()
        private const val COLOR_WHITE = 0xFFFFFFFF.toInt()
        private const val COLOR_ON_GOLD = 0xFF1B1608.toInt()
    }
}
