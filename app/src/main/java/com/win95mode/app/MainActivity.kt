package com.win95mode.app

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.IOException

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

        setupWallpaperClicks()
    }

    private fun setupWallpaperClicks() {
        wallpapers.forEach { (viewId, drawableId) ->
            findViewById<ImageView>(viewId)?.setOnClickListener {
                showWallpaperDialog(drawableId)
            }
        }
    }

    private fun showWallpaperDialog(drawableId: Int) {
        AlertDialog.Builder(this)
            .setTitle("Set Wallpaper")
            .setMessage("Apply this wallpaper?")
            .setPositiveButton("Apply") { _, _ ->
                setWallpaper(drawableId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setWallpaper(drawableId: Int) {
        try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            val bitmap = BitmapFactory.decodeResource(resources, drawableId)
            wallpaperManager.setBitmap(bitmap)
            Toast.makeText(this, "Wallpaper applied!", Toast.LENGTH_SHORT).show()
        } catch (_: IOException) {
            Toast.makeText(this, "Failed to set wallpaper", Toast.LENGTH_SHORT).show()
        }
    }
}
