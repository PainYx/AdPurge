package com.ads.purge.core

import java.io.File
import java.nio.charset.Charset

/**
 * 签名校验特征扫描器（对齐二改作者实测稳定版）。
 * 在 DEX 字节流中按关键词扫描签名校验特征，处理前提示用户：
 * 本工具处理后重新签名，若 APK 自带签名校验会导致处理后闪退，
 * 避免用户误认为是去广告修补本身的问题。
 */
object SignatureDetector {

    val SIGNATURE_KEYWORDS = listOf(
        "get_signatures",
        "getsignatures",
        "checksignature",
        "checkappsignature",
        "verifysignature",
        "signaturevalid",
        "appsignature",
        "signaturedigest",
        "signingcertificate",
        "signinginfo",
        "getpackagesignature",
        "packagesignature",
        "signature.tobytearray",
        "getsigningcertificates",
        "signaturecheck",
        "signatureverify"
    )

    /**
     * 扫描解包目录全部 .dex 文件，返回命中的关键词列表。
     * 无命中返回空列表。
     */
    fun scan(extractDir: File): List<String> {
        val dexFiles = extractDir.listFiles { f ->
            f.isFile && f.name.endsWith(".dex")
        } ?: emptyArray()

        val dexTexts = dexFiles.mapNotNull { dex ->
            runCatching {
                String(dex.readBytes(), Charset.forName("ISO-8859-1")).lowercase()
            }.getOrNull()
        }
        if (dexTexts.isEmpty()) return emptyList()

        val matched = mutableListOf<String>()
        for (keyword in SIGNATURE_KEYWORDS) {
            if (dexTexts.any { it.contains(keyword) }) {
                matched.add(keyword)
            }
        }
        return matched
    }
}
