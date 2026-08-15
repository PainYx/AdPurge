package com.ads.purge

import android.Manifest
 import android.app.Activity
 import android.app.Dialog
 import android.app.NotificationChannel
 import android.app.NotificationManager
 import android.app.PendingIntent
 import android.content.ClipData
 import android.content.ClipboardManager
 import android.content.Context
 import android.content.Intent
 import android.content.pm.PackageManager
 import android.net.Uri
 import android.os.Build
 import android.os.Bundle
 import android.os.Environment
 import android.os.SystemClock
 import android.provider.DocumentsContract
 import android.provider.OpenableColumns
 import android.provider.Settings
 import android.text.method.ScrollingMovementMethod
 import android.view.Menu
 import android.view.MenuItem
 import android.view.View
 import android.view.Window
 import android.view.Gravity
 import android.widget.LinearLayout
 import android.widget.ScrollView
 import android.widget.TextView
 import android.widget.Toast
 import androidx.appcompat.app.AppCompatActivity
 import androidx.appcompat.widget.SwitchCompat
 import androidx.appcompat.widget.Toolbar
 import androidx.core.app.ActivityCompat
 import androidx.core.app.NotificationCompat
 import androidx.core.app.NotificationManagerCompat
 import androidx.core.content.ContextCompat
 import androidx.lifecycle.lifecycleScope
import com.ads.purge.core.AdPatternConfig
import com.ads.purge.core.AdPatternConfig.AdPatterns
import com.ads.purge.core.AdVendor
import com.ads.purge.core.AdVendorCatalog
import com.ads.purge.core.ApkProcessor
import com.ads.purge.core.AppConfig
import com.ads.purge.core.CheckpointManager
import com.ads.purge.core.PatchReport
import com.ads.purge.core.PipelineStage
import com.ads.purge.core.SignatureDetector
import com.ads.purge.core.Signer
import com.ads.purge.core.UpdateChecker
import com.ads.purge.core.VendorHit
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_PICK_APK = 1001
        private const val REQUEST_CODE_PERMISSIONS = 1002
                            
        private const val SCROLL_INTERVAL_MS = 200L
        private const val LOG_FLUSH_INTERVAL_MS = 100L  // 日志合并窗口
                                                          
        private const val MAX_LOG_CHARS = 200_000
    }

    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var logView: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var cardRemoveAds: View
    private lateinit var cardRemoveVpn: View
    private lateinit var cardRemoveEmulator: View
    private lateinit var cardPermission: View
    private lateinit var cardManifest: View
    private lateinit var cardSettings: View
    private lateinit var cardAppSettings: View
    private lateinit var cardAbout: View

    private val apkProcessor = ApkProcessor()

                                                
    private val logBuffer = StringBuilder()
    private var logFlushPending = false
                                                         
    private var lastScrollTime = 0L

                                                              
private var removeVpnDetection = true
     private var removeEmulatorDetection = true
     private var cleanManifest = true
     private var cleanAssets = true
     // ★ v3.1 一键杀签开关（选项对话框可改；设置页改默认值）
     private var removeSignatureChecks = true

