package com.ads.purge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

   
                                   
   
class AdAssetRemovalTest {

    private fun tempDir(): File = Files.createTempDirectory("adassets").toFile()

    private fun writeAsset(dir: File, rel: String, content: String = "{}") {
        val f = File(dir, rel)
        f.parentFile?.mkdirs()
        f.writeText(content)
    }

    @Test
    fun testConfiguredAssetRemoved() {
        val dir = tempDir()
        val assets = File(dir, "assets")
        writeAsset(dir, "assets/gdt_android.xml", "<config/>")
        writeAsset(dir, "assets/tt_android_config.json")
        writeAsset(dir, "assets/app-ads.txt")

        val report = StringBuilder()
        val cleaned = AdRemover.cleanAdSdkAssets(
            extractDir = dir,
            adAssetFiles = listOf("gdt_android.xml", "tt_android_config.json", "app-ads.txt"),
            sdkPackages = listOf("com.qq.e.ads", "com.bytedance.sdk.openadsdk"),
            dexReferencedAssets = emptySet(),
            log = {},
            report = report
        )

        assertEquals(3, cleaned)
        assertFalse(File(assets, "gdt_android.xml").exists())
        assertFalse(File(assets, "tt_android_config.json").exists())
        assertFalse(File(assets, "app-ads.txt").exists())
    }

    @Test
    fun testSdkKeywordAssetRemovedEvenWithoutDexRef() {
        val dir = tempDir()
        val assets = File(dir, "assets")
        
        
        writeAsset(dir, "assets/gdt_ad_config.xml")

        val report = StringBuilder()
        val cleaned = AdRemover.cleanAdSdkAssets(
            extractDir = dir,
            adAssetFiles = emptyList(),
            sdkPackages = listOf("com.qq.e.ads", "com.bytedance.sdk.openadsdk"),
            dexReferencedAssets = emptySet(),
            log = {},
            report = report
        )

        assertEquals(1, cleaned)
        assertFalse(File(assets, "gdt_ad_config.xml").exists())
    }

    @Test
    fun testBusinessAssetNotRemoved() {
        val dir = tempDir()
        val assets = File(dir, "assets")
        
        writeAsset(dir, "assets/readme_config.json")
        writeAsset(dir, "assets/ads.txt")

        val report = StringBuilder()
        val cleaned = AdRemover.cleanAdSdkAssets(
            extractDir = dir,
            adAssetFiles = emptyList(),
            sdkPackages = listOf("com.qq.e.ads"),
            dexReferencedAssets = setOf("readme_config.json", "ads.txt"),
            log = {},
            report = report
        )

        assertEquals(0, cleaned)
        assertTrue(File(assets, "readme_config.json").exists())
        assertTrue(File(assets, "ads.txt").exists())
    }

    @Test
    fun testSubDirAssetMatchedByConfiguredPattern() {
        val dir = tempDir()
        val assets = File(dir, "assets")
        writeAsset(dir, "assets/pangle/tt_android_config.json")

        val report = StringBuilder()
        val cleaned = AdRemover.cleanAdSdkAssets(
            extractDir = dir,
            adAssetFiles = listOf("tt_android_config.json"),
            sdkPackages = emptyList(),
            dexReferencedAssets = emptySet(),
            log = {},
            report = report
        )

        assertEquals(1, cleaned)
        assertFalse(File(assets, "pangle/tt_android_config.json").exists())
    }

    @Test
    fun testMissingAssetsDir() {
        val dir = tempDir()
        val report = StringBuilder()
        val cleaned = AdRemover.cleanAdSdkAssets(
            extractDir = dir,
            adAssetFiles = listOf("gdt_android.xml"),
            sdkPackages = emptyList(),
            dexReferencedAssets = emptySet(),
            log = {},
            report = report
        )
        assertEquals(0, cleaned)
    }
}
