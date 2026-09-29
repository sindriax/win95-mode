package com.win95mode.app

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.roundToInt

class Wallpaper(val key: String, val labelRes: Int, val resId: Int, val live: Boolean = false)

object Wallpapers {

    val all = listOf(
        Wallpaper("teal", R.string.wall_teal, R.drawable.wall_teal),
        Wallpaper("clouds", R.string.wall_clouds, R.drawable.wall_clouds),
        Wallpaper("setup", R.string.wall_setup, R.drawable.wall_setup),
        Wallpaper("stars", R.string.wall_stars, R.drawable.wall_stars),
        Wallpaper("maze", R.string.wall_maze, R.drawable.wall_matrix),
        Wallpaper("starfield", R.string.wall_starfield, R.drawable.wall_stars, live = true)
    )

    /** Decodes a wallpaper at roughly [widthPx] wide; the full images are 1440px+. */
    fun thumbnail(context: Context, resId: Int, widthPx: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, resId, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= widthPx) sample *= 2
        return BitmapFactory.decodeResource(context.resources, resId, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Sets a still wallpaper off the main thread; [done] runs on the main thread. */
    fun apply(activity: Activity, wallpaper: Wallpaper, flags: Int, done: (Boolean) -> Unit) {
        Thread {
            val ok = try {
                val manager = WallpaperManager.getInstance(activity)
                val metrics = activity.resources.displayMetrics
                // Honor the launcher's desired size (scrolling home screens ask for more
                // than one screen width) so nothing gets stretched.
                val w = maxOf(manager.desiredMinimumWidth, metrics.widthPixels)
                val h = maxOf(manager.desiredMinimumHeight, metrics.heightPixels)
                val source = BitmapFactory.decodeResource(activity.resources, wallpaper.resId)
                manager.setBitmap(scaleAndCrop(source, w, h), null, true, flags)
                Prefs.recordWallpaper(activity, wallpaper.key)
                true
            } catch (_: Exception) {
                false
            }
            activity.runOnUiThread { if (!activity.isFinishing) done(ok) }
        }.start()
    }

    /** Opens the system preview for the Starfield live wallpaper. */
    fun openStarfield(activity: Activity): Boolean {
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(activity, StarfieldWallpaperService::class.java)
        )
        for (intent in listOf(direct, Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))) {
            try {
                activity.startActivity(intent)
                Prefs.recordWallpaper(activity, "starfield")
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    private fun scaleAndCrop(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val scale = maxOf(targetWidth.toFloat() / source.width, targetHeight.toFloat() / source.height)
        val scaled = Bitmap.createScaledBitmap(
            source,
            (source.width * scale).roundToInt().coerceAtLeast(targetWidth),
            (source.height * scale).roundToInt().coerceAtLeast(targetHeight),
            true
        )
        return Bitmap.createBitmap(
            scaled, (scaled.width - targetWidth) / 2, (scaled.height - targetHeight) / 2, targetWidth, targetHeight
        )
    }
}
