package com.ads.purge.core

import android.content.Context
import com.android.apksig.ApkSigner
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

   
                                                      
  
         
                                                                              
                                                          
                                 
  
                         
                                            
                                                     
                                                          
                                                 
                                              
                       
  
                                                                          
object Signer {

    // ★ 专用随机密钥（不再使用 AOSP platform 测试密钥）
    private const val KEYSTORE_NAME = "apk_editor_keystore.p12"
    private const val KEYSTORE_JKS_NAME = "apk_editor_keystore.jks"
    private const val KEY_ALIAS = "apkeditor"
    private const val KEY_SIZE = 2048
    private const val PREFS_NAME = "signer_prefs"
    private const val KEY_PWD = "keystore_password"
    private const val KEY_ALIAS_PREF = "keystore_alias"
    private const val KEY_FORMAT_PREF = "keystore_format" // "p12" / "jks"
    private const val KEY_KEY_PWD = "keystore_key_password"
    private const val EXPORT_NAME = "AdPurge-signing.p12"
    private const val EXPORT_JKS_NAME = "AdPurge-signing.jks"
    private const val EXPORT_PWD_NAME = "AdPurge-signing-password.txt"

    private val bcProvider: BouncyCastleProvider = BouncyCastleProvider()

    init {
        if (Security.getProvider(bcProvider.name) == null) {
            Security.addProvider(bcProvider)
        }
    }

    /**
     * 获取/生成 keystore 密码（首次生成随机密码并持久化）。
     * 密码保存于应用私有 prefs，导出/导入时随 p12 一起迁移。
     */
    private fun getPassword(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var pwd = prefs.getString(KEY_PWD, null)
        if (pwd == null) {
            pwd = java.util.UUID.randomUUID().toString().replace("-", "") + "Aa1!"
            prefs.edit().putString(KEY_PWD, pwd).apply()
        }
        return pwd
    }

    /** 检测密钥库格式：JKS 魔数 FEEDFEED，否则视为 PKCS12 */
    fun detectKeystoreFormat(file: File): String {
        return try {
            val head = ByteArray(4)
            file.inputStream().use { it.read(head) }
            if (head.size >= 4 && head[0] == 0xFE.toByte() && head[1] == 0xED.toByte()
                && head[2] == 0xFE.toByte() && head[3] == 0xED.toByte()
            ) "JKS" else "PKCS12"
        } catch (_: Exception) {
            "PKCS12"
        }
    }

    /** 当前生效的密钥别名（导入 JKS 后沿用其原条目别名） */
    fun getActiveAlias(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ALIAS_PREF, KEY_ALIAS) ?: KEY_ALIAS

    /** 当前生效的密钥库格式（"p12" / "jks"） */
    fun getActiveFormat(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FORMAT_PREF, "p12") ?: "p12"

    /** 导出签名证书（p12/jks + 密码说明）到指定目录，便于备份/换设备迁移 */
    fun exportKeystore(context: Context, destDir: File): Pair<Boolean, String> {
        return try {
            val format = getActiveFormat(context)
            val src = File(context.filesDir, if (format == "jks") KEYSTORE_JKS_NAME else KEYSTORE_NAME)
            if (!src.exists()) loadKeyAndCert(context) // 首次使用先创建
            destDir.mkdirs()
            val destName = if (format == "jks") EXPORT_JKS_NAME else EXPORT_NAME
            val dest = File(destDir, destName)
            src.copyTo(dest, overwrite = true)
            // 密码说明文件：条目密码与库密码不同时写两行，便于跨设备导入时完整还原
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val storePwd = getPassword(context)
            val keyPwd = prefs.getString(KEY_KEY_PWD, null)?.takeIf { it.isNotBlank() && it != storePwd }
            val pwdText = if (keyPwd != null) "库密码: $storePwd\n条目密码: $keyPwd\n" else storePwd
            File(destDir, EXPORT_PWD_NAME).writeText(pwdText)
            Pair(true, dest.absolutePath)
        } catch (e: Exception) {
            Pair(false, e.message ?: "导出失败")
        }
    }

