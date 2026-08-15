package com.ads.purge.core

import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.iface.DexFile
import org.jf.dexlib2.iface.ClassDef
import org.jf.dexlib2.iface.Method
import org.jf.dexlib2.iface.MethodImplementation
import org.jf.dexlib2.iface.instruction.FiveRegisterInstruction
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction
import org.jf.dexlib2.iface.instruction.TwoRegisterInstruction
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.instruction.NarrowLiteralInstruction
import org.jf.dexlib2.iface.instruction.WideLiteralInstruction
import org.jf.dexlib2.iface.instruction.formats.Instruction21c
import org.jf.dexlib2.iface.instruction.formats.Instruction31c
import org.jf.dexlib2.iface.instruction.formats.Instruction35c
import org.jf.dexlib2.iface.instruction.formats.Instruction3rc
import org.jf.dexlib2.iface.reference.MethodReference
import org.jf.dexlib2.iface.reference.StringReference
import org.jf.dexlib2.immutable.ImmutableDexFile
import org.jf.dexlib2.immutable.ImmutableClassDef
import org.jf.dexlib2.immutable.ImmutableMethod
import org.jf.dexlib2.immutable.ImmutableMethodImplementation
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11n
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11x
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21s
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21ih
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction31i
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction51l
import java.io.File

             
typealias Logger = (String) -> Unit

   
                             
                                                     
  
               
                                          
                                
                              
  
        
                                           
                                   
                                     
   
object DexPatcher {

       
                       
                              
       
    val VPN_DETECT_KEYWORDS = listOf(
        "isvpn", "checkvpn", "vpnconnected", "isvpnconnected",
        "isvpnactive", "vpnactive", "isvpninuse", "vpninuse",
        "isvpnenabled", "isbehindvpn", "hasvpn", "detectvpn",
        "vpnstate", "vpn_connected", "isvpndetected", "isusingvpn",
        "vpninterfacename", "getvpnstate", "isvpnconnection",
        "isvpnused", "isvpnopen"
    )

       
                          
                                
       
    val EMULATOR_DETECT_KEYWORDS = listOf(
        "isemulator", "checkemulator", "emulatordetected", "isemulatordetected",
        "isrunningonemulator", "isone2emulator", "isvirtualdevice",
        "isvirtualmachine", "isvm", "detectemulator", "isgenymotion",
        "isbluestacks", "isnox", "ismumu", "isldplayer", "isremixos",
        "isemulatorusingbuild", "isemulatorstate", "isdeviceemulator",
        "isemulatorenv", "isemulatortest", "checkvm", "isvmware"
    )

       
                                
       
    private data class CompiledPatterns(
                                     
        val adPatternLowercase: Set<String>,
           
                           
                                      
                                             
           
        val exactMethodNamesLowercase: Set<String>,
           
                                      
                                          
                                         
                                                                      
           
        val neutralizeMethodKeywords: Set<String>,
           
                                   
                                                                               
                                                            
                                                                 
                                              
          
                                                             
                                                                                
                                                     
           
        val adLibKeywords: Set<String>,
           
                              
                                                          
                                                                  
                                                     
           
        val forceTrueMethodNamesLowercase: Set<String>,
           
                           
                                                        
                                                                 
                                             
           
        val vpnDetectKeywords: Set<String>,
           
                              
                                                        
                                                             
                               
           
        val emulatorDetectKeywords: Set<String>,
           
                                 
                                                        
                                                    
                                                               
                                                  
                                                      
           
        val falseStateMethodKeywords: Set<String>
    )

       
                                
       
    private fun compilePatterns(
        adPatterns: List<String>,
        adMethodNames: List<String>,
        adLibKeywords: List<String> = emptyList(),
        forceTrueMethodNames: List<String> = emptyList(),
        vpnDetectKeywords: List<String> = emptyList(),
        emulatorDetectKeywords: List<String> = emptyList(),
        falseStateMethodKeywords: List<String> = emptyList(),
        enabledVendorIds: Set<String> = emptySet()
    ): CompiledPatterns {
        
        
        
        
        
        
        
        // 厂商特定的方法关键词映射（仅当用户选中对应厂商时才启用）
        val vendorKeywordMap = mapOf(
            "pangle" to listOf("ttad", "panglead"),
            "tencent" to listOf("gdtad"),
            "baidu" to listOf("baiduad"),
            "google" to listOf("admob"),
            "kuaishou" to listOf("kwad"),
            "mbridge" to listOf("mbridge")
        )
        
        // 通用内置广告方法关键词（对所有厂商有效）
        val commonBuiltinKeywords = listOf(
            "_ad_", "_ads_", "_banner_", "_adview_", "_adsdk_",
            "adshow", "showad", "showads", "loadad", "loadads",
            "bannerad", "bannerads", "nativead", "splashad",
            "interstitialad", "rewardedad", "rewardedvideo",
            "adload", "adclose", "adclick", "adfail",
            "adimpression", "adrequest", "adresponse",
            "adcontroller", "admanager", "adhelper",
            "adprovider", "adnetwork", "adsource",
            "initad", "initads", "initsdk",
            "preloadad", "cachead", "fetchad", "requestad",
            "destroyad", "resumead", "pausead",
            "displayad", "hidead", "removead",
            "adview", "adloader", "adbanner", "adsplash",
            "adwidget", "adcontainer", "adlayout",
            "ad_config", "ad_settings", "ad_unit_",
            "advertising", "adidclient", "adid",
            "adviewbinder",
            
            "loadinterstitial", "showinterstitial",
            "loadrewarded", "showrewarded",
            "loadbanner", "showbanner",
            "loadnative", "shownative",
            "loadsplash", "showsplash",
            "loadexpress", "showexpress",
            
            
            "loadadfrombid", "requestbannerad", "requestinterstitialad",
            "loadbannerad", "loadinterstitialad", "loadnativead", "loadrewardedad",
            "loadrewardedinterstitialad", "loadappopenad", "loadinterscrollerad",
            "loadnativeadforbidding", "loadnextad", "createinterstitialad",
            "setnativead", "loadadviewad", "loadadfromnetwork", "loadadfromub",
            "loadadinternal", "loadadvertisement", "loadsmartbanner",
            "loadnextadforadtoken", "loadnextadforzoneid", "loadrewardedvideo",
            "loadrewardedvideofordemandonly",
            
            "showbannerandnative", "shownativeinterstitial", "showofferwall",
            "showrewardedvideo", "showrewardedvideoad", "showinterstitialad",
            "shownativead", "showbannerad", "showvideoad",
            "resumebanner", "startadsession",
            
            "renderad"
        )
        
        // 构建完整的内置关键词列表
        val builtinMethodKeywords = mutableListOf<String>()
        builtinMethodKeywords.addAll(commonBuiltinKeywords)
        
        // 如果指定了厂商白名单，仅添加选中厂商的专属关键词
        if (enabledVendorIds.isNotEmpty()) {
            for ((vendorId, keywords) in vendorKeywordMap) {
                if (vendorId in enabledVendorIds) {
                    builtinMethodKeywords.addAll(keywords)
                }
            }
        } else {
            // 未指定白名单时，添加所有厂商专属关键词（保持向后兼容）
            for (keywords in vendorKeywordMap.values) {
                builtinMethodKeywords.addAll(keywords)
            }
        }
        
        val configMethodLowercase = adMethodNames.map { it.lowercase() }.toHashSet()
        val allKeywords = (configMethodLowercase + builtinMethodKeywords.map { it.lowercase() }).toHashSet()

        return CompiledPatterns(
            adPatternLowercase = adPatterns.map { it.lowercase() }.toHashSet(),
            exactMethodNamesLowercase = configMethodLowercase,
            neutralizeMethodKeywords = allKeywords,
            adLibKeywords = adLibKeywords.map { it.lowercase() }.toHashSet(),
            forceTrueMethodNamesLowercase = forceTrueMethodNames.map { it.lowercase() }.toHashSet(),
            vpnDetectKeywords = vpnDetectKeywords.map { it.lowercase() }.toHashSet(),
            emulatorDetectKeywords = emulatorDetectKeywords.map { it.lowercase() }.toHashSet(),
            falseStateMethodKeywords = falseStateMethodKeywords.map { it.lowercase() }.toHashSet()
        )
    }

