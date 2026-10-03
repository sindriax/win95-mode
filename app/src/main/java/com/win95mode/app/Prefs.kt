package com.win95mode.app

import android.content.Context

/** Small per-device memory: setup done, when the pack was last applied, current wallpaper. */
object Prefs {
    private fun prefs(context: Context) = context.getSharedPreferences("win95mode", Context.MODE_PRIVATE)

    fun setupDone(context: Context) = prefs(context).getBoolean("setup_done", false)
    fun markSetupDone(context: Context) = prefs(context).edit().putBoolean("setup_done", true).apply()

    fun recordApplied(context: Context, launcher: String) = prefs(context).edit()
        .putString("applied_launcher", launcher)
        .putLong("applied_at", System.currentTimeMillis())
        .apply()

    /** When the pack was last applied in [launcher], or null if never. */
    fun appliedAt(context: Context, launcher: String): Long? {
        val p = prefs(context)
        return if (p.getString("applied_launcher", null) == launcher) p.getLong("applied_at", 0).takeIf { it > 0 } else null
    }

    fun wallpaper(context: Context): String? = prefs(context).getString("wallpaper", null)
    fun recordWallpaper(context: Context, name: String) = prefs(context).edit().putString("wallpaper", name).apply()
}
