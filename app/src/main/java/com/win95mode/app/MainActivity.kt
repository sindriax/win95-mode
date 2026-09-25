package com.win95mode.app

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.Window
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.xmlpull.v1.XmlPullParser
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private val wallpapers = mapOf(
        R.id.wall_teal to R.drawable.wall_teal,
        R.id.wall_clouds to R.drawable.wall_clouds,
        R.id.wall_setup to R.drawable.wall_setup,
        R.id.wall_stars to R.drawable.wall_stars,
        R.id.wall_matrix to R.drawable.wall_matrix
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupWindowControls()
        populateIconGrid()
        setupWallpaperClicks()
        findViewById<TextView>(R.id.btn_apply_pack).setOnClickListener { showApplyDialog() }
        findViewById<TextView>(R.id.btn_request_icons).setOnClickListener { showRequestDialog() }
        findViewById<TextView>(R.id.btn_starfield).setOnClickListener { openStarfieldPreview() }
    }

    private fun openStarfieldPreview() {
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this, StarfieldWallpaperService::class.java)
        )
        try {
            startActivity(direct)
        } catch (_: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (_: Exception) {
                Toast.makeText(this, R.string.live_wallpaper_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private class UnthemedApp(val label: String, val component: String)

    private fun showRequestDialog() {
        val dialog = win95Dialog(R.layout.dialog_request)
        dialog.findViewById<TextView>(R.id.btn_cancel_request)?.setOnClickListener { dialog.dismiss() }
        dialog.show()

        Thread {
            val unthemed = findUnthemedApps()
            runOnUiThread {
                if (!dialog.isShowing) return@runOnUiThread
                val list = dialog.findViewById<LinearLayout>(R.id.request_list) ?: return@runOnUiThread
                val status = dialog.findViewById<TextView>(R.id.request_status)
                if (unthemed.isEmpty()) {
                    status?.setText(R.string.request_all_themed)
                    return@runOnUiThread
                }
                list.removeView(status)
                val boxes = unthemed.map { app ->
                    CheckBox(this).apply {
                        text = app.label
                        setTextColor(getColor(R.color.black))
                        textSize = 12f
                        tag = app
                    }.also { list.addView(it) }
                }
                fun selected() = boxes.filter { it.isChecked }.map { it.tag as UnthemedApp }
                fun submit(viaGithub: Boolean) {
                    val apps = selected()
                    if (apps.isEmpty()) {
                        Toast.makeText(this, R.string.request_none_selected, Toast.LENGTH_SHORT).show()
                    } else {
                        dialog.dismiss()
                        sendIconRequest(apps, viaGithub)
                    }
                }
                dialog.findViewById<TextView>(R.id.btn_request_github)?.setOnClickListener { submit(true) }
                dialog.findViewById<TextView>(R.id.btn_request_share)?.setOnClickListener { submit(false) }
            }
        }.start()
    }

    /** Launchable apps whose component has no appfilter mapping, in either the
     *  full or the shortened component form launchers report. */
    private fun findUnthemedApps(): List<UnthemedApp> {
        val mapped = mutableSetOf<String>()
        val parser = resources.getXml(R.xml.appfilter)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item") {
                parser.getAttributeValue(null, "component")?.let { mapped.add(it) }
            }
            parser.next()
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                val full = "ComponentInfo{${activity.packageName}/${activity.name}}"
                val short = if (activity.name.startsWith(activity.packageName)) {
                    "ComponentInfo{${activity.packageName}/${activity.name.removePrefix(activity.packageName)}}"
                } else full
                if (activity.packageName == packageName || full in mapped || short in mapped) null
                else UnthemedApp(info.loadLabel(packageManager).toString(), full)
            }
            .distinctBy { it.component }
            .sortedBy { it.label.lowercase() }
    }

    private fun sendIconRequest(apps: List<UnthemedApp>, viaGithub: Boolean) {
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

    private class LauncherTarget(
        val label: String,
        val pkg: String,
        // Launchers without a public apply intent get opened with a hint instead.
        val applyIntent: ((String) -> Intent)? = null
    )

    private val launcherTargets = listOf(
        LauncherTarget("Nova Launcher", "com.teslacoilsw.launcher") { pack ->
            Intent("com.teslacoilsw.launcher.APPLY_ICON_THEME")
                .setPackage("com.teslacoilsw.launcher")
                .putExtra("com.teslacoilsw.launcher.extra.ICON_THEME_TYPE", "GO")
                .putExtra("com.teslacoilsw.launcher.extra.ICON_THEME_PACKAGE", pack)
        },
        LauncherTarget("Lawnchair", "app.lawnchair"),
        LauncherTarget("Apex Launcher", "com.anddoes.launcher") { pack ->
            Intent("com.anddoes.launcher.SET_THEME")
                .setPackage("com.anddoes.launcher")
                .putExtra("com.anddoes.launcher.THEME_PACKAGE_NAME", pack)
        },
        LauncherTarget("Action Launcher", "com.actionlauncher.playstore"),
        LauncherTarget("Smart Launcher", "ginlemon.flowerfree") { pack ->
            Intent("ginlemon.smartlauncher.setGSLTHEME")
                .setPackage("ginlemon.flowerfree")
                .putExtra("package", pack)
        },
        LauncherTarget("Smart Launcher Pro", "ginlemon.flowerpro") { pack ->
            Intent("ginlemon.smartlauncher.setGSLTHEME")
                .setPackage("ginlemon.flowerpro")
                .putExtra("package", pack)
        }
    )

    private fun showApplyDialog() {
        val dialog = win95Dialog(R.layout.dialog_apply)
        val installed = launcherTargets.filter {
            packageManager.getLaunchIntentForPackage(it.pkg) != null
        }

        if (installed.isEmpty()) {
            dialog.findViewById<TextView>(R.id.choose_launcher_label)?.visibility = View.GONE
            dialog.findViewById<TextView>(R.id.no_launcher_text)?.visibility = View.VISIBLE
            dialog.findViewById<TextView>(R.id.btn_get_lawnchair)?.apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    dialog.dismiss()
                    openPlayStore("app.lawnchair")
                }
            }
        } else {
            val list = dialog.findViewById<LinearLayout>(R.id.launcher_list)
            installed.forEach { target ->
                list?.addView(win95Button(target.label) {
                    dialog.dismiss()
                    applyWith(target)
                })
            }
        }
        dialog.findViewById<TextView>(R.id.btn_cancel_apply)?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun applyWith(target: LauncherTarget) {
        target.applyIntent?.invoke(packageName)?.let { intent ->
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
                // Launcher installed but the apply activity moved; fall through.
            }
        }
        packageManager.getLaunchIntentForPackage(target.pkg)?.let {
            startActivity(it)
            Toast.makeText(
                this, getString(R.string.open_launcher_hint, target.label), Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openPlayStore(pkg: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
        try {
            startActivity(market)
        } catch (_: Exception) {
            startActivity(web)
        }
    }

    private fun win95Button(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextColor(getColor(R.color.black))
            textSize = 12f
            setBackgroundResource(R.drawable.win95_button_selector)
            val density = resources.displayMetrics.density
            setPadding(
                (14 * density).toInt(), (6 * density).toInt(),
                (14 * density).toInt(), (6 * density).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (6 * density).toInt() }
            gravity = android.view.Gravity.CENTER
            setOnClickListener { onClick() }
        }

    private fun setupWindowControls() {
        findViewById<TextView>(R.id.btn_close).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_minimize).setOnClickListener { moveTaskToBack(true) }
        findViewById<TextView>(R.id.btn_maximize).setOnClickListener {
            Toast.makeText(this, R.string.already_maximized, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btn_start).setOnClickListener { showAboutDialog() }
    }

    /** Fills the preview grid with every icon declared in xml/drawable.xml,
     *  so the app always showcases the full, current icon set. */
    @SuppressLint("DiscouragedApi")
    private fun populateIconGrid() {
        val grid = findViewById<GridLayout>(R.id.icon_grid)
        val density = resources.displayMetrics.density
        val size = (40 * density).toInt()
        val margin = (6 * density).toInt()

        val parser = resources.getXml(R.xml.drawable)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item") {
                val drawableName = parser.getAttributeValue(null, "drawable")
                val label = parser.getAttributeValue(null, "name") ?: drawableName
                val resId = drawableName
                    ?.let { resources.getIdentifier(it, "drawable", packageName) } ?: 0
                if (resId != 0) {
                    grid.addView(ImageView(this).apply {
                        setImageResource(resId)
                        contentDescription = label
                        layoutParams = GridLayout.LayoutParams().apply {
                            width = size
                            height = size
                            setMargins(margin, margin, margin, margin)
                        }
                    })
                }
            }
            parser.next()
        }
        setupIconSearch(grid)
    }

    private fun setupIconSearch(grid: GridLayout) {
        val emptyNote = findViewById<TextView>(R.id.icon_search_empty)
        findViewById<EditText>(R.id.icon_search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim().orEmpty()
                var visible = 0
                for (i in 0 until grid.childCount) {
                    val icon = grid.getChildAt(i)
                    val matches = query.isEmpty() ||
                        icon.contentDescription?.contains(query, ignoreCase = true) == true
                    icon.visibility = if (matches) View.VISIBLE else View.GONE
                    if (matches) visible++
                }
                emptyNote.visibility = if (visible == 0) View.VISIBLE else View.GONE
            }
        })
    }

    private fun setupWallpaperClicks() {
        wallpapers.forEach { (viewId, drawableId) ->
            findViewById<ImageView>(viewId)?.setOnClickListener {
                showWallpaperDialog(drawableId)
            }
        }
    }

    private fun showWallpaperDialog(drawableId: Int) {
        val dialog = win95Dialog(R.layout.dialog_wallpaper)
        dialog.findViewById<ImageView>(R.id.dlg_preview)?.setImageResource(drawableId)

        fun apply(flags: Int) {
            dialog.dismiss()
            applyWallpaper(drawableId, flags)
        }
        dialog.findViewById<TextView>(R.id.btn_home)?.setOnClickListener { apply(WallpaperManager.FLAG_SYSTEM) }
        dialog.findViewById<TextView>(R.id.btn_lock)?.setOnClickListener { apply(WallpaperManager.FLAG_LOCK) }
        dialog.findViewById<TextView>(R.id.btn_both)?.setOnClickListener {
            apply(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        }
        dialog.findViewById<TextView>(R.id.btn_cancel)?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showAboutDialog() {
        val dialog = win95Dialog(R.layout.dialog_about)
        dialog.findViewById<TextView>(R.id.btn_ok)?.setOnClickListener { dialog.dismiss() }
        dialog.findViewById<TextView>(R.id.btn_feedback)?.setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sindriax/win95-mode/issues/new/choose"))
            )
        }
        dialog.show()
    }

    private fun win95Dialog(layoutId: Int): Dialog =
        Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(layoutId)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

    private fun applyWallpaper(drawableId: Int, flags: Int) {
        Thread {
            val message = try {
                val manager = WallpaperManager.getInstance(this)
                val metrics = resources.displayMetrics
                // Honor the launcher's desired size (scrolling home screens ask
                // for more than one screen width) so nothing gets stretched.
                val targetWidth = maxOf(manager.desiredMinimumWidth, metrics.widthPixels)
                val targetHeight = maxOf(manager.desiredMinimumHeight, metrics.heightPixels)
                val source = BitmapFactory.decodeResource(resources, drawableId)
                manager.setBitmap(scaleAndCrop(source, targetWidth, targetHeight), null, true, flags)
                R.string.wallpaper_applied
            } catch (_: Exception) {
                R.string.wallpaper_failed
            }
            runOnUiThread {
                if (!isFinishing) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun scaleAndCrop(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val scale = maxOf(
            targetWidth.toFloat() / source.width, targetHeight.toFloat() / source.height
        )
        val scaled = Bitmap.createScaledBitmap(
            source,
            (source.width * scale).roundToInt().coerceAtLeast(targetWidth),
            (source.height * scale).roundToInt().coerceAtLeast(targetHeight),
            true
        )
        return Bitmap.createBitmap(
            scaled, (scaled.width - targetWidth) / 2, (scaled.height - targetHeight) / 2,
            targetWidth, targetHeight
        )
    }
}
