package com.ads.purge.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

   
           
  
                                
                 
                                   
                                     
  
                        
    
                                                            
                                                  
                                             
                                       
                                                  
    
   
object UpdateChecker {

                                       
    // ★ 发布时替换为你的仓库地址（设置页可改，此处为占位）
    const val DEFAULT_CHECK_URL =
        "https://raw.githubusercontent.com/YOUR_GITHUB_NAME/AdPurge/main/version.json"

    private const val PREFS_NAME = "update_checker"

                    
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

                  
    data class UpdateInfo(
        val versionCode: Long,
        val versionName: String,
        val forceUpdate: Boolean,
        val description: String,
        val downloadUrl: String
    )

       
                     
       
    fun getCheckUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("check_url", DEFAULT_CHECK_URL) ?: DEFAULT_CHECK_URL
    }

       
              
       
    fun setCheckUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("check_url", url.trim())
            .apply()
    }

       
                              
       
    fun getCurrentVersionCode(context: Context): Long {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
        } catch (_: PackageManager.NameNotFoundException) {
            0L
        }
    }

       
                               
       
    fun getCurrentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: PackageManager.NameNotFoundException) {
            "1.0"
        }
    }

       
                                    
      
                                                   
       
    fun fetchLatestUpdate(checkUrl: String): UpdateInfo? {
        return try {
            val url = URL(checkUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "APKAdRemoverEditor/1.0")
                instanceFollowRedirects = true
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) return null
                val sb = StringBuilder()
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line).append('\n')
                    }
                }
                parseUpdateInfo(sb.toString().trim())
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseUpdateInfo(jsonStr: String): UpdateInfo? {
        return try {
            val json = JSONObject(jsonStr)
            if (!json.has("versionCode") || !json.has("url")) return null
            UpdateInfo(
                versionCode = json.getLong("versionCode"),
                versionName = json.optString("versionName", ""),
                forceUpdate = json.optBoolean("force", false),
                description = json.optString("description", ""),
                downloadUrl = json.getString("url")
            )
        } catch (_: Exception) {
            null
        }
    }

       
                        
      
                                  
                                             
       
    fun showResult(activity: Activity, info: UpdateInfo?) {
        if (info == null) {
            Toast.makeText(activity, "检查更新失败，请检查网络后重试", Toast.LENGTH_SHORT).show()
            return
        }

        val currentCode = getCurrentVersionCode(activity)
        if (info.versionCode <= currentCode) {
            Toast.makeText(activity, "已是最新版本 (${getCurrentVersionName(activity)})", Toast.LENGTH_SHORT).show()
            return
        }

        val versionText = if (info.versionName.isBlank()) "${info.versionCode}" else "${info.versionName} (${info.versionCode})"
        val message = buildString {
            append("发现新版本 v$versionText\n\n")
            if (info.description.isNotBlank()) {
                append("更新内容：\n${info.description}\n\n")
            }
            append("当前版本：v${getCurrentVersionName(activity)}")
        }

        val builder = AlertDialog.Builder(activity)
            .setTitle(if (info.forceUpdate) "发现新版本（必须更新）" else "发现新版本")
            .setMessage(message)

        if (info.forceUpdate) {
            
            builder.setCancelable(false)
                .setPositiveButton("立即更新") { _, _ -> openDownload(activity, info.downloadUrl) }
                .show()
        } else {
            
            builder.setCancelable(true)
                .setPositiveButton("立即更新") { _, _ -> openDownload(activity, info.downloadUrl) }
                .setNegativeButton("稍后再说", null)
                .show()
        }
    }

       
                           
       
    private fun openDownload(activity: Activity, url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(activity, "无法打开下载地址", Toast.LENGTH_SHORT).show()
        }
    }
}