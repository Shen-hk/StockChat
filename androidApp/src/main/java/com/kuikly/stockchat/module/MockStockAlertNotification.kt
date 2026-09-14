package com.kuikly.stockchat.module

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kuikly.stockchat.KuiklyRenderActivity

/**
 * 用于风险预警页的本地 Mock 通知。系统横幅是否展示仍由用户的通知渠道设置决定；
 * 这里请求 Android 允许的最高常规提醒等级，不使用仅适用于来电/闹钟的全屏通知。
 */
internal object MockStockAlertNotification {
    const val ACTION_POST = "com.kuikly.stockchat.action.POST_MOCK_STOCK_ALERT"
    const val EXTRA_VIBRATE = "vibrate"
    private const val CHANNEL_VIBRATE = "stockchat_mock_stock_alert_v3"
    private const val CHANNEL_SILENT = "stockchat_mock_stock_alert_silent_v3"
    private const val REQUEST_CODE = 83023
    private val vibrationPattern = longArrayOf(0, 180, 80, 220)

    fun schedule(context: Context, vibrate: Boolean, delayMillis: Long) {
        if (delayMillis <= 0L) {
            post(context, vibrate)
            return
        }
        val intent = Intent(context, MockStockAlertNotificationReceiver::class.java)
            .setAction(ACTION_POST)
            .putExtra(EXTRA_VIBRATE, vibrate)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAtMillis = System.currentTimeMillis() + delayMillis
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            manager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun post(context: Context, vibrate: Boolean) {
        val channelId = if (vibrate) CHANNEL_VIBRATE else CHANNEL_SILENT
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Mock 股票预警", if (vibrate) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_LOW).apply {
                description = "风险预警页的本地演示通知"
                enableVibration(vibrate)
                vibrationPattern = if (vibrate) this@MockStockAlertNotification.vibrationPattern else longArrayOf(0)
            },
        )
        val openAppIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE + 1,
            Intent(context, KuiklyRenderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("贵州茅台上涨 4.28%（测试）")
            .setContentText("AI 归因（模拟）：模拟行情上涨 ¥68.80，请以真实数据为准。")
            .setStyle(NotificationCompat.BigTextStyle().bigText("AI 归因（模拟）：模拟行情上涨 ¥68.80（+4.28%）。本条仅用于演示预警样式，请以真实行情、公告与数据源为准。"))
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(if (vibrate) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_LOW)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setVibrate(if (vibrate) vibrationPattern else longArrayOf(0))
            .setOnlyAlertOnce(false)
            .build()
        NotificationManagerCompat.from(context).notify((System.currentTimeMillis() and 0x0FFF_FFFF).toInt(), notification)
    }
}

internal class MockStockAlertNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MockStockAlertNotification.ACTION_POST) {
            MockStockAlertNotification.post(context, intent.getBooleanExtra(MockStockAlertNotification.EXTRA_VIBRATE, true))
        }
    }
}
