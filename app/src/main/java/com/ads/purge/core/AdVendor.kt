package com.ads.purge.core

import com.ads.purge.core.AdPatternConfig.AdPatterns
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset

   
                                       
  
                                             
      
                                           
                                      
                                                                
  
                                             
                                      
                                           
   
data class AdVendor(
    val id: String,
    val name: String,
    val sdkPackages: List<String> = emptyList(),
    val classKeywords: List<String> = emptyList(),
    val methodPatterns: List<String> = emptyList(),
    val falseStateMethods: List<String> = emptyList(),
    val adKeyStrings: List<String> = emptyList(),
    val adAssetFiles: List<String> = emptyList(),
    val adPermissions: List<String> = emptyList()
)

   
                                    
   
data class VendorHit(
    val vendor: AdVendor,
    val matchedSignals: List<String>
)

/** 厂商扫描结果：厂商命中清单 + 每个DEX命中的超集预检词（供修补阶段预检零IO判定） */
data class VendorScanResult(
    val hits: List<VendorHit>,
    val dexHitKeywords: Map<String, Set<String>>
)

object AdVendorCatalog {

    /** 流式扫描缓冲区：1MB */
    private const val STREAM_BUF_SIZE = 1024 * 1024

        
                      
                                             
                                                           
        
    val vendors: List<AdVendor> = listOf(
        
        AdVendor(
            id = "tencent",
            name = "腾讯广告 GDT",
            sdkPackages = listOf("com.qq.e"),
            classKeywords = listOf("com/qq/e/comm/adevent/", "com/qq/e/comm/managers/"),
            methodPatterns = listOf("gettype"),
            adKeyStrings = listOf("qq.e"),
            adAssetFiles = listOf("gdtadv2.jar", "gdt_android.xml")
        ),
        
        AdVendor(
            id = "kuaishou",
            name = "快手广告",
            sdkPackages = listOf("com.kwad.sdk"),
            classKeywords = listOf("com/kwad/sdk/core/network/baseresultdata"),
            methodPatterns = listOf("isresultok"),
            falseStateMethods = listOf("isresultok"),
            adKeyStrings = listOf("kwad")
        ),
        
        AdVendor(
            id = "pangle",
            name = "穿山甲广告 Pangle",
            sdkPackages = listOf("com.bytedance.sdk", "com.bytedance.pangle"),
            classKeywords = listOf(
                "com/bytedance/sdk/openadsdk/ttadconfig",
                "com/bytedance/pangle/zeus",
                "com/bytedance/sdk/openadsdk"
            ),
            methodPatterns = listOf("getsdkinfo", "getappid"),
            falseStateMethods = listOf("hasinit"),
            adKeyStrings = listOf("pangle", "bytedance")
        ),
        
        AdVendor(
            id = "baidu",
            name = "百度广告",
            sdkPackages = listOf("com.baidu.mobads.sdk"),
            classKeywords = listOf("com/baidu/mobads/", "abstractprodtemplate"),
            methodPatterns = listOf("onsuccess"),
            adKeyStrings = listOf("baidu.*ads", "mobads"),
            adAssetFiles = listOf("baidu_mobads_sdk_config.xml")
        ),
        
        AdVendor(
            id = "sigmob",
            name = "Sigmob 广告",
            sdkPackages = listOf("com.sigmob.sdk"),
            classKeywords = listOf("com/sigmob/"),
            adKeyStrings = listOf("sigmob")
        ),
        
        AdVendor(
            id = "miui",
            name = "米萌广告 MIUI",
            sdkPackages = listOf("com.miui.zeus.mimo"),
            classKeywords = listOf("com/miui/zeus/mimo/sdk/mimosdk"),
            methodPatterns = listOf("mimosdk", "mimosdkinit"),
            adKeyStrings = listOf("zeus.mimo", "mimo.sdk")
        ),
        
        AdVendor(
            id = "mbridge",
            name = "Mintegral 广告",
            sdkPackages = listOf("com.mbridge"),
            classKeywords = listOf("com/mbridge/"),
            methodPatterns = listOf("getadhtml"),
            adKeyStrings = listOf("mbridge")
        ),
        
        AdVendor(
            id = "google",
            name = "谷歌广告 AdMob",
            sdkPackages = listOf("com.google.android.gms.ads", "com.google.gms"),
            classKeywords = listOf(
                "com/google/android/gms/ads",
                "com/google/ads"
            ),
            methodPatterns = listOf("loadad", "showinterstitial", "showrewardedvideo", "loadbannerad"),
            adKeyStrings = listOf("google.*ads", "ca-app-pub-"),
            adPermissions = listOf(
                "com.google.android.gms.permission.AD_ID",
                "com.google.android.gms.ads.permission.AD_ID"
            )
        ),
        
        AdVendor(
            id = "cas",
            name = "CAS 广告",
            sdkPackages = listOf("com.cleversolutions"),
            classKeywords = listOf("com/cleversolutions/", "lastpagead", "targetad"),
            adKeyStrings = listOf("cleversolutions")
        ),
        
        AdVendor(
            id = "taptap",
            name = "TapTap 广告",
            sdkPackages = listOf("com.taptap"),
            classKeywords = listOf("com/taptap/"),
            adKeyStrings = listOf("taptap")
        ),
        
        AdVendor(
            id = "topon",
            name = "TopOn 广告",
            sdkPackages = listOf("com.topon", "com.anythink"),
            classKeywords = listOf("com/topon/", "com/anythink/"),
            adKeyStrings = listOf("topon", "anythink")
        ),
        
        AdVendor(
            id = "beizi",
            name = "倍孜广告",
            sdkPackages = listOf("com.beizi.ad"),
            classKeywords = listOf("com/beizi/"),
            adKeyStrings = listOf("beizi")
        ),
        
        AdVendor(
            id = "jd",
            name = "京东广告 JAD",
            sdkPackages = listOf("com.jd.ad.sdk", "com.jd.ad"),
            classKeywords = listOf("com/jd/ad/sdk", "jingdong/ads/dsp/rtb/tp/addadgroup"),
            methodPatterns = listOf("addadgroup", "addactivityadgroup"),
            adKeyStrings = listOf("jingdong", "jd.ad")
        ),
        
        AdVendor(
            id = "moqi",
            name = "Moqi 广告",
            sdkPackages = listOf("com.moqi.sdk"),
            classKeywords = listOf("com/moqi/sdk"),
            adKeyStrings = listOf("moqi.sdk")
        )
    )