override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         setContentView(R.layout.activity_main)
         // ★ v3.1 处理选项从设置读取
         removeSignatureChecks = AppConfig.isSignatureRemove(this)
         createNotificationChannel()

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        supportActionBar?.setTitle("")
        toolbar.title = ""

        progressBar = findViewById(R.id.progressBar)
        logView = findViewById(R.id.logView)
        scrollView = findViewById(R.id.scrollView)
        cardRemoveAds = findViewById(R.id.cardRemoveAds)
        cardRemoveVpn = findViewById(R.id.cardRemoveVpn)
        cardRemoveEmulator = findViewById(R.id.cardRemoveEmulator)
        cardPermission = findViewById(R.id.cardPermission)
        cardManifest = findViewById(R.id.cardManifest)
        cardSettings = findViewById(R.id.cardSettings)
        cardAppSettings = findViewById(R.id.cardAppSettings)
        cardAbout = findViewById(R.id.cardAbout)

        logView.movementMethod = ScrollingMovementMethod.getInstance()
        val textColor = AppConfig.getTextColor(this)
        logView.setTextColor(textColor)
        
        findViewById<TextView>(R.id.tvBrandTitle).setTextColor(textColor)

        findViewById<TextView>(R.id.btnCopyLog).setOnClickListener { copyLog() }

        initFeatureCards()

        
        cardRemoveAds.setOnClickListener {
            showRemoveAdsOptions()
        }

        
        cardRemoveVpn.setOnClickListener {
            startActivity(Intent(this, DetectionRemoverActivity::class.java).putExtra("mode", DetectionRemoverActivity.MODE_VPN))
        }

        
        cardRemoveEmulator.setOnClickListener {
            startActivity(Intent(this, DetectionRemoverActivity::class.java).putExtra("mode", DetectionRemoverActivity.MODE_EMULATOR))
        }

        
        cardPermission.setOnClickListener {
            startActivity(Intent(this, PermissionManagerActivity::class.java))
        }

        
        cardManifest.setOnClickListener {
            startActivity(Intent(this, ManifestCleanerActivity::class.java))
        }

        
        cardSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        
        cardAppSettings.setOnClickListener {
            startActivity(Intent(this, AppSettingsActivity::class.java))
        }

        
        cardAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        checkPermissions()
    }

       
                          
       
    private fun initFeatureCards() {
        bindFeatureCard(
            cardRemoveAds,
            icon = android.R.drawable.ic_menu_edit,
            title = "一键去广告",
            desc = "DEX直接修补\nManifest与SDK清理"
        )
        bindFeatureCard(
            cardRemoveVpn,
            icon = android.R.drawable.ic_menu_close_clear_cancel,
            title = "去除VPN检测",
            desc = "DEX修补去除\nVPN环境检测"
        )
        bindFeatureCard(
            cardRemoveEmulator,
            icon = android.R.drawable.ic_menu_myplaces,
            title = "去除虚拟机检测",
            desc = "DEX修补去除\n模拟器检测"
        )
        bindFeatureCard(
            cardPermission,
            icon = android.R.drawable.ic_menu_manage,
            title = "权限管理",
            desc = "查看并去除\n不需要的权限"
        )
        bindFeatureCard(
            cardManifest,
            icon = android.R.drawable.ic_menu_view,
            title = "Manifest 清理",
            desc = "手动或自动清除\n广告组件与权限"
        )
        bindFeatureCard(
            cardSettings,
            icon = android.R.drawable.ic_menu_preferences,
            title = "广告特征配置",
            desc = "自定义广告\nSDK/类/URL特征"
        )
        bindFeatureCard(
            cardAppSettings,
            icon = android.R.drawable.ic_menu_manage,
            title = "应用设置",
            desc = "保存路径\n字体与标题颜色"
        )
        bindFeatureCard(
            cardAbout,
            icon = android.R.drawable.ic_menu_info_details,
            title = "关于",
            desc = "版本信息\n更新检测"
        )
    }

    private fun bindFeatureCard(card: View, icon: Int, title: String, desc: String) {
        try {
            card.findViewById<android.widget.ImageView>(R.id.ivFeatureIcon).setImageResource(icon)
            card.findViewById<android.widget.TextView>(R.id.tvFeatureTitle).text = title
            card.findViewById<android.widget.TextView>(R.id.tvFeatureDesc).text = desc
        } catch (_: Exception) {
        }
    }

       
                      
                                               
                                                      
       
    private fun log(message: String) {
        synchronized(logBuffer) {
            logBuffer.append(message).append('\n')
            
            if (logBuffer.length > MAX_LOG_CHARS) {
                val cut = logBuffer.indexOf("\n", logBuffer.length / 2)
                if (cut >= 0) logBuffer.delete(0, cut + 1)
            }
            
            if (!logFlushPending) {
                logFlushPending = true
                // 用 postDelayed 合并高频日志，减少主线程压力
                logView.postDelayed({ flushLog() }, LOG_FLUSH_INTERVAL_MS)
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
        if (chunk.isEmpty()) return
        logView.append(chunk)
        val now = SystemClock.uptimeMillis()
        if (now - lastScrollTime >= SCROLL_INTERVAL_MS) {
            lastScrollTime = now
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    /** 复制日志面板全部内容到剪贴板（先冲刷缓冲，确保不丢最后一批日志） */
    private fun copyLog() {
        flushLog()
        val text = logView.text?.toString() ?: ""
        if (text.isEmpty()) {
            Toast.makeText(this, "暂无日志可复制", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("APK去广告处理日志", text))
            Toast.makeText(this, "已复制 ${text.length} 字符日志", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "复制失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showProgress(show: Boolean) {
        runOnUiThread {
            progressBar.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

private fun checkPermissions() {
         // ★ v3.1 完成通知权限（Android 13+）
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
             ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
             PackageManager.PERMISSION_GRANTED
         ) {
             ActivityCompat.requestPermissions(
                 this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_PERMISSIONS + 1
             )
         }
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                }
            }
        } else {
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            if (permissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_PERMISSIONS)
            }
        }
    }

       
                                     
                                    
       
    private fun showRemoveAdsOptions() {
        try {
            val dialog = Dialog(this)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            val dialogView = layoutInflater.inflate(R.layout.dialog_remove_ads_options, null)
            dialog.setContentView(dialogView)
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_card_tech)
val swVpn = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swVpn)
             val swEmulator = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swEmulator)
             val swManifest = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swManifest)
             val swAssets = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swAssets)
             val swSignature = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swSignature)
             swVpn.isChecked = removeVpnDetection
             swEmulator.isChecked = removeEmulatorDetection
             swManifest.isChecked = cleanManifest
             swAssets.isChecked = cleanAssets
             swSignature.isChecked = removeSignatureChecks

             dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
             dialogView.findViewById<View>(R.id.btnStart).setOnClickListener {
                 removeVpnDetection = swVpn.isChecked
                 removeEmulatorDetection = swEmulator.isChecked
                 cleanManifest = swManifest.isChecked
                 cleanAssets = swAssets.isChecked
                 removeSignatureChecks = swSignature.isChecked
                 dialog.dismiss()
                 checkPermissionsAndPick()
             }
            dialog.show()
        } catch (_: Exception) {
            checkPermissionsAndPick()
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

       
                                    
                                      
      
                                      
                                                 
       
    /** ★ v3.1 断点续传恢复询问对话框 */
     private fun showResumeDialog(onResult: (Boolean) -> Unit) {
         try {
             val dialog = Dialog(this)
             dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
             val container = LinearLayout(this).apply {
                 orientation = LinearLayout.VERTICAL
                 setBackgroundColor(0xFF1B1F26.toInt())
             }
             val density = resources.displayMetrics.density
             fun dp(v: Int): Int = (v * density).toInt()

             val title = TextView(this).apply {
                 text = "🔄 发现未完成的任务"
                 textSize = 17f
                 setTextColor(0xFFEBC16C.toInt())
                 setPadding(dp(20), dp(22), dp(20), dp(6))
             }
             val message = TextView(this).apply {
                 text = "上次处理未完成（可能因失败或中断）。\n\n" +
                     "• 断点恢复：复用已解包的中间产物，从上次阶段继续\n" +
                     "• 放弃重来：删除中间产物，重新完整处理"
                 textSize = 13.5f
                 setTextColor(0xFFE2E2E9.toInt())
                 setLineSpacing(0f, 1.25f)
                 setPadding(dp(20), dp(4), dp(20), dp(10))
             }
             val btnRow = LinearLayout(this).apply {
                 orientation = LinearLayout.HORIZONTAL
                 setPadding(dp(20), dp(6), dp(20), dp(20))
             }
             val btnDiscard = TextView(this).apply {
                 text = "放弃重来"
                 textSize = 14f
                 setTextColor(0xFFC4C6CF.toInt())
                 gravity = Gravity.CENTER
                 setPadding(dp(10), dp(10), dp(10), dp(10))
                 background = android.graphics.drawable.GradientDrawable().apply {
                     cornerRadius = dp(18).toFloat()
                     setColor(0xFF232830.toInt())
                 }
                 setOnClickListener {
                     dialog.dismiss()
                     onResult(false)
                 }
             }
             val btnResume = TextView(this).apply {
                 text = "断点恢复"
                 textSize = 14f
                 setTextColor(0xFF162E52.toInt())
                 gravity = Gravity.CENTER
                 setPadding(dp(10), dp(10), dp(10), dp(10))
                 background = android.graphics.drawable.GradientDrawable().apply {
                     cornerRadius = dp(18).toFloat()
                     setColor(0xFFAFC6FF.toInt())
                 }
                 setOnClickListener {
                     dialog.dismiss()
                     onResult(true)
                 }
             }
             btnRow.addView(btnDiscard, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
             btnRow.addView(
                 TextView(this).apply { layoutParams = LinearLayout.LayoutParams(dp(14), 1) },
             )
             btnRow.addView(btnResume, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

             container.addView(title)
             container.addView(message)
             container.addView(btnRow)
             dialog.setContentView(container)
             dialog.window?.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                 cornerRadius = dp(28).toFloat()
                 setColor(0xFF1B1F26.toInt())
             })
             val dm = resources.displayMetrics
             dialog.window?.setLayout((dm.widthPixels * 0.88).toInt(), android.view.WindowManager.LayoutParams.WRAP_CONTENT)
             dialog.setCancelable(false)
             dialog.show()
         } catch (_: Exception) {
             // 对话框异常时默认放弃重来，走完整流程
             onResult(false)
         }
     }

     /** ★ v3.1 通知渠道（Android 8+） */
     private fun createNotificationChannel() {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             val channel = NotificationChannel(
                 "adpurge_done",
                 "处理完成通知",
                 NotificationManager.IMPORTANCE_DEFAULT
             ).apply {
                 description = "APK 去广告处理完成或失败时通知"
             }
             val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
             manager.createNotificationChannel(channel)
         }
     }

     /** ★ v3.1 完成通知（任意线程可调用） */
