package io.github.sarakborges.litewalker

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
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

class WalkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var notificationTicker: Job? = null
    @Volatile private var stopRequested = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppPreferences.localizedContext(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    ACTIVE_CHANNEL_ID,
                    getString(R.string.channel_active_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.channel_active_description)
                    setShowBadge(false)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    RESULT_CHANNEL_ID,
                    getString(R.string.channel_result_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = getString(R.string.channel_result_description)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    MILESTONE_CHANNEL_ID,
                    getString(R.string.channel_milestone_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = getString(R.string.channel_milestone_description)
                    setShowBadge(true)
                }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRequested = true
            job?.cancel()
            return START_NOT_STICKY
        }

        if (job?.isActive == true) return START_STICKY
        stopRequested = false
        val start = WalkState.ensureStarted(this)
        startAsForeground(start)
        startNotificationTicker(start)
        job = scope.launch { runWalk(start) }
        return START_STICKY
    }

    private fun startNotificationTicker(startMillis: Long) {
        notificationTicker?.cancel()
        notificationTicker = scope.launch {
            while (isActive && WalkState.isRunning(this@WalkService)) {
                val metrics = WalkState.metricsAt(
                    this@WalkService,
                    elapsedAt(startMillis)
                )
                getSystemService(NotificationManager::class.java).notify(
                    ACTIVE_NOTIFICATION_ID,
                    liveNotification(startMillis)
                )
                notifyKilometerMilestone(metrics)
                delay(1_000L)
            }
        }
    }

    private fun liveNotification(startMillis: Long): android.app.Notification {
        val endless = WalkState.isEndless(this)
        val totalMs = WalkState.totalDurationMs(this).coerceAtLeast(1L)
        val elapsedMs = elapsedAt(startMillis)
        val metrics = WalkState.metricsAt(this, elapsedMs)
        val text = getString(
            R.string.notification_progress,
            formatDuration(metrics.durationMs),
            formatDistance(metrics.distanceMeters),
            formatSteps(metrics.steps),
            WalkState.targetSpeedKmh(this)
        )

        val builder = NotificationCompat.Builder(this, ACTIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_walk)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openAppPendingIntent())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (endless) {
            builder.setProgress(0, 0, true)
        } else {
            val elapsedSeconds = (elapsedMs / 1_000L).toInt()
            val totalSeconds = (totalMs / 1_000L).toInt().coerceAtLeast(1)
            builder.setProgress(
                totalSeconds,
                elapsedSeconds.coerceIn(0, totalSeconds),
                false
            )
        }
        return builder.build()
    }

    private fun elapsedAt(startMillis: Long): Long {
        val elapsed = (System.currentTimeMillis() - startMillis).coerceAtLeast(0L)
        return if (WalkState.isEndless(this)) {
            elapsed
        } else {
            elapsed.coerceAtMost(WalkState.totalDurationMs(this))
        }
    }

    private fun notifyKilometerMilestone(metrics: WalkState.Metrics) {
        val completedKilometers = (metrics.distanceMeters / 1_000.0)
            .toInt()
            .coerceAtLeast(0)
        if (completedKilometers <= WalkState.notifiedKilometers(this)) return

        getSystemService(NotificationManager::class.java).notify(
            MILESTONE_NOTIFICATION_ID,
            milestoneNotification(completedKilometers, metrics)
        )
        WalkState.markKilometersNotified(this, completedKilometers)
    }

    private fun milestoneNotification(
        completedKilometers: Int,
        metrics: WalkState.Metrics
    ): android.app.Notification = NotificationCompat.Builder(this, MILESTONE_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_walk)
        .setContentTitle(
            resources.getQuantityString(
                R.plurals.notification_kilometer_title,
                completedKilometers,
                completedKilometers
            )
        )
        .setContentText(
            getString(
                R.string.notification_kilometer_body,
                formatDuration(metrics.durationMs),
                formatSteps(metrics.steps)
            )
        )
        .setContentIntent(openAppPendingIntent())
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setAutoCancel(true)
        .build()

    private fun resultNotification(title: String, metrics: WalkState.Metrics): android.app.Notification {
        val text = getString(
            R.string.notification_result,
            formatDuration(metrics.durationMs),
            formatDistance(metrics.distanceMeters),
            formatSteps(metrics.steps)
        )
        return NotificationCompat.Builder(this, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_walk)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppPendingIntent())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    private fun formatDistance(distanceMeters: Double): String =
        NumberFormat.getNumberInstance(resources.configuration.locales[0]).run {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            format(distanceMeters / 1_000.0)
        }

    private fun formatSteps(steps: Long): String =
        NumberFormat.getIntegerInstance(resources.configuration.locales[0]).format(steps)

    private suspend fun runWalk(startMillis: Long) {
        val client = HealthConnectClient.getOrCreate(this)

        // Use a fresh identifier to keep each activity's Health Connect records distinct.
        val sessionId = UUID.randomUUID().toString()
        var resultTitle: String? = null
        var resultMetrics: WalkState.Metrics? = null

        try {
            val first = WalkState.completedChunks(this)
            if (WalkState.isEndless(this)) {
                var index = first
                while (true) {
                    writeScheduledChunk(client, sessionId, startMillis, index)
                    index += 1
                }
            } else {
                for (index in first until WalkState.chunkCount(this)) {
                    writeScheduledChunk(client, sessionId, startMillis, index)
                }

                notifyKilometerMilestone(
                    WalkState.metricsAt(this, WalkState.totalDurationMs(this))
                )
                WalkState.finish(this)
                resultTitle = getString(R.string.notification_completed)
                resultMetrics = WalkState.finalMetrics(this)
            }
        } catch (cancelled: CancellationException) {
            if (!stopRequested) throw cancelled
        } catch (t: Throwable) {
            WalkState.fail(this, t.message ?: t.javaClass.simpleName)
        } finally {
            if (stopRequested) {
                resultMetrics = withContext(NonCancellable) {
                    finalizeStoppedWalk(client, startMillis, sessionId)
                }
                resultTitle = getString(R.string.notification_saved)
            }

            notificationTicker?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            if (resultTitle != null && resultMetrics != null) {
                getSystemService(NotificationManager::class.java).notify(
                    RESULT_NOTIFICATION_ID,
                    resultNotification(resultTitle, resultMetrics)
                )
            }
            stopSelf()
        }
    }

    private suspend fun writeScheduledChunk(
        client: HealthConnectClient,
        sessionId: String,
        startMillis: Long,
        index: Int
    ) {
        val intervalStart = Instant.ofEpochMilli(
            startMillis + WalkState.chunkStartOffsetMs(index)
        )
        val intervalEnd = Instant.ofEpochMilli(
            startMillis + WalkState.chunkEndOffsetMs(this, index)
        )
        val waitMs = intervalEnd.toEpochMilli() - System.currentTimeMillis()
        if (waitMs > 0L) delay(waitMs)

        writeChunk(
            client = client,
            sessionId = sessionId,
            index = index,
            intervalStart = intervalStart,
            intervalEnd = intervalEnd,
            meters = WalkState.distanceForChunk(this, index),
            steps = WalkState.stepsForChunk(this, index).toLong()
        )
        WalkState.markChunkWritten(this, index + 1)
    }

    private suspend fun finalizeStoppedWalk(
        client: HealthConnectClient,
        startMillis: Long,
        sessionId: String
    ): WalkState.Metrics {
        val totalDuration = WalkState.totalDurationMs(this)
        val durationMs = (System.currentTimeMillis() - startMillis).coerceIn(0L, totalDuration)
        val chunks = WalkState.chunkCount(this)
        val elapsedFullChunks = WalkState.fullChunksElapsed(this, durationMs)
        var completed = WalkState.completedChunks(this).coerceIn(0, chunks)

        // Reconcile any complete minute that elapsed before the stop request.
        for (index in completed until elapsedFullChunks) {
            val intervalStart = Instant.ofEpochMilli(
                startMillis + WalkState.chunkStartOffsetMs(index)
            )
            val intervalEnd = Instant.ofEpochMilli(
                startMillis + WalkState.chunkEndOffsetMs(this, index)
            )
            writeChunk(
                client = client,
                sessionId = sessionId,
                index = index,
                intervalStart = intervalStart,
                intervalEnd = intervalEnd,
                meters = WalkState.distanceForChunk(this, index),
                steps = WalkState.stepsForChunk(this, index).toLong()
            )
            WalkState.markChunkWritten(this, index + 1)
            completed = index + 1
        }

        // Persist the elapsed fraction of the current minute when the user stops early.
        if (durationMs < totalDuration && elapsedFullChunks < chunks) {
            val partialIndex = elapsedFullChunks
            val chunkStartOffset = WalkState.chunkStartOffsetMs(partialIndex)
            val elapsedInChunk = (durationMs - chunkStartOffset).coerceAtLeast(0L)
            val fullChunkDuration = WalkState.chunkDurationMs(this, partialIndex).coerceAtLeast(1L)
            val fraction = (elapsedInChunk.toDouble() / fullChunkDuration).coerceIn(0.0, 1.0)

            if (fraction > 0.0) {
                val partialStart = Instant.ofEpochMilli(startMillis + chunkStartOffset)
                val partialEnd = Instant.ofEpochMilli(startMillis + durationMs)
                val partialMeters = WalkState.distanceForChunk(this, partialIndex) * fraction
                val partialSteps = (WalkState.stepsForChunk(this, partialIndex) * fraction)
                    .roundToLong()
                    .coerceAtLeast(0L)

                writeChunk(
                    client = client,
                    sessionId = sessionId,
                    index = partialIndex,
                    intervalStart = partialStart,
                    intervalEnd = partialEnd,
                    meters = partialMeters,
                    steps = partialSteps
                )
            }
        }

        val metrics = WalkState.metricsAt(this, durationMs)
        notifyKilometerMilestone(metrics)
        WalkState.stop(this, metrics.durationMs, metrics.distanceMeters, metrics.steps)
        return metrics
    }

    private suspend fun writeChunk(
        client: HealthConnectClient,
        sessionId: String,
        index: Int,
        intervalStart: Instant,
        intervalEnd: Instant,
        meters: Double,
        steps: Long
    ) {
        if (!intervalEnd.isAfter(intervalStart)) return
        val device = Device(type = Device.TYPE_PHONE)
        val zone = ZoneId.systemDefault()

        // Distance and estimated steps are written for the same user-started interval.
        if (meters > 0.0) {
            client.insertRecords(
                listOf(
                    DistanceRecord(
                        distance = Length.meters(meters),
                        startTime = intervalStart,
                        startZoneOffset = zone.rules.getOffset(intervalStart),
                        endTime = intervalEnd,
                        endZoneOffset = zone.rules.getOffset(intervalEnd),
                        metadata = Metadata.activelyRecorded(
                            device = device,
                            clientRecordId = "$sessionId-distance-$index-${intervalEnd.toEpochMilli()}"
                        )
                    )
                )
            )
        }

        if (steps > 0L) {
            client.insertRecords(
                listOf(
                    StepsRecord(
                        count = steps,
                        startTime = intervalStart,
                        startZoneOffset = zone.rules.getOffset(intervalStart),
                        endTime = intervalEnd,
                        endZoneOffset = zone.rules.getOffset(intervalEnd),
                        metadata = Metadata.activelyRecorded(
                            device = device,
                            clientRecordId = "$sessionId-steps-$index-${intervalEnd.toEpochMilli()}"
                        )
                    )
                )
            )
        }
    }

    private fun startAsForeground(startMillis: Long) {
        val notification = liveNotification(startMillis)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                ACTIVE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(ACTIVE_NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        notificationTicker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "io.github.sarakborges.litewalker.STOP_WALK"
        private const val ACTIVE_CHANNEL_ID = "litewalker_active_v1"
        private const val RESULT_CHANNEL_ID = "litewalker_results_v1"
        private const val MILESTONE_CHANNEL_ID = "litewalker_milestones_v1"
        private const val ACTIVE_NOTIFICATION_ID = 5001
        private const val RESULT_NOTIFICATION_ID = 5002
        private const val MILESTONE_NOTIFICATION_ID = 5003
    }
}
