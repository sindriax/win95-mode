package com.win95mode.app

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    private enum class Page { HOME, ICONS, WALLPAPERS }
    private enum class IconTab { YOURS, ALL, SYSTEM }

    private var page = Page.HOME
    private var iconTab = IconTab.YOURS
    private var catalog: IconCatalog? = null
    private val density get() = resources.displayMetrics.density

    private val status by lazy { findViewById<TextView>(R.id.window_status) }
    private val startMenu by lazy { findViewById<View>(R.id.start_menu) }
    private val startScrim by lazy { findViewById<View>(R.id.start_scrim) }
    private val search by lazy { findViewById<EditText>(R.id.icon_search) }

    private val iconColumns by lazy {
        (resources.displayMetrics.widthPixels / density / 110f).toInt().coerceIn(4, 8)
    }
    private val iconPx by lazy {
        // Window margin, border and page padding take 52dp of the width.
        val cellPx = ((resources.displayMetrics.widthPixels - 52 * density) / iconColumns).toInt()
        IconArt.crispPx(this, 72f, maxPx = cellPx - (8 * density).toInt())
    }
    private val iconsAdapter by lazy { IconsAdapter(iconPx) }
    private val appIconCache = HashMap<String, Bitmap>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Relaunched from the app icon while already running: return to what was open
        // (Setup, for one) instead of stacking a second copy on top of it.
        if (!isTaskRoot && intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        savedInstanceState?.let {
            page = Page.valueOf(it.getString("page", Page.HOME.name))
            iconTab = IconTab.valueOf(it.getString("icon_tab", IconTab.YOURS.name))
        }
        setupChrome()
        setupIcons()
        setupWallpapers()
        showPage(page)
        // Setting a wallpaper recreates the activity (Android re-colors apps), so keep its message.
        savedInstanceState?.getString("status")?.let { status.text = it }

        if (savedInstanceState == null && !Prefs.setupDone(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Apps and launchers change while we're away, so both are re-read on every return.
        renderHome()
        Thread {
            val loaded = IconCatalog.load(this)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                catalog = loaded
                appIconCache.clear()
                renderHome()
                renderIcons()
                if (page == Page.ICONS) status.text = iconsStatus()
            }
        }.start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("page", page.name)
        outState.putString("icon_tab", iconTab.name)
        outState.putString("status", status.text.toString())
    }

    // --- Window chrome, taskbar, Start menu ---

    private fun setupChrome() {
        findViewById<View>(R.id.btn_close).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_minimize).setOnClickListener { moveTaskToBack(true) }
        listOf(
            R.id.task_home to R.drawable.ic_win95mode,
            R.id.task_icons to R.drawable.ic_files,
            R.id.task_wallpapers to R.drawable.ic_photos
        ).forEach { (id, icon) ->
            val size = Win95.dp(this, 20)
            val drawable = android.graphics.drawable.BitmapDrawable(resources, IconArt.icon(resources, icon, 96))
                .apply { setBounds(0, 0, size, size) }
            findViewById<TextView>(id).apply {
                setCompoundDrawablesRelative(drawable, null, null, null)
                compoundDrawablePadding = Win95.dp(context, 3)
            }
        }
        // The clock is decoration (the status bar shows the time); with large text it
        // would squeeze the navigation buttons, so it steps aside.
        if (resources.configuration.fontScale > 1.15f) findViewById<View>(R.id.taskbar_clock).visibility = View.GONE
        findViewById<View>(R.id.task_home).setOnClickListener { showPage(Page.HOME) }
        findViewById<View>(R.id.task_icons).setOnClickListener { showPage(Page.ICONS) }
        findViewById<View>(R.id.task_wallpapers).setOnClickListener { showPage(Page.WALLPAPERS) }
        findViewById<View>(R.id.btn_start).setOnClickListener { toggleStartMenu() }
        startScrim.setOnClickListener { toggleStartMenu(false) }
        buildStartMenu()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    startMenu.visibility == View.VISIBLE -> toggleStartMenu(false)
                    page != Page.HOME -> showPage(Page.HOME)
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    private fun showPage(target: Page) {
        page = target
        toggleStartMenu(false)
        findViewById<View>(R.id.page_home).visibility = if (target == Page.HOME) View.VISIBLE else View.GONE
        findViewById<View>(R.id.page_icons).visibility = if (target == Page.ICONS) View.VISIBLE else View.GONE
        findViewById<View>(R.id.page_wallpapers).visibility = if (target == Page.WALLPAPERS) View.VISIBLE else View.GONE

        val (title, icon) = when (target) {
            Page.HOME -> getString(R.string.app_name) to R.drawable.ic_win95mode
            Page.ICONS -> getString(R.string.title_icons) to R.drawable.ic_files
            Page.WALLPAPERS -> getString(R.string.title_wallpapers) to R.drawable.ic_photos
        }
        findViewById<TextView>(R.id.window_title).text = title
        findViewById<ImageView>(R.id.window_icon).setImageBitmap(IconArt.icon(resources, icon, 96))

        listOf(Page.HOME to R.id.task_home, Page.ICONS to R.id.task_icons, Page.WALLPAPERS to R.id.task_wallpapers)
            .forEach { (p, id) ->
                findViewById<TextView>(id).apply {
                    val active = p == target
                    setBackgroundResource(if (active) R.drawable.win95_button_pressed else R.drawable.win95_button_selector)
                    typeface = Win95.font(context, active)
                    isSelected = active
                }
            }

        if (target == Page.HOME) catalog?.let(::renderMonitor)
        status.text = when (target) {
            Page.HOME -> getString(R.string.status_ready)
            Page.ICONS -> iconsStatus()
            Page.WALLPAPERS -> getString(R.string.status_wallpapers)
        }
    }

    private fun toggleStartMenu(show: Boolean = startMenu.visibility != View.VISIBLE) {
        startMenu.visibility = if (show) View.VISIBLE else View.GONE
        startScrim.visibility = startMenu.visibility
        findViewById<View>(R.id.btn_start).setBackgroundResource(
            if (show) R.drawable.win95_button_pressed else R.drawable.win95_button_selector
        )
    }

    private fun buildStartMenu() {
        val items = findViewById<LinearLayout>(R.id.start_items)
        fun item(icon: Int, label: Int, action: () -> Unit) {
            items.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                minimumHeight = Win95.dp(context, 52)
                setPadding(Win95.dp(context, 10), 0, Win95.dp(context, 10), 0)
                setBackgroundResource(android.R.drawable.list_selector_background)
                addView(ImageView(context).apply {
                    setImageBitmap(IconArt.icon(resources, icon, 96))
                    layoutParams = LinearLayout.LayoutParams(Win95.dp(context, 32), Win95.dp(context, 32))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
                addView(TextView(context).apply {
                    setText(label)
                    setTextColor(getColor(R.color.black))
                    textSize = 15f
                    setPadding(Win95.dp(context, 12), 0, 0, 0)
                })
                setOnClickListener {
                    toggleStartMenu(false)
                    action()
                }
            })
        }
        item(R.drawable.ic_disk, R.string.start_setup) { startActivity(Intent(this, SetupActivity::class.java)) }
        item(R.drawable.ic_files, R.string.start_request) { showRequestDialog() }
        item(R.drawable.ic_mail, R.string.start_feedback) { openFeedback() }
        item(R.drawable.ic_notes, R.string.start_whats_new) {
            Win95.messageBox(this, getString(R.string.whats_new_title), getString(R.string.whats_new_body))
        }
        item(R.drawable.ic_win95mode, R.string.start_about) { showAboutDialog() }
        items.addView(View(this).apply {
            setBackgroundResource(R.drawable.win95_clock_inset)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Win95.dp(context, 2)).apply {
                setMargins(Win95.dp(context, 4), Win95.dp(context, 4), Win95.dp(context, 4), Win95.dp(context, 4))
            }
        })
        item(R.drawable.ic_my_computer, R.string.start_shutdown) { finish() }
    }

    // --- Home ---

    private fun renderHome() {
        val icon = findViewById<ImageView>(R.id.home_launcher_icon)
        val name = findViewById<TextView>(R.id.home_launcher_name)
        val state = findViewById<TextView>(R.id.home_launcher_state)
        val whenText = findViewById<TextView>(R.id.home_launcher_when)
        val detail = findViewById<TextView>(R.id.home_launcher_detail)
        val apply = findViewById<TextView>(R.id.btn_home_apply)
        val setDefault = findViewById<TextView>(R.id.link_home_set_default)
        whenText.visibility = View.GONE
        detail.visibility = View.GONE
        setDefault.visibility = View.GONE

        when (val s = Launchers.state(this)) {
            is LauncherState.Ready -> {
                icon.setImageDrawable(Launchers.icon(this, s.target.pkg))
                name.text = s.target.label
                val appliedAt = Prefs.appliedAt(this, s.target.label)
                state.setText(if (appliedAt != null) R.string.state_applied else R.string.state_ready)
                appliedAt?.let {
                    whenText.text = getString(R.string.applied_on, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)))
                    whenText.visibility = View.VISIBLE
                }
                apply.setText(if (appliedAt != null) R.string.apply_again else R.string.apply_icons)
                apply.setOnClickListener { ApplyFlow.apply(this, s.target, ::applied) }
            }
            is LauncherState.NotHome -> {
                val first = s.installed.first()
                icon.setImageDrawable(Launchers.icon(this, first.pkg))
                name.text = first.label
                state.setText(R.string.state_not_home)
                apply.setText(R.string.apply_icons)
                apply.setOnClickListener { ApplyFlow.choose(this, s.installed, ::applied) }
                setDefault.visibility = View.VISIBLE
                setDefault.setOnClickListener { Launchers.openHomeSettings(this) }
            }
            is LauncherState.Unsupported -> {
                val home = s.homePkg?.let { Launchers.icon(this, it) }
                if (home != null) icon.setImageDrawable(home)
                else icon.setImageBitmap(IconArt.icon(resources, R.drawable.ic_my_computer, 96))
                name.text = s.homeLabel ?: getString(R.string.your_home_screen)
                state.setText(R.string.state_unsupported)
                detail.setText(R.string.state_unsupported_detail)
                detail.visibility = View.VISIBLE
                apply.setText(R.string.how_to_fix)
                apply.setOnClickListener {
                    startActivity(Intent(this, SetupActivity::class.java).putExtra(SetupActivity.EXTRA_STEP, 1))
                }
            }
        }

        val c = catalog ?: return
        val total = c.installedCount.coerceAtLeast(1)
        findViewById<BlockProgressBar>(R.id.home_coverage_bar).progress = c.themedCount / total.toFloat()
        findViewById<TextView>(R.id.home_coverage_text).text =
            if (c.unthemedInstalled.isEmpty()) getString(R.string.coverage_all, c.themedCount)
            else getString(R.string.coverage_text, c.themedCount, c.installedCount)

        findViewById<TextView>(R.id.btn_home_request).apply {
            visibility = if (c.unthemedInstalled.isEmpty()) View.GONE else View.VISIBLE
            text = getString(R.string.request_count, c.unthemedInstalled.size)
            setOnClickListener { showRequestDialog() }
        }
        renderMonitor(c)
    }

    /** The monitor on Home: the current wallpaper with this phone's apps laid out
     *  as desktop icons, as many rows as the screen area fits. */
    private fun renderMonitor(c: IconCatalog) {
        val screen = findViewById<View>(R.id.home_monitor_screen)
        val grid = findViewById<LinearLayout>(R.id.home_monitor_icons)
        val wallpaper = findViewById<ImageView>(R.id.home_monitor_wallpaper)
        screen.setOnClickListener { showPage(Page.WALLPAPERS) }
        screen.post {
            val w = screen.width - screen.paddingLeft - screen.paddingRight
            val h = screen.height - screen.paddingTop - screen.paddingBottom
            if (w <= 0 || h <= 0) return@post

            val current = Wallpapers.all.firstOrNull { it.key == Prefs.wallpaper(this) }
            if (current == null) wallpaper.setImageDrawable(null)
            else wallpaper.setImageBitmap(Wallpapers.thumbnail(this, current.resId, w))

            grid.removeAllViews()
            // Too little room (landscape, huge text): just the wallpaper.
            if (h < Win95.dp(this, 70)) return@post
            val columns = 4
            val cellW = w / columns
            val iconPx = IconArt.crispPx(this, 44f, maxPx = cellW - Win95.dp(this, 12))
            val rowH = iconPx + Win95.dp(this, 26)
            val rows = ((h - grid.paddingTop) / rowH).coerceAtLeast(1)
            c.yourIcons.take(columns * rows).chunked(columns).forEach { rowIcons ->
                grid.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    rowIcons.forEach { icon -> addView(desktopIcon(icon, cellW, iconPx)) }
                })
            }
        }
    }

    private fun desktopIcon(icon: PackIcon, width: Int, iconPx: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = android.view.Gravity.CENTER_HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT)
        setPadding(0, Win95.dp(context, 2), 0, Win95.dp(context, 4))
        contentDescription = icon.label
        setOnClickListener {
            iconTab = IconTab.YOURS
            buildIconTabs()
            renderIcons()
            showPage(Page.ICONS)
        }
        addView(ImageView(context).apply {
            setImageBitmap(IconArt.icon(resources, icon.resId, iconPx))
            layoutParams = LinearLayout.LayoutParams(iconPx, iconPx)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        // White label with a dark edge, like desktop icon text on the teal desktop.
        addView(TextView(context).apply {
            text = icon.label
            setTextColor(getColor(R.color.white))
            setShadowLayer(0.01f, 1f, 1f, getColor(R.color.black))
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = android.view.Gravity.CENTER
            setPadding(Win95.dp(context, 2), 0, Win95.dp(context, 2), 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
    }

    private fun applied(message: String) {
        status.text = message
        Win95.confirmHaptic(status)
        renderHome()
    }

    // --- Icons ---

    private fun setupIcons() {
        val list = findViewById<RecyclerView>(R.id.icons_list)
        list.layoutManager = GridLayoutManager(this, iconColumns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) = if (iconsAdapter.isFullWidth(position)) iconColumns else 1
            }
        }
        list.adapter = iconsAdapter
        buildIconTabs()
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = renderIcons()
        })
        findViewById<View>(R.id.btn_icons_request).setOnClickListener { showRequestDialog() }
    }

    private fun buildIconTabs() {
        val row = findViewById<LinearLayout>(R.id.icon_tabs)
        row.removeAllViews()
        listOf(IconTab.YOURS to R.string.tab_your_apps, IconTab.ALL to R.string.tab_all, IconTab.SYSTEM to R.string.tab_system)
            .forEach { (tab, label) ->
                val active = tab == iconTab
                row.addView(TextView(this).apply {
                    setText(label)
                    setTextColor(getColor(R.color.black))
                    textSize = 14f
                    gravity = android.view.Gravity.CENTER
                    setPadding(Win95.dp(context, 14), 0, Win95.dp(context, 14), 0)
                    setBackgroundResource(if (active) R.drawable.win95_tab_active else R.drawable.win95_tab_inactive)
                    typeface = Win95.font(context, active)
                    isSelected = active
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        Win95.dp(context, if (active) 44 else 40)
                    )
                    setOnClickListener {
                        iconTab = tab
                        search.text = null
                        buildIconTabs()
                        renderIcons()
                    }
                })
            }
        row.addView(View(this).apply {
            setBackgroundResource(R.drawable.win95_tab_filler)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        })
    }

    private fun renderIcons() {
        val c = catalog ?: return
        val query = search.text?.toString()?.trim().orEmpty()
        val summary = findViewById<TextView>(R.id.icons_summary)
        val request = findViewById<View>(R.id.btn_icons_request)
        val rows = mutableListOf<IconRow>()

        if (query.isNotEmpty()) {
            val icons = (c.icons + c.system).filter { it.label.contains(query, ignoreCase = true) }
            val apps = c.unthemedInstalled.filter { it.label.contains(query, ignoreCase = true) }
            rows += icons.map(::iconCell)
            rows += apps.map(::appCell)
            if (icons.isEmpty()) {
                rows += IconRow.Header(getString(R.string.search_none))
                rows += IconRow.Action(getString(R.string.request_it)) {
                    showRequestDialog(preselect = apps.map { it.component }.toSet())
                }
            }
            summary.text = getString(R.string.search_results, query)
            request.visibility = View.GONE
        } else when (iconTab) {
            IconTab.YOURS -> {
                val yours = c.yourIcons
                summary.text = getString(R.string.summary_yours, c.themedCount, c.unthemedInstalled.size)
                request.visibility = if (c.unthemedInstalled.isEmpty()) View.GONE else View.VISIBLE
                rows += yours.map(::iconCell)
                if (c.unthemedInstalled.isNotEmpty()) {
                    rows += IconRow.Header(
                        getString(R.string.header_not_themed, c.unthemedInstalled.size),
                        getString(R.string.header_not_themed_caption)
                    )
                    rows += c.unthemedInstalled.map(::appCell)
                    rows += IconRow.Action(getString(R.string.request_these)) { showRequestDialog() }
                }
            }
            IconTab.ALL -> {
                summary.text = getString(R.string.summary_all, c.icons.size)
                request.visibility = View.GONE
                c.icons.groupBy { it.category }.forEach { (category, icons) ->
                    rows += IconRow.Header(category)
                    rows += icons.map(::iconCell)
                }
            }
            IconTab.SYSTEM -> {
                summary.text = getString(R.string.summary_system, c.system.size)
                request.visibility = View.GONE
                rows += IconRow.Header(getString(R.string.tab_system), getString(R.string.system_caption))
                rows += c.system.map(::iconCell)
            }
        }
        iconsAdapter.rows = rows
    }

    private fun iconCell(icon: PackIcon) = IconRow.Cell(
        icon.drawable, icon.label, { IconArt.icon(resources, icon.resId, iconPx) }, { showIconProperties(icon) }
    )

    private fun appCell(app: InstalledApp) = IconRow.Cell(
        app.component,
        app.label,
        { appIconCache.getOrPut(app.component) { IconArt.plaque(this, app.info.loadIcon(packageManager), iconPx) } },
        { showUnthemedProperties(app) }
    )

    private fun iconsStatus(): String {
        val c = catalog ?: return getString(R.string.status_ready)
        return getString(R.string.status_icons, c.icons.size + c.system.size, c.pack.appCount())
    }

    private fun showIconProperties(icon: PackIcon) {
        val c = catalog ?: return
        val dialog = Win95.dialog(this, R.layout.dialog_icon_properties)
        dialog.findViewById<TextView>(R.id.properties_title).text = getString(R.string.icon_properties_title, icon.label)
        dialog.findViewById<ImageView>(R.id.properties_icon).apply {
            val px = IconArt.crispPx(context, 88f)
            setImageBitmap(IconArt.icon(resources, icon.resId, px))
            layoutParams = layoutParams.apply { width = px; height = px }
        }
        dialog.findViewById<TextView>(R.id.properties_name).text = icon.label
        val themed = c.appsFor(icon.drawable)
        dialog.findViewById<TextView>(R.id.properties_apps).text = when {
            icon in c.system -> getString(R.string.icon_system_props)
            themed.isEmpty() -> getString(R.string.icon_not_installed)
            else -> getString(R.string.icon_themes_here) + "\n" + themed.joinToString("\n") { "• $it" }
        }
        dialog.findViewById<View>(R.id.btn_properties_ok).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btn_close_properties).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showUnthemedProperties(app: InstalledApp) {
        Win95.messageBox(
            this,
            getString(R.string.icon_properties_title, app.label),
            getString(R.string.icon_unthemed_props, app.label),
            choices = listOf(
                Win95.Choice(getString(R.string.cancel)),
                Win95.Choice(getString(R.string.request_it), primary = true) { showRequestDialog(setOf(app.component)) }
            )
        )
    }

    // --- Wallpapers ---

    private fun setupWallpapers() {
        val list = findViewById<RecyclerView>(R.id.wallpapers_list)
        val tileWidth = ((resources.displayMetrics.widthPixels - 48 * density) / 2).toInt()
        val thumbs = HashMap<Int, Bitmap>()
        list.layoutManager = GridLayoutManager(this, 2)
        list.adapter = WallpapersAdapter(
            tileWidth,
            { w -> thumbs.getOrPut(w.resId) { Wallpapers.thumbnail(this, w.resId, tileWidth) } },
            { Prefs.wallpaper(this) },
            ::onWallpaperTapped
        )
    }

    private fun onWallpaperTapped(wallpaper: Wallpaper) {
        if (wallpaper.live) {
            if (!Wallpapers.openStarfield(this)) {
                Win95.messageBox(this, getString(R.string.title_wallpapers), getString(R.string.live_wallpaper_unavailable), warning = true)
            }
            return
        }
        val dialog = Win95.dialog(this, R.layout.dialog_wallpaper)
        dialog.findViewById<TextView>(R.id.wallpaper_title).text = getString(R.string.set_wallpaper_title)
        dialog.findViewById<ImageView>(R.id.dlg_preview)
            .setImageBitmap(Wallpapers.thumbnail(this, wallpaper.resId, Win95.dp(this, 140)))
        fun set(flags: Int) {
            dialog.dismiss()
            Wallpapers.apply(this, wallpaper, flags) { ok ->
                if (ok) {
                    status.setText(R.string.status_wallpaper_set)
                    Win95.confirmHaptic(status)
                    catalog?.let(::renderMonitor)
                    @Suppress("NotifyDataSetChanged")
                    findViewById<RecyclerView>(R.id.wallpapers_list).adapter?.notifyDataSetChanged()
                } else {
                    Win95.messageBox(this, getString(R.string.set_wallpaper_title), getString(R.string.wallpaper_failed), warning = true)
                }
            }
        }
        dialog.findViewById<View>(R.id.btn_home).setOnClickListener { set(WallpaperManager.FLAG_SYSTEM) }
        dialog.findViewById<View>(R.id.btn_lock).setOnClickListener { set(WallpaperManager.FLAG_LOCK) }
        dialog.findViewById<View>(R.id.btn_both).setOnClickListener { set(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) }
        dialog.findViewById<View>(R.id.btn_close_wallpaper).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // --- Add/Remove Programs, About, feedback ---

    private fun showRequestDialog(preselect: Set<String> = emptySet()) {
        val dialog = Win95.dialog(this, R.layout.dialog_request)
        dialog.findViewById<View>(R.id.btn_close_request).setOnClickListener { dialog.dismiss() }
        dialog.show()

        fun populate(c: IconCatalog) {
            if (!dialog.isShowing) return
            if (c.unthemedInstalled.isEmpty()) {
                dialog.findViewById<TextView>(R.id.request_status).setText(R.string.request_all_themed)
                dialog.findViewById<View>(R.id.request_progress).visibility = View.GONE
                return
            }
            dialog.findViewById<View>(R.id.request_loading).visibility = View.GONE
            dialog.findViewById<ScrollView>(R.id.request_scroll).visibility = View.VISIBLE
            val list = dialog.findViewById<LinearLayout>(R.id.request_list)
            val boxes = c.unthemedInstalled.map { app ->
                val box = CheckBox(this).apply {
                    isChecked = app.component in preselect
                    setButtonDrawable(R.drawable.win95_checkbox)
                    setPadding(Win95.dp(context, 8), 0, Win95.dp(context, 8), 0)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                list.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    minimumHeight = Win95.dp(context, 48)
                    contentDescription = app.label
                    addView(box)
                    addView(ImageView(context).apply {
                        setImageDrawable(app.info.loadIcon(packageManager))
                        layoutParams = LinearLayout.LayoutParams(Win95.dp(context, 32), Win95.dp(context, 32)).apply {
                            marginEnd = Win95.dp(context, 10)
                        }
                    })
                    addView(TextView(context).apply {
                        text = app.label
                        setTextColor(getColor(R.color.black))
                        textSize = 14f
                    })
                    setOnClickListener { box.isChecked = !box.isChecked }
                })
                box to app
            }
            dialog.findViewById<CheckBox>(R.id.request_select_all).apply {
                visibility = View.VISIBLE
                isChecked = boxes.all { it.first.isChecked }
                setOnCheckedChangeListener { _, checked -> boxes.forEach { it.first.isChecked = checked } }
            }
            fun submit(viaGithub: Boolean) {
                val apps = boxes.filter { it.first.isChecked }.map { it.second }
                if (apps.isEmpty()) {
                    Win95.messageBox(this, getString(R.string.add_remove_programs), getString(R.string.request_none_selected))
                    return
                }
                dialog.dismiss()
                sendIconRequest(apps, viaGithub)
                status.setText(R.string.status_request_sent)
            }
            dialog.findViewById<View>(R.id.btn_request_github).setOnClickListener { submit(true) }
            dialog.findViewById<View>(R.id.btn_request_share).setOnClickListener { submit(false) }
        }

        val bar = dialog.findViewById<BlockProgressBar>(R.id.request_progress)
        val c = catalog
        if (c != null) {
            bar.animateTo(1f, 400) { populate(c) }
        } else Thread {
            val loaded = IconCatalog.load(this)
            runOnUiThread {
                catalog = loaded
                populate(loaded)
            }
        }.start()
    }

    private fun sendIconRequest(apps: List<InstalledApp>, viaGithub: Boolean) {
        val body = buildString {
            appendLine("Icon request sent from the app:")
            appendLine()
            apps.forEach {
                appendLine("- ${it.label}")
                appendLine("  `${it.component}`")
            }
        }
        if (viaGithub) {
            val url = "https://github.com/sindriax/win95-mode/issues/new" +
                "?title=" + Uri.encode("[icon] in-app request (${apps.size} apps)") +
                "&labels=icon-request&body=" + Uri.encode(body)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } else {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_EMAIL, arrayOf("hello@sindriax.dev"))
                putExtra(Intent.EXTRA_SUBJECT, "Win95 Mode icon request")
                putExtra(Intent.EXTRA_TEXT, body)
            }
            startActivity(Intent.createChooser(send, null))
        }
    }

    private fun showAboutDialog() {
        val dialog = Win95.dialog(this, R.layout.dialog_about)
        dialog.findViewById<ImageView>(R.id.about_icon).apply {
            val px = IconArt.crispPx(context, 56f)
            setImageBitmap(IconArt.icon(resources, R.drawable.ic_win95mode, px))
            layoutParams = layoutParams.apply { width = px; height = px }
        }
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        dialog.findViewById<TextView>(R.id.about_version).text = getString(R.string.about_version, version)
        catalog?.let {
            dialog.findViewById<TextView>(R.id.about_text).text =
                getString(R.string.about_text, it.icons.size + it.system.size, it.pack.appCount())
        }
        dialog.findViewById<View>(R.id.btn_ok).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btn_close_about).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btn_feedback).setOnClickListener {
            dialog.dismiss()
            openFeedback()
        }
        dialog.show()
    }

    private fun openFeedback() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sindriax/win95-mode/issues/new/choose")))
    }
}
