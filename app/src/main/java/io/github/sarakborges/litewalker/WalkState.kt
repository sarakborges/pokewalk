package io.github.sarakborges.litewalker

import android.content.Context
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

object WalkState {

    const val MIN_SPEED_KMH = 1
    const val MAX_SPEED_KMH = 8
    const val DEFAULT_SPEED_KMH = 7

    private const val BASE_STEPS_PER_KM = 1304.0
    private const val PREFS = "litewalker_state"
    private const val KEY_RUNNING = "running"
    private const val KEY_START_TIME = "start_time"
    private const val KEY_COMPLETED_CHUNKS = "completed_chunks"
    private const val KEY_FINISHED = "finished"
    private const val KEY_STOPPED = "stopped"
    private const val KEY_ERROR = "error"
    private const val KEY_TARGET_KM = "target_km"
    private const val KEY_PREFERRED_KM = "preferred_km"
    private const val KEY_TARGET_SPEED = "target_speed_kmh"
    private const val KEY_PREFERRED_SPEED = "preferred_speed_kmh"
    private const val KEY_STEP_PLAN = "step_plan"
    private const val KEY_FINAL_DURATION = "final_duration"
    private const val KEY_FINAL_DISTANCE = "final_distance"
    private const val KEY_FINAL_STEPS = "final_steps"
    private const val KEY_RUN_HISTORY = "run_history"
    private const val MAX_RUN_HISTORY = 5

    data class Metrics(
        val durationMs: Long,
        val distanceMeters: Double,
        val steps: Long
    )

    data class RunEntry(
        val timestampMillis: Long,
        val durationMs: Long,
        val distanceMeters: Double,
        val steps: Long,
        val completed: Boolean
    )

    fun begin(
        context: Context,
        distanceKm: Int,
        speedKmh: Int = preferredSpeedKmh(context),
        startTimeMillis: Long = System.currentTimeMillis()
    ) {
        val safeDistance = distanceKm.coerceIn(1, 20)
        val safeSpeed = speedKmh.coerceIn(MIN_SPEED_KMH, MAX_SPEED_KMH)
        val durationMs = calculateDurationMs(safeDistance, safeSpeed)
        val stepPlan = buildStepPlan(
            safeSpeed,
            durationMs,
            calculateChunkCount(durationMs)
        )

        prefs(context).edit()
            .putBoolean(KEY_RUNNING, true)
            .putLong(KEY_START_TIME, startTimeMillis)
            .putInt(KEY_COMPLETED_CHUNKS, 0)
            .putBoolean(KEY_FINISHED, false)
            .putBoolean(KEY_STOPPED, false)
            .putInt(KEY_TARGET_KM, safeDistance)
            .putInt(KEY_PREFERRED_KM, safeDistance)
            .putInt(KEY_TARGET_SPEED, safeSpeed)
            .putInt(KEY_PREFERRED_SPEED, safeSpeed)
            .putString(KEY_STEP_PLAN, stepPlan.joinToString(","))
            .putLong(KEY_FINAL_DURATION, 0L)
            .putString(KEY_FINAL_DISTANCE, "0")
            .putLong(KEY_FINAL_STEPS, 0L)
            .remove(KEY_ERROR)
            .apply()
    }

    fun ensureStarted(context: Context): Long {
        val preferences = prefs(context)
        val existing = preferences.getLong(KEY_START_TIME, 0L)
        if (preferences.getBoolean(KEY_RUNNING, false) && existing > 0L) {
            return existing
        }

        val now = System.currentTimeMillis()
        begin(context, preferredDistanceKm(context), preferredSpeedKmh(context), now)
        return now
    }

    private fun buildStepPlan(speedKmh: Int, durationMs: Long, chunks: Int): List<Int> {
        val averageStepsPerMinute = speedKmh * BASE_STEPS_PER_KM / 60.0
        return List(chunks) { index ->
            val variedRate = (averageStepsPerMinute + Random.nextInt(-5, 6))
                .coerceAtLeast(1.0)
            (variedRate * chunkDurationMs(durationMs, chunks, index) / 60_000.0)
                .roundToInt()
                .coerceAtLeast(1)
        }
    }

    fun setPreferredDistanceKm(context: Context, km: Int) {
        prefs(context).edit().putInt(KEY_PREFERRED_KM, km.coerceIn(1, 20)).apply()
    }

    fun setPreferredSpeedKmh(context: Context, speedKmh: Int) {
        prefs(context).edit()
            .putInt(KEY_PREFERRED_SPEED, speedKmh.coerceIn(MIN_SPEED_KMH, MAX_SPEED_KMH))
            .apply()
    }

    fun preferredDistanceKm(context: Context): Int =
        prefs(context).getInt(KEY_PREFERRED_KM, 5).coerceIn(1, 20)

