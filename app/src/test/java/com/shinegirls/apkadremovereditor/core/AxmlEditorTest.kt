package com.ads.purge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

   
                                                 
   
class AxmlEditorTest {

    private fun loadManifest(): ByteArray {
        val res = javaClass.classLoader?.getResource("AndroidManifest_test.xml")
        requireNotNull(res) { "测试资源 AndroidManifest_test.xml 不存在" }
        return File(res.toURI()).readBytes()
    }

    @Test
    fun testIsAxml() {
        assertTrue(AxmlEditor.isAxml(loadManifest()))
        assertFalse(AxmlEditor.isAxml("not axml".toByteArray()))
    }

    @Test
    fun testListPermissions() {
        val perms = AxmlEditor.listPermissions(loadManifest())
        assertTrue(perms.contains("android.permission.INTERNET"))
        assertTrue(perms.contains("android.permission.READ_EXTERNAL_STORAGE"))
        assertTrue(perms.contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
        assertTrue(perms.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertTrue(perms.contains("com.ads.purge.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))
        assertEquals(6, perms.size)
    }

    @Test
    fun testListComponents() {
        val comps = AxmlEditor.listComponents(loadManifest())
        
        assertEquals(8, comps.size)
        assertTrue(comps.any { it.tag == "activity" && it.name == "com.ads.purge.MainActivity" })
        assertTrue(comps.any { it.tag == "provider" })
        assertTrue(comps.any { it.tag == "receiver" })
    }

    @Test
    fun testRemovePermissions() {
        val original = loadManifest()
        val result = AxmlEditor.removePermissions(original, setOf("android.permission.INTERNET"))
        assertTrue(result !== original) 
        val perms = AxmlEditor.listPermissions(result)
        assertFalse(perms.contains("android.permission.INTERNET"))
        assertEquals(5, perms.size)
    }

    @Test
    fun testRemoveMultiplePermissions() {
        val original = loadManifest()
        val result = AxmlEditor.removePermissions(
            original,
            setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE")
        )
        val perms = AxmlEditor.listPermissions(result)
        assertFalse(perms.contains("android.permission.INTERNET"))
        assertFalse(perms.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertEquals(4, perms.size)
    }

    @Test
    fun testRemoveNonexistentPermission() {
        val original = loadManifest()
        val result = AxmlEditor.removePermissions(original, setOf("android.permission.NOT_EXIST"))
        
        assertTrue(result === original)
    }

    @Test
    fun testRemoveAdComponents() {
        val original = loadManifest()
        val removed = mutableListOf<AxmlEditor.ComponentInfo>()
        val result = AxmlEditor.removeAdComponents(
            original,
            sdkPackages = listOf("androidx.profileinstaller", "com.qq.e.ads"),
            adComponents = listOf("ProfileInstallReceiver", "TTAdActivity"),
            removed = removed
        )
        
        assertTrue(removed.isNotEmpty())
        val comps = AxmlEditor.listComponents(result)
        assertFalse(comps.any { it.name == "androidx.profileinstaller.ProfileInstallReceiver" })
        assertFalse(comps.any { it.name == "androidx.profileinstaller.ProfileInstallerInitializer" })
        
        assertTrue(comps.any { it.name == "com.ads.purge.MainActivity" })
    }

    @Test
    fun testRemoveAdComponentByPackagePrefix() {
        val original = loadManifest()
        val removed = mutableListOf<AxmlEditor.ComponentInfo>()
        val result = AxmlEditor.removeAdComponents(
            original,
            sdkPackages = listOf("androidx.startup"),
            adComponents = emptyList(),
            removed = removed
        )
        assertTrue(removed.isNotEmpty())
        assertTrue(removed.all { it.name.startsWith("androidx.startup") })
        val comps = AxmlEditor.listComponents(result)
        assertFalse(comps.any { it.name.startsWith("androidx.startup") })
    }

    @Test
    fun testResultStillValidAxml() {
        val original = loadManifest()
        
        val perms = AxmlEditor.listPermissions(original).toSet()
        val removed = mutableListOf<AxmlEditor.ComponentInfo>()
        var cur = AxmlEditor.removePermissions(original, perms)
        cur = AxmlEditor.removeAdComponents(
            cur,
            sdkPackages = listOf("androidx.startup", "androidx.profileinstaller"),
            adComponents = listOf("EmojiCompatInitializer", "ProcessLifecycleInitializer"),
            removed = removed
        )
        
        assertTrue(AxmlEditor.isAxml(cur))
        val comps = AxmlEditor.listComponents(cur)
        
        assertTrue(comps.any { it.name == "com.ads.purge.MainActivity" })
        assertTrue(comps.any { it.name == "com.ads.purge.ui.EditorActivity" })
        assertFalse(comps.any { it.name.startsWith("androidx.startup") })
        assertFalse(comps.any { it.name.startsWith("androidx.profileinstaller") })
    }
}