    private val vendorsById: Map<String, AdVendor> = vendors.associateBy { it.id }

    fun byId(id: String): AdVendor? = vendorsById[id]

       
                                   
      
            
                                                                                
                                                  
                                                              
      
                                                 
                          
      
                                       
       
    /**
     * 扫描提取目录中的 DEX/assets/Manifest，识别已知广告SDK厂商。
     *
     * 匹配策略（按优先级）：
     * 1. DEX 文本中匹配 sdkPackages（点格式 + 斜杠格式）或 classKeywords
     * 2. assets 文件名匹配 adAssetFiles
     * 3. AndroidManifest.xml 中匹配 sdkPackages
     *
     * ★ 单遍扫描：旧实现按厂商外循环逐个扫 DEX（14家×10个DEX，最坏全量重复读上百遍，
     * 是「识别广告商慢」的根因）。现改为 Aho-Corasick 多模式匹配器一次流式扫描
     * 全部 DEX，同时收集「超集预检词」命中明细（供修补阶段预检零IO判定）。
     *
     * 注意：adKeyStrings 不参与扫描阶段匹配（短字符串在二进制 DEX 中误报率极高），
     * 仅用于 DEX 修补阶段的字符串常量替换。
     *
     * @param superPrecheckKeywords 超集预检词（全厂商特征+内置通用词+全量配置特征）。
     *        词集必须 ⊇ 修补阶段使用的预检词集，缓存判定才无假阴性。
     */
    fun scanVendors(extractDir: File, superPrecheckKeywords: Set<String> = emptySet()): VendorScanResult {
        val dexFiles = extractDir.listFiles { f ->
            f.isFile && f.name.endsWith(".dex")
        } ?: emptyArray()

        val manifestBytes = runCatching {
            File(extractDir, "AndroidManifest.xml").readBytes()
        }.getOrNull()

        val assetNames = runCatching {
            val dir = File(extractDir, "assets")
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown().filter { it.isFile }.map { it.name.lowercase() }.toList()
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())

        // 厂商特征目标 -> 厂商ID集合
        val targetToVendors = LinkedHashMap<String, MutableSet<String>>()
        for (vendor in vendors) {
            for (t in buildMatchTargets(vendor)) {
                targetToVendors.getOrPut(t.lowercase()) { mutableSetOf() }.add(vendor.id)
            }
        }

        // 超集预检词中非厂商特征的部分（通用词/方法词/URL等）
        val genericTargets = superPrecheckKeywords
            .map { it.lowercase() }
            .filter { it.isNotEmpty() && it !in targetToVendors }
            .distinct()

        val allTargets = targetToVendors.keys.toList() + genericTargets

        // Aho-Corasick 单遍扫描全部 DEX
        val matcher = ByteAhoCorasick(allTargets)
        val dexHitMap = HashMap<String, Set<String>>()
        val vendorHitSamples = HashMap<String, MutableSet<String>>()
        for (dexFile in dexFiles) {
            val found = matcher.scanFile(dexFile)
            if (found.isEmpty()) continue
            val hitWords = mutableSetOf<String>()
            for (idx in found) {
                val t = allTargets[idx]
                hitWords.add(t)
                targetToVendors[t]?.forEach { vid ->
                    vendorHitSamples.getOrPut(vid) { mutableSetOf() }.add(t)
                }
            }
            dexHitMap[dexFile.name] = hitWords
        }

        val hits = mutableListOf<VendorHit>()
        for (vendor in vendors) {
            val signals = mutableListOf<String>()

            // DEX 命中信号（取样例词保持日志简洁）
            vendorHitSamples[vendor.id]?.let { dexHits ->
                if (dexHits.isNotEmpty()) signals.add("DEX: ${dexHits.first()}")
            }

            
            if (vendor.adAssetFiles.isNotEmpty() && assetNames.isNotEmpty()) {
                for (asset in vendor.adAssetFiles) {
                    val key = asset.lowercase()
                    if (assetNames.any { it.contains(key) }) {
                        signals.add("assets: $asset")
                        break
                    }
                }
            }

            
            if (manifestBytes != null) {
                val manifestText = String(manifestBytes, Charset.forName("ISO-8859-1")).lowercase()
                for (pkg in vendor.sdkPackages) {
                    if (manifestText.contains(pkg.lowercase())) {
                        signals.add("Manifest: $pkg")
                        break
                    }
                }
            }

            if (signals.isNotEmpty()) {
                hits.add(VendorHit(vendor, signals.distinct()))
            }
        }
        return VendorScanResult(hits.sortedByDescending { it.matchedSignals.size }, dexHitMap)
    }

