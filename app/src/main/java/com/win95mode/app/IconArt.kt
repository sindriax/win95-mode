package com.win95mode.app

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlin.math.roundToInt

/** Draws pack icons so pixel art stays crisp. The icons are 192px drawn on 32- or
 *  48-cell grids, so any multiple of 96px keeps every art pixel whole. */
object IconArt {

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun crispPx(context: Context, targetDp: Float, maxPx: Int = Int.MAX_VALUE): Int {
        var k = (targetDp * context.resources.displayMetrics.density / 96f).roundToInt().coerceAtLeast(1)
        while (k > 1 && k * 96 > maxPx) k--
        return k * 96
    }

    fun icon(resources: Resources, resId: Int, px: Int): Bitmap {
        val key = "$resId@$px"
        cache.get(key)?.let { return it }
        val source = BitmapFactory.decodeResource(resources, resId, BitmapFactory.Options().apply { inScaled = false })
        val out = if (source.width == px) source else Bitmap.createScaledBitmap(source, px, px, px < source.width)
        cache.put(key, out)
        return out
    }

    /** An unthemed app's own icon on the beveled plaque, as launchers draw it (appfilter scale 0.72). */
    fun plaque(context: Context, appIcon: Drawable, px: Int): Bitmap {
        val back = icon(context.resources, R.drawable.iconback, px)
        val out = back.copy(Bitmap.Config.ARGB_8888, true)
        val inset = (px * (1 - 0.72f) / 2).toInt()
        appIcon.setBounds(inset, inset, px - inset, px - inset)
        appIcon.draw(Canvas(out))
        return out
    }
}
