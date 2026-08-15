package com.ads.purge

import android.app.Activity
import android.content.Intent
import android.graphics.Color
 import android.graphics.drawable.GradientDrawable
 import android.net.Uri
 import android.os.Bundle
 import android.view.View
 import android.widget.EditText
 import android.widget.LinearLayout
 import android.widget.TextView
 import android.widget.Toast
 import androidx.appcompat.app.AppCompatActivity
 import androidx.appcompat.widget.SwitchCompat
 import androidx.appcompat.widget.Toolbar
 import com.google.android.material.button.MaterialButton
 import com.google.android.material.textfield.TextInputEditText
 import com.ads.purge.core.AppConfig
import com.ads.purge.core.Signer
import java.io.File

    
           
    
        
                  
               
     
 class AppSettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_PICK_KEYSTORE = 201
    }

    private lateinit var etExportDir: TextInputEditText
    private lateinit var tvColorPreview: TextView
    private lateinit var llColorOptions: LinearLayout
    private lateinit var tvKeystoreInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

etExportDir = findViewById(R.id.etExportDir)
         tvColorPreview = findViewById(R.id.tvColorPreview)
         llColorOptions = findViewById(R.id.llColorOptions)

         // ★ v3.1 处理选项开关绑定（即时生效，无需保存按钮）
         bindSwitch(R.id.swParallel, { AppConfig.isParallelEnabled(this) }, { AppConfig.setParallelEnabled(this, it) })
         bindSwitch(R.id.swDropDebugInfo, { AppConfig.isDropDebugInfo(this) }, { AppConfig.setDropDebugInfo(this, it) })
         bindSwitch(R.id.swSplashShorten, { AppConfig.isSplashShorten(this) }, { AppConfig.setSplashShorten(this, it) })
         bindSwitch(R.id.swNotify, { AppConfig.isNotifyEnabled(this) }, { AppConfig.setNotifyEnabled(this, it) })
         bindSwitch(R.id.swSignatureDefault, { AppConfig.isSignatureRemove(this) }, { AppConfig.setSignatureRemove(this, it) })
         bindSwitch(R.id.swReport, { AppConfig.isReportEnabled(this) }, { AppConfig.setReportEnabled(this, it) })

        
        val textColor = AppConfig.getTextColor(this)
        findViewById<TextView>(R.id.tvPathTitle).setTextColor(textColor)
        findViewById<TextView>(R.id.tvPathDesc).setTextColor(textColor)
        findViewById<TextView>(R.id.tvDirHint).setTextColor(textColor)
        findViewById<TextView>(R.id.tvColorTitle).setTextColor(textColor)
        findViewById<TextView>(R.id.tvColorDesc).setTextColor(textColor)
        etExportDir.setTextColor(textColor)

        
        etExportDir.setText(AppConfig.getExportDir(this))

        val currentColor = AppConfig.getTextColor(this)
        applyColorPreview(currentColor)
        buildColorOptions(currentColor)

        findViewById<MaterialButton>(R.id.btnSavePath).setOnClickListener {
            val path = etExportDir.text?.toString()?.trim()
            if (path.isNullOrEmpty()) {
                Toast.makeText(this, "保存路径不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppConfig.setExportDir(this, path)
            Toast.makeText(this, "保存路径已更新", Toast.LENGTH_SHORT).show()
        }

        // ★ v3.1.1 签名密钥管理
        tvKeystoreInfo = findViewById(R.id.tvKeystoreInfo)
        loadKeystoreInfo()

        findViewById<MaterialButton>(R.id.btnExportKeystore).setOnClickListener {
            exportKeystore()
        }
        findViewById<MaterialButton>(R.id.btnImportKeystore).setOnClickListener {
            pickKeystoreFile()
        }
    }

    /** ★ v3.1.1 后台读取密钥信息（首次生成 RSA 密钥可能耗时数秒） */
    private fun loadKeystoreInfo() {
        tvKeystoreInfo.text = "正在读取密钥信息…"
        Thread {
            val info = Signer.getKeystoreInfo(this@AppSettingsActivity)
            runOnUiThread { tvKeystoreInfo.text = info }
        }.start()
    }

    /** ★ v3.1.1 导出 p12 + 密码说明到导出目录 */
    private fun exportKeystore() {
        Thread {
            val destDir = File(AppConfig.getExportDir(this@AppSettingsActivity))
            val (ok, msg) = Signer.exportKeystore(this@AppSettingsActivity, destDir)
            runOnUiThread {
                if (ok) {
                    Toast.makeText(this, "已导出: $msg", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "导出失败: $msg", Toast.LENGTH_LONG).show()
                }
                loadKeystoreInfo()
            }
        }.start()
    }

    /** ★ v3.1.1 SAF 选择待导入的密钥库文件（p12 / .jks / .keystore） */
    private fun pickKeystoreFile() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            }
            startActivityForResult(intent, REQUEST_PICK_KEYSTORE)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开文件选择器: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** ★ v3.1.1 导入密钥库：自动识别 p12/JKS，先尝试自动导入（旁密码文件/本机密码），失败按格式弹对应密码框 */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_KEYSTORE || resultCode != Activity.RESULT_OK || data == null) return
        val uri: Uri = data.data ?: return
        Thread {
            val src = File(cacheDir, "import_keystore.tmp")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    src.outputStream().use { out -> input.copyTo(out) }
                } ?: return@Thread
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "读取文件失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
                return@Thread
            }

            val format = Signer.detectKeystoreFormat(src) // "JKS" / "PKCS12"
            val firstTry = Signer.importKeystore(this@AppSettingsActivity, src)
            if (firstTry.first) {
                src.delete()
                runOnUiThread {
                    Toast.makeText(this, "导入成功（$format），后续签名将使用该密钥", Toast.LENGTH_LONG).show()
                    loadKeystoreInfo()
                }
                return@Thread
            }

            // 自动导入失败（多为跨设备密钥密码不匹配）→ 按格式弹对应密码框
            runOnUiThread {
                if (format == "JKS") showJksPasswordDialog(src) else showPasswordDialog(src)
            }
        }.start()
    }

    private fun showPasswordDialog(src: File) {
        val input = EditText(this).apply {
            hint = "粘贴导出时保存的密码"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("导入签名密钥 (PKCS12)")
            .setMessage("自动导入失败（可能是跨设备密钥，密码与本机不同）。\n请粘贴导出密钥时保存的密码（见 AdPurge-signing-password.txt）：")
            .setView(input)
            .setPositiveButton("导入") { _, _ ->
                val pwd = input.text.toString().trim()
                if (pwd.isEmpty()) {
                    Toast.makeText(this, "密码不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Thread {
                    val result = Signer.importKeystore(this@AppSettingsActivity, src, pwd)
                    src.delete()
                    runOnUiThread {
                        if (result.first) {
                            Toast.makeText(this, "导入成功，后续签名将使用该密钥", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "导入失败: ${result.second}", Toast.LENGTH_LONG).show()
                        }
                        loadKeystoreInfo()
                    }
                }.start()
            }
            .setNegativeButton("取消") { _, _ -> src.delete() }
            .show()
    }

    /** ★ v3.1.1 JKS/.keystore 导入密码框：库密码与条目密码分开（Android Studio 生成的默认两者相同） */
    private fun showJksPasswordDialog(src: File) {
        val storeInput = EditText(this).apply {
            hint = "库密码（KeyStore password）"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        val keyInput = EditText(this).apply {
            hint = "条目密码（Key password，通常与库密码相同）"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(storeInput)
            addView(keyInput, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("导入签名密钥 (JKS)")
            .setMessage("JKS/.keystore 文件需要两个密码：\n• 库密码：打开密钥库文件\n• 条目密码：读取签名私钥\nAndroid Studio 生成的默认两者相同，填相同密码即可。")
            .setView(container)
            .setPositiveButton("导入") { _, _ ->
                val storePwd = storeInput.text.toString().trim()
                val keyPwd = keyInput.text.toString().trim().ifEmpty { storePwd }
                if (storePwd.isEmpty()) {
                    Toast.makeText(this, "库密码不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Thread {
                    val result = Signer.importKeystore(this@AppSettingsActivity, src, storePwd, keyPwd)
                    src.delete()
                    runOnUiThread {
                        if (result.first) {
                            Toast.makeText(this, "导入成功，后续签名将使用该密钥", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "导入失败: ${result.second}", Toast.LENGTH_LONG).show()
                        }
                        loadKeystoreInfo()
                    }
                }.start()
            }
            .setNegativeButton("取消") { _, _ -> src.delete() }
            .show()
    }

       
                  
       
    private fun buildColorOptions(selected: Int) {
        llColorOptions.removeAllViews()
        AppConfig.TEXT_COLOR_OPTIONS.forEach { (_, color) ->
            val dot = buildColorDot(color, color == selected) {
                AppConfig.setTextColor(this@AppSettingsActivity, color)
                applyColorPreview(color)
                buildColorOptions(color)
                Toast.makeText(this@AppSettingsActivity, "字体颜色已更新", Toast.LENGTH_SHORT).show()
            }
            llColorOptions.addView(dot)
        }
    }

       
                
       
    private fun buildColorDot(color: Int, isSelected: Boolean, onClick: () -> Unit): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(56, 56).apply {
                marginEnd = dp(16)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                if (isSelected) {
                    setStroke(dp(4), Color.WHITE)
                }
            }
            setOnClickListener { onClick() }
        }
    }

       
                 
       
private fun applyColorPreview(color: Int) {
         tvColorPreview.setTextColor(color)
     }

     /** 开关绑定辅助：读当前值并设置监听（改动即时保存） */
     private fun bindSwitch(viewId: Int, current: () -> Boolean, onChange: (Boolean) -> Unit) {
         val sw = findViewById<SwitchCompat>(viewId)
         sw.isChecked = current()
         sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
     }

     private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
