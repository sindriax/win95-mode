package com.win95mode.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import org.xmlpull.v1.XmlPullParser

class PackIcon(val drawable: String, val label: String, val category: String, val resId: Int)

class InstalledApp(val label: String, val packageName: String, val activityName: String, val info: ResolveInfo) {
    val component get() = "ComponentInfo{$packageName/$activityName}"
}

/** Everything the screens need to know about the pack and this phone's apps. */
class IconCatalog private constructor(
    val pack: IconPack,
    /** Icons that theme at least one app, in drawable.xml order. */
    val icons: List<PackIcon>,
    /** Win95 system art that themes no app; for assigning by hand. */
    val system: List<PackIcon>,
    val themedInstalled: List<Pair<PackIcon, InstalledApp>>,
    val unthemedInstalled: List<InstalledApp>
) {
    /** Installed apps an icon themes, by label. */
    fun appsFor(drawable: String): List<String> =
        themedInstalled.filter { it.first.drawable == drawable }.map { it.second.label }.distinct().sorted()

    /** Pack icons for installed apps, one per icon, in drawable.xml order. */
    val yourIcons: List<PackIcon> by lazy {
        val mine = themedInstalled.map { it.first.drawable }.toSet()
        icons.filter { it.drawable in mine }
    }

    val installedCount get() = themedInstalled.map { it.second.packageName }.toSet().size + unthemedInstalled.size
    val themedCount get() = themedInstalled.map { it.second.packageName }.toSet().size

    companion object {
        @SuppressLint("DiscouragedApi")
        fun load(context: Context): IconCatalog {
            val res = context.resources
            val mappings = mutableMapOf<String, String>()
            res.getXml(R.xml.appfilter).forEachItem { p ->
                val component = p.getAttributeValue(null, "component")
                val drawable = p.getAttributeValue(null, "drawable")
                if (component != null && drawable != null) mappings[component] = drawable
            }
            val pack = IconPack(mappings)

            val all = mutableListOf<PackIcon>()
            var category = ""
            val parser = res.getXml(R.xml.drawable)
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "category" -> category = parser.getAttributeValue(null, "title") ?: ""
                        "item" -> {
                            val name = parser.getAttributeValue(null, "drawable")
                            val resId = name?.let { res.getIdentifier(it, "drawable", context.packageName) } ?: 0
                            if (resId != 0) {
                                all += PackIcon(name, parser.getAttributeValue(null, "name") ?: name, category, resId)
                            }
                        }
                    }
                }
                parser.next()
            }
            val (icons, system) = all.partition { pack.themesAnything(it.drawable) }
            val byName = all.associateBy { it.drawable }

            val pm = context.packageManager
            val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .mapNotNull { info ->
                    val a = info.activityInfo ?: return@mapNotNull null
                    if (a.packageName == context.packageName) null
                    else InstalledApp(info.loadLabel(pm).toString(), a.packageName, a.name, info)
                }
                .distinctBy { it.component }
                .sortedBy { it.label.lowercase() }
            val themed = mutableListOf<Pair<PackIcon, InstalledApp>>()
            val unthemed = mutableListOf<InstalledApp>()
            for (app in apps) {
                val icon = pack.drawableFor(app.packageName, app.activityName)?.let { byName[it] }
                if (icon != null) themed += icon to app else unthemed += app
            }
            return IconCatalog(pack, icons, system, themed, unthemed)
        }

        private fun XmlPullParser.forEachItem(block: (XmlPullParser) -> Unit) {
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && name == "item") block(this)
                next()
            }
        }
    }
}
