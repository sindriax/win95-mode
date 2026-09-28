package com.win95mode.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconPackTest {

    private val pack = IconPack(
        mapOf(
            "ComponentInfo{com.sec.android.app.camera/.Camera}" to "ic_camera",
            "ComponentInfo{com.android.dialer/com.android.dialer.DialtactsActivity}" to "ic_phone",
            "ComponentInfo{com.instagram.barcelona/com.instagram.barcelona.mainactivity.BarcelonaActivity}" to "ic_thread",
        )
    )

    @Test
    fun `full activity name matches a shortened appfilter entry`() {
        assertEquals("ic_camera", pack.drawableFor("com.sec.android.app.camera", "com.sec.android.app.camera.Camera"))
    }

    @Test
    fun `shortened activity name matches a full appfilter entry`() {
        assertEquals("ic_phone", pack.drawableFor("com.android.dialer", ".DialtactsActivity"))
    }

    @Test
    fun `exact full match`() {
        assertEquals(
            "ic_thread",
            pack.drawableFor("com.instagram.barcelona", "com.instagram.barcelona.mainactivity.BarcelonaActivity")
        )
    }

    @Test
    fun `activity outside its own package only matches in full form`() {
        assertEquals(
            listOf("ComponentInfo{com.oplus.dialer/com.android.contacts.DialtactsActivityAlias}"),
            IconPack.componentKeys("com.oplus.dialer", "com.android.contacts.DialtactsActivityAlias")
        )
    }

    @Test
    fun `package-name prefix without a dot is not shortened`() {
        assertEquals(
            listOf("ComponentInfo{com.foo/com.foobar.Main}"),
            IconPack.componentKeys("com.foo", "com.foobar.Main")
        )
    }

    @Test
    fun `unmapped app has no drawable`() {
        assertNull(pack.drawableFor("org.example.app", "org.example.app.MainActivity"))
    }
}
