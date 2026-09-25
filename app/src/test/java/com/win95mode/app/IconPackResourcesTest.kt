package com.win95mode.app

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Validates the icon pack resources themselves: every drawable referenced in
 * appfilter.xml and drawable.xml must exist, and no launcher component may be
 * mapped twice (launchers pick one arbitrarily, hiding the conflict).
 */
class IconPackResourcesTest {

    private val resDir: File = run {
        var dir = File(System.getProperty("user.dir")!!)
        while (!File(dir, "src/main/res").isDirectory) {
            dir = dir.parentFile ?: error("could not locate src/main/res from ${System.getProperty("user.dir")}")
        }
        File(dir, "src/main/res")
    }

    private fun elements(xmlFile: String, tag: String): List<Element> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resDir, "xml/$xmlFile"))
        val nodes = doc.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun items(xmlFile: String): List<Element> = elements(xmlFile, "item")

    private fun drawableExists(name: String): Boolean =
        File(resDir, "drawable/$name.png").isFile || File(resDir, "drawable/$name.xml").isFile

    @Test
    fun `appfilter drawables all exist`() {
        val missing = items("appfilter.xml")
            .map { it.getAttribute("drawable") }
            .filter { it.isNotEmpty() && !drawableExists(it) }
        assertTrue("appfilter.xml references missing drawables: $missing", missing.isEmpty())
    }

    @Test
    fun `appfilter has no duplicate component mappings`() {
        val duplicates = items("appfilter.xml")
            .map { it.getAttribute("component") }
            .filter { it.isNotEmpty() }
            .groupBy { it }
            .filterValues { it.size > 1 }
            .keys
        assertTrue("appfilter.xml maps components more than once: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `dynamic calendar prefixes have all 31 day drawables`() {
        val prefixes = elements("appfilter.xml", "calendar")
            .map { it.getAttribute("prefix") }.toSet()
        val missing = prefixes.flatMap { prefix ->
            (1..31).map { day -> "$prefix$day" }.filterNot { drawableExists(it) }
        }
        assertTrue("calendar day drawables missing: $missing", missing.isEmpty())
    }

    @Test
    fun `every pack icon is mapped to an app or explicitly decorative`() {
        val decorative = setOf(
            "ic_recycle_bin", "ic_disk", "ic_start", "ic_paint", "ic_langjump"
        )
        val mapped = items("appfilter.xml").map { it.getAttribute("drawable") }.toSet()
        val unmapped = items("drawable.xml")
            .map { it.getAttribute("drawable") }
            .filter { it.isNotEmpty() && it !in mapped && it !in decorative }
        assertTrue(
            "drawable.xml icons with no appfilter mapping (map them or add to the decorative list): $unmapped",
            unmapped.isEmpty()
        )
    }

    @Test
    fun `drawable list entries all exist`() {
        val missing = items("drawable.xml")
            .map { it.getAttribute("drawable") }
            .filter { it.isNotEmpty() && !drawableExists(it) }
        assertTrue("drawable.xml references missing drawables: $missing", missing.isEmpty())
    }
}