private fun sendFinishNotification(title: String, text: String) {
          try {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                  ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                  PackageManager.PERMISSION_GRANTED
              ) {
                  return
              }
              val contentIntent = PendingIntent.getActivity(
                  this, 0,
                  Intent(this, MainActivity::class.java).apply {
                      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                  },
                  PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
              )
              val notification = NotificationCompat.Builder(this, "adpurge_done")
                  .setSmallIcon(android.R.drawable.ic_dialog_info)
                  .setContentTitle(title)
                  .setContentText(text)
                  .setAutoCancel(true)
                  .setContentIntent(contentIntent)
                  .build()
              NotificationManagerCompat.from(this).notify(1001, notification)
          } catch (_: Exception) {
          }
      }

      /** ★ v3.1.1 启动处理保命服务（前台服务，切后台防杀） */
      private fun startProcessingService() {
          try {
              val intent = Intent(this, ProcessingService::class.java)
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                  startForegroundService(intent)
              } else {
                  startService(intent)
              }
          } catch (_: Exception) {
              // 启动失败不阻塞处理流程（断点续传兜底）
          }
      }

      /** ★ v3.1.1 停止处理保命服务（处理结束/失败后调用） */
      private fun stopProcessingService() {
          try {
              stopService(Intent(this, ProcessingService::class.java))
          } catch (_: Exception) {
          }
      }

     private fun showVendorSelectDialog(hits: List<VendorHit>, onResult: (List<AdVendor>) -> Unit) {
        try {
            val dialog = Dialog(this)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_vendor_select)
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_card_tech)

            // 自适应屏幕尺寸：宽度取92%，高度取85%（确保小屏手机按钮可见）
            val dm = resources.displayMetrics
            val maxWidth = (dm.widthPixels * 0.92).toInt()
            val maxHeight = (dm.heightPixels * 0.85).toInt()
            dialog.window?.setLayout(maxWidth, maxHeight)

            val density = resources.displayMetrics.density
            fun dp(v: Int): Int = (v * density).toInt()

            val container = dialog.findViewById<LinearLayout>(R.id.vendorContainer)
            val selected = HashMap<String, Boolean>()
            val trackColor = android.content.res.ColorStateList.valueOf(0xFFAFC6FF.toInt())

            for (hit in hits) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(2), dp(5), dp(2), dp(5))
                }
                val sw = SwitchCompat(this).apply {
                    isChecked = true
                    trackTintList = trackColor
                    thumbTintList = android.content.res.ColorStateList.valueOf(0xFF162E52.toInt())
                }
                selected[hit.vendor.id] = true
                sw.setOnCheckedChangeListener { _, checked -> selected[hit.vendor.id] = checked }

                val tv = TextView(this).apply {
                    text = "${hit.vendor.name}  (${hit.matchedSignals.size} 处命中)"
                    textSize = 14f
                    setTextColor(0xFFE2E2E9.toInt())
                    setPadding(dp(10), 0, 0, 0)
                }
                row.addView(sw)
                row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                container.addView(row)
            }

            dialog.findViewById<View>(R.id.btnVendorStart).setOnClickListener {
                val chosen = hits.map { it.vendor }.filter { selected[it.id] == true }
                dialog.dismiss()
                onResult(chosen)
            }
            dialog.setCancelable(false)
            dialog.show()
        } catch (_: Exception) {
            
            onResult(hits.map { it.vendor })
        }
    }