    /**
     * 构建 DEX 预检关键词集合——与 compilePatterns 完全同源（含内置通用关键词），
     * 供 AdRemover 在加载 DEX 前做流式字节预检，跳过不含任何特征的 DEX。
     */
    internal fun buildPrecheckKeywords(
        adPatterns: List<String>,
        adMethodNames: List<String>,
        adLibKeywords: List<String> = emptyList(),
        forceTrueMethodNames: List<String> = emptyList(),
        vpnDetectKeywords: List<String> = emptyList(),
        emulatorDetectKeywords: List<String> = emptyList(),
        falseStateMethodKeywords: List<String> = emptyList(),
        enabledVendorIds: Set<String> = emptySet()
    ): Set<String> {
        val p = compilePatterns(
            adPatterns, adMethodNames, adLibKeywords, forceTrueMethodNames,
            vpnDetectKeywords, emulatorDetectKeywords, falseStateMethodKeywords, enabledVendorIds
        )
        return buildSet {
            addAll(p.adPatternLowercase)
            addAll(p.neutralizeMethodKeywords)
            addAll(p.adLibKeywords)
            addAll(p.forceTrueMethodNamesLowercase)
            addAll(p.vpnDetectKeywords)
            addAll(p.emulatorDetectKeywords)
            addAll(p.falseStateMethodKeywords)
        }
    }

       
                      
                                       
                           
       
    private fun fastMatchAdClass(className: String, patterns: CompiledPatterns): String? {
        val lowerName = className.lowercase()
        
        if (lowerName in patterns.adPatternLowercase) return lowerName
        
        for (pattern in patterns.adPatternLowercase) {
            if (lowerName.contains(pattern)) return pattern
        }
        return null
    }

       
                                                    
      
                          
                                                 
                              
                                                         
                                                      
                                       
                                                   
      
                         
                                    
                                                
                                                   
                                                     
                                                    
                                                            
                                                 
                                
      
                                    
       
    fun patchDex(
        dexFile: File,
        adPatterns: List<String>,
        adMethodNames: List<String>,
        adLibKeywords: List<String> = emptyList(),
        forceTrueMethodNames: List<String> = emptyList(),
        vpnDetectKeywords: List<String> = emptyList(),
        emulatorDetectKeywords: List<String> = emptyList(),
        falseStateMethodKeywords: List<String> = emptyList(),
        enabledVendorIds: Set<String> = emptySet(),
        collectAssetRefs: Boolean = false,
        // ★ v3.1 新增开关：一键杀签 / 开屏倒计时缩短 / 丢弃 debug_info
        removeSignatureChecks: Boolean = false,
        enableSplashShorten: Boolean = true,
        dropDebugInfo: Boolean = true,
        logger: Logger? = null,
        // ★ 进度回调（已处理类数, 总类数），供 UI 显示真实处理进度
        progress: ((Int, Int) -> Unit)? = null
    ): PatchResult {

        val log = logger ?: {}
        val startTime = System.currentTimeMillis()

        // 开始日志由外层统一输出（▶ 正在处理 [n/total]），此处不再重复
        val tLoadStart = System.currentTimeMillis()
        // ★ var + 可空：写盘前置空，释放原始 dex-backed 对象树（newClasses 已全量预解析为 Immutable 深拷贝）
        var dex: DexFile? = try {
            DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault())
        } catch (e: Exception) {
            log("  [错误] 无法加载 ${dexFile.name}: ${e.message}")
            return PatchResult(0, 0)
        }
        val tLoadMs = System.currentTimeMillis() - tLoadStart

        
        val patterns = compilePatterns(
            adPatterns, adMethodNames,
            adLibKeywords,
            forceTrueMethodNames, vpnDetectKeywords, emulatorDetectKeywords,
            falseStateMethodKeywords,
            enabledVendorIds
        )

        val totalClasses = dex!!.classes.size
        log("  共 $totalClasses 个类")

        var patchedClasses = 0
        var neutralizedMethods = 0
        var nopLoadLibrary = 0
        var forcedTrueMethods = 0
        var neutralizedVpnMethods = 0
        var neutralizedEmulatorMethods = 0
        var falseStateNeutralized = 0
        var splashCountdownShortened = 0
        var signatureChecksNeutralized = 0
        var failedClasses = 0
        var processedCount = 0

        // 预分配容量避免扩容；使用 ClassDef（非 Immutable）以便未修改类直接复用原始引用
        val newClasses = ArrayList<ClassDef>(totalClasses)
        // ★ 代码引用的资源文件集合：cleanAssets 时在类遍历中顺手收集，
        // 消除「阶段2」对全部 DEX 的二次全量加载（对齐二改作者版本的收集方式）
        val assetRefs = HashSet<String>()

