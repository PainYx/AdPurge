package com.ads.purge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

   
             
                                                       
                    
                           
   
class AdVendorCatalogTest {

    @Test
    fun testScanDetectsTencentAndSigmob() {
        val dir = Files.createTempDirectory("vendor").toFile()
        
        File(dir, "classes.dex").writeBytes(
            "com/qq/e/comm/adevent/ADEvent gettype".toByteArray()
        )
        
        File(dir, "classes2.dex").writeBytes(
            "com/sigmob/sdk/SigmobAd loadAd".toByteArray()
        )
        
        File(dir, "AndroidManifest.xml").writeBytes(
            "<manifest package=\"com.demo\">com.qq.e.sdk</manifest>".toByteArray()
        )
        
        File(dir, "assets").mkdirs()
        File(dir, "assets/gdt_android.xml").writeText("<config/>")

        val hits = AdVendorCatalog.scanVendors(dir)
        val ids = hits.map { it.vendor.id }
        assertTrue("应识别到腾讯广告", ids.contains("tencent"))
        assertTrue("应识别到 sigmob", ids.contains("sigmob"))

        val tencent = hits.first { it.vendor.id == "tencent" }
        
        assertTrue("腾讯应有 DEX 信号", tencent.matchedSignals.any { it.startsWith("DEX:") })
        assertTrue("腾讯应有 Manifest 信号", tencent.matchedSignals.any { it.startsWith("Manifest:") })
        assertTrue("腾讯应有 assets 信号", tencent.matchedSignals.any { it.startsWith("assets:") })
    }

    @Test
    fun testScanNoMatchReturnsEmpty() {
        val dir = Files.createTempDirectory("vendor").toFile()
        File(dir, "classes.dex").writeBytes("com/demo/business/MainActivity".toByteArray())
        val hits = AdVendorCatalog.scanVendors(dir)
        assertEquals("不应识别到任何厂商", 0, hits.size)
    }

    @Test
    fun testMergeIntoAddsVendorFeatures() {
        val base = AdPatternConfig.AdPatterns(
            sdkPackages = mutableListOf("com.user.own"),
            methodPatterns = mutableListOf("userMethod")
        )
        val selected = listOf(
            AdVendorCatalog.byId("tencent")!!,
            AdVendorCatalog.byId("jd")!!
        )
        val merged = AdVendorCatalog.mergeInto(base, selected)

        assertTrue("应保留原配置 sdkPackages", merged.sdkPackages.contains("com.user.own"))
        assertTrue("应加入腾讯包名", merged.sdkPackages.contains("com.qq.e"))
        assertTrue("应加入京东包名", merged.sdkPackages.contains("com.jd.ad.sdk"))
        assertTrue("应去重", merged.sdkPackages.count { it == "com.qq.e" } == 1)
        assertTrue("应加入腾讯 classKeywords", merged.classKeywords.any { it.contains("com/qq/e") })
        assertTrue("应加入京东 classKeywords", merged.classKeywords.any { it.contains("jingdong") })
        assertTrue("应保留原配置 methodPatterns", merged.methodPatterns.contains("userMethod"))
    }

    @Test
    fun testCollectFalseStateMethods() {
        val selected = listOf(
            AdVendorCatalog.byId("kuaishou")!!,
            AdVendorCatalog.byId("pangle")!!
        )
        val methods = AdVendorCatalog.collectFalseStateMethods(selected)
        assertTrue("应包含 isResultOk", methods.contains("isresultok"))
        assertTrue("应包含 hasInit", methods.contains("hasinit"))
    }

    @Test
    fun testVendorCatalogHasKeyVendors() {
        for (expected in listOf(
            "tencent", "kuaishou", "pangle", "baidu", "sigmob", "miui", "mbridge",
            "google", "cas", "taptap", "topon", "beizi", "jd", "moqi"
        )) {
            assertNotNull("厂商特征库应包含 $expected", AdVendorCatalog.byId(expected))
        }
    }
}