private fun showSignatureWarningDialog(onResult: (Int) -> Unit) {
         try {
             val dialog = Dialog(this)
             dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
             val container = LinearLayout(this).apply {
                 orientation = LinearLayout.VERTICAL
                 setBackgroundColor(0xFF1B1F26.toInt())
             }
             val density = resources.displayMetrics.density
             fun dp(v: Int): Int = (v * density).toInt()

             val title = TextView(this).apply {
                 text = "⚠️ 检测到签名校验"
                 textSize = 17f
                 setTextColor(0xFFEBC16C.toInt())
                 setPadding(dp(20), dp(22), dp(20), dp(6))
             }
             val message = TextView(this).apply {
                 text = "该 APK 可能包含签名校验逻辑。本工具处理后会重新签名，" +
                     "应用启动时若校验到签名不一致，将无法正常运行（闪退）。\n\n" +
                     "本工具支持一键去除签名校验（把校验方法改为恒通过），建议先去除再处理。\n\n" +
                     "提示：本检测基于特征扫描，可能误检测，也可能存在更隐秘的签名校验未被发现，处理后请实机验证。"
                 textSize = 13.5f
                 setTextColor(0xFFE2E2E9.toInt())
                 setLineSpacing(0f, 1.25f)
                 setPadding(dp(20), dp(4), dp(20), dp(10))
             }
             val btnRow = LinearLayout(this).apply {
                 orientation = LinearLayout.HORIZONTAL
                 setPadding(dp(20), dp(6), dp(20), dp(20))
             }
             val btnCancel = TextView(this).apply {
                 text = "取消"
                 textSize = 14f
                 setTextColor(0xFFC4C6CF.toInt())
                 gravity = Gravity.CENTER
                 setPadding(dp(10), dp(10), dp(10), dp(10))
                 background = android.graphics.drawable.GradientDrawable().apply {
                     cornerRadius = dp(18).toFloat()
                     setColor(0xFF232830.toInt())
                 }
                 setOnClickListener {
                     dialog.dismiss()
                     onResult(0)
                 }
             }
             val btnProceed = TextView(this).apply {
                 text = "仍要处理"
                 textSize = 14f
                 setTextColor(0xFFFFFFFF.toInt())
                 gravity = Gravity.CENTER
                 setPadding(dp(10), dp(10), dp(10), dp(10))
                 background = android.graphics.drawable.GradientDrawable().apply {
                     cornerRadius = dp(18).toFloat()
                     setColor(0xFF344A71.toInt())
                 }
                 setOnClickListener {
                     dialog.dismiss()
                     onResult(1)
                 }
             }
             val btnNeutralize = TextView(this).apply {
                 text = "去签并处理"
                 textSize = 14f
                 setTextColor(0xFF162E52.toInt())
                 gravity = Gravity.CENTER
                 setPadding(dp(10), dp(10), dp(10), dp(10))
                 background = android.graphics.drawable.GradientDrawable().apply {
                     cornerRadius = dp(18).toFloat()
                     setColor(0xFFAFC6FF.toInt())
                 }
                 setOnClickListener {
                     dialog.dismiss()
                     onResult(2)
                 }
             }
             btnRow.addView(btnCancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
             btnRow.addView(
                 TextView(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) },
             )
             btnRow.addView(btnProceed, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
             btnRow.addView(
                 TextView(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) },
             )
             btnRow.addView(btnNeutralize, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

             container.addView(title)
             container.addView(message)
             container.addView(btnRow)
             dialog.setContentView(container)
             dialog.window?.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                 cornerRadius = dp(28).toFloat()
                 setColor(0xFF1B1F26.toInt())
             })
             // 自适应宽度
             val dm = resources.displayMetrics
             dialog.window?.setLayout((dm.widthPixels * 0.92).toInt(), android.view.WindowManager.LayoutParams.WRAP_CONTENT)
             dialog.setCancelable(false)
             dialog.show()
         } catch (_: Exception) {
             // 对话框异常时默认继续处理，不阻断流程
             onResult(1)
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
             // ★ v3.1 批量处理：允许多选
             putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
         }
         startActivityForResult(Intent.createChooser(intent, "选择APK文件（可多选批量处理）"), REQUEST_CODE_PICK_APK)
     }

     override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
         super.onActivityResult(requestCode, resultCode, data)
         if (resultCode == Activity.RESULT_OK && data != null) {
             when (requestCode) {
                 REQUEST_CODE_PICK_APK -> {
                     // ★ v3.1 批量处理：优先取多选列表，否则单文件
                     val uris = mutableListOf<Uri>()
                     val clip: ClipData? = data.clipData
                     if (clip != null && clip.itemCount > 1) {
                         for (i in 0 until clip.itemCount) {
                             clip.getItemAt(i).uri?.let { uris.add(it) }
                         }
                     } else {
                         data.data?.let { uris.add(it) }
                     }
                     if (uris.isNotEmpty()) processApks(uris)
                 }
             }
         }
     }

       
                                                 
      
            
                      
                        
                         
                        
                      
       
