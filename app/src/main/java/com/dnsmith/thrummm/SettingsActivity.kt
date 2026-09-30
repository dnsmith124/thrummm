// SPDX-License-Identifier: GPL-3.0-or-later
package com.dnsmith.thrummm

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var ui: Ui
    private lateinit var accessStatus: TextView
    private lateinit var loadingText: TextView
    private lateinit var callLoopChoice: Ui.Choice<Boolean>
    private lateinit var callPatternRow: View
    private lateinit var callPatternLink: TextView
    private val adapter = AppAdapter()
    private lateinit var list: ListView
    private lateinit var pages: Map<Tab, View>
    private var tab = Tab.PATTERN
    /** Scroll position to restore once the app list has loaded (after a re-theme). */
    private var pendingScroll: Pair<Int, Int>? = null

    private enum class Tab(val label: String) { PATTERN("Pattern"), CALLS("Calls"), APPS("Apps"), THEME("Theme") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val amb = Ambient.forSettings(this, prefs)
        ui = Ui(this, amb)

        window.setDecorFitsSystemWindows(false)
        val metrics = resources.displayMetrics
        window.setBackgroundDrawable(amb.background(metrics.widthPixels, metrics.heightPixels))

        tab = savedInstanceState?.getString(KEY_TAB)?.let { name -> Tab.values().firstOrNull { it.name == name } }
            ?: Tab.PATTERN
        pendingScroll = savedInstanceState?.let { it.getInt(KEY_SCROLL_POS) to it.getInt(KEY_SCROLL_TOP) }

        pages = mapOf(
            Tab.PATTERN to scrollPage(buildPatternPage()),
            Tab.CALLS to scrollPage(buildCallsPage()),
            Tab.APPS to buildAppsPage(),
            Tab.THEME to scrollPage(buildThemePage()),
        )
        val content = FrameLayout(this).apply {
            pages.values.forEach { addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)) }
        }

        // Title and tabs stay put; only the page below them scrolls.
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(4))
                addView(ui.pageTitle("Thrummm"))
                addView(ui.spacer(16))
                addView(
                    ui.Choice(Tab.values().map { it.label to it }, tab, fill = true) { showTab(it) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                )
            })
            addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        }
        setContentView(root)
        showTab(tab)
        // Dark status/nav icons on light skies. Needs the decor view, so after setContentView.
        val lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (amb.fgDark) lightBars else 0, lightBars)
        loadApps()
    }

    private fun showTab(t: Tab) {
        tab = t
        pages.forEach { (key, page) -> page.visibility = if (key == t) View.VISIBLE else View.GONE }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TAB, tab.name)
        outState.putInt(KEY_SCROLL_POS, list.firstVisiblePosition)
        // setSelectionFromTop measures from inside the list's padding, so save it that way too.
        outState.putInt(KEY_SCROLL_TOP, (list.getChildAt(0)?.top ?: 0) - list.paddingTop)
    }

    override fun onResume() {
        super.onResume()
        // Picks up a SideHome theme change (or the sky moving on) since we were last here.
        if (Ambient.forSettings(this, prefs).differsFrom(ui.amb)) {
            recreate()
            return
        }
        val granted = getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(listenerComponent())
        accessStatus.text = if (granted) "On" else "Off"
        accessStatus.typeface = if (granted) ui.semibold else ui.regular
        accessStatus.setTextColor(if (granted) ui.amb.accent else ui.amb.fg(0.7f))
    }

    private fun listenerComponent() = ComponentName(this, VibeListenerService::class.java)

    /** A padded vertical column; spacers bring their own height, everything else spans the width. */
    private fun column(bottomPadDp: Int = 24, build: LinearLayout.(add: (View) -> Unit) -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(24), ui.dp(20), ui.dp(24), ui.dp(bottomPadDp))
            build { v ->
                addView(v, v.layoutParams ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }

    private fun scrollPage(body: View) = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(body)
    }

    private fun buildPatternPage() = column { add ->
        add(ui.explainer("The buzz your chosen apps' notifications get instead of the short system one."))
        add(ui.spacer(16))
        add(PatternEditor(ui, prefs.defaultPattern) { prefs.defaultPattern = it!! })
    }

    private fun buildCallsPage() = column { add ->
        callLoopChoice = ui.onOff(prefs.callLoop && hasPhonePermission()) { onCallLoopToggled(it) }
        add(ui.settingRow("Loop while ringing", callLoopChoice))
        add(ui.spacer(6))
        add(ui.explainer(
            "Repeats the pattern with a ${Buzzer.CALL_GAP_MS} ms pause until the call is answered " +
                "or ends. Follows the system: vibrate mode, or ring mode with \"vibrate for calls\" " +
                "on; never in silent or Do Not Disturb."
        ))
        add(ui.spacer(28))
        add(ui.settingRow("Separate pattern", ui.onOff(prefs.separateCallPattern) {
            prefs.separateCallPattern = it
            refreshCallPattern()
        }))
        add(ui.spacer(6))
        add(ui.explainer("Off: calls loop the same pattern as notifications."))
        callPatternLink = ui.link("") {
            editPattern("Call pattern", prefs.callPattern, null) { spec ->
                prefs.callPattern = spec!!
                refreshCallPattern()
            }
        }
        callPatternRow = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.spacer(16))
            addView(ui.settingRow("Call pattern", callPatternLink))
        }
        add(callPatternRow)
        refreshCallPattern()
    }

    private fun buildAppsPage(): View {
        val header = column(bottomPadDp = 8) { add ->
            // Like the launcher's notification-dots row: the system grant is the switch,
            // so the row just shows its state and opens the page that owns it.
            accessStatus = ui.text("", 15f)
            add(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setOnClickListener { openListenerSettings() }
                addView(ui.settingRow("Notification access", accessStatus))
                addView(ui.spacer(6))
                addView(ui.explainer(
                    "Lets the app see when your chosen apps post a notification, so it can buzz " +
                        "with your pattern. Nothing is stored or sent."
                ))
            })
            add(ui.spacer(24))
            add(ui.settingRow("Pattern per app", ui.onOff(prefs.perAppPatterns) {
                prefs.perAppPatterns = it
                adapter.notifyDataSetChanged()
            }))
            add(ui.spacer(16))
            add(ui.explainer("Tap an app to use your pattern for it. Long-press to open its notification settings."))
            add(ui.spacer(12))
            add(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(ui.pill("Select all") { adapter.setAllChecked(true) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(ui.pill("Deselect all") { adapter.setAllChecked(false) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ui.dp(8) })
            })
            loadingText = ui.explainer("Loading apps…").apply { setPadding(0, ui.dp(12), 0, 0) }
            add(loadingText)
        }
        list = ListView(this).apply {
            divider = null
            selector = ColorDrawable(Color.TRANSPARENT)
            cacheColorHint = Color.TRANSPARENT
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, 0, 0, ui.dp(16))
            clipToPadding = false
            addHeaderView(header, null, false)
            adapter = this@SettingsActivity.adapter
            setOnItemClickListener { _, _, pos, _ -> this@SettingsActivity.adapter.toggle(pos - headerViewsCount) }
            setOnItemLongClickListener { _, _, pos, _ ->
                this@SettingsActivity.adapter.getItem(pos - headerViewsCount)?.let { openAppNotificationSettings(it.pkg) }
                true
            }
        }
        return list
    }

    private fun buildThemePage() = column { add ->
        val sideHome = Ambient.isSideHomeInstalled(this@SettingsActivity)
        if (sideHome) {
            add(ui.settingRow("Match SideHome", ui.onOff(prefs.matchSideHome) {
                prefs.matchSideHome = it
                recreate()
            }))
            add(ui.spacer(6))
            add(ui.explainer(
                "Follows SideHome's theme through the wallpaper it keeps in sync. If this " +
                    "screen doesn't match, turn on Home wallpaper in SideHome's settings."
            ))
            add(ui.spacer(24))
        }
        val themeRow = ui.settingRow("Theme", ui.Choice(AppTheme.values().map { it.label to it }, prefs.theme) {
            prefs.theme = it
            recreate()
        })
        // SideHome decides while matching; the choice still applies if matching finds nothing.
        if (sideHome && prefs.matchSideHome) themeRow.disable()
        add(themeRow)

        add(ui.spacer(36))
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        add(ui.sectionTitle("About"))
        add(ui.spacer(10))
        add(ui.text("Thrummm ${version.orEmpty()}".trim(), 15f))
        add(ui.spacer(6))
        add(ui.explainer("Inter typeface — SIL Open Font License 1.1."))
    }

    private fun View.disable() {
        alpha = 0.4f
        disableDeep()
    }

    private fun View.disableDeep() {
        isEnabled = false
        if (this is ViewGroup) (0 until childCount).forEach { getChildAt(it).disableDeep() }
    }

    private fun refreshCallPattern() {
        callPatternRow.visibility = if (prefs.separateCallPattern) View.VISIBLE else View.GONE
        callPatternLink.text = "${prefs.callPattern.shortLabel()}  ›"
    }

    /** Opens a [PatternEditor] in a dialog; [defaultSpec] non-null adds a "Use default" choice. */
    private fun editPattern(
        title: String,
        initial: PatternSpec?,
        defaultSpec: PatternSpec?,
        onSave: (PatternSpec?) -> Unit,
    ) {
        val editor = PatternEditor(ui, initial, defaultSpec)
        ui.dialog(title, editor, "Save") { onSave(editor.spec) }
    }

    private fun hasPhonePermission() =
        checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    private fun onCallLoopToggled(on: Boolean) {
        if (on && !hasPhonePermission()) {
            // Leave the pref off until the permission result comes back.
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), REQ_PHONE)
            return
        }
        prefs.callLoop = on
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        if (requestCode != REQ_PHONE) return
        val granted = results.firstOrNull() == PackageManager.PERMISSION_GRANTED
        prefs.callLoop = granted
        callLoopChoice.select(granted)
        if (!granted) Toast.makeText(this, "Phone permission is needed to detect ringing", Toast.LENGTH_SHORT).show()
    }

    private fun openListenerSettings() {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent().flattenToString())
        val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startFirstAvailable(detail, fallback)
    }

    private fun openAppNotificationSettings(pkg: String) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        startFirstAvailable(intent)
    }

    private fun startFirstAvailable(vararg intents: Intent) {
        for (i in intents) {
            try {
                startActivity(i)
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(this, "Couldn't open settings on this device", Toast.LENGTH_SHORT).show()
    }

    private fun loadApps() {
        val pm = packageManager
        val selected = prefs.selectedPackages
        thread {
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { ai ->
                    ai.packageName != packageName && (
                        ai.flags and ApplicationInfo.FLAG_SYSTEM == 0 ||
                            pm.getLaunchIntentForPackage(ai.packageName) != null ||
                            ai.packageName in selected
                        )
                }
                .map { AppEntry(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it)) }
                .sortedWith(compareBy<AppEntry> { it.pkg !in selected }.thenBy { it.label.lowercase() })
            runOnUiThread {
                loadingText.visibility = View.GONE
                adapter.setApps(apps, selected)
                pendingScroll?.let { (pos, top) -> list.setSelectionFromTop(pos, top) }
                pendingScroll = null
            }
        }
    }

    companion object {
        private const val REQ_PHONE = 1
        private const val KEY_TAB = "tab"
        private const val KEY_SCROLL_POS = "scroll_pos"
        private const val KEY_SCROLL_TOP = "scroll_top"
    }

    data class AppEntry(val pkg: String, val label: String, val icon: Drawable)

    private class RowViews(
        val card: LinearLayout,
        val icon: ImageView,
        val label: TextView,
        val detail: TextView,
        val pattern: TextView,
        val check: TextView,
    )

    private inner class AppAdapter : BaseAdapter() {
        private var apps: List<AppEntry> = emptyList()
        private val checked = HashSet<String>()

        fun setApps(list: List<AppEntry>, selected: Set<String>) {
            apps = list
            checked.clear()
            checked += selected
            notifyDataSetChanged()
        }

        fun toggle(pos: Int) {
            val app = getItem(pos) ?: return
            if (!checked.remove(app.pkg)) checked += app.pkg
            prefs.selectedPackages = checked.toSet()
            notifyDataSetChanged()
        }

        fun setAllChecked(on: Boolean) {
            if (on) checked += apps.map { it.pkg } else checked.clear()
            prefs.selectedPackages = checked.toSet()
            notifyDataSetChanged()
        }

        override fun getCount() = apps.size
        override fun getItem(pos: Int): AppEntry? = apps.getOrNull(pos)
        override fun getItemId(pos: Int) = pos.toLong()

        private fun createRow(): View {
            val icon = ImageView(this@SettingsActivity)
            val label = ui.text("", 17f)
            val detail = ui.text("", 13f, color = ui.amb.fg(0.7f)).apply { setPadding(0, ui.dp(3), 0, 0) }
            // Not focusable, so the row itself still receives item clicks.
            val pattern = ui.text("Pattern", 13f).apply {
                background = ui.rounded(ui.amb.fg(0.1f))
                setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6))
                isFocusable = false
            }
            val check = ui.text("✓", 18f, ui.semibold, ui.amb.accent).apply {
                gravity = Gravity.CENTER
                setPadding(ui.dp(10), 0, ui.dp(4), 0)
            }
            val card = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
                addView(icon, LinearLayout.LayoutParams(ui.dp(34), ui.dp(34)))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(ui.dp(12), 0, ui.dp(8), 0)
                    addView(label)
                    addView(detail)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(pattern)
                addView(check)
            }
            return FrameLayout(this@SettingsActivity).apply {
                setPadding(ui.dp(16), ui.dp(2), ui.dp(16), ui.dp(2))
                addView(card)
                tag = RowViews(card, icon, label, detail, pattern, check)
            }
        }

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: createRow()
            val v = row.tag as RowViews
            val app = apps[pos]
            val isChecked = app.pkg in checked
            val showPattern = prefs.perAppPatterns && isChecked

            v.card.background = if (isChecked) ui.rounded(ui.amb.accent(0.22f)) else null
            v.icon.setImageDrawable(app.icon)
            v.label.text = app.label
            v.label.typeface = if (isChecked) ui.semibold else ui.regular
            v.detail.text = if (showPattern) prefs.appPattern(app.pkg)?.shortLabel() ?: "Default pattern" else app.pkg
            v.check.visibility = if (isChecked) View.VISIBLE else View.INVISIBLE
            v.pattern.visibility = if (showPattern) View.VISIBLE else View.GONE
            v.pattern.setOnClickListener {
                editPattern(app.label, prefs.appPattern(app.pkg), prefs.defaultPattern) { spec ->
                    prefs.setAppPattern(app.pkg, spec)
                    notifyDataSetChanged()
                }
            }
            return row
        }
    }
}
