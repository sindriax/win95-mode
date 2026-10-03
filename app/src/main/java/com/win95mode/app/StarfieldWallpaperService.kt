package com.win95mode.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.random.Random

/** The Win95 Starfield Simulation screensaver as a live wallpaper: white
 *  pixel-square stars flying out of the centre, no antialiasing anywhere. */
class StarfieldWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = StarfieldEngine()

    private class Star(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
        fun reset(rng: Random) {
            x = rng.nextFloat() - 0.5f
            y = rng.nextFloat() - 0.5f
            z = 0.3f + rng.nextFloat() * 0.7f
        }
    }

    private inner class StarfieldEngine : Engine() {

        private val rng = Random(95)
        private val stars = List(160) { Star().also { it.reset(rng) } }
        private val paint = Paint().apply { color = Color.WHITE }
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false

        private val frame = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) handler.postDelayed(this, FRAME_MS)
            }
        }

        override fun onVisibilityChanged(nowVisible: Boolean) {
            visible = nowVisible
            handler.removeCallbacks(frame)
            if (nowVisible) handler.post(frame)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            super.onDestroy()
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return
                canvas.drawColor(Color.BLACK)
                val w = canvas.width
                val h = canvas.height
                val cx = w / 2f
                val cy = h / 2f
                for (star in stars) {
                    star.z -= SPEED
                    val px = cx + star.x / star.z * w
                    val py = cy + star.y / star.z * w
                    if (star.z <= SPEED || px < 0 || px > w || py < 0 || py > h) {
                        star.reset(rng)
                        continue
                    }
                    // Nearer stars are bigger; sizes snap to whole density-scaled
                    // pixels so they stay chunky on high-res screens.
                    val density = resources.displayMetrics.density
                    val size = (1 + ((1f - star.z) * 3f).toInt()) * density
                    canvas.drawRect(px, py, px + size, py + size, paint)
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    private companion object {
        const val FRAME_MS = 33L
        const val SPEED = 0.004f
    }
}