        for (classDef in dex!!.classes) {
            processedCount++
            val className = classDef.type

            // 进度日志（低频：每5000个类显示一次百分比）
            if (processedCount % 5000 == 0) {
                val pct = processedCount * 100 / totalClasses.coerceAtLeast(1)
                log("  进度: $processedCount/$totalClasses (${pct}%)")
                progress?.invoke(processedCount, totalClasses)
            }

            // ★ 开屏广告倒计时缩短（对齐二改作者）：仅处理类名含 splash 的类，
            // 把 ≥1000ms 的延迟常量（Handler.postDelayed/CountDownTimer）置 0，立即进入主界面
            // 声明在 try 外：catch 保底分支也要用 effectiveClass 保留 splash 修改
            val splashPatch = if (enableSplashShorten) patchClassForSplashCountdown(classDef, dropDebugInfo) else null
            val effectiveClass: ClassDef = splashPatch?.first ?: classDef
            if (splashPatch != null) {
                splashCountdownShortened += splashPatch.second
            }

            try {
                // ★ 顺手收集该类的字符串常量引用（资源文件名），零额外DEX加载
                if (collectAssetRefs) {
                    collectAssetRefsFromClass(classDef, assetRefs)
                }

                val matchedPattern = fastMatchAdClass(className, patterns)

                val hasForceTrue = patterns.forceTrueMethodNamesLowercase.isNotEmpty() &&
                    effectiveClass.methods.any { it.name.lowercase() in patterns.forceTrueMethodNamesLowercase }

                val hasDetection = effectiveClass.methods.any { method ->
                    isVpnDetectMethod(method, patterns) || isEmulatorDetectMethod(method, patterns) ||
                        isFalseStateMethod(method, patterns)
                }

                // ★ 杀签预判：类内存在签名校验方法（方法名含 signature 且方法体引用校验关键词）
                val hasSignatureCheck = removeSignatureChecks && effectiveClass.methods.any { method ->
                    val impl = method.implementation ?: return@any false
                    isSignatureVerifyMethod(method, impl)
                }
                if (matchedPattern != null || hasForceTrue || hasDetection || (removeSignatureChecks && hasSignatureCheck)) {
                    // 精简日志：不再逐类输出，仅累计统计
                    val result = patchSingleClass(effectiveClass, patterns, removeSignatureChecks)
                    newClasses.add(result.classDef)
                    // ★ 统计口径修正：只计「实际有修改」的类。
                    // 此前命中类一律计数（22053类 vs 实际842方法），虚高 20 倍，
                    // 且导致 totalChanged 恒大于0、零修改跳过写回判定失效。
                    val hasRealChange = result.neutralized > 0 || result.nopLoadLibrary > 0 ||
                        result.forcedTrue > 0 || result.vpnNeutralized > 0 ||
                        result.emulatorNeutralized > 0 || result.falseStateNeutralized > 0 ||
                        result.signatureNeutralized > 0
                    if (hasRealChange) patchedClasses++
                    neutralizedMethods += result.neutralized
                    nopLoadLibrary += result.nopLoadLibrary
                    forcedTrueMethods += result.forcedTrue
                    neutralizedVpnMethods += result.vpnNeutralized
                    neutralizedEmulatorMethods += result.emulatorNeutralized
                    falseStateNeutralized += result.falseStateNeutralized
                    signatureChecksNeutralized += result.signatureNeutralized
                } else {
                    // 扫描 loadLibrary NOP（可能改变类但不影响广告匹配）
                    val libPatch = patchClassForLoadLibrary(effectiveClass, patterns)
                    if (libPatch != null) {
                        newClasses.add(libPatch.first)
                        nopLoadLibrary += libPatch.second
                    } else {
                        // ★ 方法级深拷贝预解析：dexlib2 的 dex-backed 对象是懒解析的，
                        // DexWriter 写盘时要遍历全部指令多遍（字符串池/类型池/编码），
                        // 每遍 getInstructions() 都重新解析字节流 → N 遍遍历 = N 次全量解析+GC 风暴。
                        // 提前在扫描期把方法解析成 Immutable 对象，写盘时零解析、零分配。
                        // 注意：传入 effectiveClass 保留 splash 倒计时缩短的修改
                        newClasses.add(deepCopyClass(effectiveClass, dropDebugInfo))
                    }
                }
            } catch (_: Exception) {
                // 失败的类保底：深拷贝预解析（能拷多少拷多少，避免写盘时再集中解析）
                try {
                    newClasses.add(deepCopyClass(effectiveClass, dropDebugInfo))
                } catch (_: Exception) {
                    newClasses.add(effectiveClass)
                }
                failedClasses++
            }
        }

        // 显式解除辅助结构，帮助GC
        newClasses.trimToSize()
        val tScanMs = System.currentTimeMillis() - tLoadStart - tLoadMs

        // 零修改：跳过写回，省去最慢的磁盘写回步骤（DEX 保持原样）
        val totalChanged = patchedClasses + neutralizedMethods + nopLoadLibrary + forcedTrueMethods +
            neutralizedVpnMethods + neutralizedEmulatorMethods + falseStateNeutralized +
            splashCountdownShortened + signatureChecksNeutralized
        if (totalChanged == 0) {
            val elapsed = System.currentTimeMillis() - startTime
            log("  零修改，跳过写回 (${elapsed}ms)")
            return PatchResult(0, 0, 0, 0, 0, 0, 0, 0, 0, assetRefs)
        }

        log("  扫描完成，已修改 $patchedClasses 个类，开始写入 DEX ...")

        // ★ 释放原始 dex-backed 对象树：newClasses 已全部预解析为 Immutable 深拷贝，写盘不再需要原始树。
        // 置空后原始树不可达，交给 ART 后台 GC 渐进回收（不显式 System.gc()——实测其 STW 开销大于收益）
        dex = null

        // 写入前备份原DEX（防止写入中断导致DEX损坏）
        val bakFile = File(dexFile.parentFile, "${dexFile.name}.bak")
        try {
            dexFile.copyTo(bakFile, overwrite = true)
        } catch (_: Exception) {}

        try {
            val heapBefore = heapUsedMB()
            val tBuildStart = System.currentTimeMillis()
            val newDex = ImmutableDexFile(Opcodes.getDefault(), newClasses)
            val tBuildMs = System.currentTimeMillis() - tBuildStart

            val tmpDex = File(dexFile.parentFile, "${dexFile.name}.tmp")
            if (tmpDex.exists()) tmpDex.delete()
            val tWriteStart = System.currentTimeMillis()
            DexFileFactory.writeDexFile(tmpDex.absolutePath, newDex)
            val tWriteMs = System.currentTimeMillis() - tWriteStart

            if (!tmpDex.renameTo(dexFile)) {
                dexFile.delete()
                if (!tmpDex.renameTo(dexFile)) {
                    tmpDex.copyTo(dexFile, overwrite = true)
                    tmpDex.delete()
                }
            }

            // 写入成功，清理备份
            try { bakFile.delete() } catch (_: Exception) {} 

            val elapsed = System.currentTimeMillis() - startTime
            log("  DEX 写入成功: ${dexFile.name} (${elapsed}ms, ${formatSize(dexFile.length())})")
            // ★ 性能打点（排查写回瓶颈用）：拆解加载/扫描/构建ImmutableDexFile/写盘四段耗时
            log("  [计时] 加载=${tLoadMs}ms 扫描=${tScanMs}ms 构建ImmutableDexFile=${tBuildMs}ms 写盘=${tWriteMs}ms 写前堆=${heapBefore}MB 写后堆=${heapUsedMB()}MB")
        } catch (e: OutOfMemoryError) {
            newClasses.clear()
            // 写入失败，恢复原DEX
            try { if (bakFile.exists()) { dexFile.delete(); bakFile.renameTo(dexFile) } } catch (_: Exception) {}
            throw RuntimeException("DEX写入内存不足: ${dexFile.name}", e)
        }

        if (failedClasses > 0) {
            log("  [注意] $failedClasses 个类处理失败已跳过")
        }
        if (forcedTrueMethods > 0) {
            log("  ✓ 强制返回 true 方法: $forcedTrueMethods 个")
        }
        if (nopLoadLibrary > 0) {
            log("  ✓ NOP 广告so加载调用: $nopLoadLibrary 处（so 文件保留，不触发 UnsatisfiedLinkError）")
        }
        if (neutralizedVpnMethods > 0) {
            log("  ✓ 去除 VPN 检测方法: $neutralizedVpnMethods 个")
        }
        if (neutralizedEmulatorMethods > 0) {
            log("  ✓ 去除虚拟机检测方法: $neutralizedEmulatorMethods 个")
        }
        if (splashCountdownShortened > 0) {
            log("  ✓ 缩短开屏广告倒计时: $splashCountdownShortened 处（立即进入主界面）")
        }
        if (signatureChecksNeutralized > 0) {
            log("  ✓ 去除签名校验方法: $signatureChecksNeutralized 个（置 return true）")
        }

