package com.modatherali.linkloot.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.modatherali.linkloot.LinkLootApplication
import com.modatherali.linkloot.MainActivity
import com.modatherali.linkloot.R
import com.modatherali.linkloot.domain.model.DownloadStatus
import com.modatherali.linkloot.domain.model.DownloadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var startedForeground = false
    private var lastStartId: Int = 0
    private var lastNotifyTimeMs: Long = 0L
    private var lastRunningCount: Int = -1

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val app = application as LinkLootApplication
        val coordinator = app.appContainer.downloadCoordinator

        if (!startedForeground) {
            val initialNotification = buildNotification(emptyList())
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, initialNotification, fgsType)
            startedForeground = true
        }

        when (intent?.action) {
            ACTION_PAUSE_ALL -> {
                coordinator.pauseAll()
            }
            ACTION_CANCEL_ALL -> {
                coordinator.cancelAllActive()
            }
        }

        if (observerJob == null) {
            observerJob = serviceScope.launch {
                app.appContainer.downloadRepository.observeAllTasks().collectLatest { tasks ->
                    val running = tasks.filter {
                        it.status == DownloadStatus.QUEUED ||
                            it.status == DownloadStatus.DOWNLOADING ||
                            it.status == DownloadStatus.MUXING
                    }
                    if (running.isEmpty()) {
                        ServiceCompat.stopForeground(
                            this@DownloadForegroundService,
                            ServiceCompat.STOP_FOREGROUND_REMOVE
                        )
                        startedForeground = false
                        stopSelfResult(lastStartId)
                    } else {
                        val now = System.currentTimeMillis()
                        val countChanged = running.size != lastRunningCount
                        if (countChanged || now - lastNotifyTimeMs >= 500L) {
                            lastRunningCount = running.size
                            lastNotifyTimeMs = now
                            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            manager.notify(NOTIFICATION_ID, buildNotification(running))
                        }
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(running: List<DownloadTask>): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, DownloadForegroundService::class.java).setAction(ACTION_PAUSE_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, DownloadForegroundService::class.java).setAction(ACTION_CANCEL_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val current = running.firstOrNull {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.MUXING
        } ?: running.firstOrNull()

        val title = getString(R.string.notif_downloading_title, running.size.coerceAtLeast(1))
        val progressPercent = current?.progressFraction?.let { (it * 100f).roundToInt() }
        val text = when {
            current == null -> getString(R.string.status_queued)
            current.status == DownloadStatus.MUXING -> getString(R.string.notif_muxing_text, current.fileName)
            progressPercent != null -> getString(R.string.notif_progress_text, current.fileName, progressPercent)
            else -> current.fileName
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .setProgress(100, progressPercent ?: 0, progressPercent == null)
            .addAction(0, getString(R.string.action_pause_all), pauseIntent)
            .addAction(0, getString(R.string.action_cancel), cancelIntent)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "linkloot_downloads"
        const val NOTIFICATION_ID = 4041
        const val ACTION_PAUSE_ALL = "com.modatherali.linkloot.action.PAUSE_ALL"
        const val ACTION_CANCEL_ALL = "com.modatherali.linkloot.action.CANCEL_ALL"
    }
}