    /** 查询当前签名密钥信息（别名 + 证书 SHA256 指纹），用于设置页展示 */
    fun getKeystoreInfo(context: Context): String {
        return try {
            val (_, cert) = loadKeyAndCert(context)
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(cert.encoded)
            val hex = digest.take(16).joinToString("") { "%02X".format(it) }
            val format = getActiveFormat(context)
            "别名: ${getActiveAlias(context)} | 格式: ${format.uppercase()} | 证书指纹: $hex…"
        } catch (e: Exception) {
            "签名密钥未初始化: ${e.message}"
        }
    }

    /**
     * 导入签名密钥库：自动识别 JKS/.keystore（FEEDFEED 魔数）与 PKCS12。
     * password = 库密码；keyPassword = 条目密码（JKS 支持与库密码不同，p12 可省略）。
     * 密码优先级：显式传入 > 旁边密码说明文件 > 现有 prefs。
     * 先在源文件上完整验证（可加载 + 能读出私钥与证书），成功后才落盘并持久化，避免导入坏文件导致后续签名全挂。
     */
    fun importKeystore(context: Context, srcFile: File, password: String? = null, keyPassword: String? = null): Pair<Boolean, String> {
        return try {
            if (!srcFile.exists()) return Pair(false, "文件不存在")
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val format = detectKeystoreFormat(srcFile) // "JKS" / "PKCS12"
            val type = if (format == "JKS") "JKS" else "PKCS12"

            // 库密码优先级：显式传入 > 旁边密码说明文件 > 现有 prefs
            // 密码说明文件格式：单行 = 库密码；两行 = "库密码: xxx\n条目密码: yyy"
            var storePwd: String
            var fileKeyPwd: String? = null
            if (!password.isNullOrBlank()) {
                storePwd = password.trim()
            } else {
                val pwdFile = File(srcFile.parentFile, EXPORT_PWD_NAME)
                val fromFile = if (pwdFile.exists()) pwdFile.readText().trim() else ""
                if (fromFile.isNotBlank()) {
                    val lines = fromFile.lines()
                    val storeLine = lines.firstOrNull { it.startsWith("库密码:") }?.substringAfter("库密码:")?.trim()
                    val keyLine = lines.firstOrNull { it.startsWith("条目密码:") }?.substringAfter("条目密码:")?.trim()
                    if (storeLine.isNullOrBlank()) {
                        storePwd = fromFile // 旧版单行格式
                    } else {
                        storePwd = storeLine
                        fileKeyPwd = keyLine
                    }
                } else {
                    storePwd = getPassword(context)
                }
            }
            // 条目密码：显式传入 > 密码说明文件条目行 > 沿用库密码
            val keyPwd = when {
                !keyPassword.isNullOrBlank() -> keyPassword.trim()
                !fileKeyPwd.isNullOrBlank() -> fileKeyPwd
                else -> storePwd
            }

            // ── 先在源文件上验证：可加载 + 能取出私钥与证书 ──
            val srcKs = KeyStore.getInstance(type)
            srcFile.inputStream().use { srcKs.load(it, storePwd.toCharArray()) }
            val aliases = srcKs.aliases()
            val alias = if (aliases.hasMoreElements()) aliases.nextElement() else null
                ?: return Pair(false, "密钥库中没有任何条目")
            srcKs.getKey(alias, keyPwd.toCharArray())
                ?: return Pair(false, "条目密码错误或密钥不可读取")
            (srcKs.getCertificate(alias) as? X509Certificate)
                ?: return Pair(false, "条目中缺少证书")

            // ── 验证通过才落盘 + 持久化 ──
            val dest = File(context.filesDir, if (format == "JKS") KEYSTORE_JKS_NAME else KEYSTORE_NAME)
            srcFile.copyTo(dest, overwrite = true)
            prefs.edit()
                .putString(KEY_PWD, storePwd)
                .putString(KEY_KEY_PWD, keyPwd)
                .putString(KEY_ALIAS_PREF, alias)
                .putString(KEY_FORMAT_PREF, format.lowercase())
                .apply()
            Pair(true, dest.absolutePath)
        } catch (e: Exception) {
            Pair(false, e.message ?: "导入失败")
        }
    }