    /**
     * Aho-Corasick 字节级多模式流式匹配器。
     * 一次扫描同时匹配全部模式（复杂度与模式数量无关），
     * 1MB 块 + 重叠窗口防跨块遗漏，ASCII 大写按小写语义匹配。
     */
    private class ByteAhoCorasick(patterns: List<String>) {

        private class Node {
            val next = HashMap<Int, Int>() // 小写字节 -> 子节点
            var fail = 0
            var output = -1 // 本节点终止的模式索引
        }

        private val nodes = ArrayList<Node>()
        // UTF-8 编码：DEX 字节流中的中文字符串为 UTF-8 编码，
        // 用 ISO-8859-1 会把非 ASCII 词变成 '?'（不可逆）导致无法匹配
        private val maxLen = patterns.maxOfOrNull { it.toByteArray(Charsets.UTF_8).size } ?: 0
        private val patternBytes = patterns.map { it.toByteArray(Charsets.UTF_8) }

        init {
            build()
        }

        private fun build() {
            nodes.add(Node())
            // 构建 trie
            for ((pi, pb) in patternBytes.withIndex()) {
                var cur = 0
                for (byte in pb) {
                    val key = byte.toInt() and 0xFF
                    var ni = nodes[cur].next[key]
                    if (ni == null) {
                        ni = nodes.size
                        nodes.add(Node())
                        nodes[cur].next[key] = ni
                    }
                    cur = ni
                }
                nodes[cur].output = pi
            }
            // BFS 构建 fail 链
            val queue = ArrayList<Int>()
            for (ni in nodes[0].next.values.distinct()) {
                nodes[ni].fail = 0
                queue.add(ni)
            }
            var qi = 0
            while (qi < queue.size) {
                val v = queue[qi++]
                for ((b, ni) in nodes[v].next.entries) {
                    var f = nodes[v].fail
                    while (f != 0 && b !in nodes[f].next) f = nodes[f].fail
                    nodes[ni].fail = nodes[f].next[b] ?: 0
                    queue.add(ni)
                }
            }
        }

