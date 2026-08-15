package com.ads.purge

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.view.Window
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.ads.purge.core.ApkProcessor
import com.ads.purge.core.AppConfig
import com.ads.purge.core.DexPatcher
import com.ads.purge.core.ScreenKeeper
import com.ads.purge.core.Signer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

   
            
  
                                         
                                    
  
                              
                          
                                 
   
class DetectionRemoverActivity : AppCompatActivity() {

    companion object {
        const val MODE_VPN = "vpn"
        const val MODE_EMULATOR = "emulator"
        private const val REQUEST_CODE_PICK_APK = 4001
    }

    private lateinit var tvStatus: TextView
    private lateinit var btnPickApk: MaterialButton
    private lateinit var logView: TextView
    private lateinit var scrollView: ScrollView

    private val apkProcessor = ApkProcessor()

                                    
    private val logBuffer = StringBuilder()
    private var logFlushPending = false
    private var lastScrollTime = 0L

    private var mode = MODE_VPN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detection_remover)

        mode = intent.getStringExtra("mode") ?: MODE_VPN

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        
        if (mode == MODE_EMULATOR) {
            toolbar.title = "去除虚拟机检测"
            toolbar.subtitle = "DEX 修补去除模拟器/虚拟机检测"
            findViewById<TextView>(R.id.tvStatus).text =
                "选择一个 APK，通过直接修补 DEX 去除其中的虚拟机 / 模拟器检测方法（isEmulator / isVirtualDevice 等），让检测恒返回\"非模拟器\"。"
        } else {
            toolbar.title = "去除VPN检测"
            toolbar.subtitle = "DEX 修补去除 VPN 环境检测"
            findViewById<TextView>(R.id.tvStatus).text =
                "选择一个 APK，通过直接修补 DEX 去除其中的 VPN 检测方法（isVpn / isVpnConnected 等），让检测恒返回\"未检测到 VPN\"。"
        }

        tvStatus = findViewById(R.id.tvStatus)
        btnPickApk = findViewById(R.id.btnPickApk)
        logView = findViewById(R.id.logView)
        scrollView = findViewById(R.id.scrollView)

        logView.movementMethod = ScrollingMovementMethod.getInstance()
        logView.setTextColor(AppConfig.getTextColor(this))

        btnPickApk.setOnClickListener { checkPermissionsAndPick() }

        checkPermissions()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                }
            }
        } else {
            val permissions = arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            if (permissions.any {
                ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }) {
                requestPermissions(permissions, 2003)
            }
        }
    }

    private fun checkPermissionsAndPick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Toast.makeText(this, "请先授予\"所有文件访问\"权限", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
            }
            return
        }
        pickApkFile()
    }

    private fun pickApkFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/vnd.android.package-archive"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "选择APK文件"), REQUEST_CODE_PICK_APK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_APK && resultCode == Activity.RESULT_OK && data?.data != null) {
            processApk(data.data!!)
        }
    }

       
                                                           
       
    private fun processApk(uri: Uri) {
        val displayName = queryDisplayName(uri) ?: "output"
        val baseName = displayName.substringBeforeLast('.').ifBlank { "output" }
        val suffix = if (mode == MODE_EMULATOR) "_noemu.apk" else "_novpn.apk"
        val fileName = "$baseName$suffix"

        val targetName = if (mode == MODE_EMULATOR) "虚拟机检测" else "VPN 检测"

        ScreenKeeper.keepOn(this)
        btnPickApk.isEnabled = false
        tvStatus.text = "正在处理，请稍候 ..."
        logView.text = ""
        log("━━━ 开始去除 $targetName ━━━")

        lifecycleScope.launch(Dispatchers.IO) {
            var workDir: File? = null
            try {
                val totalStartTime = System.currentTimeMillis()
                workDir = File(cacheDir, "detect_work_${System.currentTimeMillis()}")
                workDir.mkdirs()

                
                log("步骤 1/4: 读取 APK 文件 ...")
                val sourceApk = File(workDir, "source.apk")
                contentResolver.openInputStream(uri)?.use { input ->
                    sourceApk.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("无法读取所选文件")
                log("  ✓ APK 已读取: ${sourceApk.name} (${formatSize(sourceApk.length())})")

                
                log("步骤 2/4: 解包 APK ...")
                val extractDir = File(workDir, "extracted")
                extractDir.mkdirs()
                apkProcessor.extractApk(sourceApk, extractDir)
                val totalFiles = extractDir.walkTopDown().filter { it.isFile }.count()
                log("  ✓ 解包完成: $totalFiles 个文件")

                
                log("步骤 3/4: DEX 修补去除 $targetName ...")
                val dexFiles = extractDir.listFiles { f -> f.isFile && f.name.endsWith(".dex") } ?: emptyArray()
                if (dexFiles.isEmpty()) {
                    log("  未找到 DEX 文件")
                } else {
                    log("  找到 ${dexFiles.size} 个 DEX 文件: ${dexFiles.joinToString { it.name }}")
                    var totalVpn = 0
                    var totalEmulator = 0
                    for (dexFile in dexFiles.sortedBy { it.name }) {
                        log("▶ 正在处理: ${dexFile.name} (${formatSize(dexFile.length())})")
                        try {
                            val result = DexPatcher.patchDex(
                                dexFile,
                                adPatterns = emptyList(),
                                adMethodNames = emptyList(),
                                vpnDetectKeywords = if (mode == MODE_VPN) DexPatcher.VPN_DETECT_KEYWORDS else emptyList(),
                                emulatorDetectKeywords = if (mode == MODE_EMULATOR) DexPatcher.EMULATOR_DETECT_KEYWORDS else emptyList(),
                                logger = { msg -> log(msg) }
                            )
                            totalVpn += result.neutralizedVpnMethods
                            totalEmulator += result.neutralizedEmulatorMethods
                            log("  ✓ ${dexFile.name} 完成: VPN检测=${result.neutralizedVpnMethods}, 虚拟机检测=${result.neutralizedEmulatorMethods}")
                        } catch (e: OutOfMemoryError) {
                            log("  ✗ ${dexFile.name} 内存不足: ${e.message}")
                        } catch (e: Exception) {
                            log("  ✗ ${dexFile.name} 修补失败: ${e.message}")
                        } finally {
                            System.gc()
                        }
                    }
                    log("  去除 VPN 检测方法: $totalVpn 个")
                    log("  去除虚拟机检测方法: $totalEmulator 个")
                }

                
                log("步骤 4/4: 打包并签名 APK ...")
                val unsignedApk = File(workDir, "unsigned.apk")
                apkProcessor.buildApk(extractDir, unsignedApk) { msg -> log(msg) }
                log("  ✓ 打包完成: ${formatSize(unsignedApk.length())}")

                log("  正在签名 (v1+v2) ...")
                val signedApk = File(workDir, "signed.apk")
                Signer.signApk(this@DetectionRemoverActivity, unsignedApk, signedApk)
                log("  ✓ 签名完成: ${formatSize(signedApk.length())}")

                
                val exportedViaSaf = try {
                    val resultUri = createOutputInSelectedDir(uri, fileName)
                    if (resultUri != null) {
                        contentResolver.openOutputStream(resultUri)?.use { out ->
                            signedApk.inputStream().use { it.copyTo(out) }
                        }
                        true
                    } else false
                } catch (_: Exception) {
                    false
                }

                val exportDesc: String
                if (exportedViaSaf) {
                    exportDesc = docUriToReadablePath(uri, fileName)
                } else {
                    val exportDir = File(AppConfig.getExportDir(this@DetectionRemoverActivity))
                    if (!exportDir.exists()) exportDir.mkdirs()
                    val exportFile = File(exportDir, fileName)
                    signedApk.copyTo(exportFile, overwrite = true)
                    exportDesc = exportFile.absolutePath
                }
                log("  ✓ 已导出: $exportDesc")

                val totalTime = System.currentTimeMillis() - totalStartTime
                log("━━━ 处理完成! 总耗时 ${totalTime}ms ━━━")

                withContext(Dispatchers.Main) {
                    tvStatus.text = "处理完成！\n已导出: $exportDesc"
                    showResultDialog("处理完成", "已去除 $targetName。\n导出路径:\n$exportDesc")
                }
            } catch (e: OutOfMemoryError) {
                log("━━━ 处理失败: 内存不足 ━━━")
                log("建议: 该APK可能过大，请尝试关闭其他应用后重试")
                System.gc()
                withContext(Dispatchers.Main) {
                    tvStatus.text = "处理失败: 内存不足"
                    Toast.makeText(this@DetectionRemoverActivity, "内存不足，处理失败", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                log("━━━ 处理失败 ━━━")
                log("错误: ${e.message}")
                withContext(Dispatchers.Main) {
                    tvStatus.text = "处理失败: ${e.message}"
                    Toast.makeText(this@DetectionRemoverActivity, "处理失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                ScreenKeeper.release(this@DetectionRemoverActivity)
                workDir?.let { d -> try { d.deleteRecursively() } catch (_: Exception) {} }
                withContext(Dispatchers.Main) {
                    btnPickApk.isEnabled = true
                }
            }
        }
    }

       
                              
                                      
       
    private fun showResultDialog(title: String, message: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogView = layoutInflater.inflate(R.layout.dialog_result, null)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_card_tech)
        dialogView.findViewById<TextView>(R.id.dialogTitle).text = title
        dialogView.findViewById<TextView>(R.id.dialogMessage).text = message
        dialogView.findViewById<View>(R.id.btnOk).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

                                      
    private fun log(message: String) {
        synchronized(logBuffer) {
            logBuffer.append(message).append('\n')
            if (!logFlushPending) {
                logFlushPending = true
                runOnUiThread { flushLog() }
            }
        }
    }

                                  
    private fun flushLog() {
        val chunk: String
        synchronized(logBuffer) {
            chunk = logBuffer.toString()
            logBuffer.setLength(0)
            logFlushPending = false
        }
        logView.append(chunk)
        val now = SystemClock.uptimeMillis()
        if (now - lastScrollTime >= 200L) {
            lastScrollTime = now
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun createOutputInSelectedDir(uri: Uri, fileName: String): Uri? {
        return try {
            if (!DocumentsContract.isDocumentUri(this, uri)) return null
            val docId = DocumentsContract.getDocumentId(uri)
            val slash = docId.lastIndexOf('/')
            if (slash <= 0) return null
            val parentDocId = docId.substring(0, slash)
            val parentUri = DocumentsContract.buildDocumentUri(uri.authority, parentDocId)
            DocumentsContract.createDocument(
                contentResolver,
                parentUri,
                "application/vnd.android.package-archive",
                fileName
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun docUriToReadablePath(uri: Uri, fileName: String): String {
        return try {
            if (DocumentsContract.isDocumentUri(this, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val slash = docId.lastIndexOf('/')
                if (slash > 0) {
                    val parentDocId = docId.substring(0, slash)
                    if (parentDocId.startsWith("primary:")) {
                        val dir = parentDocId.substringAfter(':')
                        val base = if (dir.isEmpty()) "/storage/emulated/0" else "/storage/emulated/0/$dir"
                        return "$base/$fileName"
                    }
                }
            }
            uri.toString()
        } catch (_: Exception) {
            uri.toString()
        }
    }
}