        return PatchResult(
            patchedClasses = patchedClasses,
            neutralizedMethods = neutralizedMethods,
            nopLoadLibrary = nopLoadLibrary,
            forcedTrueMethods = forcedTrueMethods,
            neutralizedVpnMethods = neutralizedVpnMethods,
            neutralizedEmulatorMethods = neutralizedEmulatorMethods,
            falseStateNeutralized = falseStateNeutralized,
            splashCountdownShortened = splashCountdownShortened,
            signatureChecksNeutralized = signatureChecksNeutralized,
            assetRefs = assetRefs
        )
    }

    /**
     * 收集一个类中所有方法指令里的字符串常量引用（资源文件名）。
     * 在 patchDex 类遍历中顺手调用，避免单独二次加载 DEX。
     */
    /** 方法级全量深拷贝：把 dex-backed 懒解析兑现为 Immutable 对象。
     * DexWriter 写盘遍历全部指令多遍，dex-backed 每次 getInstructions() 都重新解析；
     * 提前深拷贝一次，写盘时遍历内存对象，零解析、零重复分配。
     * ★ dropDebugInfo=true 时丢弃 debug_info（行号表/局部变量表）：写盘时省去最耗时的调试信息状态机编码，
     * 不影响 APK 运行，仅处理后 APK 的崩溃日志不显示源码行号。 */
    private fun deepCopyClass(classDef: ClassDef, dropDebugInfo: Boolean = true): ImmutableClassDef {
        val methods = ArrayList<Method>(classDef.methods.count())
        for (m in classDef.methods) {
            try {
                val impl = m.implementation
                if (impl != null) {
                    methods.add(
                        ImmutableMethod(
                            m.definingClass, m.name, m.parameters.toList(),
                            m.returnType, m.accessFlags,
                            m.annotations.toSet(), m.hiddenApiRestrictions.toSet(),
                            ImmutableMethodImplementation(
                                impl.registerCount.coerceAtLeast(1),
                                impl.instructions,
                                impl.tryBlocks.toList(),
                                if (dropDebugInfo) emptyList() else impl.debugItems.toList()
                            )
                        )
                    )
                } else {
                    methods.add(ImmutableMethod.of(m))
                }
            } catch (_: Exception) {
                methods.add(m)
            }
        }
        return ImmutableClassDef(
            classDef.type, classDef.accessFlags, classDef.superclass,
            classDef.interfaces.toList(), classDef.sourceFile,
            classDef.annotations.toSet(), classDef.fields.toList(), methods
        )
    }

    private fun collectAssetRefsFromClass(
        classDef: ClassDef,
        refs: MutableSet<String>
    ) {
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
                    if (lower.contains("/")) {
                        refs.add(lower.substringAfterLast('/'))
                    }
                }
            }
        }
    }

    /** 判断字符串是否像资源文件引用（assets 路径或带常见资源扩展名） */
    private fun isAssetLikeRef(value: String): Boolean {
        if (value.startsWith("assets/")) return true
        val ext = value.substringAfterLast('.')
        return ext.length in 2..4 && ext.all { it.isLetter() } &&
            ext in RESOURCE_EXTENSIONS
    }

    /** 常见资源文件扩展名 */
    private val RESOURCE_EXTENSIONS = setOf(
        "xml", "json", "plist", "png", "jpg", "jpeg", "gif", "webp",
        "js", "txt", "dat", "bin", "db", "sqlite", "zip", "mp3", "mp4"
    )

       
              
       
    private data class SingleClassPatch(
        val classDef: ClassDef,
        val neutralized: Int,
        val nopLoadLibrary: Int = 0,
        val forcedTrue: Int,
        val vpnNeutralized: Int = 0,
        val emulatorNeutralized: Int = 0,
        val falseStateNeutralized: Int = 0,
        val signatureNeutralized: Int = 0
    )

       
                                          
      
                                   
                                        
                          
      
                                             
       
     private fun patchSingleClass(
         classDef: ClassDef,
         patterns: CompiledPatterns,
         removeSignatureChecks: Boolean = false
     ): SingleClassPatch {
        // ── 阶段1：轻量预判（不构建任何新方法对象，仅做判定）──
        // 实测数据显示「命中类数」远大于「实际修改方法数」（22053类 vs 842方法），
        // 旧实现为每个命中类全量深拷贝所有方法后再判断零修改，浪费巨大。
        // 预判先行：确定本类是否有任何可修改点，无则直接复用原始类引用。
        var hasAnyMod = false
        for (method in classDef.methods) {
            try {
                val impl = method.implementation ?: continue
                val methodName = method.name

                if (patterns.adLibKeywords.isNotEmpty() &&
                    nopOutAdLibLoadLibrary(impl, patterns.adLibKeywords) != null
                ) {
                    hasAnyMod = true
                    break
                }

                if (methodName == "<init>" || methodName == "<clinit>") continue

                // ★ VPN/虚拟机检测：方法名匹配 + 方法体含检测证据 + 仅处理 boolean 返回。
                // 仅名字匹配就置 false 会误伤业务方法（getVpnState 等）导致处理后 APK 闪退；
                // int 返回类型常承载状态码，置 0 后调用方 switch/分支逻辑断裂，同样跳过。
                val isVpnDetect = isVpnDetectMethod(method, patterns) && methodHasDetectionEvidence(impl)
                val isEmulatorDetect = isEmulatorDetectMethod(method, patterns) && methodHasDetectionEvidence(impl)
                if ((isVpnDetect || isEmulatorDetect) && method.returnType == "Z") {
                    hasAnyMod = true
                    break
                }

                if (isFalseStateMethod(method, patterns) &&
                    (method.returnType == "Z" || method.returnType == "I")
                ) {
                    hasAnyMod = true
                    break
                }

                if (patterns.forceTrueMethodNamesLowercase.isNotEmpty() &&
                    methodName.lowercase() in patterns.forceTrueMethodNamesLowercase &&
                    (method.returnType == "Z" || method.returnType == "I")
                ) {
                    hasAnyMod = true
                    break
                }

                val isAdMethod = fastMatchNeutralizeMethod(methodName, patterns) &&
                     !isCallbackOrListenerMethod(methodName)
                 if (isAdMethod && method.returnType == "V") {
                     hasAnyMod = true
                     break
                 }

                 // ★ 一键杀签预判：签名校验方法（方法名含 signature 且方法体引用校验关键词）
                 if (removeSignatureChecks && isSignatureVerifyMethod(method, impl)) {
                     hasAnyMod = true
                     break
                 }
             } catch (_: Exception) {
             }
         }

        if (!hasAnyMod) {
            // 无任何可修改方法：直接复用原始类引用，完全跳过深拷贝与重建
            return SingleClassPatch(classDef, 0, 0, 0, 0, 0, 0)
        }

        // ── 阶段2：确认有修改，执行完整重建 ──
        var neutralizedCount = 0
        var nopLoadLibraryCount = 0
        var forcedTrueCount = 0
        var vpnNeutralizedCount = 0
        var emulatorNeutralizedCount = 0
        var falseStateNeutralizedCount = 0
         var signatureNeutralizedCount = 0
         var skippedCount = 0

        // ★ 方法级零拷贝：未修改方法直接复用原始 Method 引用（dexlib2 写回走接口读取，无需 Immutable 包装），
        // 仅被修改的方法重建 ImmutableMethod。此前逐方法 ImmutableMethod.of() 深拷贝导致大量无谓对象创建。
        val newMethods = ArrayList<Method>(classDef.methods.count())
        for (method in classDef.methods) {
            try {
                val methodName = method.name
                val impl = method.implementation
                if (impl == null) {
                    newMethods.add(method)
                    continue
                }

                
                
                
                
                
                
                if (patterns.adLibKeywords.isNotEmpty()) {
                    val nopImpl = nopOutAdLibLoadLibrary(impl, patterns.adLibKeywords)
                    if (nopImpl != null) {
                        newMethods.add(
                            ImmutableMethod(
                                method.definingClass, method.name, method.parameters.toList(),
                                method.returnType, method.accessFlags,
                                method.annotations.toSet(), method.hiddenApiRestrictions.toSet(), nopImpl
                            )
                        )
                        nopLoadLibraryCount++
                        continue
                    }
                }

                
                if (methodName == "<init>" || methodName == "<clinit>") {
                    newMethods.add(method)
                    skippedCount++
                    continue
                }

                
                
                
                
                // ★ VPN/虚拟机检测：方法名匹配 + 方法体含检测证据 + 仅处理 boolean 返回。
                // 防止误伤业务方法（getVpnState 等）与 int 状态码方法导致处理后 APK 闪退。
                val isVpnDetect = isVpnDetectMethod(method, patterns) && methodHasDetectionEvidence(impl)
                val isEmulatorDetect = isEmulatorDetectMethod(method, patterns) && methodHasDetectionEvidence(impl)
                if (isVpnDetect || isEmulatorDetect) {
                    if (method.returnType == "Z") {
                        val newImpl = ImmutableMethodImplementation(
                            impl.registerCount.coerceAtLeast(1),
                            createReturnFalseInstructions(),
                            emptyList(),
                            emptyList()
                        )
                        newMethods.add(
                            ImmutableMethod(
                                method.definingClass, method.name, method.parameters.toList(),
                                method.returnType, method.accessFlags,
                                method.annotations.toSet(), method.hiddenApiRestrictions.toSet(), newImpl
                            )
                        )
                        if (isVpnDetect) vpnNeutralizedCount++ else emulatorNeutralizedCount++
                        continue
                    }
                }

                
                
                
                
                if (isFalseStateMethod(method, patterns)) {
                    if (method.returnType == "Z" || method.returnType == "I") {
                        val newImpl = ImmutableMethodImplementation(
                            impl.registerCount.coerceAtLeast(1),
                            createReturnFalseInstructions(),
                            emptyList(),
                            emptyList()
                        )
                        newMethods.add(
                            ImmutableMethod(
                                method.definingClass, method.name, method.parameters.toList(),
                                method.returnType, method.accessFlags,
                                method.annotations.toSet(), method.hiddenApiRestrictions.toSet(), newImpl
                            )
                        )
                        falseStateNeutralizedCount++
                        continue
                    }
                }

                
                
                
                
                
                if (patterns.forceTrueMethodNamesLowercase.isNotEmpty() &&
                    methodName.lowercase() in patterns.forceTrueMethodNamesLowercase
                ) {
                    if (method.returnType == "Z" || method.returnType == "I") {
                        val newImpl = ImmutableMethodImplementation(
                            impl.registerCount.coerceAtLeast(1),
                            createReturnTrueInstructions(),
                            emptyList(),
                            emptyList()
                        )
                        newMethods.add(
                            ImmutableMethod(
                                method.definingClass, method.name, method.parameters.toList(),
                                method.returnType, method.accessFlags,
                                method.annotations.toSet(), method.hiddenApiRestrictions.toSet(), newImpl
                            )
                        )
                        forcedTrueCount++
                        continue
                    }
                    
                }

                
                
                
                
                val isAdMethod = fastMatchNeutralizeMethod(methodName, patterns) &&
                    !isCallbackOrListenerMethod(methodName)

                if (isAdMethod) {
                    
                    
                    
                    
                    
                    
                    
                    
                    if (method.returnType != "V") {
                        newMethods.add(method)
                        skippedCount++
                        continue
                    }
                    val newImpl = ImmutableMethodImplementation(
                        impl.registerCount.coerceAtLeast(1),
                        listOf(ImmutableInstruction10x(Opcode.RETURN_VOID)),
                        emptyList(),
                        emptyList()
                    )
                    newMethods.add(
                        ImmutableMethod(
                            method.definingClass, method.name, method.parameters.toList(),
                            method.returnType, method.accessFlags,
                            method.annotations.toSet(), method.hiddenApiRestrictions.toSet(), newImpl
                        )
                    )
neutralizedCount++
                 } else {
                     // ★ 一键杀签：签名校验方法置 return true（校验恒通过，绕过自校验防闪退）
                     if (removeSignatureChecks && isSignatureVerifyMethod(method, impl)) {
                         if (method.returnType == "Z" || method.returnType == "I") {
                             val newImpl = ImmutableMethodImplementation(
                                 impl.registerCount.coerceAtLeast(1),
                                 createReturnTrueInstructions(),
                                 emptyList(),
                                 emptyList()
                             )
                             newMethods.add(
                                 ImmutableMethod(
                                     method.definingClass, method.name, method.parameters.toList(),
                                     method.returnType, method.accessFlags,
                                     method.annotations.toSet(), method.hiddenApiRestrictions.toSet(), newImpl
                                 )
                             )
                             signatureNeutralizedCount++
                             continue
                         }
                     }
                     newMethods.add(method)
                     skippedCount++
                 }
            } catch (_: Exception) {
                // 异常兜底：直接复用原始引用（无需深拷贝，也不会再抛异常）
                newMethods.add(method)
            }
        }

        // 逐类日志已精简：修改统计由 patchDex 汇总输出

val newClass: ClassDef = if (
             neutralizedCount == 0 && nopLoadLibraryCount == 0 && forcedTrueCount == 0 &&
             vpnNeutralizedCount == 0 && emulatorNeutralizedCount == 0 && falseStateNeutralizedCount == 0 &&
             signatureNeutralizedCount == 0
         ) {
            // 无实际修改：复用原始类引用，避免不必要的深拷贝
            classDef
        } else {
            ImmutableClassDef(
                classDef.type, classDef.accessFlags, classDef.superclass,
                classDef.interfaces.toList(), classDef.sourceFile,
                classDef.annotations.toSet(), classDef.fields.toList(), newMethods
            )
        }
return SingleClassPatch(
             classDef = newClass,
             neutralized = neutralizedCount,
             nopLoadLibrary = nopLoadLibraryCount,
             forcedTrue = forcedTrueCount,
             vpnNeutralized = vpnNeutralizedCount,
             emulatorNeutralized = emulatorNeutralizedCount,
             falseStateNeutralized = falseStateNeutralizedCount,
             signatureNeutralized = signatureNeutralizedCount
         )
    }

       
                                                       
      
                                                                          
                                    
      
                                              
       