/** ★ v3.1 批量处理入口：多选 APK 逐个串行处理（每个 APK 完整走 解包→修补→打包） */
     private fun processApks(uris: List<Uri>) {
         val isBatch = uris.size > 1
         logView.text = ""
         showProgress(true)
         runOnUiThread { progressBar.progress = 0 }
         cardRemoveAds.isEnabled = false

         com.ads.purge.core.ScreenKeeper.keepOn(this)

         if (isBatch) {
             log("━━━ 开始批量处理 ${uris.size} 个 APK ━━━")
         }

lifecycleScope.launch(Dispatchers.IO) {
              // ★ v3.1.1 前台服务保命壳：处理期间切后台防杀
              startProcessingService()
              log("  [保活] 前台服务已启动：处理中切后台不会被系统杀进程")
              try {
              var successCount = 0
              var failCount = 0
              for ((idx, uri) in uris.withIndex()) {
                  if (isBatch) {
                      log("")
                      log("━━━ [${idx + 1}/${uris.size}] 处理: ${queryDisplayName(uri) ?: "未知文件"} ━━━")
                  } else {
                      log("━━━ 开始处理 APK ━━━")
                  }
                  val ok = processOneApk(uri, isBatch)
                  if (ok) successCount++ else failCount++
              }

              com.ads.purge.core.ScreenKeeper.release(this@MainActivity)

              withContext(Dispatchers.Main) {
                  showProgress(false)
                  progressBar.progress = 100
                  cardRemoveAds.isEnabled = true
                  if (isBatch) {
                      val summary = "批量处理完成\n成功: $successCount 个 / 失败: $failCount 个"
                      log(summary)
                      showResultDialog("批量处理完成", summary)
                  }
              }
              // ★ v3.1 完成通知
              if (AppConfig.isNotifyEnabled(this@MainActivity)) {
                  if (isBatch) {
                      sendFinishNotification(
                          "AdPurge 批量处理完成",
                          "成功 $successCount 个，失败 $failCount 个"
                      )
                  } else if (successCount > 0) {
                      sendFinishNotification("AdPurge 处理完成", "APK 去广告处理完成，已导出")
                  } else {
                      sendFinishNotification("AdPurge 处理失败", "点击查看失败原因")
                  }
              }
              } finally {
                  // ★ v3.1.1 无论成败都停掉保命服务（通知转为完成/失败通知）
                  stopProcessingService()
              }
          }
     }

     /** ★ v3.1 单个 APK 处理（含断点续传）：成功返回 true */
     private suspend fun processOneApk(uri: Uri, isBatch: Boolean): Boolean {
         val totalStartTime = System.currentTimeMillis()
         // 单文件模式使用固定工作目录以支持断点续传；批量模式每文件独立目录
         val workDir = if (isBatch) {
             File(cacheDir, "apk_work_b${System.currentTimeMillis()}")
         } else {
             File(cacheDir, "apk_work_resume")
         }

         try {
             // ── 断点续传检查（仅单文件模式）──
             var resumeFromStage: PipelineStage? = null
             var resumeExtra: Map<String, String> = emptyMap()
             if (!isBatch) {
                 val ck = CheckpointManager(workDir)
                 val (stage, extra) = ck.load()
                 if (stage != null && workDir.exists() && File(workDir, "extracted").exists()) {
                     val resumeDeferred = CompletableDeferred<Boolean>()
                     withContext(Dispatchers.Main) {
                         showResumeDialog { resumeDeferred.complete(it) }
                     }
                     if (resumeDeferred.await()) {
                         resumeFromStage = stage
                         resumeExtra = extra
                         log("  [断点续传] 从阶段 $stage 恢复上次未完成的任务")
                     } else {
                         log("  [断点续传] 用户放弃上次任务，重新开始")
                         workDir.deleteRecursively()
                     }
                 }
             }
             workDir.mkdirs()

             val sourceApk = File(workDir, "source.apk")
             val extractDir = File(workDir, "extracted")
             var originalApkSize = resumeExtra["originalApkSize"]?.toLongOrNull() ?: 0L
             var dexCount = resumeExtra["dexCount"]?.toIntOrNull() ?: 0

             if (resumeFromStage == null) {
                 val step1Start = System.currentTimeMillis()
                 log("步骤 1/4: 读取 APK 文件 ...")
                 contentResolver.openInputStream(uri)?.use { input ->
                     sourceApk.outputStream().use { output -> input.copyTo(output) }
                 } ?: throw IllegalStateException("无法读取所选文件")

                 originalApkSize = sourceApk.length()
                 log("  ✓ APK 已读取: ${sourceApk.name} (${formatSize(originalApkSize)})")
                 logStepTime("读取APK", step1Start)

                 val apkInfo = apkProcessor.getApkInfo(sourceApk)
                 log("  APK 信息: DEX=${apkInfo["dex_count"]}, 资源=${apkInfo["res_count"]}, 库=${apkInfo["lib_count"]}")

                 val step2Start = System.currentTimeMillis()
                 log("步骤 2/4: 解包 APK ...")
                 extractDir.mkdirs()
                 apkProcessor.extractApk(sourceApk, extractDir)

                 dexCount = extractDir.listFiles { f -> f.name.endsWith(".dex") }?.size ?: 0
                 val totalFiles = extractDir.walkTopDown().filter { it.isFile }.count()
                 log("  ✓ 解包完成: $totalFiles 个文件, $dexCount 个DEX")
                 logStepTime("解包", step2Start)

                 // ★ 断点存档：解包完成即存档（后续步骤失败可从解包直接恢复）
                 val ck = CheckpointManager(workDir)
                 ck.save(PipelineStage.EXTRACTED, mapOf(
                     "originalApkSize" to originalApkSize.toString(),
                     "dexCount" to dexCount.toString(),
                     "displayName" to (queryDisplayName(uri) ?: "output")
                 ))

                 // ★ 签名校验扫描 + 三态选择（取消 / 仍要处理 / 去签并处理）
                 val signatureHits = SignatureDetector.scan(extractDir)
                 if (signatureHits.isNotEmpty()) {
                     log("  [签名校验] 检测到可能的签名校验逻辑（${signatureHits.size} 处特征）:")
                     signatureHits.forEach { log("    • $it") }
                     val sigChoice = CompletableDeferred<Int>()
                     withContext(Dispatchers.Main) {
                         showSignatureWarningDialog { choice -> sigChoice.complete(choice) }
                     }
                     when (sigChoice.await()) {
                         0 -> {
                             log("  [签名校验] 用户选择取消处理")
                             log("━━━ 处理已取消 ━━━")
                             try {
                                 CheckpointManager(workDir).clear()
                                 workDir.deleteRecursively()
                             } catch (_: Exception) {
                             }
                             return false
                         }
                         2 -> {
                             removeSignatureChecks = true
                             log("  [杀签] 已开启一键去除签名校验（校验方法置恒通过）")
                         }
                         else -> log("  [签名校验] 用户选择继续处理（未去签，可能存在闪退风险）")
                     }
                 }
             } else {
                 log("  [断点续传] 复用已解包目录，跳过读取/解包步骤")
             }

                
// ── 厂商识别（断点恢复至 DEX_PATCHED 时跳过）──
             var vendorHits: List<VendorHit> = emptyList()
             var dexPrecheckHits: Map<String, Set<String>>? = null
             var selectedVendors: List<AdVendor> = emptyList()
             var overrideConfig: AdPatterns? = null
             if (resumeFromStage != PipelineStage.DEX_PATCHED) {
                 log("步骤 2.5: 识别广告SDK厂商 ...")
                 // ★ 单遍扫描：构建超集预检词（全厂商+全量配置+内置通用词），
                 // Aho-Corasick 一次流式扫描完成厂商识别 + 收集每DEX命中词，
                 // 修补阶段预检直接复用缓存（零IO），消除「按厂商逐个重复全量读」的旧瓶颈
                 val baseConfig = AdPatternConfig.loadConfig(this@MainActivity)
                 val superPrecheckKeywords = com.ads.purge.core.DexPatcher.buildPrecheckKeywords(
                     baseConfig.allAdPatterns(),
                     baseConfig.methodPatterns,
                     com.ads.purge.core.AdRemover.buildSdkLibKeywords(baseConfig.sdkPackages).toList(),
                     baseConfig.forceTrueMethodNames,
                     com.ads.purge.core.DexPatcher.VPN_DETECT_KEYWORDS,
                     com.ads.purge.core.DexPatcher.EMULATOR_DETECT_KEYWORDS,
                     AdVendorCatalog.collectFalseStateMethods(AdVendorCatalog.vendors),
                     emptySet() // 全厂商（超集）
                 )
                 val scanResult = AdVendorCatalog.scanVendors(extractDir, superPrecheckKeywords)
                 vendorHits = scanResult.hits
                 dexPrecheckHits = scanResult.dexHitKeywords
                 if (vendorHits.isEmpty()) {
                     log("  [识别] 未识别到已知广告SDK厂商，按全部特征处理")
                 } else {
                     log("  [识别] 检测到 ${vendorHits.size} 家广告SDK厂商:")
                     vendorHits.forEach {
                         log("    • ${it.vendor.name} <- ${it.matchedSignals.joinToString("、")}")
                     }
                 }

                 if (vendorHits.isNotEmpty()) {
                     if (isBatch) {
                         // ★ 批量模式：自动全选，不打断批处理流程
                         selectedVendors = vendorHits.map { it.vendor }
                         log("  [批量] 自动全选 ${selectedVendors.size} 家厂商")
                     } else {
                         val deferred = CompletableDeferred<List<AdVendor>>()
                         withContext(Dispatchers.Main) {
                             showVendorSelectDialog(vendorHits) { chosen -> deferred.complete(chosen) }
                         }
                         selectedVendors = deferred.await()
                     }
                     if (selectedVendors.isEmpty()) {
                         log("  [选择] 未选择任何广告厂商，全部保留（跳过厂商特征注入）")
                     } else {
                         log("  [选择] 已选择处理 ${selectedVendors.size} 家广告厂商:")
                         selectedVendors.forEach { log("    • ${it.name}") }
                     }
                 }

                 overrideConfig = if (selectedVendors.isNotEmpty()) {
                     val base = AdPatternConfig.loadConfig(this@MainActivity)
                     AdVendorCatalog.mergeInto(base, selectedVendors)
                 } else {
                     null
                 }
             } else {
                 log("  [断点续传] DEX 已修补完成，跳过识别与修补步骤")
             }

                
var patchReport: PatchReport? = null
             if (resumeFromStage != PipelineStage.DEX_PATCHED) {
                 val step3Start = System.currentTimeMillis()
                 log("步骤 3/4: 直接修补 DEX 去广告 ...")
                 log("  选项: VPN检测去除=${if (removeVpnDetection) "开启" else "关闭"}, 虚拟机检测去除=${if (removeEmulatorDetection) "开启" else "关闭"}, Manifest清理=${if (cleanManifest) "开启" else "关闭"}, assets清理=${if (cleanAssets) "开启" else "关闭"}, 杀签=${if (removeSignatureChecks) "开启" else "关闭"}")

                 try {
                     val (textResult, report) = com.ads.purge.core.AdRemover.removeAdsWithReport(
                         extractDir,
                         this@MainActivity,
                         logger = { msg -> log(msg) },
                         removeVpnDetection = removeVpnDetection,
                         removeEmulatorDetection = removeEmulatorDetection,
                         cleanManifest = cleanManifest,
                         cleanAssets = cleanAssets,
                         overrideConfig = overrideConfig,
                         selectedVendors = selectedVendors,
                         dexPrecheckHits = dexPrecheckHits,
                         // ★ v3.1 引擎新参数（设置页开关）
                         removeSignatureChecks = removeSignatureChecks,
                         enableSplashShorten = AppConfig.isSplashShorten(this@MainActivity),
                         dropDebugInfo = AppConfig.isDropDebugInfo(this@MainActivity),
                         parallelEnabled = AppConfig.isParallelEnabled(this@MainActivity),
                         // ★ 真实进度条：DEX 修补进度 0~1 → 0~100
                         progress = { frac ->
                             runOnUiThread {
                                 progressBar.progress = (frac * 100).toInt().coerceIn(0, 100)
                             }
                         }
                     )
                     patchReport = report
                     patchReport.vendorHits = vendorHits
                     patchReport.selectedVendors = selectedVendors
                     patchReport.originalApkSize = originalApkSize
                     log(textResult)

                     // 保存 checkpoint: DEX 修补 + Manifest + Assets 全部完成
                     val ck = CheckpointManager(workDir)
                     ck.save(PipelineStage.DEX_PATCHED, mapOf(
                         "dexCount" to dexCount.toString(),
                         "originalApkSize" to originalApkSize.toString(),
                         "displayName" to (queryDisplayName(uri) ?: "output"),
                         "patchedClasses" to patchReport.totalPatchedClasses.toString()
                     ))
                 } catch (e: OutOfMemoryError) {
                     log("  [严重] 内存不足: ${e.message}")
                     log("  建议: 减少同时处理的DEX大小或关闭其他应用后重试")
                     System.gc()
                 } catch (e: Exception) {
                     log("  去广告处理异常: ${e.message}")
                     log("  堆栈: ${e.stackTraceToString().take(200)}")
                 }
                 logStepTime("去广告处理", step3Start)
             }

                
                val step4Start = System.currentTimeMillis()
                log("步骤 4/4: 打包并签名 APK ...")
                val unsignedApk = File(workDir, "unsigned.apk")
                log("  正在打包 ...")
                apkProcessor.buildApk(extractDir, unsignedApk) { msg ->
                    log(msg)
                }
                val unsignedSize = unsignedApk.length()
                log("  ✓ 打包完成: ${formatSize(unsignedSize)}")

                log("  正在签名 (v1+v2+v3 兼容全部Android版本) ...")
                val tempSigned = File(workDir, "temp_signed.apk")
                Signer.signApk(this@MainActivity, unsignedApk, tempSigned)
                val signedSize = tempSigned.length()
                log("  ✓ 签名完成: ${formatSize(signedSize)}")
                logStepTime("打包签名", step4Start)
                patchReport?.let {
                    it.finalApkSize = signedSize
                    it.endTimeMs = System.currentTimeMillis()
                }

                
                log("  正在导出 ...")

                
                val displayName = resumeExtra["displayName"]?.takeIf { it.isNotBlank() }
                     ?: queryDisplayName(uri) ?: "output"
                val baseName = displayName.substringBeforeLast('.').ifBlank { "output" }
                val fileName = "${baseName}_noads.apk"

                // 保存 Markdown 报告到导出目录（v4.0 起可开关控制）
                val totalTime = System.currentTimeMillis() - totalStartTime
                if (patchReport != null && AppConfig.isReportEnabled(this@MainActivity)) {
                    try {
                        val reportDir = File(AppConfig.getExportDir(this@MainActivity))
                        if (!reportDir.exists()) reportDir.mkdirs()
                        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(java.util.Date())
                        val reportFile = File(reportDir, "${baseName}_${ts}_report.md")
                        patchReport.fullProcessTimeMs = totalTime
                        reportFile.writeText(patchReport.toMarkdown())
                        log("  📄 处理报告已保存: ${reportFile.absolutePath}")
                    } catch (_: Exception) {
                        log("  ⚠️ 报告保存失败")
                    }
                } else if (patchReport != null) {
                    log("  [已关闭] 生成处理报告开关已停用，跳过保存报告")
                }

                var finalSize: Long
                val exportDesc: String
                val exportedViaSaf = try {
                    
                    val resultUri = createOutputInSelectedDir(uri, fileName)
                    if (resultUri != null) {
                        contentResolver.openOutputStream(resultUri)?.use { out ->
                            tempSigned.inputStream().use { it.copyTo(out) }
                        }
                        true
                    } else {
                        false
                    }
                } catch (_: Exception) {
                    false
                }

                if (exportedViaSaf) {
                    
                    finalSize = tempSigned.length()
                    exportDesc = docUriToReadablePath(uri, fileName)
                } else {
                    
                    val exportDir = File(AppConfig.getExportDir(this@MainActivity))
                    if (!exportDir.exists()) exportDir.mkdirs()
                    val exportFile = File(exportDir, fileName)
                    tempSigned.copyTo(exportFile, overwrite = true)
                    finalSize = exportFile.length()
                    exportDesc = exportFile.absolutePath
                }

                val savedBytes = originalApkSize - finalSize

                log("  ✓ 已导出: $exportDesc")
                log("━━━ 处理完成! ━━━")
                log("导出路径: $exportDesc")
                log("原始大小: ${formatSize(originalApkSize)}")
                log("处理后大小: ${formatSize(finalSize)}")
                if (savedBytes > 0) {
                    log("节省空间: ${formatSize(savedBytes)}")
                }
                log("总耗时: ${totalTime}ms (${String.format("%.1f", totalTime / 1000.0)}秒)")

withContext(Dispatchers.Main) {
                     showProgress(false)
                     progressBar.progress = 100
                     if (!isBatch) {
                         cardRemoveAds.isEnabled = true
                         showResultDialog(
                             title = "处理完成",
                             message = "已导出到:\n$exportDesc\n\n" +
                                 "原始: ${formatSize(originalApkSize)}\n" +
                                 "处理后: ${formatSize(finalSize)}\n" +
                                 (if (savedBytes > 0) "节省: ${formatSize(savedBytes)}\n" else "") +
                                 "耗时: ${String.format("%.1f", totalTime / 1000.0)}秒"
                         )
                     }
                 }

                 // ★ 成功：清理 checkpoint 与工作目录（断点使命完成）
                 try {
                     CheckpointManager(workDir).clear()
                     workDir.deleteRecursively()
                 } catch (_: Exception) {
                 }
                 return true
             } catch (e: OutOfMemoryError) {
                 log("━━━ 处理失败: 内存不足 ━━━")
                 log("错误: ${e.message}")
                 log("建议: 该APK可能过大，请尝试关闭其他应用后重试")
                 System.gc()
                 failUi("内存不足，处理失败", isBatch)
                 return false
             } catch (e: StackOverflowError) {
                 log("━━━ 处理失败: 嵌套过深(StackOverflow) ━━━")
                 log("错误: ${e.message}")
                 failUi("处理失败: 文件结构异常", isBatch)
                 return false
             } catch (e: Exception) {
                 log("━━━ 处理失败 ━━━")
                 log("错误: ${e.message}")
                 log("堆栈: ${e.stackTraceToString().take(300)}")
                 // ★ 失败保留中间产物与 checkpoint（单文件模式下次可断点恢复）
                 if (!isBatch) {
                     log("  [断点续传] 已保留中间产物，下次选择同一 APK 处理时可从断点恢复")
                 } else {
                     try { workDir.deleteRecursively() } catch (_: Exception) {}
                 }
                 failUi("处理失败: ${e.message}", isBatch)
                 return false
             }
         }

         /** 失败提示（批量模式下不重置进度条/卡片状态） */
         private suspend fun failUi(msg: String, isBatch: Boolean) {
             withContext(Dispatchers.Main) {
                 if (!isBatch) {
                     showProgress(false)
                     cardRemoveAds.isEnabled = true
                 }
                 Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
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
            if (parentUri == null) return null
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_check_update -> {
                checkForUpdate()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

       
                                      
                                        
       
    private fun checkForUpdate() {
        Toast.makeText(this, "正在检查更新 ...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            val info = UpdateChecker
                .fetchLatestUpdate(UpdateChecker.getCheckUrl(this@MainActivity))
            withContext(Dispatchers.Main) {
                UpdateChecker.showResult(this@MainActivity, info)
            }
        }
    }
}
