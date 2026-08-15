package com.ads.purge.core

/**
 * 去广告处理详细报告，记录每个DEX的每处修改。
 */
data class PatchReport(
    val startTimeMs: Long = System.currentTimeMillis(),
    val dexReports: MutableList<DexReport> = mutableListOf(),
    var manifestResult: ManifestReport? = null,
    var assetCleanCount: Int = 0,
    var errorMessage: String? = null,
    var endTimeMs: Long = 0L,
    var vendorHits: List<VendorHit> = emptyList(),
    var selectedVendors: List<AdVendor> = emptyList(),
    var originalApkSize: Long = 0L,
    var finalApkSize: Long = 0L,
    var fullProcessTimeMs: Long = 0L,
) {
    val totalTimeMs: Long get() = endTimeMs - startTimeMs
    val totalPatchedClasses: Int get() = dexReports.sumOf { it.patchedClasses }
    val totalNeutralizedMethods: Int get() = dexReports.sumOf { it.neutralizedMethods }
    val totalNopLoadLibrary: Int get() = dexReports.sumOf { it.nopLoadLibrary }
    val totalForcedTrue: Int get() = dexReports.sumOf { it.forcedTrueMethods }
    val totalVpnNeutralized: Int get() = dexReports.sumOf { it.neutralizedVpnMethods }
    val totalEmulatorNeutralized: Int get() = dexReports.sumOf { it.neutralizedEmulatorMethods }
    val totalFalseState: Int get() = dexReports.sumOf { it.falseStateNeutralized }
     val totalSplashShortened: Int get() = dexReports.sumOf { it.splashCountdownShortened }
     val totalSignatureNeutralized: Int get() = dexReports.sumOf { it.signatureChecksNeutralized }

    fun toMarkdown(): String = buildString {
        appendLine("# 🔧 APK 去广告处理报告")
        appendLine()
        appendLine("- 处理时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(startTimeMs))}")
        appendLine("- 处理耗时: ${totalTimeMs}ms (${"%.1f".format(totalTimeMs / 1000.0)}秒，DEX修补+assets+Manifest清理)")
        if (fullProcessTimeMs > 0) {
            appendLine("- 全程耗时: ${fullProcessTimeMs}ms (${"%.1f".format(fullProcessTimeMs / 1000.0)}秒，读取→解包→修补→打包签名)")
        }
        appendLine("- 原始大小: ${formatSize(originalApkSize)} → 处理后: ${formatSize(finalApkSize)}")
        appendLine()

        if (vendorHits.isNotEmpty()) {
            appendLine("## 识别到的广告SDK厂商")
            appendLine()
            appendLine("| 厂商 | 命中信号 | 处理 |")
            appendLine("|------|----------|------|")
            val selected = selectedVendors.map { it.id }.toSet()
            for (hit in vendorHits) {
                val processed = if (hit.vendor.id in selected) "✅ 处理" else "⏭️ 跳过"
                appendLine("| ${hit.vendor.name.esc()} | ${hit.matchedSignals.joinToString(", ").esc()} | $processed |")
            }
            appendLine()
        }

        appendLine("## 汇总统计")
        appendLine()
        appendLine("| 项目 | 数量 |")
        appendLine("|------|------|")
        appendLine("| 广告类置空 | $totalPatchedClasses |")
        appendLine("| 广告方法置空 | $totalNeutralizedMethods |")
        appendLine("| 强制返回true | $totalForcedTrue |")
        appendLine("| 去除VPN检测 | $totalVpnNeutralized |")
        appendLine("| 去除虚拟机检测 | $totalEmulatorNeutralized |")
appendLine("| 广告状态false | $totalFalseState |")
         appendLine("| 缩短开屏倒计时 | $totalSplashShortened |")
         appendLine("| 去除签名校验 | $totalSignatureNeutralized |")
         appendLine("| NOP so加载 | $totalNopLoadLibrary |")
        appendLine("| 清理assets | $assetCleanCount |")
        if (manifestResult != null) {
            appendLine("| 清理权限 | ${manifestResult!!.removedPermissions.size} |")
        }
        appendLine()

        for (dex in dexReports) {
            appendLine("## 📦 ${dex.dexName} (${formatSize(dex.dexSize)})")
            appendLine()
            if (dex.skipped) {
                appendLine("> ⏭️ 已跳过：预检未发现任何匹配特征（未加载、未写回）")
                appendLine()
            }
            appendLine("| 统计 | 数量 |")
            appendLine("|------|------|")
            appendLine("| 处理状态 | ${if (dex.skipped) "⏭️ 跳过" else "✅ 已处理"} |")
            appendLine("| 修改类数 | ${dex.patchedClasses} |")
            appendLine("| 置空方法 | ${dex.neutralizedMethods} |")
            appendLine("| 强制true | ${dex.forcedTrueMethods} |")
            appendLine("| NOP so加载 | ${dex.nopLoadLibrary} |")
            appendLine("| 去除VPN | ${dex.neutralizedVpnMethods} |")
appendLine("| 去除模拟器 | ${dex.neutralizedEmulatorMethods} |")
             appendLine("| 缩短开屏倒计时 | ${dex.splashCountdownShortened} |")
             appendLine("| 去除签名校验 | ${dex.signatureChecksNeutralized} |")
             appendLine("| 处理耗时 | ${dex.elapsedMs}ms |")
            dex.error?.let { err ->
                appendLine("| 错误 | ${err.esc()} |")
            }
            appendLine()

            if (dex.modifiedClasses.isNotEmpty()) {
                appendLine("**修改明细** (共 ${dex.modifiedClasses.size} 个类):")
                appendLine()
                for (mc in dex.modifiedClasses.take(100)) {
                    appendLine("- `**${mc.className.esc()}**`: ${mc.modifiedMethods.joinToString(", ").esc()}")
                }
                if (dex.modifiedClasses.size > 100) {
                    appendLine("- ... 还有 ${dex.modifiedClasses.size - 100} 个类")
                }
                appendLine()
            }
        }

        if (errorMessage != null) {
            appendLine("## ⚠️ 错误")
            appendLine()
            appendLine(errorMessage)
        }
    }

    companion object {
        fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
        }

        /** Markdown 表格单元格转义：防竖线/换行破坏表格结构 */
        private fun String.esc(): String = this.replace("|", "\\|").replace("\r", " ").replace("\n", " ")
    }
}

data class DexReport(
    val dexName: String,
    val dexSize: Long,
    /** 预检未发现匹配特征，整个 DEX 被跳过（未加载、未写回） */
    var skipped: Boolean = false,
    var patchedClasses: Int = 0,
    var neutralizedMethods: Int = 0,
    var nopLoadLibrary: Int = 0,
    var forcedTrueMethods: Int = 0,
    var neutralizedVpnMethods: Int = 0,
    var neutralizedEmulatorMethods: Int = 0,
var falseStateNeutralized: Int = 0,
     var splashCountdownShortened: Int = 0,
     var signatureChecksNeutralized: Int = 0,
     val modifiedClasses: MutableList<ClassModification> = mutableListOf(),
    var elapsedMs: Long = 0L,
    var error: String? = null,
)

data class ClassModification(
    val className: String,
    val modifiedMethods: MutableList<String> = mutableListOf(),
)

data class ManifestReport(
    val removedPermissions: List<String> = emptyList(),
    val removedComponents: List<String> = emptyList(),
)