/**
     * 判断方法是否为签名校验方法（一键杀签预判）：
     * ① 方法名含 signature / checksig / verifysig 等校验词根；
     * ② 方法体指令引用签名校验关键词（字符串常量命中 SignatureDetector 关键词，
     *    或调用 signature / signinginfo / packageinfo 相关类型）。
     * 双条件缺一不可：仅名字匹配会误伤业务方法（如 getSignature 只是取签名字段）。
     */
    private fun isSignatureVerifyMethod(method: Method, impl: MethodImplementation): Boolean {
        val name = method.name.lowercase()
        val nameHits = name.contains("signature") || name.contains("checksig") ||
            name.contains("verifysig") || name.contains("sigcheck") || name.contains("signcheck")
        if (!nameHits) return false
        for (ins in impl.instructions) {
            val ref = (ins as? ReferenceInstruction)?.reference ?: continue
            when (ref) {
                is StringReference -> {
                    val lower = ref.string.lowercase()
                    if (SignatureDetector.SIGNATURE_KEYWORDS.any { lower.contains(it) }) return true
                }
                is MethodReference -> {
                    val owner = ref.definingClass.lowercase()
                    if (owner.contains("signature") || owner.contains("signinginfo") ||
                        owner.contains("packageinfo")
                    ) return true
                }
                else -> {}
            }
        }
        return false
    }

    /**
     * 开屏广告倒计时缩短（对齐二改作者实测稳定版）：
     * 类名含 splash 的类中，追踪 CONST 常量写入寄存器的值，
     * 命中 Handler.postDelayed/sendEmptyMessageDelayed/sendMessageDelayed/CountDownTimer.<init>
     * 且延迟值 ≥1000ms 时，把最近一次写入该延迟寄存器的 const 指令置 0 → 立即进入主界面。
     */