    fun signApk(context: Context, inputApk: File, outputApk: File) {
        
        val (privateKey, certificate) = loadKeyAndCert(context)

        if (outputApk.exists()) outputApk.delete()
        outputApk.parentFile?.mkdirs()

        val signerConfig = ApkSigner.SignerConfig.Builder(
            getActiveAlias(context),
            privateKey,
            listOf(certificate)
        ).build()

        val apkSigner = ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(inputApk)
            .setOutputApk(outputApk)
            .setMinSdkVersion(21)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()

        apkSigner.sign()
    }

    /** 加载（或首次生成）签名密钥对：仅使用应用私有 keystore，不再内置 AOSP 测试密钥 */
    private fun loadKeyAndCert(context: Context): Pair<PrivateKey, X509Certificate> {
        return loadOrCreateKeyAndCert(context)
    }

    private fun loadOrCreateKeyAndCert(context: Context): Pair<PrivateKey, X509Certificate> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var format = prefs.getString(KEY_FORMAT_PREF, "p12") ?: "p12"

        // 密钥库文件丢失时回退到默认 p12 自签名密钥，保证签名流程永远可用
        var keystoreFile = File(context.filesDir, if (format == "jks") KEYSTORE_JKS_NAME else KEYSTORE_NAME)
        if (!keystoreFile.exists() && format == "jks") {
            format = "p12"
            prefs.edit().putString(KEY_FORMAT_PREF, "p12").putString(KEY_ALIAS_PREF, KEY_ALIAS).apply()
            keystoreFile = File(context.filesDir, KEYSTORE_NAME)
        }

        val keyStore = KeyStore.getInstance(if (format == "jks") "JKS" else "PKCS12")
        val pwd = getPassword(context)
        val alias = getActiveAlias(context)
        val keyPwd = prefs.getString(KEY_KEY_PWD, null) ?: pwd

        if (!keystoreFile.exists()) {
            createKeystore(keystoreFile, keyStore, pwd, alias)
            prefs.edit().remove(KEY_KEY_PWD).apply() // 新生成密钥：条目密码 = 库密码
        } else {
            keystoreFile.inputStream().use { fis ->
                keyStore.load(fis, pwd.toCharArray())
            }
            
            if (!keyStore.containsAlias(alias)) {
                createKeystore(keystoreFile, keyStore, pwd, alias)
                prefs.edit().remove(KEY_KEY_PWD).apply()
            }
        }

        val privateKey = keyStore.getKey(alias, keyPwd.toCharArray()) as PrivateKey
        val certificate = keyStore.getCertificate(alias) as X509Certificate
        return Pair(privateKey, certificate)
    }

    private fun createKeystore(keystoreFile: File, keyStore: KeyStore, pwd: String, alias: String = KEY_ALIAS) {
        keyStore.load(null, null)

        val keyPair = generateKeyPair()
        val certificate = generateSelfSignedCertificate(keyPair)

        keyStore.setKeyEntry(
            alias,
            keyPair.private,
            pwd.toCharArray(),
            arrayOf(certificate)
        )

        keystoreFile.parentFile?.mkdirs()
        keystoreFile.outputStream().use { fos ->
            keyStore.store(fos, pwd.toCharArray())
        }
    }

    private fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(KEY_SIZE)
        return keyPairGenerator.generateKeyPair()
    }

       
                                               
       
    private fun generateSelfSignedCertificate(keyPair: KeyPair): X509Certificate {
        val subject = X500Name("CN=APKEditor, O=APKEditor, C=CN")
        val notBefore = Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000)
        
        val notAfter = Date(System.currentTimeMillis() + 3650L * 24 * 60 * 60 * 1000)
        val serial = BigInteger.valueOf(System.currentTimeMillis())

        val certBuilder = JcaX509v3CertificateBuilder(
            subject,            
            serial,
            notBefore,
            notAfter,
            subject,            
            keyPair.public
        )

        val contentSigner = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(bcProvider)
            .build(keyPair.private)

        val certHolder: X509CertificateHolder = certBuilder.build(contentSigner)

        return JcaX509CertificateConverter()
            .setProvider(bcProvider)
            .getCertificate(certHolder)
    }
}
