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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ads.purge.core.ApkProcessor
import com.ads.purge.core.AppConfig
import com.ads.purge.core.AxmlEditor
import com.ads.purge.core.PermissionInfo
import com.ads.purge.core.ScreenKeeper
import com.ads.purge.core.Signer
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

   
         
  
      
                                            
                                  
                       
   
class PermissionManagerActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_PICK_APK = 2001
    }

    private lateinit var tvStatus: TextView
    private lateinit var rvPermissions: RecyclerView
    private lateinit var btnPickApk: MaterialButton
    private lateinit var btnSelectAll: MaterialButton
    private lateinit var btnClearAll: MaterialButton
    private lateinit var btnApply: MaterialButton
    private lateinit var logView: TextView
    private lateinit var scrollView: ScrollView

    private val apkProcessor = ApkProcessor()

                                                
    private val logBuffer = StringBuilder()
    private var logFlushPending = false
                 
    private var lastScrollTime = 0L

                       
    private val permissionItems = mutableListOf<PermissionInfo.PermissionItem>()
    private val selected = mutableSetOf<String>()
    private lateinit var adapter: PermissionAdapter

                                
    private var sourceUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_manager)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        tvStatus = findViewById(R.id.tvStatus)
        rvPermissions = findViewById(R.id.rvPermissions)
        btnPickApk = findViewById(R.id.btnPickApk)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnClearAll = findViewById(R.id.btnClearAll)
        btnApply = findViewById(R.id.btnApply)
        logView = findViewById(R.id.logView)
        scrollView = findViewById(R.id.scrollView)

        logView.movementMethod = ScrollingMovementMethod.getInstance()
        logView.setTextColor(AppConfig.getTextColor(this))

        adapter = PermissionAdapter(permissionItems, selected, ::onSelectionChanged)
        rvPermissions.layoutManager = LinearLayoutManager(this)
        rvPermissions.adapter = adapter

        btnPickApk.setOnClickListener { checkPermissionsAndPick() }
        btnSelectAll.setOnClickListener {
            selected.clear()
            selected.addAll(permissionItems.map { it.name })
            adapter.notifyDataSetChanged()
            updateApplyButton()
        }
        btnClearAll.setOnClickListener {
            selected.clear()
            adapter.notifyDataSetChanged()
            updateApplyButton()
        }
        btnApply.setOnClickListener {
            if (sourceUri != null) processApk(sourceUri!!)
        }

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
                requestPermissions(permissions, 2002)
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
            analyzeApk(data.data!!)
        }
    }

       
                         
       
    private fun analyzeApk(uri: Uri) {
        sourceUri = uri
        tvStatus.text = "正在解析 APK 权限 ..."
        btnPickApk.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val displayName = queryDisplayName(uri) ?: "selected.apk"
            try {
                
                val workDir = File(cacheDir, "perm_work_${System.currentTimeMillis()}")
                workDir.mkdirs()
                val sourceApk = File(workDir, "source.apk")
                contentResolver.openInputStream(uri)?.use { input ->
                    sourceApk.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("无法读取所选文件")

                val permissions = readPermissionsFromApk(sourceApk)

                withContext(Dispatchers.Main) {
                    permissionItems.clear()
                    permissions.forEach { permissionItems.add(PermissionInfo.resolve(it)) }
                    adapter.notifyDataSetChanged()
                    selected.clear()
                    updateApplyButton()

                    if (permissionItems.isEmpty()) {
                        tvStatus.text = "APK: $displayName\n未发现任何权限声明。"
                    } else {
                        tvStatus.text = "APK: $displayName\n共发现 ${permissionItems.size} 个权限，勾选要去除的权限："
                    }
                    btnPickApk.isEnabled = true
                    
                    try { workDir.deleteRecursively() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "解析失败: ${e.message}"
                    btnPickApk.isEnabled = true
                    Toast.makeText(this@PermissionManagerActivity, "解析 APK 失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

       
                                                    
       
    private fun readPermissionsFromApk(apkFile: File): List<String> {
        return try {
            java.util.zip.ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml") ?: return emptyList()
                zip.getInputStream(entry).use { it.readBytes() }
            }.let { bytes ->
                if (AxmlEditor.isAxml(bytes)) AxmlEditor.listPermissions(bytes) else emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun onSelectionChanged() {
        updateApplyButton()
    }

    private fun updateApplyButton() {
        btnApply.isEnabled = selected.isNotEmpty() && sourceUri != null
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

    private fun logStepTime(stepName: String, startTime: Long) {
        val elapsed = System.currentTimeMillis() - startTime
        log("  ⏱ $stepName 耗时: ${elapsed}ms")
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
        }
    }

       
                       
       
    private fun processApk(uri: Uri) {
        val displayName = queryDisplayName(uri) ?: "output"
        val baseName = displayName.substringBeforeLast('.').ifBlank { "output" }
        val fileName = "${baseName}_noperm.apk"
        val permsToRemove = selected.toList()

        AlertDialog.Builder(this)
            .setTitle("确认处理")
            .setMessage("将去除以下 ${permsToRemove.size} 个权限：\n\n" +
                permsToRemove.joinToString("\n") { "• ${it.substringAfterLast('.')}" } +
                "\n\n注意：去除权限后应用对应功能可能失效。继续？")
            .setPositiveButton("开始处理") { _, _ ->
                ScreenKeeper.keepOn(this)
                btnPickApk.isEnabled = false
                btnApply.isEnabled = false
                tvStatus.text = "正在处理，请稍候 ..."
                logView.text = ""
                log("━━━ 开始去除权限 ━━━")
                log("目标: ${permsToRemove.size} 个权限")
                permsToRemove.forEach { log("  ✗ $it") }

                lifecycleScope.launch(Dispatchers.IO) {
                    var workDir: File? = null
                    try {
                        val totalStartTime = System.currentTimeMillis()
                        workDir = File(cacheDir, "perm_work_${System.currentTimeMillis()}")
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

                        log("步骤 3/4: 修改 AndroidManifest.xml ...")
                        val manifestFile = File(extractDir, "AndroidManifest.xml")
                        if (!manifestFile.exists()) throw IllegalStateException("未找到 AndroidManifest.xml")
                        val manifestBytes = manifestFile.readBytes()
                        val manifestSizeBefore = manifestBytes.size
                        val newManifest = AxmlEditor.removePermissions(manifestBytes, permsToRemove.toSet())
                        if (newManifest !== manifestBytes) {
                            manifestFile.writeBytes(newManifest)
                            log("  ✓ 已移除 ${permsToRemove.size} 个权限节点 (${manifestSizeBefore} -> ${newManifest.size} 字节)")
                        } else {
                            log("  [警告] 未检测到权限节点变化")
                        }
                        logStepTime("修改清单", totalStartTime)

                        log("步骤 4/4: 打包并签名 ...")
                        val unsignedApk = File(workDir, "unsigned.apk")
                        apkProcessor.buildApk(extractDir, unsignedApk) { msg -> log(msg) }
                        log("  ✓ 打包完成: ${formatSize(unsignedApk.length())}")

                        log("  正在签名 (v1+v2) ...")
                        val signedApk = File(workDir, "signed.apk")
                        Signer.signApk(this@PermissionManagerActivity, unsignedApk, signedApk)
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
                            val exportDir = File(AppConfig.getExportDir(this@PermissionManagerActivity))
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
                            showResultDialog(
                                "处理完成",
                                "已去除 ${permsToRemove.size} 个权限。\n导出路径:\n$exportDesc"
                            )
                        }
                    } catch (e: Exception) {
                        log("━━━ 处理失败 ━━━")
                        log("错误: ${e.message}")
                        withContext(Dispatchers.Main) {
                            tvStatus.text = "处理失败: ${e.message}"
                            Toast.makeText(this@PermissionManagerActivity, "处理失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        ScreenKeeper.release(this@PermissionManagerActivity)
                        workDir?.let { d -> try { d.deleteRecursively() } catch (_: Exception) {} }
                        withContext(Dispatchers.Main) {
                            btnPickApk.isEnabled = true
                            updateApplyButton()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
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

   
           
   
class PermissionAdapter(
    private val items: MutableList<PermissionInfo.PermissionItem>,
    private val selected: MutableSet<String>,
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<PermissionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cb: CheckBox = view.findViewById(R.id.cbPermission)
        val tvName: TextView = view.findViewById(R.id.tvPermissionName)
        val tvDesc: TextView = view.findViewById(R.id.tvPermissionDesc)
        val tvDanger: TextView = view.findViewById(R.id.tvDanger)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_permission, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        holder.tvDesc.text = "${item.label} · ${item.group}"
        holder.tvDanger.visibility = if (item.dangerous) View.VISIBLE else View.GONE
        holder.cb.isChecked = item.name in selected

        holder.cb.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selected.add(item.name) else selected.remove(item.name)
            onChanged()
        }
        holder.itemView.setOnClickListener {
            holder.cb.isChecked = !holder.cb.isChecked
        }
    }

    override fun getItemCount(): Int = items.size
}
