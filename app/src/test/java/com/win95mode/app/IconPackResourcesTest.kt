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

    private fun items(xmlFile: String): List<Element> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resDir, "xml/$xmlFile"))
        val nodes = doc.getElementsByTagName("item")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

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
    fun `drawable list entries all exist`() {
        val missing = items("drawable.xml")
            .map { it.getAttribute("drawable") }
            .filter { it.isNotEmpty() && !drawableExists(it) }
        assertTrue("drawable.xml references missing drawables: $missing", missing.isEmpty())
    }
}
