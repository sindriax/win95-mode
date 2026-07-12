package com.win95mode.app

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.xmlpull.v1.XmlPullParser

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
                val bitmap = BitmapFactory.decodeResource(resources, drawableId)
                WallpaperManager.getInstance(this).setBitmap(bitmap, null, true, flags)
                R.string.wallpaper_applied
            } catch (_: Exception) {
                R.string.wallpaper_failed
            }
            runOnUiThread {
                if (!isFinishing) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}