        fun scanFile(file: File): Set<Int> {
            val found = HashSet<Int>()
            if (maxLen == 0) return found
            val overlap = ByteArray(maxLen - 1)
            var ovLen = 0
            val buf = ByteArray(STREAM_BUF_SIZE)
            try {
                FileInputStream(file).use { fis ->
                    BufferedInputStream(fis, buf.size).use { bis ->
                        var read: Int
                        while (bis.read(buf).also { read = it } > 0) {
                            val search = ByteArray(ovLen + read)
                            if (ovLen > 0) System.arraycopy(overlap, 0, search, 0, ovLen)
                            System.arraycopy(buf, 0, search, ovLen, read)
                            scanBlock(search, search.size, found)
                            ovLen = minOf(overlap.size, search.size)
                            if (ovLen > 0) {
                                System.arraycopy(search, search.size - ovLen, overlap, 0, ovLen)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            return found
        }

        private fun scanBlock(bytes: ByteArray, len: Int, found: MutableSet<Int>) {
            var state = 0
            for (i in 0 until len) {
                val raw = bytes[i].toInt() and 0xFF
                // ASCII 大写转小写（DEX 字节流中的标识符匹配语义）
                val b = if (raw in 65..90) raw + 32 else raw
                var s = state
                while (s != 0 && b !in nodes[s].next) s = nodes[s].fail
                state = nodes[s].next[b] ?: 0
                var t = state
                while (t != 0) {
                    if (nodes[t].output >= 0) found.add(nodes[t].output)
                    t = nodes[t].fail
                }
            }
        }
    }

    /** 构建厂商匹配目标（sdkPackages 点格式 + 斜杠格式 + classKeywords） */

       
                                                     
       
    private fun buildMatchTargets(vendor: AdVendor): List<String> {
        val targets = mutableListOf<String>()
        for (pkg in vendor.sdkPackages) {
            targets.add(pkg)                       
            targets.add(pkg.replace('.', '/'))     
        }
        targets.addAll(vendor.classKeywords)
        // adKeyStrings 不再用于 DEX 文本匹配，仅用于 DEX 修补阶段的字符串替换
        // 短字符串如 "taptap"/"sigmob"/"beizi" 在二进制 DEX 中极易误匹配
        return targets
    }

       
                         
                                             
      
               
                                                                                                        
                                                             
       
    fun mergeInto(config: AdPatterns, selectedVendors: List<AdVendor>): AdPatterns {
        val result = config.copy(
            sdkPackages = (config.sdkPackages + selectedVendors.flatMap { it.sdkPackages }).distinct().toMutableList(),
            classKeywords = (config.classKeywords + selectedVendors.flatMap { it.classKeywords }).distinct().toMutableList(),
            methodPatterns = (config.methodPatterns + selectedVendors.flatMap { it.methodPatterns }).distinct().toMutableList(),
            adKeyStrings = (config.adKeyStrings + selectedVendors.flatMap { it.adKeyStrings }).distinct().toMutableList(),
            adAssetFiles = (config.adAssetFiles + selectedVendors.flatMap { it.adAssetFiles }).distinct().toMutableList(),
            adPermissions = (config.adPermissions + selectedVendors.flatMap { it.adPermissions }).distinct().toMutableList()
        )
        
        return result
    }

       
                                                            
       
    fun collectFalseStateMethods(selectedVendors: List<AdVendor>): List<String> =
        selectedVendors.flatMap { it.falseStateMethods }.distinct()
}
