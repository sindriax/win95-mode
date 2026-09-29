package com.win95mode.app

/** The appfilter mapping, as component string → drawable name. */
class IconPack(private val mappings: Map<String, String>) {

    /** Drawable themeing this launcher activity, or null if it has none.
     *  Launchers report components in full and in shortened form, and
     *  appfilter.xml contains both, so either may match. */
    fun drawableFor(packageName: String, activityName: String): String? =
        componentKeys(packageName, activityName).firstNotNullOfOrNull { mappings[it] }

    fun themesAnything(drawable: String): Boolean = drawable in drawables

    /** Distinct apps (packages) the pack themes. */
    fun appCount(): Int = mappings.keys.map { it.substringAfter('{').substringBefore('/') }.toSet().size

    private val drawables = mappings.values.toSet()

    companion object {
        fun componentKeys(packageName: String, activityName: String): List<String> {
            val full = "ComponentInfo{$packageName/$activityName}"
            val short = when {
                activityName.startsWith("$packageName.") ->
                    "ComponentInfo{$packageName/${activityName.removePrefix(packageName)}}"
                activityName.startsWith(".") ->
                    return listOf("ComponentInfo{$packageName/$packageName$activityName}", full)
                else -> return listOf(full)
            }
            return listOf(full, short)
        }
    }
}
