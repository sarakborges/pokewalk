package com.example.pokewalklite

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null

    private lateinit var actionButton: Button
    private lateinit var activityState: TextView
    private lateinit var elapsedValue: TextView
    private lateinit var distanceValue: TextView
    private lateinit var stepsValue: TextView
    private lateinit var distanceSeek: SeekBar
    private lateinit var speedSeek: SeekBar
    private lateinit var selectedDistanceLabel: TextView
    private lateinit var selectedSpeedLabel: TextView
    private lateinit var estimatedTimeLabel: TextView
    private lateinit var activityProgress: ProgressBar

    private var selectedKm = 5
    private var selectedSpeedKmh = WalkState.DEFAULT_SPEED_KMH
    private var lastErrorShown: String? = null

    private val healthPermissions = setOf(
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class)
    )

    private val healthPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(healthPermissions)) {
            ensureNotificationPermissionAndStart()
        } else {
            showMessage("Permita passos e distância no Health Connect.")
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ensureActivityPermissionAndStart()
        } else {
            showMessage("Permita notificações para iniciar a atividade.")
        }
    }

    private val activityRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startActivityRun()
        } else {
            showMessage("Permissão de atividade necessária.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        selectedKm = WalkState.preferredDistanceKm(this)
        selectedSpeedKmh = WalkState.preferredSpeedKmh(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
        }
        scroll.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        content.addView(createHero())
        content.addView(space(18))
        content.addView(createConfigurationCard())
        content.addView(space(14))
        content.addView(createActivityCard())

        val footer = TextView(this).apply {
            text = "PokeWalk Lite  •  v${BuildConfig.VERSION_NAME}"
            textSize = 11f
            setTextColor(TEXT_MUTED)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.WHITE)
            elevation = dp(6).toFloat()
        }
        root.addView(
            footer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
        ViewCompat.requestApplyInsets(root)
        updateSelectedConfigText()
        render()
    }

    private fun createHero(): View {
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconFrame = FrameLayout(this).apply {
            background = roundedDrawable(ICON_BACKGROUND, 22)
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            contentDescription = getString(R.string.launcher_icon_description)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        iconFrame.addView(
            icon,
            FrameLayout.LayoutParams(dp(82), dp(82), Gravity.CENTER)
        )
        hero.addView(iconFrame, LinearLayout.LayoutParams(dp(82), dp(82)).apply {
            marginEnd = dp(16)
        })

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        copy.addView(TextView(this).apply {
            text = "PokeWalk Lite"
            textSize = 28f
            setTextColor(TEXT_PRIMARY)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            includeFontPadding = false
        })
        copy.addView(TextView(this).apply {
            text = "Defina sua distância e seu ritmo."
            textSize = 15f
            setTextColor(TEXT_MUTED)
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(6), 0, 0)
        })
        hero.addView(copy, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
        return hero
    }

    private fun createConfigurationCard(): View {
        val card = card()
        card.addView(sectionTitle("Configurar atividade"))
        card.addView(sectionSubtitle("Ajuste os controles antes de começar."))
        card.addView(space(20))

        settingHeader("VELOCIDADE").also {
            selectedSpeedLabel = it.second
            card.addView(it.first)
        }
        speedSeek = SeekBar(this).apply {
            max = WalkState.MAX_SPEED_KMH - WalkState.MIN_SPEED_KMH
            progress = selectedSpeedKmh - WalkState.MIN_SPEED_KMH
            progressTintList = ColorStateList.valueOf(ACCENT)
            thumbTintList = ColorStateList.valueOf(ACCENT)
            setPadding(0, dp(4), 0, 0)
            contentDescription = "Velocidade"
        }
        card.addView(speedSeek, matchWrap())
        card.addView(endpointRow("1 km/h", "8 km/h"))

        speedSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (WalkState.isRunning(this@MainActivity)) return
                selectedSpeedKmh = progress + WalkState.MIN_SPEED_KMH
                WalkState.setPreferredSpeedKmh(this@MainActivity, selectedSpeedKmh)
                updateSelectedConfigText()
            }
        })

        card.addView(divider(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(1)
        ).apply {
            topMargin = dp(18)
            bottomMargin = dp(18)
        })

        settingHeader("DISTÂNCIA").also {
            selectedDistanceLabel = it.second
            card.addView(it.first)
        }
        distanceSeek = SeekBar(this).apply {
            max = 19
            progress = selectedKm - 1
            progressTintList = ColorStateList.valueOf(ACCENT)
            thumbTintList = ColorStateList.valueOf(ACCENT)
            setPadding(0, dp(4), 0, 0)
            contentDescription = "Distância"
        }
        card.addView(distanceSeek, matchWrap())
        card.addView(endpointRow("1 km", "20 km"))

        distanceSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (WalkState.isRunning(this@MainActivity)) return
                selectedKm = progress + 1
                WalkState.setPreferredDistanceKm(this@MainActivity, selectedKm)
                updateSelectedConfigText()
            }
        })

        val estimateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedDrawable(ACCENT_SOFT, 14)
        }
        estimateRow.addView(TextView(this).apply {
            text = "TEMPO ESTIMADO"
            textSize = 11f
            letterSpacing = 0.08f
            setTextColor(TEXT_MUTED)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
        estimatedTimeLabel = TextView(this).apply {
            textSize = 15f
            setTextColor(ACCENT_DARK)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        estimateRow.addView(estimatedTimeLabel)
        card.addView(estimateRow, matchWrap().apply { topMargin = dp(18) })
        return card
    }

    private fun createActivityCard(): View {
        val card = card()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(sectionTitle("Sua atividade"), LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
        activityState = TextView(this).apply {
            textSize = 12f
            setTextColor(ACCENT_DARK)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.END
        }
        header.addView(activityState)
        card.addView(header)
        card.addView(space(18))

        val metrics = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        elapsedValue = addMetric(metrics, "TEMPO", "00:00", true)
        distanceValue = addMetric(metrics, "DISTÂNCIA", "0,00 km", true)
        stepsValue = addMetric(metrics, "PASSOS", "0", false)
        card.addView(metrics, matchWrap())

        activityProgress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 1_000
            progress = 0
            progressTintList = ColorStateList.valueOf(ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(PROGRESS_TRACK)
        }
        card.addView(
            activityProgress,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)).apply {
                topMargin = dp(18)
                bottomMargin = dp(18)
            }
        )

        actionButton = Button(this).apply {
            textSize = 16f
            isAllCaps = false
            minHeight = dp(58)
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            stateListAnimator = null
            elevation = 0f
            setOnClickListener {
                if (WalkState.isRunning(this@MainActivity)) {
                    stopActivityRun()
                } else {
                    prepareActivityRun()
                }
            }
        }
        card.addView(actionButton, matchWrap())
        return card
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(20))
        background = roundedDrawable(CARD, 20, CARD_BORDER)
        elevation = dp(2).toFloat()
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 20f
        setTextColor(TEXT_PRIMARY)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        includeFontPadding = false
    }

    private fun sectionSubtitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(TEXT_MUTED)
        setPadding(0, dp(5), 0, 0)
    }

    private fun settingHeader(label: String): Pair<LinearLayout, TextView> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 12f
            letterSpacing = 0.08f
            setTextColor(TEXT_MUTED)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
        val value = TextView(this).apply {
            textSize = 15f
            setTextColor(ACCENT_DARK)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = roundedDrawable(ACCENT_SOFT, 999)
        }
        row.addView(value)
        return row to value
    }

    private fun endpointRow(start: String, end: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(TextView(this@MainActivity).apply {
            text = start
            textSize = 11f
            setTextColor(TEXT_MUTED)
        }, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
        addView(TextView(this@MainActivity).apply {
            text = end
            textSize = 11f
            setTextColor(TEXT_MUTED)
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
    }

    private fun addMetric(
        parent: LinearLayout,
        label: String,
        initial: String,
        addEndMargin: Boolean
    ): TextView {
        val value = TextView(this).apply {
            text = initial
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(TEXT_PRIMARY)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            includeFontPadding = false
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(13), dp(8), dp(13))
            background = roundedDrawable(METRIC_BACKGROUND, 14)
            addView(value)
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 10f
                letterSpacing = 0.07f
                gravity = Gravity.CENTER
                setTextColor(TEXT_MUTED)
                setPadding(0, dp(5), 0, 0)
            })
        }
        parent.addView(box, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            if (addEndMargin) marginEnd = dp(7)
        })
        return value
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(DIVIDER)
    }

    private fun space(heightDp: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun roundedDrawable(
        color: Int,
        radiusDp: Int,
        strokeColor: Int? = null
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        strokeColor?.let { setStroke(dp(1), it) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun updateSelectedConfigText() {
        selectedSpeedLabel.text = "$selectedSpeedKmh km/h"
        selectedDistanceLabel.text = "$selectedKm km"
        estimatedTimeLabel.text = formatEstimatedDuration(
            WalkState.calculateDurationMs(selectedKm, selectedSpeedKmh)
        )
        if (!WalkState.isRunning(this)) {
            actionButton.text = "Iniciar $selectedKm km"
        }
    }

    override fun onResume() {
        super.onResume()
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                render()
                delay(1_000L)
            }
        }
    }

    override fun onPause() {
        ticker?.cancel()
        ticker = null
        super.onPause()
    }

    private fun render() {
        val running = WalkState.isRunning(this)
        val metrics = when {
            running -> WalkState.metricsAt(
                this,
                (System.currentTimeMillis() - WalkState.startTimeMillis(this))
                    .coerceIn(0L, WalkState.totalDurationMs(this))
            )
            WalkState.hasResult(this) -> WalkState.finalMetrics(this)
            else -> WalkState.Metrics(0L, 0.0, 0L)
        }

        elapsedValue.text = formatDuration(metrics.durationMs / 1_000L)
        distanceValue.text = String.format(
            Locale.getDefault(),
            "%.2f km",
            metrics.distanceMeters / 1_000.0
        )
        stepsValue.text = String.format(Locale.getDefault(), "%,d", metrics.steps)

        if (running) {
            selectedKm = WalkState.targetDistanceKm(this)
            selectedSpeedKmh = WalkState.targetSpeedKmh(this)
            distanceSeek.progress = selectedKm - 1
            speedSeek.progress = selectedSpeedKmh - WalkState.MIN_SPEED_KMH
            selectedDistanceLabel.text = "$selectedKm km"
            selectedSpeedLabel.text = "$selectedSpeedKmh km/h"
            estimatedTimeLabel.text = formatEstimatedDuration(WalkState.totalDurationMs(this))
            activityState.text = "Em andamento"
            activityState.setTextColor(ACCENT_DARK)
            actionButton.text = "Cancelar atividade"
            actionButton.background = roundedDrawable(DANGER, 16)
        } else {
            activityState.text = when {
                WalkState.isFinished(this) -> "Concluída"
                WalkState.isStopped(this) -> "Progresso salvo"
                else -> "Pronta para começar"
            }
            activityState.setTextColor(
                if (WalkState.isFinished(this)) SUCCESS else TEXT_MUTED
            )
            actionButton.background = roundedDrawable(ACCENT, 16)
            updateSelectedConfigText()
        }

        distanceSeek.isEnabled = !running
        speedSeek.isEnabled = !running
        actionButton.setTextColor(Color.WHITE)

        val totalDuration = WalkState.totalDurationMs(this).coerceAtLeast(1L)
        activityProgress.progress = (
            metrics.durationMs.toDouble() / totalDuration.toDouble() * 1_000.0
        ).toInt().coerceIn(0, 1_000)

        val error = WalkState.error(this)
        if (error != null && error != lastErrorShown) {
            lastErrorShown = error
            showMessage("Não foi possível concluir: $error")
        }
    }

    private fun formatDuration(totalSeconds: Long): String {
        return if (totalSeconds >= 3_600L) {
            String.format(
                Locale.getDefault(),
                "%02d:%02d:%02d",
                totalSeconds / 3_600L,
                (totalSeconds % 3_600L) / 60L,
                totalSeconds % 60L
            )
        } else {
            String.format(
                Locale.getDefault(),
                "%02d:%02d",
                totalSeconds / 60L,
                totalSeconds % 60L
            )
        }
    }

    private fun formatEstimatedDuration(durationMs: Long): String {
        val totalSeconds = ((durationMs + 500L) / 1_000L).coerceAtLeast(1L)
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            hours > 0L && seconds > 0L -> "$hours h $minutes min $seconds s"
            hours > 0L -> "$hours h $minutes min"
            seconds > 0L -> "$minutes min $seconds s"
            else -> "$minutes min"
        }
    }

    private fun prepareActivityRun() {
        if (WalkState.isRunning(this)) return
        if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) {
            showMessage("Health Connect indisponível ou desatualizado.")
            return
        }

        val client = HealthConnectClient.getOrCreate(this)
        scope.launch {
            try {
                val granted = client.permissionController.getGrantedPermissions()
                if (granted.containsAll(healthPermissions)) {
                    ensureNotificationPermissionAndStart()
                } else {
                    healthPermissionLauncher.launch(healthPermissions)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                showMessage("Não foi possível acessar o Health Connect.")
            }
        }
    }

    private fun ensureNotificationPermissionAndStart() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            showMessage("Ative as notificações do PokeWalk nas configurações do Android.")
        } else {
            ensureActivityPermissionAndStart()
        }
    }

    private fun ensureActivityPermissionAndStart() {
        if (
            Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            startActivityRun()
        }
    }

    private fun startActivityRun() {
        WalkState.begin(this, selectedKm, selectedSpeedKmh)
        ContextCompat.startForegroundService(this, Intent(this, WalkService::class.java))
        render()
    }

    private fun stopActivityRun() {
        if (!WalkState.isRunning(this)) return
        startService(Intent(this, WalkService::class.java).setAction(WalkService.ACTION_STOP))
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private val BACKGROUND = Color.rgb(245, 247, 251)
        private val CARD = Color.WHITE
        private val CARD_BORDER = Color.rgb(230, 234, 241)
        private val TEXT_PRIMARY = Color.rgb(28, 34, 48)
        private val TEXT_MUTED = Color.rgb(103, 112, 130)
        private val ACCENT = Color.rgb(229, 56, 59)
        private val ACCENT_DARK = Color.rgb(174, 35, 39)
        private val ACCENT_SOFT = Color.rgb(255, 238, 238)
        private val ICON_BACKGROUND = Color.rgb(234, 240, 248)
        private val METRIC_BACKGROUND = Color.rgb(247, 249, 252)
        private val PROGRESS_TRACK = Color.rgb(229, 233, 240)
        private val DIVIDER = Color.rgb(235, 238, 243)
        private val DANGER = Color.rgb(188, 43, 47)
        private val SUCCESS = Color.rgb(35, 132, 81)
    }
}

