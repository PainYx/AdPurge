package com.ads.purge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * ★ v3.1.1 处理期间前台服务保命壳
 * 作用：把进程优先级从后台缓存提升到前台服务级，用户切后台回微信时进程不被系统杀死。
 * 处理逻辑仍在 MainActivity 的协程中运行，本服务只负责"占住前台"；
 * 若进程仍被用户主动划掉任务卡/厂商强杀，由断点续传（v3.1）兜底恢复。
 */
class ProcessingService : Service() {

    companion object {
        private const val CHANNEL_ID = "adpurge_processing"
        private const val NOTIF_ID = 1002
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // 启动后 5 秒内必须调用 startForeground，否则系统抛 ForegroundServiceDidNotStartInTimeException
            startForeground(NOTIF_ID, buildNotification())
        } catch (_: Exception) {
            // 极少数机型失败时退化为普通服务（进程优先级仍高于纯后台）
        }
        // 不粘性：处理中被杀不自动重启，避免与断点续传检查点竞争
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "处理进度",
                NotificationManager.IMPORTANCE_LOW // 低优先级：静默常驻不打扰
            ).apply {
                description = "APK 去广告处理期间保持运行"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("AdPurge 正在处理 APK")
            .setContentText("可切后台等待，完成后自动通知")
            .setOngoing(true) // 常驻不可划掉，防止误删
            .setContentIntent(contentIntent)
            .build()
    }
}