    fun targetDistanceKm(context: Context): Int =
        prefs(context)
            .getInt(KEY_TARGET_KM, preferredDistanceKm(context))
            .coerceIn(1, 20)

    fun preferredSpeedKmh(context: Context): Int =
        prefs(context)
            .getInt(KEY_PREFERRED_SPEED, DEFAULT_SPEED_KMH)
            .coerceIn(MIN_SPEED_KMH, MAX_SPEED_KMH)

    fun targetSpeedKmh(context: Context): Int =
        prefs(context)
            .getInt(KEY_TARGET_SPEED, preferredSpeedKmh(context))
            .coerceIn(MIN_SPEED_KMH, MAX_SPEED_KMH)

    fun targetDistanceMeters(context: Context): Double = targetDistanceKm(context) * 1_000.0

    fun calculateDurationMs(distanceKm: Int, speedKmh: Int): Long =
        (distanceKm.coerceIn(1, 20) * 3_600_000.0 /
            speedKmh.coerceIn(MIN_SPEED_KMH, MAX_SPEED_KMH))
            .roundToLong()
            .coerceAtLeast(1_000L)

    private fun calculateChunkCount(durationMs: Long): Int =
        ceil(durationMs / 60_000.0).toInt().coerceAtLeast(1)

    fun totalDurationMs(context: Context): Long =
        calculateDurationMs(targetDistanceKm(context), targetSpeedKmh(context))

    fun chunkCount(context: Context): Int = calculateChunkCount(totalDurationMs(context))

    private fun chunkDurationMs(totalDurationMs: Long, chunks: Int, index: Int): Long {
        if (index !in 0 until chunks) return 0L
        return (totalDurationMs - index * 60_000L).coerceIn(0L, 60_000L)
    }

    fun chunkDurationMs(context: Context, index: Int): Long =
        chunkDurationMs(totalDurationMs(context), chunkCount(context), index)

    fun chunkStartOffsetMs(index: Int): Long = index.coerceAtLeast(0) * 60_000L

    fun chunkEndOffsetMs(context: Context, index: Int): Long =
        (chunkStartOffsetMs(index) + chunkDurationMs(context, index))
            .coerceAtMost(totalDurationMs(context))

    fun fullChunksElapsed(context: Context, elapsedMs: Long): Int {
        val safeElapsed = elapsedMs.coerceIn(0L, totalDurationMs(context))
        if (safeElapsed >= totalDurationMs(context)) return chunkCount(context)
        return (safeElapsed / 60_000L).toInt().coerceIn(0, chunkCount(context))
    }

    fun metersPerMinute(context: Context): Double =
        targetSpeedKmh(context) * 1_000.0 / 60.0

    fun distanceForChunk(context: Context, index: Int): Double {
        val durationMs = chunkDurationMs(context, index)
        if (durationMs <= 0L) return 0.0

        val calculated = metersPerMinute(context) * durationMs / 60_000.0
        if (index != chunkCount(context) - 1) return calculated

        val priorDistance = (0 until index).sumOf { priorIndex ->
            metersPerMinute(context) * chunkDurationMs(context, priorIndex) / 60_000.0
        }
        return (targetDistanceMeters(context) - priorDistance).coerceAtLeast(0.0)
    }

    fun stepPlan(context: Context): List<Int> {
        val chunks = chunkCount(context)
        val stored = prefs(context).getString(KEY_STEP_PLAN, "").orEmpty()
            .split(',')
            .mapNotNull(String::toIntOrNull)
        return if (stored.size == chunks) {
            stored
        } else {
            buildStepPlan(targetSpeedKmh(context), totalDurationMs(context), chunks)
        }
    }

    fun stepsForChunk(context: Context, index: Int): Int =
        stepPlan(context).getOrElse(index) { 0 }

    fun metricsAt(context: Context, elapsedMs: Long): Metrics {
        val totalDuration = totalDurationMs(context)
        val safeElapsed = elapsedMs.coerceIn(0L, totalDuration)
        val chunks = chunkCount(context)
        val plan = stepPlan(context)
        val fullChunks = fullChunksElapsed(context, safeElapsed)

        var distance = (0 until fullChunks).sumOf { distanceForChunk(context, it) }
        var steps = plan.take(fullChunks).sumOf { it.toLong() }

        if (fullChunks < chunks && safeElapsed < totalDuration) {
            val elapsedInChunk = (safeElapsed - chunkStartOffsetMs(fullChunks)).coerceAtLeast(0L)
            val fraction = (
                elapsedInChunk.toDouble() /
                    chunkDurationMs(context, fullChunks).coerceAtLeast(1L).toDouble()
                ).coerceIn(0.0, 1.0)
            distance += distanceForChunk(context, fullChunks) * fraction
            steps += (plan.getOrElse(fullChunks) { 0 } * fraction).roundToLong()
        }

        return Metrics(
            durationMs = safeElapsed,
            distanceMeters = distance.coerceIn(0.0, targetDistanceMeters(context)),
            steps = steps.coerceAtLeast(0L)
        )
    }