private fun patchClassForSplashCountdown(
         classDef: ClassDef,
         dropDebugInfo: Boolean = true
     ): Pair<ImmutableClassDef, Int>? {
        val lowerType = classDef.type.lowercase()
        if (!lowerType.contains("splash")) return null
        if (classDef.methods.none { it.implementation != null }) return null
        var shortened = 0
        var changed = false
        val newMethods = ArrayList<Method>(classDef.methods.count())
        for (method in classDef.methods) {
            val impl = method.implementation
            if (impl == null) {
                newMethods.add(ImmutableMethod.of(method))
                continue
            }
            val newImpl = shortenSplashCountdownImpl(impl)
            if (newImpl != null) {
                newMethods.add(
                    ImmutableMethod(
                        method.definingClass, method.name, method.parameters.toList(),
                        method.returnType, method.accessFlags,
                        method.annotations.toSet(), method.hiddenApiRestrictions.toSet(), newImpl
                    )
                )
                shortened++
                changed = true
            } else {
// ★ 未修改方法也深拷贝并按 dropDebugInfo 决定是否丢弃 debug_info（与 deepCopyClass 策略一致）
                 try {
                     newMethods.add(
                         ImmutableMethod(
                             method.definingClass, method.name, method.parameters.toList(),
                             method.returnType, method.accessFlags,
                             method.annotations.toSet(), method.hiddenApiRestrictions.toSet(),
                             ImmutableMethodImplementation(
                                 impl.registerCount.coerceAtLeast(1),
                                 impl.instructions,
                                 impl.tryBlocks.toList(),
                                 if (dropDebugInfo) emptyList() else impl.debugItems.toList()
                             )
                         )
                     )
                } catch (_: Exception) {
                    newMethods.add(method)
                }
            }
        }
        if (!changed) return null
        val newClass = ImmutableClassDef(
            classDef.type, classDef.accessFlags, classDef.superclass,
            classDef.interfaces.toList(), classDef.sourceFile,
            classDef.annotations.toSet(), classDef.fields.toList(), newMethods
        )
        return Pair(newClass, shortened)
    }

    private fun shortenSplashCountdownImpl(impl: MethodImplementation): ImmutableMethodImplementation? {
        // 追踪「最近一次写入各寄存器的 const 常量」：寄存器号 -> (指令下标, 常量值, 原指令)
        val recentConsts = HashMap<Int, Triple<Int, Long, org.jf.dexlib2.iface.instruction.Instruction>>()
        val newInstructions = mutableListOf<ImmutableInstruction>()

        for (ins in impl.instructions) {
            if (isConstLiteral(ins)) {
                val reg = (ins as? OneRegisterInstruction)?.registerA ?: -1
                val value = constLiteralValue(ins)
                if (reg >= 0 && value != null) {
                    recentConsts[reg] = Triple(newInstructions.size, value, ins)
                }
                newInstructions.add(ImmutableInstruction.of(ins))
                continue
            }
            clearTrackedRegister(ins, recentConsts)
            if (isCountdownInvoke(ins)) {
                val delayReg = countdownDelayRegister(ins)
                val tracked = recentConsts[delayReg]
                if (delayReg >= 0 && tracked != null && tracked.second >= 1000L) {
                    // 把延迟常量置 0 → 倒计时立即结束进入主界面
                    newInstructions[tracked.first] = makeZeroConst(tracked.third)
                }
            }
            newInstructions.add(ImmutableInstruction.of(ins))
        }

        val original = impl.instructions.toList()
        if (newInstructions.size != original.size) return null
        var changed = false
        for (i in newInstructions.indices) {
            if (newInstructions[i] != ImmutableInstruction.of(original[i])) {
                changed = true
                break
            }
        }
        if (!changed) return null
        // ★ 丢弃 debug_info（与全项目丢 debug 策略一致：写盘更快、处理后体积更小）
        return ImmutableMethodImplementation(
            impl.registerCount.coerceAtLeast(1),
            newInstructions,
            impl.tryBlocks.toList(),
            emptyList()
        )
    }

    private fun makeZeroConst(ins: org.jf.dexlib2.iface.instruction.Instruction): ImmutableInstruction {
        val reg = (ins as? OneRegisterInstruction)?.registerA ?: 0
        return when (ins.opcode) {
            Opcode.CONST_4 -> ImmutableInstruction11n(Opcode.CONST_4, reg, 0)
            Opcode.CONST_16 -> ImmutableInstruction21s(Opcode.CONST_16, reg, 0)
            Opcode.CONST -> ImmutableInstruction31i(Opcode.CONST, reg, 0)
            Opcode.CONST_HIGH16 -> ImmutableInstruction21ih(Opcode.CONST_HIGH16, reg, 0)
            Opcode.CONST_WIDE_16 -> ImmutableInstruction21s(Opcode.CONST_WIDE_16, reg, 0)
            Opcode.CONST_WIDE_32 -> ImmutableInstruction31i(Opcode.CONST_WIDE_32, reg, 0)
            Opcode.CONST_WIDE -> ImmutableInstruction51l(Opcode.CONST_WIDE, reg, 0)
            Opcode.CONST_WIDE_HIGH16 -> ImmutableInstruction21ih(Opcode.CONST_WIDE_HIGH16, reg, 0)
            else -> ImmutableInstruction.of(ins)
        }
    }

    private fun isConstLiteral(ins: org.jf.dexlib2.iface.instruction.Instruction): Boolean {
        val op = ins.opcode
        return op == Opcode.CONST_4 || op == Opcode.CONST_16 || op == Opcode.CONST ||
            op == Opcode.CONST_HIGH16 || op == Opcode.CONST_WIDE_16 ||
            op == Opcode.CONST_WIDE_32 || op == Opcode.CONST_WIDE || op == Opcode.CONST_WIDE_HIGH16
    }

    private fun constLiteralValue(ins: org.jf.dexlib2.iface.instruction.Instruction): Long? {
        return when (ins) {
            is WideLiteralInstruction -> ins.wideLiteral
            is NarrowLiteralInstruction -> ins.narrowLiteral.toLong()
            else -> null
        }
    }

    private fun clearTrackedRegister(
        ins: org.jf.dexlib2.iface.instruction.Instruction,
        recentConsts: HashMap<Int, Triple<Int, Long, org.jf.dexlib2.iface.instruction.Instruction>>
    ) {
        // invoke 指令只读不写，不清理（延迟常量常在 invoke 前写入该寄存器）
        if (ins is Instruction35c || ins is Instruction3rc) return
        val writtenReg: Int = when (ins) {
            is OneRegisterInstruction -> ins.registerA
            is TwoRegisterInstruction -> ins.registerA
            else -> -1
        }
        if (writtenReg >= 0) recentConsts.remove(writtenReg)
    }

    private fun isCountdownInvoke(ins: org.jf.dexlib2.iface.instruction.Instruction): Boolean {
        val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference ?: return false
        val clazz = ref.definingClass
        val name = ref.name
        return when {
            clazz == "Landroid/os/Handler;" && (name == "postDelayed" ||
                name == "sendEmptyMessageDelayed" || name == "sendMessageDelayed") -> true
            clazz == "Landroid/os/CountDownTimer;" && name == "<init>" -> true
            else -> false
        }
    }

    private fun countdownDelayRegister(ins: org.jf.dexlib2.iface.instruction.Instruction): Int {
        val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference ?: return -1
        return when {
            // Handler.postDelayed(runnable, delay)：35c 寄存器 E 为第 5 参数 = delay；3rc start+2 为第 3 参数 = delay
            ref.definingClass == "Landroid/os/Handler;" && ins is Instruction35c -> ins.registerE
            ref.definingClass == "Landroid/os/Handler;" && ins is Instruction3rc -> ins.startRegister + 2
            // CountDownTimer(millisInFuture, countDownInterval)：35c 寄存器 D 为第 4 参数 = millisInFuture
            ref.definingClass == "Landroid/os/CountDownTimer;" && ins is Instruction35c -> ins.registerD
            ref.definingClass == "Landroid/os/CountDownTimer;" && ins is Instruction3rc -> ins.startRegister + 1
            else -> -1
        }
    }

    private fun patchClassForLoadLibrary(
        classDef: ClassDef,
        patterns: CompiledPatterns
    ): Pair<ImmutableClassDef, Int>? {
        if (patterns.adLibKeywords.isEmpty()) return null
        var nopCount = 0
        var changed = false
        // ★ 方法级零拷贝：未修改方法直接复用原始引用
        val newMethods = ArrayList<Method>(classDef.methods.count())
        for (method in classDef.methods) {
            val impl = method.implementation
            if (impl == null) {
                newMethods.add(method)
                continue
            }
            val nopImpl = nopOutAdLibLoadLibrary(impl, patterns.adLibKeywords)
            if (nopImpl != null) {
                newMethods.add(
                    ImmutableMethod(
                        method.definingClass, method.name, method.parameters.toList(),
                        method.returnType, method.accessFlags,
                        method.annotations.toSet(), method.hiddenApiRestrictions.toSet(), nopImpl
                    )
                )
                nopCount++
                changed = true
            } else {
                newMethods.add(method)
            }
        }
        if (!changed) return null
        val newClass = ImmutableClassDef(
            classDef.type, classDef.accessFlags, classDef.superclass,
            classDef.interfaces.toList(), classDef.sourceFile,
            classDef.annotations.toSet(), classDef.fields.toList(), newMethods
        )
        return Pair(newClass, nopCount)
    }

       
                                                                              
                                        
      
               
                                                                                         
                                                                           
                                                     
                                                    
                                         
      
                                                
       
    private fun nopOutAdLibLoadLibrary(
        impl: MethodImplementation,
        libKeywords: Set<String>
    ): ImmutableMethodImplementation? {
        // 快速预检：扫描是否存在 CONST_STRING + loadLibrary 调用模式
        var hasConstString = false
        var hasLoadLibrary = false
        for (ins in impl.instructions) {
            val op = ins.opcode
            if (op == Opcode.CONST_STRING || op == Opcode.CONST_STRING_JUMBO) {
                hasConstString = true
            } else if (hasConstString && isLoadLibraryInvoke(ins)) {
                hasLoadLibrary = true
                break // 找到模式，跳出预检
            }
        }
        if (!hasLoadLibrary) return null // 无loadLibrary调用，快速退出，避免复制全部指令

        var changed = false
        val newInstructions = mutableListOf<ImmutableInstruction>()
        
        val recentStrings = HashMap<Int, String>()

        for (ins in impl.instructions) {
            when {
                ins.opcode == Opcode.CONST_STRING || ins.opcode == Opcode.CONST_STRING_JUMBO -> {
                    val reg = when (ins) {
                        is Instruction21c -> ins.registerA
                        is Instruction31c -> ins.registerA
                        else -> -1
                    }
                    val str = (ins as? ReferenceInstruction)?.reference as? StringReference
                    if (reg >= 0 && str != null) recentStrings[reg] = str.string
                    newInstructions.add(ImmutableInstruction.of(ins))
                }
                isLoadLibraryInvoke(ins) -> {
                    val reg = when (ins) {
                        is Instruction35c -> ins.registerC
                        is Instruction3rc -> ins.startRegister
                        else -> -1
                    }
                    val libName = recentStrings[reg].orEmpty()
                    if (isAdLibName(libName, libKeywords)) {
                        newInstructions.addAll(nopPaddingFor(ins))
                        changed = true
                    } else {
                        newInstructions.add(ImmutableInstruction.of(ins))
                    }
                }
                else -> newInstructions.add(ImmutableInstruction.of(ins))
            }
        }
        if (!changed) return null
        
        return ImmutableMethodImplementation(
            impl.registerCount.coerceAtLeast(1),
            newInstructions,
            impl.tryBlocks.toList(),
            impl.debugItems.toList()
        )
    }

       
                                                                                       
       
    private fun isLoadLibraryInvoke(ins: org.jf.dexlib2.iface.instruction.Instruction): Boolean {
        if (ins !is FiveRegisterInstruction) return false
        val opcode = ins.opcode
        if (opcode != Opcode.INVOKE_STATIC && opcode != Opcode.INVOKE_VIRTUAL &&
            opcode != Opcode.INVOKE_DIRECT && opcode != Opcode.INVOKE_SUPER &&
            opcode != Opcode.INVOKE_INTERFACE) return false
        val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference ?: return false
        val name = ref.name
        if (name != "loadLibrary" && name != "load") return false
        val clazz = ref.definingClass
        return clazz == "Ljava/lang/System;" || clazz == "Ljava/lang/Runtime;"
    }

       
                                              
                                                                       
       
    private fun nopPaddingFor(ins: org.jf.dexlib2.iface.instruction.Instruction): List<ImmutableInstruction10x> {
        val count = if (ins is Instruction3rc) 5 else 3
        return List(count) { ImmutableInstruction10x(Opcode.NOP) }
    }

       
                                           
                                                                  
       
    private fun isAdLibName(libName: String, libKeywords: Set<String>): Boolean {
        if (libName.isEmpty() || libKeywords.isEmpty()) return false
        var name = libName.lowercase()
        name = name.substringAfterLast('/')
        if (name.startsWith("lib")) name = name.removePrefix("lib")
        if (name.endsWith(".so")) name = name.dropLast(3)
        if (name.isEmpty()) return false
        return libKeywords.any { name.contains(it) }
    }

       
                              
      
                   
                                               
                        
                                          
                                  
                                                                
                                                                     
      
                           
       
    private fun fastMatchNeutralizeMethod(methodName: String, patterns: CompiledPatterns): Boolean {
        val lower = methodName.lowercase()

        
        if (lower in patterns.exactMethodNamesLowercase) return true

        
        for (keyword in patterns.neutralizeMethodKeywords) {
            if (isKeywordAtBoundary(methodName, lower, keyword)) return true
        }
        return false
    }

       
                                                  
      
                          
                                                                              
                                                            
                          
                                                                                  
                     
                               
       
    private fun isCallbackOrListenerMethod(methodName: String): Boolean {
        val n = methodName.lowercase()
        if (n.startsWith("on")) return true
        if (n.contains("listener")) return true
        if (n.contains("callback")) return true
        if (n.contains("observer")) return true
        if (n.contains("vpaid")) return true
        return false
    }

       
                                        
                                                           
      
                                                    
       
    private fun isKeywordAtBoundary(name: String, nameLower: String, keyword: String): Boolean {
        if (keyword.isEmpty()) return false
        var fromIndex = 0
        while (true) {
            val idx = nameLower.indexOf(keyword, fromIndex)
            if (idx < 0) return false
            
            val prevOk = idx == 0 ||
                !name[idx - 1].isLetter() ||
                (name[idx].isUpperCase() && name[idx - 1].isLowerCase())
            
            val nextIdx = idx + keyword.length
            val nextOk = nextIdx >= name.length ||
                !name[nextIdx].isLetter() ||
                name[nextIdx].isUpperCase()
            if (prevOk && nextOk) return true
            fromIndex = idx + 1
        }
    }

       
                        
      
                                                                     
                                                         
       
    private fun isVpnDetectMethod(method: Method, patterns: CompiledPatterns): Boolean {
        if (patterns.vpnDetectKeywords.isEmpty()) return false
        val lower = method.name.lowercase()
        for (keyword in patterns.vpnDetectKeywords) {
            if (lower.contains(keyword)) return true
        }
        return false
    }

    /** 检测证据字符串：方法体指令中引用了这些字符串才认为是真正的环境检测方法。
     * 此前仅按方法名匹配（contains），业务方法只要名字带 vpn/emulator 就被置 false，
     * 导致调用方初始化/分支逻辑断裂、处理后 APK 闪退。
     * 列表与二改作者实测稳定版完全一致。 */
    private val DETECTION_EVIDENCE = listOf(
        // 网络接口检测
        "java/net/networkinterface", "networkinterface", "getnetworkinterfaces",
        "getinetaddresses", "java/net/socket",
        // 系统属性/构建信息检测
        "android/os/build", "build.fingerprint",
        "getproperty", "systemproperties", "getprop",
        "java/lang/system", "java/util/vector",
        // 文件/进程检测
        "java/lang/runtime", "java/io/file", "java/io/bufferedreader",
        "java/io/inputstream", "exec(",
        // 系统服务检测
        "getsystemservice", "connectivitymanager", "ethernetmanager",
        "settings.secure", "contentresolver", "wifiinfo",
        // 模拟器特征路径
        "net/eth", "dev/qemu", "microsoft/windows", "genymotion", "bluestacks"
    )

    /** 检查方法实现中是否包含检测证据字符串引用 */
    private fun methodHasDetectionEvidence(impl: MethodImplementation): Boolean {
        for (ins in impl.instructions) {
            val ref = (ins as? ReferenceInstruction)?.reference ?: continue
            val s = ref.toString().lowercase()
            for (keyword in DETECTION_EVIDENCE) {
                if (s.contains(keyword)) return true
            }
        }
        return false
    }

       
                          
      
                                 
                                                                         
       
    private fun isEmulatorDetectMethod(method: Method, patterns: CompiledPatterns): Boolean {
        if (patterns.emulatorDetectKeywords.isEmpty()) return false
        val lower = method.name.lowercase()
        for (keyword in patterns.emulatorDetectKeywords) {
            if (lower.contains(keyword)) return true
        }
        return false
    }

       
                                     
      
                                                   
                                                     
                             
       
    private fun isFalseStateMethod(method: Method, patterns: CompiledPatterns): Boolean {
        if (patterns.falseStateMethodKeywords.isEmpty()) return false
        val lower = method.name.lowercase()
        for (keyword in patterns.falseStateMethodKeywords) {
            if (lower.contains(keyword)) return true
        }
        return false
    }

       
                                                     
      
                      
                
      
                                             
       
    private fun createReturnTrueInstructions(): List<ImmutableInstruction> {
        return listOf(
            ImmutableInstruction11n(Opcode.CONST_4, 0, 1),
            ImmutableInstruction11x(Opcode.RETURN, 0)
        )
    }

       
                                                          
      
                      
                
      
                                                                 
       
    private fun createReturnFalseInstructions(): List<ImmutableInstruction> {
        return listOf(
            ImmutableInstruction11n(Opcode.CONST_4, 0, 0),
            ImmutableInstruction11x(Opcode.RETURN, 0)
        )
    }

       
                        
                                                                          
                                            
       
    internal fun createReturnInstructions(returnType: String): List<ImmutableInstruction> {
        if (returnType.isEmpty()) {
            return listOf(ImmutableInstruction10x(Opcode.RETURN_VOID))
        }

        val firstChar = returnType.first()

        return when (firstChar) {
            'V' -> listOf(ImmutableInstruction10x(Opcode.RETURN_VOID))
            'Z', 'B', 'S', 'C', 'I' -> listOf(
                ImmutableInstruction11n(Opcode.CONST_4, 0, 0),
                ImmutableInstruction11x(Opcode.RETURN, 0)
            )
            'J' -> listOf(
                ImmutableInstruction21s(Opcode.CONST_WIDE_16, 0, 0),
                ImmutableInstruction11x(Opcode.RETURN_WIDE, 0)
            )
            'F' -> listOf(
                ImmutableInstruction11n(Opcode.CONST_4, 0, 0),
                ImmutableInstruction11x(Opcode.RETURN, 0)
            )
            'D' -> listOf(
                ImmutableInstruction21s(Opcode.CONST_WIDE_16, 0, 0),
                ImmutableInstruction11x(Opcode.RETURN_WIDE, 0)
            )
            else -> listOf(
                ImmutableInstruction11n(Opcode.CONST_4, 0, 0),
                ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0)
            )
        }
    }

       
                                         
                                            
       
    internal fun isReferenceReturnType(returnType: String): Boolean {
        if (returnType.isEmpty()) return false
        val c = returnType[0]
        return c == 'L' || c == '['
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
        }
    }

    /** 当前堆已用内存（MB），性能打点用 */
    private fun heapUsedMB(): Long {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
    }
}

   
           
   
data class PatchResult(
    val patchedClasses: Int,
    val neutralizedMethods: Int,
    val nopLoadLibrary: Int = 0,
    val forcedTrueMethods: Int = 0,
    val neutralizedVpnMethods: Int = 0,
    val neutralizedEmulatorMethods: Int = 0,
    val falseStateNeutralized: Int = 0,
    val splashCountdownShortened: Int = 0,
    val signatureChecksNeutralized: Int = 0,
    val assetRefs: Set<String> = emptySet()
)