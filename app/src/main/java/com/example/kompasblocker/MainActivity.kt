package com.example.kompasblocker

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(BlockerAccessibilityService.PREFS, MODE_PRIVATE) }

    private val pageBackground = Color.parseColor("#F4F6F2")
    private val ink = Color.parseColor("#182A24")
    private val muted = Color.parseColor("#687970")
    private val forest = Color.parseColor("#173F36")
    private val green = Color.parseColor("#226D58")
    private val mint = Color.parseColor("#CFE68A")
    private val amber = Color.parseColor("#E7B96B")
    private val border = Color.parseColor("#E2E8E2")
    private val white = Color.WHITE

    private lateinit var serviceBadge: TextView
    private lateinit var serviceTitle: TextView
    private lateinit var serviceDetails: TextView
    private lateinit var serviceAction: Button
    private lateinit var selectedCount: TextView
    private lateinit var appList: LinearLayout
    private lateinit var searchInput: EditText
    private var launchableApps: List<ResolveInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)
        window.statusBarColor = pageBackground
        window.navigationBarColor = pageBackground
        var systemUi = if (Build.VERSION.SDK_INT >= 23) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0
        if (Build.VERSION.SDK_INT >= 26) systemUi = systemUi or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.decorView.systemUiVisibility = systemUi

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(26))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        applySystemBarInsets(scroll)

        buildHeader(content)
        buildServiceCard(content)
        buildAppsSection(content)
        buildPrivacyNote(content)
        setContentView(scroll)

        reloadApps()
        updateServiceStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::serviceTitle.isInitialized) updateServiceStatus()
    }

    private fun buildHeader(parent: LinearLayout) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(17))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_compass_logo)
            contentDescription = null
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(46), dp(46)).apply {
            marginEnd = dp(12)
        })

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleBlock.addView(label("Компас", 24f, ink, bold = true), wrapParams())
        titleBlock.addView(label("Блокировка приложений", 13f, muted).apply {
            setPadding(0, dp(2), 0, 0)
        }, wrapParams())
        header.addView(titleBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        parent.addView(header, wrapParams())
    }

    private fun buildServiceCard(parent: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = rounded(forest, 22)
        }
        val topLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topLine.addView(label("СОСТОЯНИЕ ЗАЩИТЫ", 11f, Color.parseColor("#C6D8CD"), bold = true).apply {
            letterSpacing = 0.08f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        serviceBadge = label("ПРОВЕРКА", 10f, forest, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = rounded(mint, 30)
            letterSpacing = 0.03f
        }
        topLine.addView(serviceBadge, wrapContentParams())
        card.addView(topLine, wrapParams())

        serviceTitle = label("Включите защиту", 20f, white, bold = true).apply {
            setPadding(0, dp(12), 0, 0)
        }
        card.addView(serviceTitle, wrapParams())
        serviceDetails = label("Разрешите службе Компаса работать в настройках Android.", 13.5f, Color.parseColor("#D5E1DA")).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(5), 0, dp(14))
        }
        card.addView(serviceDetails, wrapParams())

        serviceAction = Button(this).apply {
            text = "Открыть настройки доступности"
            isAllCaps = false
            textSize = 14f
            setTextColor(forest)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minimumHeight = dp(46)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = rounded(mint, 14)
            stateListAnimator = null
            contentDescription = "Открыть настройки службы доступности Компаса"
            setOnClickListener { openAccessibilitySettings() }
        }
        card.addView(serviceAction, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        parent.addView(card, marginParams(bottom = 22))
    }

    private fun buildAppsSection(parent: LinearLayout) {
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(label("Приложения", 20f, ink, bold = true), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        selectedCount = label("Выбрано: 0 приложений", 11.5f, green, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = rounded(Color.parseColor("#E5EFE8"), 30)
        }
        heading.addView(selectedCount, wrapContentParams())
        parent.addView(heading, wrapParams())
        parent.addView(label("Отмеченные приложения будут закрываться.", 13f, muted).apply {
            setPadding(0, dp(4), 0, dp(12))
        }, wrapParams())

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        searchInput = EditText(this).apply {
            hint = "Найти приложение"
            hintTextColor = Color.parseColor("#829188")
            textSize = 14f
            setTextColor(ink)
            singleLine = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            background = rounded(white, 15, border)
            setPadding(dp(14), 0, dp(14), 0)
            contentDescription = "Поиск установленных приложений"
        }
        tools.addView(searchInput, LinearLayout.LayoutParams(0, dp(50), 1f))
        val refresh = TextView(this).apply {
            text = "↻"
            textSize = 25f
            setTextColor(green)
            gravity = Gravity.CENTER
            background = rounded(white, 15, border)
            contentDescription = "Обновить список приложений"
            isClickable = true
            isFocusable = true
            setOnClickListener { reloadApps() }
        }
        tools.addView(refresh, LinearLayout.LayoutParams(dp(50), dp(50)).apply {
            marginStart = dp(9)
        })
        parent.addView(tools, marginParams(bottom = 12))

        appList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(white, 18, border)
            clipToOutline = true
        }
        parent.addView(appList, wrapParams())

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = renderAppList(s?.toString().orEmpty())
        })
    }

    private fun buildPrivacyNote(parent: LinearLayout) {
        val note = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.parseColor("#EAF0EA"), 16)
        }
        note.addView(label("Прозрачно и под вашим контролем", 13f, ink, bold = true), wrapParams())
        note.addView(label(
            "Компас использует службу доступности, чтобы замечать открытие выбранных приложений. Отключить её можно в настройках Android.",
            12f,
            muted
        ).apply { setPadding(0, dp(4), 0, 0); setLineSpacing(dp(2).toFloat(), 1f) }, wrapParams())
        parent.addView(note, marginParams(top = 14))
    }

    private fun reloadApps() {
        if (!::appList.isInitialized) return
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val protected = protectedPackages()
        launchableApps = queryActivities(launcherIntent)
            .filter { it.activityInfo.packageName !in protected }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) }

        val validPackages = launchableApps.map { it.activityInfo.packageName }.toSet()
        val stored = prefs.getStringSet(BlockerAccessibilityService.BLOCKED_PACKAGES, emptySet())
            ?.toSet().orEmpty()
        val clean = stored.intersect(validPackages).minus(protected)
        if (clean != stored) {
            prefs.edit().putStringSet(BlockerAccessibilityService.BLOCKED_PACKAGES, HashSet(clean)).apply()
        }
        renderAppList(searchInput.text?.toString().orEmpty())
        updateSelectedCount()
    }

    private fun renderAppList(query: String = "") {
        if (!::appList.isInitialized) return
        appList.removeAllViews()
        val normalizedQuery = query.trim().lowercase(Locale.getDefault())
        val visibleApps = launchableApps.filter {
            it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()).contains(normalizedQuery)
        }
        if (visibleApps.isEmpty()) {
            val message = when {
                launchableApps.isEmpty() -> "Не найдено приложений с ярлыком запуска."
                normalizedQuery.isNotEmpty() -> "Ничего не найдено. Попробуйте другое название."
                else -> "Список приложений пуст. Нажмите обновить."
            }
            appList.addView(label(message, 14f, muted).apply {
                setPadding(dp(16), dp(19), dp(16), dp(19))
                gravity = Gravity.CENTER_VERTICAL
            }, wrapParams())
            updateSelectedCount()
            return
        }

        val selected = prefs.getStringSet(BlockerAccessibilityService.BLOCKED_PACKAGES, emptySet())
            ?.toSet().orEmpty()
        visibleApps.forEachIndexed { index, info ->
            val pkg = info.activityInfo.packageName
            val checked = pkg in selected
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(13), 0, dp(12), 0)
                minimumHeight = dp(70)
                isClickable = true
                isFocusable = true
            }
            val appIcon = ImageView(this).apply {
                setImageDrawable(info.loadIcon(packageManager))
                contentDescription = null
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            row.addView(appIcon, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                marginEnd = dp(12)
            })

            val textColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val appName = label(info.loadLabel(packageManager).toString(), 14.5f, ink, bold = true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val stateText = label(if (checked) "Блокировка включена" else "Не блокируется", 11.5f, muted).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(3), 0, 0)
            }
            textColumn.addView(appName, wrapParams())
            textColumn.addView(stateText, wrapParams())
            row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val toggle = Switch(this).apply {
                isChecked = checked
                if (Build.VERSION.SDK_INT >= 24) showText = false
                contentDescription = "Блокировать ${info.loadLabel(packageManager)}"
                val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                thumbTintList = ColorStateList(states, intArrayOf(mint, Color.parseColor("#D5DDD6")))
                trackTintList = ColorStateList(states, intArrayOf(green, Color.parseColor("#BFCAC1")))
                setOnCheckedChangeListener { _, isChecked ->
                    saveSelection(pkg, isChecked)
                    stateText.text = if (isChecked) "Блокировка включена" else "Не блокируется"
                }
            }
            row.addView(toggle, wrapContentParams())
            row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
            appList.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(70)
            ))
            if (index < visibleApps.lastIndex) {
                appList.addView(View(this).apply { setBackgroundColor(border) }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(1)
                ).apply { marginStart = dp(67); marginEnd = dp(13) })
            }
        }
        updateSelectedCount()
    }

    private fun saveSelection(packageName: String, enabled: Boolean) {
        val updated = prefs.getStringSet(BlockerAccessibilityService.BLOCKED_PACKAGES, emptySet())
            ?.toMutableSet() ?: mutableSetOf()
        if (enabled) updated.add(packageName) else updated.remove(packageName)
        prefs.edit().putStringSet(BlockerAccessibilityService.BLOCKED_PACKAGES, HashSet(updated)).apply()
        updateSelectedCount()
    }

    private fun updateSelectedCount() {
        if (!::selectedCount.isInitialized) return
        val count = prefs.getStringSet(BlockerAccessibilityService.BLOCKED_PACKAGES, emptySet())?.size ?: 0
        selectedCount.text = "Выбрано: ${countLabel(count)}"
    }

    private fun countLabel(count: Int): String {
        val tens = count % 100
        val ones = count % 10
        val word = when {
            tens in 11..14 -> "приложений"
            ones == 1 -> "приложение"
            ones in 2..4 -> "приложения"
            else -> "приложений"
        }
        return "$count $word"
    }

    private fun updateServiceStatus() {
        if (!::serviceBadge.isInitialized) return
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val component = ComponentName(this, BlockerAccessibilityService::class.java)
        val enabled = enabledServices.split(':').any {
            ComponentName.unflattenFromString(it) == component
        }
        if (enabled) {
            serviceBadge.text = "ВКЛЮЧЕНА"
            serviceBadge.setTextColor(forest)
            serviceBadge.background = rounded(mint, 30)
            serviceTitle.text = "Защита активна"
            serviceDetails.text = "При запуске отмеченного приложения Компас вернёт вас на главный экран."
            serviceAction.text = "Настройки доступности"
        } else {
            serviceBadge.text = "НУЖНА НАСТРОЙКА"
            serviceBadge.setTextColor(Color.parseColor("#493315"))
            serviceBadge.background = rounded(amber, 30)
            serviceTitle.text = "Включите защиту"
            serviceDetails.text = "Разрешите службе Компаса работать в настройках Android."
            serviceAction.text = "Открыть настройки доступности"
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "Не удалось открыть настройки Android.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun queryActivities(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

    // Системные экраны и домашний лаунчер нельзя включить в блокировку.
    private fun protectedPackages(): Set<String> {
        val result = mutableSetOf("com.android.settings", "com.android.systemui", packageName)
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            Intent(Settings.ACTION_SETTINGS),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        )
        for (intent in intents) {
            queryActivities(intent).forEach { result.add(it.activityInfo.packageName) }
        }
        return result
    }

    // targetSdk 35+ may draw edge-to-edge: keep content clear of system bars and cutouts.
    private fun applySystemBarInsets(root: ScrollView) {
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom
                )
            }
            insets
        }
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            includeFontPadding = false
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun wrapContentParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun wrapParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun marginParams(top: Int = 0, bottom: Int = 0) = wrapParams().apply {
        setMargins(0, dp(top), 0, dp(bottom))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