    fun markChunkWritten(context: Context, completedChunks: Int) {
        prefs(context).edit()
            .putInt(KEY_COMPLETED_CHUNKS, completedChunks.coerceIn(0, chunkCount(context)))
            .apply()
    }

    fun finish(context: Context) {
        saveResult(
            context = context,
            durationMs = totalDurationMs(context),
            distanceMeters = targetDistanceMeters(context),
            steps = stepPlan(context).sumOf { it.toLong() },
            finished = true,
            stopped = false
        )
    }

    fun stop(context: Context, durationMs: Long, distanceMeters: Double, steps: Long) {
        saveResult(
            context = context,
            durationMs = durationMs.coerceIn(0L, totalDurationMs(context)),
            distanceMeters = distanceMeters.coerceIn(0.0, targetDistanceMeters(context)),
            steps = steps.coerceAtLeast(0L),
            finished = false,
            stopped = true
        )
    }

    private fun saveResult(
        context: Context,
        durationMs: Long,
        distanceMeters: Double,
        steps: Long,
        finished: Boolean,
        stopped: Boolean
    ) {
        prefs(context).edit()
            .putBoolean(KEY_RUNNING, false)
            .putBoolean(KEY_FINISHED, finished)
            .putBoolean(KEY_STOPPED, stopped)
            .putLong(KEY_FINAL_DURATION, durationMs)
            .putString(KEY_FINAL_DISTANCE, distanceMeters.toString())
            .putLong(KEY_FINAL_STEPS, steps)
            .remove(KEY_ERROR)
            .apply()

        if (durationMs > 0L || distanceMeters > 0.0 || steps > 0L) {
            addRunToHistory(
                context,
                RunEntry(
                    timestampMillis = System.currentTimeMillis(),
                    durationMs = durationMs,
                    distanceMeters = distanceMeters,
                    steps = steps,
                    completed = finished
                )
            )
        }
    }

    fun recentRuns(context: Context): List<RunEntry> =
        prefs(context).getString(KEY_RUN_HISTORY, "").orEmpty()
            .split(';')
            .mapNotNull(::decodeRun)
            .take(MAX_RUN_HISTORY)

    fun clearRunHistory(context: Context) {
        prefs(context).edit().remove(KEY_RUN_HISTORY).apply()
    }

    private fun addRunToHistory(context: Context, run: RunEntry) {
        val updated = (listOf(run) + recentRuns(context)).take(MAX_RUN_HISTORY)
        prefs(context).edit()
            .putString(KEY_RUN_HISTORY, updated.joinToString(";") { encodeRun(it) })
            .apply()
    }

    private fun encodeRun(run: RunEntry): String = listOf(
        run.timestampMillis,
        run.durationMs,
        run.distanceMeters,
        run.steps,
        if (run.completed) 1 else 0
    ).joinToString("|")

    private fun decodeRun(value: String): RunEntry? {
        val fields = value.split('|')
        if (fields.size != 5) return null
        return RunEntry(
            timestampMillis = fields[0].toLongOrNull() ?: return null,
            durationMs = fields[1].toLongOrNull() ?: return null,
            distanceMeters = fields[2].toDoubleOrNull() ?: return null,
            steps = fields[3].toLongOrNull() ?: return null,
            completed = fields[4] == "1"
        )
    }

    fun finalMetrics(context: Context): Metrics = Metrics(
        durationMs = prefs(context).getLong(KEY_FINAL_DURATION, 0L),
        distanceMeters = prefs(context).getString(KEY_FINAL_DISTANCE, "0")
            ?.toDoubleOrNull() ?: 0.0,
        steps = prefs(context).getLong(KEY_FINAL_STEPS, 0L)
    )

    fun fail(context: Context, message: String) {
        prefs(context).edit()
            .putBoolean(KEY_RUNNING, false)
            .putBoolean(KEY_FINISHED, false)
            .putBoolean(KEY_STOPPED, false)
            .putString(KEY_ERROR, message)
            .apply()
    }

    fun isRunning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RUNNING, false)

    fun startTimeMillis(context: Context): Long =
        prefs(context).getLong(KEY_START_TIME, 0L)

    fun completedChunks(context: Context): Int =
        prefs(context).getInt(KEY_COMPLETED_CHUNKS, 0).coerceIn(0, chunkCount(context))

    fun isFinished(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FINISHED, false)

    fun isStopped(context: Context): Boolean =
        prefs(context).getBoolean(KEY_STOPPED, false)

    fun hasResult(context: Context): Boolean = isFinished(context) || isStopped(context)

    fun error(context: Context): String? = prefs(context).getString(KEY_ERROR, null)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
