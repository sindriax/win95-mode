package com.win95mode.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings

class LauncherTarget(
    val label: String,
    val pkg: String,
    // Launchers without a public apply intent are opened so the user can pick the pack.
    val applyIntent: ((String) -> Intent)? = null
)

sealed interface LauncherState {
    /** The default home app can't use icon packs; [homeLabel] and [homePkg] name it when known. */
    data class Unsupported(val homeLabel: String?, val homePkg: String?) : LauncherState
    data class NotHome(val installed: List<LauncherTarget>) : LauncherState
    data class Ready(val target: LauncherTarget) : LauncherState
}

object Launchers {

    val targets = listOf(
        LauncherTarget("Nova Launcher", "com.teslacoilsw.launcher") { pack ->
            Intent("com.teslacoilsw.launcher.APPLY_ICON_THEME")
                .setPackage("com.teslacoilsw.launcher")
                .putExtra("com.teslacoilsw.launcher.extra.ICON_THEME_TYPE", "GO")
                .putExtra("com.teslacoilsw.launcher.extra.ICON_THEME_PACKAGE", pack)
        },
        LauncherTarget("Lawnchair", "app.lawnchair"),
        LauncherTarget("Lawnchair 2", "ch.deletescape.lawnchair.plah"),
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

    /** Launchers offered to people who have none; Play Store packages. */
    val recommended = listOf(
        "Lawnchair" to "app.lawnchair",
        "Nova Launcher" to "com.teslacoilsw.launcher",
        "Smart Launcher" to "ginlemon.flowerfree"
    )

    fun state(context: Context): LauncherState {
        val pm = context.packageManager
        val installed = targets.filter { pm.getLaunchIntentForPackage(it.pkg) != null }
        val home = defaultHome(pm)
        installed.firstOrNull { it.pkg == home }?.let { return LauncherState.Ready(it) }
        if (installed.isNotEmpty()) return LauncherState.NotHome(installed)
        return LauncherState.Unsupported(home?.let { label(pm, it) }, home)
    }

    fun icon(context: Context, pkg: String): Drawable? =
        try { context.packageManager.getApplicationIcon(pkg) } catch (_: PackageManager.NameNotFoundException) { null }

    /** Fires the launcher's apply intent. Returns false if it has none or it failed. */
    fun applyDirect(context: Context, target: LauncherTarget): Boolean {
        val intent = target.applyIntent?.invoke(context.packageName) ?: return false
        return try {
            context.startActivity(intent)
            Prefs.recordApplied(context, target.label)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun open(context: Context, target: LauncherTarget) {
        context.packageManager.getLaunchIntentForPackage(target.pkg)?.let {
            context.startActivity(it)
            Prefs.recordApplied(context, target.label)
        }
    }

    fun openHomeSettings(context: Context) {
        for (action in listOf(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_SETTINGS)) {
            try {
                context.startActivity(Intent(action))
                return
            } catch (_: Exception) {
            }
        }
    }

    fun openStore(context: Context, pkg: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        } catch (_: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
        }
    }

    private fun defaultHome(pm: PackageManager): String? =
        pm.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName?.takeIf { it != "android" }

    private fun label(pm: PackageManager, pkg: String): String? =
        try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { null }
}
