package com.ads.purge.core

import android.content.Context
import com.ads.purge.core.AdPatternConfig.AdPatterns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.instruction.formats.Instruction21c
import org.jf.dexlib2.iface.instruction.formats.Instruction31c
import org.jf.dexlib2.iface.reference.StringReference
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset

   
                     
  
                                     
                                   
  
        
                          
                       
  
                                                     
                                                       
   
object AdRemover {
    
        
                                             
        
    private const val DEX_MEMORY_FACTOR = 12L  // dexlib2 内存膨胀系数（保守估计）

    /** 打印当前内存状态 */
    private fun logMemory(label: String, log: Logger) {
        val rt = Runtime.getRuntime()
        val usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024.0 * 1024.0)
        val maxMB = rt.maxMemory() / (1024.0 * 1024.0)
        log("  [内存] $label: 已用 %.0fMB / 最大 %.0fMB".format(usedMB, maxMB))
    }

    /**
     * 条件GC：仅在堆使用率超过85%时才触发一次GC。
     * 无条件 System.gc() 在 ART 上是全堆 stop-the-world 暂停，
     * 堆接近上限时（如处理到第3个DEX后）会连主线程一起冻结数秒——这正是页面卡死的根因。
     */
    private fun conditionalGc(log: Logger) {
        val rt = Runtime.getRuntime()
        val max = rt.maxMemory()
        if (max <= 0) return
        val used = rt.totalMemory() - rt.freeMemory()
        if (used > max * 0.85) {
            log("  [内存] 堆使用率 ${used * 100 / max}%，触发一次GC")
            System.gc()
        }
    }

    /** 静默版条件GC，用于扫描循环内部避免日志刷屏 */
    private fun maybeGcSilent() {
        val rt = Runtime.getRuntime()
        val max = rt.maxMemory()
        if (max <= 0) return
        val used = rt.totalMemory() - rt.freeMemory()
        if (used > max * 0.85) {
            System.gc()
        }
    }

    /**
     * 流式扫描 DEX 字节，判断是否包含任意预检关键词。
     * 1MB 块 + 重叠窗口，峰值内存约 1MB；DEX 的类名/方法名/字符串常量
     * 都物理存在于字节流中，字节级查找与 dexlib2 语义级匹配结果一致（无假阴性）。
     */
    private fun dexContainsAnyKeyword(dexFile: File, keywords: Set<String>): Boolean {
        if (keywords.isEmpty()) return true // 无词集：保守处理，不跳过
        val lowerTargets = keywords.map { it.lowercase() }.distinct()
        val maxLen = lowerTargets.maxOf { it.length }
        val overlap = ByteArray(maxLen + 256)
        var overlapLen = 0
        val buf = ByteArray(1024 * 1024)
        try {
            FileInputStream(dexFile).use { fis ->
                BufferedInputStream(fis, buf.size).use { bis ->
                    var read: Int
                    while (bis.read(buf).also { read = it } > 0) {
                        val search = ByteArray(overlapLen + read)
                        if (overlapLen > 0) System.arraycopy(overlap, 0, search, 0, overlapLen)
                        System.arraycopy(buf, 0, search, overlapLen, read)
                        val lower = String(search, Charset.forName("ISO-8859-1")).lowercase()
                        for (t in lowerTargets) {
                            if (lower.contains(t)) return true
                        }
                        overlapLen = minOf(overlap.size, search.size)
                        if (overlapLen > 0) System.arraycopy(search, search.size - overlapLen, overlap, 0, overlapLen)
                    }
                }
            }
        } catch (_: Exception) {
            return true // 读取失败：保守处理，交给完整流程
        }
        return false
    }

    /** 估算处理DEX需要的内存，超过maxMemory的70%则警告 */
    private fun checkMemoryBudget(dexSize: Long, log: Logger): Boolean {
        val estimated = dexSize * DEX_MEMORY_FACTOR
        val maxMem = Runtime.getRuntime().maxMemory()
        val ratio = estimated.toDouble() / maxMem.toDouble()
        if (ratio > 0.7) {
            log("  [警告] 预估需要 ${formatSize(estimated)}，可能内存不足，将使用安全模式")
            return false
        }
        return true
    }

       
                                
      
                                  
                                           
                                            
                                                
                                                       
                                                                
                                                                
                                                          
                     
       
    suspend fun removeAds(
        extractDir: File,
        context: Context,
        logger: Logger? = null,
        removeVpnDetection: Boolean = true,
        removeEmulatorDetection: Boolean = true,
        cleanManifest: Boolean = true,
        cleanAssets: Boolean = true,
        overrideConfig: AdPatterns? = null,
        selectedVendors: List<AdVendor> = emptyList(),
        dexPrecheckHits: Map<String, Set<String>>? = null,
        removeSignatureChecks: Boolean = false,
        enableSplashShorten: Boolean = true,
        dropDebugInfo: Boolean = true,
        parallelEnabled: Boolean = true,
        progress: ((Float) -> Unit)? = null
    ): String {
        val (textReport, _) = removeAdsWithReport(
            extractDir, context, logger, removeVpnDetection, removeEmulatorDetection,
            cleanManifest, cleanAssets, overrideConfig, selectedVendors, dexPrecheckHits,
            removeSignatureChecks, enableSplashShorten, dropDebugInfo, parallelEnabled, progress
        )
        return textReport
    }

    /** 执行去广告处理并返回文本报告 + 结构化 PatchReport */
    suspend fun removeAdsWithReport(
        extractDir: File,
        context: Context,
        logger: Logger? = null,
        removeVpnDetection: Boolean = true,
        removeEmulatorDetection: Boolean = true,
        cleanManifest: Boolean = true,
        cleanAssets: Boolean = true,
        overrideConfig: AdPatterns? = null,
        selectedVendors: List<AdVendor> = emptyList(),
        dexPrecheckHits: Map<String, Set<String>>? = null,
        removeSignatureChecks: Boolean = false,
        enableSplashShorten: Boolean = true,
        dropDebugInfo: Boolean = true,
        parallelEnabled: Boolean = true,
        progress: ((Float) -> Unit)? = null
    ): Pair<String, PatchReport> {
        val log = logger ?: {}
        val report = StringBuilder()
        val patchReport = PatchReport()
        val totalStartTime = System.currentTimeMillis()

        
        log("━━━ 加载广告特征配置 ━━━")
        val config = overrideConfig ?: AdPatternConfig.loadConfig(context)
        val configFile = AdPatternConfig.getConfigFile()

        log("  配置文件: ${configFile.absolutePath}")
        if (selectedVendors.isNotEmpty()) {
            log("  已选择处理的广告SDK厂商 (${selectedVendors.size} 家):")
            selectedVendors.forEach {
                log("    • ${it.name} (${it.id})")
            }
        }

        log("  特征: SDK包名${config.sdkPackages.size} | 类关键词${config.classKeywords.size} | 方法名${config.methodPatterns.size} | URL${config.urlPatterns.size} | 字符串${config.adKeyStrings.size} | assets${config.adAssetFiles.size} | 权限${config.adPermissions.size} (共${config.totalCount()}条)")

        if (config.totalCount() == 0) {
            log("  [警告] 广告特征配置为空，跳过去广告处理")
            return Pair("广告特征配置为空，请在设置中添加广告特征。", PatchReport())
        }

        val allAdPatterns = config.allAdPatterns()
        val adMethodPatterns = config.methodPatterns
        
        val forceTrueMethods = config.forceTrueMethodNames
        
        val falseStateMethods = AdVendorCatalog.collectFalseStateMethods(selectedVendors)

        if (forceTrueMethods.isNotEmpty()) {
            log("  强制返回true方法名: ${forceTrueMethods.size} 条")
        }
        log("  去除VPN检测: ${if (removeVpnDetection) "开启" else "关闭"}")
        log("  去除虚拟机检测: ${if (removeEmulatorDetection) "开启" else "关闭"}")
        log("  清理Manifest广告声明: ${if (cleanManifest) "开启" else "关闭"}")
        log("  清理assets广告文件: ${if (cleanAssets) "开启" else "关闭"}")

        
         
         
        detectShellPacking(extractDir, log, report)

        var totalPatchedClasses = 0
        var totalNeutralizedMethods = 0
        var totalNopLoadLibrary = 0
        var totalForcedTrue = 0
        var totalNeutralizedVpn = 0
        var totalNeutralizedEmulator = 0
        var totalFalseState = 0
        var totalSignatureChecksNeutralized = 0

        // ★ 代码引用的资源文件集合：cleanAssets 时由 patchDex 在类遍历中顺手收集，
        // 阶段2 不再二次加载全部 DEX（对齐二改作者版本，省一次全量加载+指令遍历）
        val dexReferencedAssets = mutableSetOf<String>()

         
         
         
         
        val phase1Start = System.currentTimeMillis()
        log("━━━ 阶段 1/4: DEX 直接修补（并行度自适应） ━━━")
        logMemory("开始DEX处理", log)
        report.appendLine("=== DEX 直接修补 ===")

        val dexFiles = extractDir.listFiles { f ->
            f.isFile && f.name.endsWith(".dex")
        } ?: emptyArray()

        // 收集选中厂商的ID用于精确过滤
        val enabledVendorIds = selectedVendors.map { it.id }.toSet()

        if (dexFiles.isNotEmpty()) {
            log("找到 ${dexFiles.size} 个 DEX 文件: ${dexFiles.joinToString { it.name }}")
            if (selectedVendors.isNotEmpty()) {
                log("  厂商白名单过滤已启用 (${enabledVendorIds.size} 家)，关闭的厂商专属关键词将被排除")
            }

            // 串行处理：每次只处理一个DEX；自然排序（classes.dex → classes2 → ... → classes10）
            val sortedDex = dexFiles.sortedWith(
                compareBy<File> { f ->
                    if (f.name == "classes.dex") 0
                    else f.name.removePrefix("classes").removeSuffix(".dex").toIntOrNull() ?: Int.MAX_VALUE
                }
            )

            // ★ DEX 预检词集：与 DexPatcher.compilePatterns 完全同源。
            // 用流式字节搜索预检，不含任何特征的 DEX 直接跳过（不加载、不写回）
            val precheckKeywords = DexPatcher.buildPrecheckKeywords(
                allAdPatterns,
                adMethodPatterns,
                buildSdkLibKeywords(config.sdkPackages).toList(),
                forceTrueMethods,
                if (removeVpnDetection) DexPatcher.VPN_DETECT_KEYWORDS else emptyList(),
                if (removeEmulatorDetection) DexPatcher.EMULATOR_DETECT_KEYWORDS else emptyList(),
                falseStateMethods,
                enabledVendorIds
            )
            // 缓存判定词集（小写）——扫描阶段已用超集词收集每个DEX的命中词，
            // 此处只需集合交集判定，零IO
            val lowerPrecheck = precheckKeywords.map { it.lowercase() }.toHashSet()
            log("  [预检] 特征词集 ${precheckKeywords.size} 条；无匹配特征的 DEX 将直接跳过" +
                if (dexPrecheckHits != null) "（复用识别阶段缓存，零IO）" else "")
            // ★ 并行度预算：内存充足的设备双线程并行（每线程写前堆约 200MB），否则串行保稳定
            val maxParallel = if (parallelEnabled && Runtime.getRuntime().maxMemory() >= 700L * 1024 * 1024) 2 else 1
            log(if (maxParallel > 1) "  [并行] 双线程并行修补 DEX（内存预算充足）" else "  [串行] 单线程串行修补 DEX")
            val logLock = Any()
            val syncLog: Logger = { msg -> synchronized(logLock) { log(msg) } }
            var finishedDex = 0

            // 按并行度分批处理，批内并发、批间串行（控制峰值内存）
            val batches = (0 until sortedDex.size).chunked(maxParallel)
            for (batch in batches) {
                val tasks = batch.map { index ->
                    val dexFile = sortedDex[index]
                    val dexReport = DexReport(dexFile.name, dexFile.length())
                    Pair(index, Pair(dexFile, dexReport))
                }
                val outcomes = coroutineScope {
                    tasks.map { (index, pair) ->
                        async(Dispatchers.IO) {
                            patchOneDex(
                                index = index,
                                dexFile = pair.first,
                                dexReport = pair.second,
                                dexCount = sortedDex.size,
                                precheckKeywords = precheckKeywords,
                                lowerPrecheck = lowerPrecheck,
                                dexPrecheckHits = dexPrecheckHits,
                                allAdPatterns = allAdPatterns,
                                adMethodPatterns = adMethodPatterns,
                                config = config,
                                forceTrueMethods = forceTrueMethods,
                                falseStateMethods = falseStateMethods,
                                removeVpnDetection = removeVpnDetection,
                                removeEmulatorDetection = removeEmulatorDetection,
                                cleanAssets = cleanAssets,
                                enabledVendorIds = enabledVendorIds,
                                removeSignatureChecks = removeSignatureChecks,
                                enableSplashShorten = enableSplashShorten,
                                dropDebugInfo = dropDebugInfo,
                                report = report,
                                log = syncLog,
                                progress = if (maxParallel > 1) null else progress
                            )
                        }
                    }.awaitAll()
                }
                // 按顺序合并结果（批内并行、结果有序落表）
                for ((i, outcome) in outcomes.withIndex()) {
                    val (index, pair) = tasks[i]
                    val dexReport = pair.second
                    if (cleanAssets) {
                        dexReferencedAssets.addAll(outcome.assetRefs)
                    }
                    patchReport.dexReports.add(dexReport)
                    when (outcome) {
                        is DexOutcome.Patched -> {
                            totalPatchedClasses += outcome.result.patchedClasses
                            totalNeutralizedMethods += outcome.result.neutralizedMethods
                            totalNopLoadLibrary += outcome.result.nopLoadLibrary
                            totalForcedTrue += outcome.result.forcedTrueMethods
                            totalNeutralizedVpn += outcome.result.neutralizedVpnMethods
                            totalNeutralizedEmulator += outcome.result.neutralizedEmulatorMethods
                            totalFalseState += outcome.result.falseStateNeutralized
                            totalSignatureChecksNeutralized += outcome.result.signatureChecksNeutralized
                        }
                        else -> {}
                    }
                    finishedDex++
                    progress?.invoke(finishedDex.toFloat() / sortedDex.size)
                }
            }
        } else {
            log("未找到 DEX 文件")
        }

        logPhaseTime("DEX修补", phase1Start, log)

        // DEX修补完成，按需释放内存后再扫描资源引用
        conditionalGc(log)
        logMemory("DEX修补后", log)

        log("━━━ 阶段2: 收集代码引用的资源文件 ━━━")
        if (cleanAssets) {
            log("  代码引用的资源文件: ${dexReferencedAssets.size} 个（已在 DEX 修补阶段同步收集，零额外开销）")
        } else {
            log("  [已关闭] 用户选择不清理 assets 广告文件，跳过收集")
        }

        
        
        
        
        log("━━━ 阶段3: 广告SDK原生库处理 ━━━")
        report.appendLine("=== 广告SDK原生库处理 ===")
        log("  [策略] 按 lib_file_keywords 清理广告 .so；加载调用已在 DEX 阶段 NOP，无闪退风险")

        
        val totalCleanedAssetFiles = if (cleanAssets) {
            cleanAdSdkAssets(
                extractDir, config.adAssetFiles, config.adAssetPaths,
                config.assetKeywords, config.rootFileKeywords,
                config.sdkPackages, dexReferencedAssets, log, report
            )
        } else {
            log("━━━ 阶段3: 清理 assets 广告文件 ━━━")
            log("  [已关闭] 用户选择不清理 assets 广告文件，跳过")
            0
        }

        // ★ v3.1.1 广告 .so 原生库清理（原作者 lib_file_keywords）
        val totalCleanedLibs = if (cleanAssets) {
            cleanAdSdkLibs(extractDir, config.libFileKeywords, log, report)
        } else {
            log("  [已关闭] 用户选择不清理 assets，广告 .so 一并跳过")
            0
        }

        
        
        
        
        
        var manifestResult = AdManifestCleaner.CleanResult()
        if (cleanManifest) {
            val phase4Start = System.currentTimeMillis()
            log("━━━ 阶段 4/4: 清理 AndroidManifest.xml 广告权限 ━━━")
            manifestResult = AdManifestCleaner.cleanManifest(extractDir, config)
            if (manifestResult.totalRemoved == 0) {
                log("  未发现广告权限，无需清理")
            } else {
                log("  删除广告权限 ${manifestResult.removedPermissions.size} 个:")
                manifestResult.removedPermissions.forEach {
                    log("    ✗ $it")
                    report.appendLine("  删除权限: $it")
                }
            }
            log("  [防闪退] 广告组件注册已保留（不删除），避免跳转 ActivityNotFoundException 崩溃")
            logPhaseTime("Manifest清理", phase4Start, log)
        } else {
            log("━━━ 阶段 4/4: 清理 Manifest 广告权限 ━━━")
            log("  [已关闭] 用户选择不清理 Manifest 广告声明，跳过")
        }

        
        patchReport.endTimeMs = System.currentTimeMillis()
        patchReport.manifestResult = ManifestReport(
            removedPermissions = manifestResult.removedPermissions,
            removedComponents = manifestResult.removedComponents.map { it.name }
        )
        patchReport.assetCleanCount = totalCleanedAssetFiles

        log("━━━ 处理汇总 ━━━")
        report.appendLine("=== 处理汇总 ===")
        report.appendLine("  配置特征总数: ${config.totalCount()} 条")
        report.appendLine("  广告SDK类置空: $totalPatchedClasses 个")
        report.appendLine("  广告方法置空: $totalNeutralizedMethods 个")
        report.appendLine("  强制返回true: $totalForcedTrue 个")
        report.appendLine("  去除VPN检测: $totalNeutralizedVpn 个")
        report.appendLine("  去除虚拟机检测: $totalNeutralizedEmulator 个")
        report.appendLine("  广告状态方法返回false: $totalFalseState 个")
        report.appendLine("  NOP广告so加载调用: $totalNopLoadLibrary 个")
        report.appendLine("  广告SDK配置文件清理: $totalCleanedAssetFiles 个")
        report.appendLine("  Manifest广告权限清理: ${manifestResult.removedPermissions.size} 个")
        report.appendLine("  Manifest广告组件: 保留（防闪退，不删除注册）")
        report.appendLine("  总耗时: ${patchReport.totalTimeMs}ms")

        log("  修改类 $totalPatchedClasses | 置空方法 $totalNeutralizedMethods | 强制true $totalForcedTrue | 状态false $totalFalseState")
        log("  VPN检测 $totalNeutralizedVpn | 模拟器检测 $totalNeutralizedEmulator | NOP so $totalNopLoadLibrary")
        log("  assets清理 $totalCleanedAssetFiles | Manifest权限清理 ${manifestResult.removedPermissions.size} | 组件保留(防闪退)")
        log("  总耗时: ${patchReport.totalTimeMs}ms")
        log("━━━ 去广告处理完成 ━━━")

        return Pair(report.toString(), patchReport)
    }

    
    private fun logPhaseTime(phaseName: String, startTime: Long, log: Logger) {
        val elapsed = System.currentTimeMillis() - startTime
        log("  ⏱ $phaseName 耗时: ${elapsed}ms")
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
        }
    }

       
                                  
                                                                            
                                                              
       
    internal fun buildSdkLibKeywords(sdkPackages: List<String>): Set<String> {
        val keywords = mutableSetOf<String>()
        val joined = sdkPackages.joinToString(" ").lowercase()

        
        val knownMappings = mapOf(
            "bytedance" to listOf("ttad", "pangle", "openadsdk", "bytedance"),
            "pangle" to listOf("pangle", "ttad"),
            "qq.e" to listOf("gdt", "qqad", "gdtad"),
            "gdt" to listOf("gdt"),
            "baidu" to listOf("baidu", "mobads", "mobad"),
            "kuaishou" to listOf("kuaishou", "gdfp"),
            "unity3d" to listOf("unityads", "unity_ad"),
            "mintegral" to listOf("mintegral", "mbridge", "mtg"),
            "mobvista" to listOf("mobvista", "mtg"),
            "vungle" to listOf("vungle"),
            "chartboost" to listOf("chartboost"),
            "appnext" to listOf("appnext"),
            "inmobi" to listOf("inmobi"),
            "flurry" to listOf("flurry"),
            "adcolony" to listOf("adcolony"),
            "applovin" to listOf("applovin", "applvn"),
            "ironsource" to listOf("ironsource", "is_adapt"),
            "startapp" to listOf("startapp"),
            "smaato" to listOf("smaato"),
            "pubmatic" to listOf("pubmatic"),
            "amazon" to listOf("amazon", "amoad"),
            "yandex" to listOf("yandex"),
            "mytarget" to listOf("mytarget"),
            "huawei" to listOf("huawei_hms", "hms_ads"),
            "sigmob" to listOf("sigmob"),
            "anythink" to listOf("anythink", "topon"),
            "topon" to listOf("topon"),
            "facebook" to listOf("facebook", "fb_ads", "audience"),
            "admob" to listOf("admob", "gms"),
            "googleadb" to listOf("gms"),
            "appodeal" to listOf("appodeal"),
            "pollfish" to listOf("pollfish"),
            "tapjoy" to listOf("tapjoy"),
            "mopub" to listOf("mopub"),
            "pubnative" to listOf("pubnative"),
            "fyber" to listOf("fyber", "inneractive"),
            "oneway" to listOf("oneway")
        )

        for ((pkgFragment, libNames) in knownMappings) {
            if (joined.contains(pkgFragment)) {
                keywords.addAll(libNames)
            }
        }

        
        
        if (joined.contains("adsdk") || joined.contains("_ads")) {
            keywords.add("adsdk")
        }
        return keywords
    }

       
                                           
      
                                      
                                                         
                               
                                       
       
    private fun detectShellPacking(extractDir: File, log: Logger, report: StringBuilder) {
        
        val shellLibNames = listOf(
            "libjiagu",        
            "libprotectclass", 
            "libijiami",       
            "libdexhelper",    
            "libapkprotect",   
            "libshell",        
            "libexec",         
            "libsecmain",      
            "libsecexe",       
            "libtool",         
            "libnesec",        
            "libnqshield",     
            "libddog",         
            "libegis",         
            "libsansec",       
            "libchaosvmp",     
            "libtprt",         
            "libnibiru",       
            "libsogouprotect"  
        )
        val libDir = File(extractDir, "lib")
        val libFiles = libDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".so") }
            ?: emptyList()
        if (libFiles.isEmpty()) {
            log("  [检查] 未检测到加固壳（无 lib 目录），安全")
            return
        }

        val lowerNames = libFiles.map { it.name.lowercase() }
        val hit = shellLibNames.firstOrNull { shell ->
            lowerNames.any { name -> name.contains(shell) }
        }
        if (hit != null) {
            log("━━━ [警告] 检测到加固壳（${hit}.so）━━━")
            log("  该 APK 已加固，真实广告代码藏在加固壳内，DEX 修补基本无效")
            log("  且 lib 目录中均为壳文件，任何删除/替换 .so 的操作都会导致启动闪退")
            log("  [建议] 加固应用不建议去广告；如必须处理，请仅保留 DEX 修补结果并实机验证")
            report.appendLine("=== 加固应用检测 ===")
            report.appendLine("  检测到加固壳（${hit}.so），DEX 修补可能无效，lib 目录不可修改")
        } else {
            log("  [检查] 未检测到加固壳，lib 目录为普通原生库，安全")
        }
    }

       
                                                 
      
                                                      
                                                                                
                                              
      
                        
                                                             
                                              
                                       
      
                                                       
                                                     
                                               
      
                                                                    
                      
       
    internal fun cleanAdSdkAssets(
        extractDir: File,
        adAssetFiles: List<String>,
        adAssetPaths: List<String>,
        assetKeywords: List<String>,
        rootFileKeywords: List<String>,
        sdkPackages: List<String>,
        dexReferencedAssets: Set<String>,
        log: Logger,
        report: StringBuilder
    ): Int {
        log("━━━ 阶段3: 清理广告SDK配置文件(assets) ━━━")
        report.appendLine("=== 清理广告SDK配置文件(assets) ===")

        val assetsDir = File(extractDir, "assets")
        if (!assetsDir.exists() || !assetsDir.isDirectory) {
            log("  未找到 assets 目录，跳过广告配置清理")
        } else {
            cleanAssetDir(assetsDir, adAssetFiles, adAssetPaths, assetKeywords,
                sdkPackages, dexReferencedAssets, log, report)
        }

        // ★ v3.1.1 APK 根目录广告残留文件（root_file_keywords：tt_version/oaid/device_id 等）
        var rootCleaned = 0
        val rootKeywords = rootFileKeywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (rootKeywords.isNotEmpty()) {
            extractDir.listFiles()?.filter { it.isFile }?.forEach { file ->
                val name = file.name.lowercase()
                if (rootKeywords.any { name.contains(it) }) {
                    log("  [广告根文件] 删除 /${file.name}")
                    report.appendLine("  删除 /${file.name}")
                    if (file.delete()) rootCleaned++
                }
            }
        }

        log("  广告SDK配置文件清理完成")
        return rootCleaned + totalCleanedAssets
    }

    private var totalCleanedAssets = 0

    private fun cleanAssetDir(
        assetsDir: File,
        adAssetFiles: List<String>,
        adAssetPaths: List<String>,
        assetKeywords: List<String>,
        sdkPackages: List<String>,
        dexReferencedAssets: Set<String>,
        log: Logger,
        report: StringBuilder
    ) {
        totalCleanedAssets = 0

        val configuredPatterns = adAssetFiles
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

        // ★ v3.1.1 原作者 ad_asset_paths：完整路径/路径前缀（assets/gdt_plugin、assets/ow 等）
        val pathPatterns = adAssetPaths
            .map { it.trim().lowercase().removePrefix("assets/") }
            .filter { it.isNotEmpty() }
            .toSet()

        // ★ v3.1.1 原作者 asset_keywords：文件名关键词（含哈希前缀类确定性特征）
        val assetKws = assetKeywords
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && it.length >= 3 }
            .toSet()

        val pkgKeywords = buildAssetKeywords(sdkPackages)

        val allAssets = assetsDir.walkTopDown().filter { it.isFile }.toList()
        for (file in allAssets) {
            val relPath = assetsDir.toURI().relativize(file.toURI()).path
            val lowerPath = relPath.lowercase()
            val fileName = file.name.lowercase()

            val matchedConfigured = configuredPatterns.any { pattern ->
                lowerPath == pattern || fileName.contains(pattern) || lowerPath.contains("/$pattern")
            }

            // 路径匹配：等于/子路径/以路径开头
            val matchedPath = pathPatterns.any { p ->
                lowerPath == p || lowerPath.startsWith("$p/") || lowerPath.startsWith(p)
            }

            // 文件名关键词匹配
            val matchedAssetKw = assetKws.any { kw -> fileName.contains(kw) }

            val matchedBySdk = pkgKeywords.any { kw ->
                fileName.contains(kw) && fileName.containsAny(ASSET_AD_HINTS)
            }

            val isDexReferenced = fileName in dexReferencedAssets || lowerPath in dexReferencedAssets

            if (matchedConfigured || matchedPath || matchedAssetKw || matchedBySdk) {
                log("  [广告配置] 删除 assets/$relPath${if (isDexReferenced) " (代码引用)" else ""}")
                report.appendLine("  删除 assets/$relPath")
                if (file.delete()) {
                    totalCleanedAssets++
                } else {
                    log("  [警告] 删除失败: $relPath")
                }
            }
        }

        log("  assets 清理完成: $totalCleanedAssets 个文件")
    }

    /** ★ v3.1.1 广告 .so 原生库清理（lib_file_keywords：ttad/pangle/gdt/mbridge 等） */
    internal fun cleanAdSdkLibs(
        extractDir: File,
        libFileKeywords: List<String>,
        log: Logger,
        report: StringBuilder
    ): Int {
        val libDir = File(extractDir, "lib")
        if (!libDir.exists() || !libDir.isDirectory) {
            log("  未找到 lib 目录，跳过广告 .so 清理")
            return 0
        }
        val keywords = libFileKeywords
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && it.length >= 3 }
            .toSet()
        if (keywords.isEmpty()) {
            log("  lib_file_keywords 为空，跳过广告 .so 清理")
            return 0
        }

        var cleaned = 0
        val soFiles = libDir.walkTopDown().filter { it.isFile && it.extension == "so" }.toList()
        for (file in soFiles) {
            val name = file.name.lowercase()
            if (keywords.any { kw -> name.contains(kw) }) {
                val rel = libDir.toURI().relativize(file.toURI()).path
                log("  [广告SO库] 删除 lib/$rel")
                report.appendLine("  删除 lib/$rel")
                if (file.delete()) {
                    cleaned++
                } else {
                    log("  [警告] 删除失败: $rel")
                }
            }
        }
        log("  广告 .so 清理完成: $cleaned 个文件")
        return cleaned
    }

       
                          
                                               
       
    private val ASSET_AD_HINTS = listOf(
        "config", "setting", "sdk", "ad", "android", "json", "xml"
    )

       
                                     
                                                                                         
       
    private fun buildAssetKeywords(sdkPackages: List<String>): Set<String> {
        val keywords = mutableSetOf<String>()
        val joined = sdkPackages.joinToString(" ").lowercase()

        val knownMappings = mapOf(
            "bytedance" to listOf("ttad", "pangle", "openadsdk", "bytedance", "pangolin"),
            "pangle" to listOf("pangle", "ttad", "pangolin"),
            "qq.e" to listOf("gdt", "qqad", "gdtad"),
            "gdt" to listOf("gdt"),
            "baidu" to listOf("baidu", "mobads", "mobad"),
            "kuaishou" to listOf("kuaishou", "gdfp", "ksad"),
            "unity3d" to listOf("unityads", "unity_ad"),
            "mintegral" to listOf("mintegral", "mbridge", "mtg"),
            "mobvista" to listOf("mobvista", "mtg"),
            "vungle" to listOf("vungle"),
            "chartboost" to listOf("chartboost"),
            "appnext" to listOf("appnext"),
            "inmobi" to listOf("inmobi"),
            "flurry" to listOf("flurry"),
            "adcolony" to listOf("adcolony"),
            "applovin" to listOf("applovin", "applvn"),
            "ironsource" to listOf("ironsource"),
            "startapp" to listOf("startapp"),
            "smaato" to listOf("smaato"),
            "pubmatic" to listOf("pubmatic"),
            "amazon" to listOf("amazon", "amoad"),
            "yandex" to listOf("yandex"),
            "mytarget" to listOf("mytarget"),
            "huawei" to listOf("huawei", "hms_ads"),
            "sigmob" to listOf("sigmob"),
            "anythink" to listOf("anythink", "topon"),
            "topon" to listOf("topon"),
            "facebook" to listOf("facebook", "fb_ads", "audiencenetwork"),
            "admob" to listOf("admob"),
            "appodeal" to listOf("appodeal"),
            "tapjoy" to listOf("tapjoy"),
            "mopub" to listOf("mopub"),
            "pubnative" to listOf("pubnative"),
            "fyber" to listOf("fyber", "inneractive"),
            "oneway" to listOf("oneway"),
            "mintegral" to listOf("mintegral", "mtg"),
            "beizi" to listOf("beizi"),
            "mobisage" to listOf("mobisage"),
            "zhangyue" to listOf("zyad"),
            "heytap" to listOf("heytap"),
            "oppo" to listOf("oppo"),
            "vivo" to listOf("vivo"),
            "xiaomi" to listOf("xiaomi", "mimo"),
            "miui" to listOf("miui", "mimo")
        )

        for ((pkgFragment, names) in knownMappings) {
            if (joined.contains(pkgFragment)) {
                keywords.addAll(names)
            }
        }
        return keywords
    }

       
                                                 
                                                                       
      
                                                                   
                                            
      
                                         
       
    /** 单个 DEX 的修补任务：预检 → patchDex → 报告落表。并发安全（log/report 由调用方同步）。 */
    private suspend fun patchOneDex(
        index: Int,
        dexFile: File,
        dexReport: DexReport,
        dexCount: Int,
        precheckKeywords: Set<String>,
        lowerPrecheck: Set<String>,
        dexPrecheckHits: Map<String, Set<String>>?,
        allAdPatterns: List<String>,
        adMethodPatterns: List<String>,
        config: AdPatterns,
        forceTrueMethods: List<String>,
        falseStateMethods: List<String>,
        removeVpnDetection: Boolean,
        removeEmulatorDetection: Boolean,
        cleanAssets: Boolean,
        enabledVendorIds: Set<String>,
        removeSignatureChecks: Boolean,
        enableSplashShorten: Boolean,
        dropDebugInfo: Boolean,
        report: StringBuilder,
        log: Logger,
        progress: ((Float) -> Unit)?
    ): DexOutcome {
        val dexStart = System.currentTimeMillis()

        // ★ 预检：优先用识别阶段缓存判定；无缓存时流式扫描兜底
        val cachedHits = dexPrecheckHits?.get(dexFile.name)
        val hasFeature = when {
            precheckKeywords.isEmpty() -> true // 无词集：保守处理
            cachedHits != null -> cachedHits.any { it in lowerPrecheck } // 缓存∩当前词集
            else -> dexContainsAnyKeyword(dexFile, precheckKeywords)
        }
        if (!hasFeature) {
            dexReport.skipped = true
            dexReport.elapsedMs = System.currentTimeMillis() - dexStart
            synchronized(report) { report.appendLine("  ${dexFile.name}: 跳过（无匹配特征）") }
            log("⏭ 跳过 [${index + 1}/$dexCount] ${dexFile.name}：预检未发现任何匹配特征（${dexReport.elapsedMs}ms）")
            return DexOutcome.Skipped
        }

        log("▶ 正在处理 [${index + 1}/$dexCount]: ${dexFile.name} (${formatSize(dexFile.length())})")
        synchronized(report) { report.appendLine("  ${dexFile.name}:") }

        // 内存预算检查
        checkMemoryBudget(dexFile.length(), log)

        return try {
            val result = DexPatcher.patchDex(
                dexFile,
                allAdPatterns,
                adMethodPatterns,
                adLibKeywords = buildSdkLibKeywords(config.sdkPackages).toList(),
                forceTrueMethodNames = forceTrueMethods,
                vpnDetectKeywords = if (removeVpnDetection) DexPatcher.VPN_DETECT_KEYWORDS else emptyList(),
                emulatorDetectKeywords = if (removeEmulatorDetection) DexPatcher.EMULATOR_DETECT_KEYWORDS else emptyList(),
                falseStateMethodKeywords = falseStateMethods,
                enabledVendorIds = enabledVendorIds,
                collectAssetRefs = cleanAssets,
                removeSignatureChecks = removeSignatureChecks,
                enableSplashShorten = enableSplashShorten,
                dropDebugInfo = dropDebugInfo,
                logger = { msg ->
                    log("[${dexFile.name}] $msg")
                },
                progress = { done, total -> progress?.invoke((done.toFloat() / total).coerceIn(0f, 1f)) }
            )

            dexReport.patchedClasses = result.patchedClasses
            dexReport.neutralizedMethods = result.neutralizedMethods
            dexReport.nopLoadLibrary = result.nopLoadLibrary
            dexReport.forcedTrueMethods = result.forcedTrueMethods
            dexReport.neutralizedVpnMethods = result.neutralizedVpnMethods
            dexReport.neutralizedEmulatorMethods = result.neutralizedEmulatorMethods
dexReport.falseStateNeutralized = result.falseStateNeutralized
             dexReport.splashCountdownShortened = result.splashCountdownShortened
             dexReport.signatureChecksNeutralized = result.signatureChecksNeutralized
             dexReport.elapsedMs = System.currentTimeMillis() - dexStart

            val extras = buildString {
                if (result.forcedTrueMethods > 0) append(", 强制true ${result.forcedTrueMethods}")
                if (result.neutralizedVpnMethods > 0) append(", VPN ${result.neutralizedVpnMethods}")
                if (result.neutralizedEmulatorMethods > 0) append(", 模拟器 ${result.neutralizedEmulatorMethods}")
                if (result.nopLoadLibrary > 0) append(", NOP so ${result.nopLoadLibrary}")
                if (result.signatureChecksNeutralized > 0) append(", 杀签 ${result.signatureChecksNeutralized}")
                if (result.splashCountdownShortened > 0) append(", 开屏倒计时 ${result.splashCountdownShortened}")
            }
            synchronized(report) {
                report.appendLine("  ${dexReport.dexName}: 修改${result.patchedClasses}类, 置空${result.neutralizedMethods}方法$extras (${dexReport.elapsedMs}ms)")
            }
            log("  ✓ ${dexReport.dexName}: 修改${result.patchedClasses}类, 置空${result.neutralizedMethods}方法$extras (${dexReport.elapsedMs}ms)")

            // ★ 每个DEX处理完后无条件GC：确保下一个DEX的加载与写回在干净的堆上进行
            System.gc()
            logMemory("${dexFile.name} 处理后", log)
            DexOutcome.Patched(result)
        } catch (e: OutOfMemoryError) {
            dexReport.error = "OOM: ${e.message}"
            log("  ✗ ${dexFile.name} 内存不足: ${e.message}")
            synchronized(report) { report.appendLine("  ${dexFile.name} 内存不足: ${e.message}") }
            DexOutcome.Failed("OOM: ${e.message}")
        } catch (e: Exception) {
            dexReport.error = "${e.javaClass.simpleName}: ${e.message}"
            log("  ✗ ${dexFile.name} 修补失败: ${e.message}")
            synchronized(report) { report.appendLine("  ${dexFile.name} 修补失败: ${e.message}") }
            DexOutcome.Failed("${e.message}")
        }
    }

    private fun collectDexReferencedAssetNames(extractDir: File, log: Logger): Set<String> {
        val refs = mutableSetOf<String>()
        val dexFiles = extractDir.listFiles { f -> f.isFile && f.name.endsWith(".dex") } ?: emptyArray()
        for ((index, dexFile) in dexFiles.withIndex()) {
            try {
                val dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault())
                for (classDef in dex.classes) {
                    for (method in classDef.methods) {
                        val impl = method.implementation ?: continue
                        for (ins in impl.instructions) {
                            val value = when (ins) {
                                is Instruction21c -> (ins.reference as? StringReference)?.string
                                is Instruction31c -> (ins.reference as? StringReference)?.string
                                else -> null
                            } ?: continue
                            val lower = value.lowercase()
                            if (isAssetLikeRef(lower)) {
                                refs.add(lower)
                                // 同时加入文件名部分
                                if (lower.contains("/")) {
                                    refs.add(lower.substringAfterLast('/'))
                                }
                            }
                        }
                    }
                }
                // 每扫描完一个DEX按需释放引用
                if (index < dexFiles.size - 1) {
                    maybeGcSilent()
                }
            } catch (e: Exception) {
                log("  [警告] 扫描 ${dexFile.name} 资源引用失败: ${e.message}")
            }
        }
        return refs
    }

                                
    private fun isAssetLikeRef(value: String): Boolean {
        if (value.startsWith("assets/")) return true
        val ext = value.substringAfterLast('.')
        return ext.length in 2..4 && ext.all { it.isLetter() } &&
            ext in RESOURCE_EXTENSIONS
    }

                         
    private val RESOURCE_EXTENSIONS = setOf(
        "xml", "json", "plist", "png", "jpg", "jpeg", "gif", "webp",
        "js", "txt", "dat", "bin", "db", "sqlite", "zip", "mp3", "mp4"
    )

       
                                 
       
    private fun String.containsAny(needles: List<String>): Boolean {
        for (needle in needles) {
            if (this.contains(needle)) return true
        }
        return false
    }
}

/** 单个 DEX 修补任务的结果 */
sealed class DexOutcome {
    val assetRefs: Set<String> get() = when (this) {
        is Patched -> result.assetRefs
        else -> emptySet()
    }

    data class Patched(val result: PatchResult) : DexOutcome()
    object Skipped : DexOutcome()
    data class Failed(val error: String) : DexOutcome()
}