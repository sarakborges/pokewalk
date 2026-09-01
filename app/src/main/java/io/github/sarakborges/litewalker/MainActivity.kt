package io.github.sarakborges.litewalker

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
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
import android.widget.Switch
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
import java.text.DateFormat
import java.text.NumberFormat
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
    private lateinit var distanceSliderGroup: LinearLayout
    private lateinit var speedSeek: SeekBar
    private lateinit var endlessSwitch: Switch
    private lateinit var selectedDistanceLabel: TextView
    private lateinit var selectedSpeedLabel: TextView
    private lateinit var estimatedTimeLabel: TextView
    private lateinit var activityProgress: ProgressBar
    private lateinit var historyList: LinearLayout
    private lateinit var clearHistoryButton: Button

    private var selectedKm = 5
    private var selectedSpeedKmh = WalkState.DEFAULT_SPEED_KMH
    private var selectedEndless = false
    private var lastErrorShown: String? = null
    private var lastHistorySignature: String? = null
    private var darkMode = false
    private lateinit var palette: Palette

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
            showMessage(getString(R.string.health_permission_denied))
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ensureActivityPermissionAndStart()
        } else {
            showMessage(getString(R.string.notification_permission_denied))
        }
    }

    private val activityRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startActivityRun()
        } else {
            showMessage(getString(R.string.activity_permission_denied))
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppPreferences.localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        darkMode = AppPreferences.isDarkMode(this)
        setTheme(
            if (darkMode) R.style.Theme_LiteWalker_Dark else R.style.Theme_LiteWalker_Light
        )
        super.onCreate(savedInstanceState)

        palette = Palette.forMode(darkMode)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = !darkMode
        }
        window.navigationBarColor = palette.background

        selectedKm = WalkState.preferredDistanceKm(this)
        selectedSpeedKmh = WalkState.preferredSpeedKmh(this)
        selectedEndless = WalkState.preferredEndless(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.header)
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

        root.addView(
            createFixedHeader(),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setBackgroundColor(palette.background)
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

        content.addView(createConfigurationCard())
        content.addView(space(14))
        content.addView(createActivityCard())
        content.addView(space(14))
        content.addView(createHistoryCard())

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(9), dp(16), dp(9))
            setBackgroundColor(palette.surface)
            elevation = 0f
        }
        footer.addView(TextView(this).apply {
            text = getString(R.string.footer_version, BuildConfig.VERSION_NAME)
            textSize = 11f
            setTextColor(palette.textMuted)
            isSingleLine = true
        }, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
        footer.addView(
            compactButton(
                text = getString(R.string.changelog),
                description = getString(R.string.changelog_description),
                onClick = ::showChangelog
            ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(7) }
        )
        footer.addView(
            compactButton(
                text = getString(R.string.privacy),
                description = getString(R.string.privacy_description),
                onClick = ::showPrivacyPolicy
            )
        )
        root.addView(
            divider(),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        )
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

    private fun createFixedHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(palette.header)
            elevation = dp(8).toFloat()
        }

        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iconFrame = FrameLayout(this).apply {
            background = roundedDrawable(palette.iconBackground, 18)
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            contentDescription = getString(R.string.launcher_icon_description)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            scaleX = 1.58f
            scaleY = 1.58f
        }
        iconFrame.addView(
            icon,
            FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER)
        )
        brandRow.addView(iconFrame, LinearLayout.LayoutParams(dp(64), dp(64)).apply {
            marginEnd = dp(14)
        })

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        copy.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 23f
            setTextColor(palette.headerTextPrimary)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            includeFontPadding = false
        })
        copy.addView(TextView(this).apply {
            text = getString(R.string.subtitle)
            textSize = 13f
            setTextColor(palette.headerTextMuted)
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(6), 0, 0)
        })
        brandRow.addView(copy, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
        header.addView(brandRow, matchWrap())

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        controls.addView(
            createHeaderToggle(
                leftText = getString(R.string.theme_light),
                rightText = getString(R.string.theme_dark),
                checked = darkMode,
                description = getString(R.string.theme_toggle_description)
            ) { checked ->
                if (checked != darkMode) {
                    AppPreferences.setDarkMode(this@MainActivity, checked)
                    recreate()
                }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        )

        val englishSelected =
            AppPreferences.languageTag(this) == AppPreferences.LANGUAGE_EN_US
        controls.addView(
            createHeaderToggle(
                leftText = "PT-BR",
                rightText = "EN-US",
                checked = englishSelected,
                description = getString(R.string.language_toggle_description)
            ) { checked ->
                val selectedLanguage = if (checked) {
                    AppPreferences.LANGUAGE_EN_US
                } else {
                    AppPreferences.LANGUAGE_PT_BR
                }
                if (selectedLanguage != AppPreferences.languageTag(this@MainActivity)) {
                    AppPreferences.setLanguageTag(this@MainActivity, selectedLanguage)
                    recreate()
                }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
        )
        header.addView(controls, matchWrap().apply { topMargin = dp(11) })

        return header
    }

    private fun createHeaderToggle(
        leftText: String,
        rightText: String,
        checked: Boolean,
        description: String,
        onCheckedChanged: (Boolean) -> Unit
    ): View {
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = roundedDrawable(palette.headerControlBackground, 12)
        }

        fun label(text: String, selected: Boolean) = TextView(this).apply {
            this.text = text
            textSize = 10.5f
            isSingleLine = true
            gravity = Gravity.CENTER
            setTextColor(
                if (selected) palette.headerTextPrimary else palette.headerTextMuted
            )
            typeface = Typeface.create(
                "sans-serif-medium",
                if (selected) Typeface.BOLD else Typeface.NORMAL
            )
        }

        group.addView(
            label(leftText, !checked),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        group.addView(Switch(this).apply {
            isChecked = checked
            text = ""
            showText = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            contentDescription = description
            thumbTintList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(Color.WHITE, Color.WHITE)
            )
            trackTintList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(palette.accent, palette.headerToggleTrack)
            )
            setOnCheckedChangeListener { _, value -> onCheckedChanged(value) }
        }, LinearLayout.LayoutParams(dp(48), dp(32)).apply {
            marginStart = dp(2)
            marginEnd = dp(2)
        })
        group.addView(
            label(rightText, checked),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        return group
    }

    private fun createConfigurationCard(): View {
        val card = card()
        card.addView(sectionTitle(getString(R.string.configure_activity)))
        card.addView(sectionSubtitle(getString(R.string.configure_subtitle)))
        card.addView(space(20))

        val endlessRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(10), dp(12))
            background = roundedDrawable(palette.accentSoft, 14)
        }
        val endlessCopy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        endlessCopy.addView(TextView(this).apply {
            text = getString(R.string.endless_mode)
            textSize = 14f
            setTextColor(palette.textPrimary)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        endlessCopy.addView(TextView(this).apply {
            text = getString(R.string.endless_mode_description)
            textSize = 12f
            setTextColor(palette.textMuted)
            setPadding(0, dp(3), dp(8), 0)
        })
        endlessRow.addView(endlessCopy, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
        endlessSwitch = Switch(this).apply {
            isChecked = selectedEndless
            text = ""
            minWidth = 0
            minimumWidth = 0
            contentDescription = getString(R.string.endless_mode_toggle_description)
            setOnCheckedChangeListener { _, checked ->
                if (WalkState.isRunning(this@MainActivity)) return@setOnCheckedChangeListener
                selectedEndless = checked
                WalkState.setPreferredEndless(this@MainActivity, checked)
                updateSelectedConfigText()
            }
        }
        endlessRow.addView(endlessSwitch)
        card.addView(endlessRow, matchWrap())

        card.addView(divider(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(1)
        ).apply {
            topMargin = dp(18)
            bottomMargin = dp(18)
        })

        settingHeader(getString(R.string.speed)).also {
            selectedSpeedLabel = it.second
            card.addView(it.first)
        }
        speedSeek = SeekBar(this).apply {
            max = WalkState.MAX_SPEED_KMH - WalkState.MIN_SPEED_KMH
            progress = selectedSpeedKmh - WalkState.MIN_SPEED_KMH
            progressTintList = ColorStateList.valueOf(palette.accent)
            thumbTintList = ColorStateList.valueOf(palette.accent)
            setPadding(0, dp(4), 0, 0)
            contentDescription = getString(R.string.speed)
        }
        card.addView(speedSeek, matchWrap())
        card.addView(endpointRow(getString(R.string.speed_min), getString(R.string.speed_max)))

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

        settingHeader(getString(R.string.distance)).also {
            selectedDistanceLabel = it.second
            card.addView(it.first)
        }
        distanceSeek = SeekBar(this).apply {
            max = 19
            progress = selectedKm - 1
            progressTintList = ColorStateList.valueOf(palette.accent)
            thumbTintList = ColorStateList.valueOf(palette.accent)
            setPadding(0, dp(4), 0, 0)
            contentDescription = getString(R.string.distance)
        }
        distanceSliderGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(distanceSeek, matchWrap())
            addView(endpointRow(getString(R.string.distance_min), getString(R.string.distance_max)))
        }
        card.addView(distanceSliderGroup, matchWrap())

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
            background = roundedDrawable(palette.accentSoft, 14)
        }
        estimateRow.addView(TextView(this).apply {
            text = getString(R.string.estimated_time)
            textSize = 11f
            letterSpacing = 0.08f
            setTextColor(palette.textMuted)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
        estimatedTimeLabel = TextView(this).apply {
            textSize = 15f
            setTextColor(palette.accentStrong)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            isSingleLine = true
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
        header.addView(sectionTitle(getString(R.string.activity_title)), LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
        activityState = TextView(this).apply {
            textSize = 12f
            setTextColor(palette.accentStrong)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.END
        }
        header.addView(activityState)
        card.addView(header)
        card.addView(space(18))

        val metrics = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(5), dp(14), dp(5))
            background = roundedDrawable(palette.metricBackground, 14)
        }
        elapsedValue = addMetricRow(
            metrics,
            getString(R.string.metric_time),
            "00:00",
            true
        )
        distanceValue = addMetricRow(
            metrics,
            getString(R.string.metric_distance),
            "0.00\u00A0km",
            true
        )
        stepsValue = addMetricRow(metrics, getString(R.string.metric_steps), "0", false)
        card.addView(metrics, matchWrap())

        activityProgress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 1_000
            progress = 0
            progressTintList = ColorStateList.valueOf(palette.accent)
            progressBackgroundTintList = ColorStateList.valueOf(palette.progressTrack)
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

    private fun createHistoryCard(): View {
        val card = card()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            sectionTitle(getString(R.string.history_title)),
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )
        clearHistoryButton = compactButton(
            text = getString(R.string.clear_history),
            description = getString(R.string.clear_history_description),
            backgroundColor = palette.dangerSoft,
            pressedColor = palette.dangerButtonPressed,
            textColor = palette.danger,
            strokeColor = palette.danger,
            onClick = ::confirmClearHistory
        )
        header.addView(clearHistoryButton)
        card.addView(header)

        historyList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        card.addView(historyList, matchWrap().apply { topMargin = dp(12) })
        return card
    }

    private fun renderHistory(force: Boolean = false) {
        val runs = WalkState.recentRuns(this)
        val signature = runs.joinToString(";") {
            "${it.timestampMillis}|${it.durationMs}|${it.distanceMeters}|${it.steps}|${it.completed}"
        }
        if (!force && signature == lastHistorySignature) return
        lastHistorySignature = signature

        historyList.removeAllViews()
        clearHistoryButton.isEnabled = runs.isNotEmpty()
        clearHistoryButton.alpha = if (runs.isNotEmpty()) 1f else 0.45f

        if (runs.isEmpty()) {
            historyList.addView(TextView(this).apply {
                text = getString(R.string.history_empty)
                textSize = 13f
                setTextColor(palette.textMuted)
                setPadding(0, dp(6), 0, dp(2))
            })
            return
        }

        val locale = resources.configuration.locales[0]
        val dateFormat = DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT,
            locale
        )
        val distanceFormat = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        val integerFormat = NumberFormat.getIntegerInstance(locale)

        runs.forEachIndexed { index, run ->
            if (index > 0) {
                historyList.addView(
                    divider(),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(1)
                    )
                )
            }

            val entry = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(12), 0, dp(12))
            }
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            topRow.addView(TextView(this).apply {
                text = getString(
                    if (run.completed) R.string.history_completed else R.string.history_stopped
                )
                textSize = 13f
                setTextColor(if (run.completed) palette.success else palette.textMuted)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }, LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ))
            topRow.addView(TextView(this).apply {
                text = dateFormat.format(run.timestampMillis)
                textSize = 11f
                setTextColor(palette.textMuted)
                gravity = Gravity.END
            })
            entry.addView(topRow)
            entry.addView(TextView(this).apply {
                text = getString(
                    R.string.history_metrics,
                    distanceFormat.format(run.distanceMeters / 1_000.0),
                    formatDuration(run.durationMs / 1_000L),
                    integerFormat.format(run.steps)
                )
                textSize = 13f
                setTextColor(palette.textPrimary)
                setPadding(0, dp(5), 0, 0)
            })
            historyList.addView(entry)
        }
    }

    private fun confirmClearHistory() {
        if (WalkState.recentRuns(this).isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_history_title)
            .setMessage(R.string.clear_history_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear_history_confirm) { _, _ ->
                WalkState.clearRunHistory(this)
                renderHistory(force = true)
            }
            .show()
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(20))
        background = roundedDrawable(palette.surface, 20, palette.border)
        elevation = 0f
    }

    private fun compactButton(
        text: String,
        description: String,
        backgroundColor: Int = palette.primaryAction,
        pressedColor: Int = palette.primaryActionPressed,
        textColor: Int = Color.WHITE,
        strokeColor: Int? = null,
        onClick: () -> Unit
    ) = Button(this).apply {
        this.text = text
        contentDescription = description
        textSize = 11.5f
        isAllCaps = false
        isSingleLine = true
        minWidth = 0
        minimumWidth = 0
        minHeight = dp(38)
        minimumHeight = dp(38)
        setPadding(dp(12), 0, dp(12), 0)
        setTextColor(textColor)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        stateListAnimator = null
        elevation = 0f
        background = statefulRoundedDrawable(
            normalColor = backgroundColor,
            pressedColor = pressedColor,
            radiusDp = 12,
            strokeColor = strokeColor
        )
        setOnClickListener { onClick() }
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 20f
        setTextColor(palette.textPrimary)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        includeFontPadding = false
    }

    private fun sectionSubtitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(palette.textMuted)
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
            setTextColor(palette.textMuted)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
        val value = TextView(this).apply {
            textSize = 15f
            setTextColor(palette.accentStrong)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = roundedDrawable(palette.accentSoft, 999)
        }
        row.addView(value)
        return row to value
    }

    private fun endpointRow(start: String, end: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(TextView(this@MainActivity).apply {
            text = start
            textSize = 11f
            setTextColor(palette.textMuted)
        }, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
        addView(TextView(this@MainActivity).apply {
            text = end
            textSize = 11f
            setTextColor(palette.textMuted)
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
    }

    private fun addMetricRow(
        parent: LinearLayout,
        label: String,
        initial: String,
        addDivider: Boolean
    ): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(40)
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 10f
            letterSpacing = 0.07f
            setTextColor(palette.textMuted)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isSingleLine = true
        }, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
        val value = TextView(this).apply {
            text = initial
            textSize = 16f
            gravity = Gravity.END
            setTextColor(palette.textPrimary)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            includeFontPadding = false
            isSingleLine = true
            maxLines = 1
        }
        row.addView(value)
        parent.addView(row, matchWrap())
        if (addDivider) {
            parent.addView(
                divider(),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            )
        }
        return value
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(palette.divider)
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

    private fun statefulRoundedDrawable(
        normalColor: Int,
        pressedColor: Int,
        radiusDp: Int,
        strokeColor: Int? = null
    ) = StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_pressed),
            roundedDrawable(pressedColor, radiusDp, strokeColor)
        )
        addState(
            intArrayOf(),
            roundedDrawable(normalColor, radiusDp, strokeColor)
        )
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun updateSelectedConfigText() {
        selectedSpeedLabel.text = "$selectedSpeedKmh km/h"
        selectedDistanceLabel.text = if (selectedEndless) {
            getString(R.string.endless_value)
        } else {
            "$selectedKm km"
        }
        estimatedTimeLabel.text = if (selectedEndless) {
            getString(R.string.until_you_stop)
        } else {
            formatEstimatedDuration(WalkState.calculateDurationMs(selectedKm, selectedSpeedKmh))
        }
        val running = WalkState.isRunning(this)
        distanceSliderGroup.visibility = if (selectedEndless) View.GONE else View.VISIBLE
        distanceSeek.isEnabled = !running
        distanceSeek.alpha = if (distanceSeek.isEnabled) 1f else 0.45f
        if (!WalkState.isRunning(this)) {
            actionButton.text = if (selectedEndless) {
                getString(R.string.start_endless)
            } else {
                getString(R.string.start_distance, selectedKm)
            }
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
        val metrics = if (running) {
            WalkState.metricsAt(
                this,
                (System.currentTimeMillis() - WalkState.startTimeMillis(this))
                    .coerceIn(0L, WalkState.totalDurationMs(this))
            )
        } else {
            WalkState.Metrics(0L, 0.0, 0L)
        }

        elapsedValue.text = formatDuration(metrics.durationMs / 1_000L)
        distanceValue.text = String.format(
            Locale.getDefault(),
            "%.2f\u00A0km",
            metrics.distanceMeters / 1_000.0
        )
        stepsValue.text = String.format(Locale.getDefault(), "%,d", metrics.steps)

        if (running) {
            selectedKm = WalkState.targetDistanceKm(this)
            selectedSpeedKmh = WalkState.targetSpeedKmh(this)
            selectedEndless = WalkState.isEndless(this)
            distanceSeek.progress = selectedKm - 1
            speedSeek.progress = selectedSpeedKmh - WalkState.MIN_SPEED_KMH
            selectedDistanceLabel.text = if (selectedEndless) {
                getString(R.string.endless_value)
            } else {
                "$selectedKm km"
            }
            selectedSpeedLabel.text = "$selectedSpeedKmh km/h"
            estimatedTimeLabel.text = if (selectedEndless) {
                getString(R.string.until_you_stop)
            } else {
                formatEstimatedDuration(WalkState.totalDurationMs(this))
            }
            activityState.text = getString(
                if (selectedEndless) R.string.status_endless else R.string.status_in_progress
            )
            activityState.setTextColor(palette.accentStrong)
            actionButton.text = getString(R.string.cancel_activity)
            actionButton.background = roundedDrawable(palette.danger, 16)
        } else {
            activityState.text = getString(R.string.status_ready)
            activityState.setTextColor(palette.textMuted)
            actionButton.background = statefulRoundedDrawable(
                normalColor = palette.primaryAction,
                pressedColor = palette.primaryActionPressed,
                radiusDp = 16
            )
            updateSelectedConfigText()
        }

        distanceSliderGroup.visibility = if (selectedEndless) View.GONE else View.VISIBLE
        distanceSeek.isEnabled = !running
        distanceSeek.alpha = if (distanceSeek.isEnabled) 1f else 0.45f
        speedSeek.isEnabled = !running
        endlessSwitch.isEnabled = !running
        actionButton.setTextColor(Color.WHITE)

        activityProgress.isIndeterminate = running && selectedEndless
        if (!activityProgress.isIndeterminate) {
            activityProgress.progress = if (selectedEndless) {
                0
            } else {
                val totalDuration = WalkState.totalDurationMs(this).coerceAtLeast(1L)
                (metrics.durationMs.toDouble() / totalDuration.toDouble() * 1_000.0)
                    .toInt()
                    .coerceIn(0, 1_000)
            }
        }

        renderHistory()

        val error = WalkState.error(this)
        if (error != null && error != lastErrorShown) {
            lastErrorShown = error
            showMessage(getString(R.string.activity_error, error))
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
            showMessage(getString(R.string.health_connect_unavailable))
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
                showMessage(getString(R.string.health_connect_error))
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
            showMessage(getString(R.string.notifications_disabled))
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
        WalkState.begin(this, selectedKm, selectedSpeedKmh, selectedEndless)
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

    private fun showChangelog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.changelog_title)
            .setMessage(R.string.changelog_text)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun showPrivacyPolicy() {
        AlertDialog.Builder(this)
            .setTitle(R.string.privacy_title)
            .setMessage(R.string.privacy_policy_text)
            .setNeutralButton(R.string.privacy_view_online) { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
            }
            .setPositiveButton(R.string.close, null)
            .show()
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private data class Palette(
        val background: Int,
        val surface: Int,
        val header: Int,
        val border: Int,
        val textPrimary: Int,
        val textMuted: Int,
        val accent: Int,
        val accentStrong: Int,
        val accentSoft: Int,
        val iconBackground: Int,
        val metricBackground: Int,
        val progressTrack: Int,
        val divider: Int,
        val danger: Int,
        val dangerSoft: Int,
        val dangerButtonPressed: Int,
        val success: Int,
        val primaryAction: Int,
        val primaryActionPressed: Int,
        val headerTextPrimary: Int,
        val headerTextMuted: Int,
        val headerControlBackground: Int,
        val headerToggleTrack: Int
    ) {
        companion object {
            fun forMode(dark: Boolean): Palette = if (dark) {
                Palette(
                    background = Color.rgb(19, 15, 23),
                    surface = Color.rgb(33, 25, 39),
                    header = Color.rgb(74, 31, 102),
                    border = Color.rgb(60, 46, 68),
                    textPrimary = Color.rgb(252, 248, 253),
                    textMuted = Color.rgb(189, 175, 196),
                    accent = Color.rgb(239, 79, 107),
                    accentStrong = Color.rgb(217, 182, 239),
                    accentSoft = Color.rgb(57, 40, 72),
                    iconBackground = Color.rgb(214, 61, 89),
                    metricBackground = Color.rgb(42, 33, 48),
                    progressTrack = Color.rgb(71, 56, 79),
                    divider = Color.rgb(60, 46, 68),
                    danger = Color.rgb(255, 117, 142),
                    dangerSoft = Color.rgb(74, 38, 49),
                    dangerButtonPressed = Color.rgb(93, 44, 56),
                    success = Color.rgb(217, 182, 239),
                    primaryAction = Color.rgb(122, 77, 160),
                    primaryActionPressed = Color.rgb(99, 58, 133),
                    headerTextPrimary = Color.WHITE,
                    headerTextMuted = Color.rgb(229, 215, 237),
                    headerControlBackground = Color.rgb(93, 48, 120),
                    headerToggleTrack = Color.rgb(130, 93, 152)
                )
            } else {
                Palette(
                    background = Color.rgb(247, 242, 250),
                    surface = Color.WHITE,
                    header = Color.rgb(80, 35, 111),
                    border = Color.rgb(227, 217, 234),
                    textPrimary = Color.rgb(37, 28, 42),
                    textMuted = Color.rgb(114, 103, 119),
                    accent = Color.rgb(211, 62, 90),
                    accentStrong = Color.rgb(111, 58, 145),
                    accentSoft = Color.rgb(240, 230, 246),
                    iconBackground = Color.rgb(211, 62, 90),
                    metricBackground = Color.rgb(248, 244, 250),
                    progressTrack = Color.rgb(233, 223, 237),
                    divider = Color.rgb(236, 228, 240),
                    danger = Color.rgb(181, 47, 73),
                    dangerSoft = Color.rgb(249, 231, 236),
                    dangerButtonPressed = Color.rgb(243, 206, 216),
                    success = Color.rgb(111, 58, 145),
                    primaryAction = Color.rgb(111, 58, 145),
                    primaryActionPressed = Color.rgb(87, 38, 111),
                    headerTextPrimary = Color.WHITE,
                    headerTextMuted = Color.rgb(228, 211, 236),
                    headerControlBackground = Color.rgb(99, 53, 129),
                    headerToggleTrack = Color.rgb(137, 104, 160)
                )
            }
        }
    }

    companion object {
        private const val PRIVACY_POLICY_URL =
            "https://sarakborges.github.io/pokewalk/privacy.html"
    }
}
