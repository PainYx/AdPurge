package com.ads.purge.core

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

   
                             
  
      
                                              
                 
   
object AppConfig {

private const val PREFS_NAME = "app_settings"
     private const val KEY_EXPORT_DIR = "export_dir"
     private const val KEY_TEXT_COLOR = "text_color"
     // ★ v3.1 新增设置项
     private const val KEY_PARALLEL_ENABLED = "parallel_enabled"
     private const val KEY_DROP_DEBUG_INFO = "drop_debug_info"
     private const val KEY_SPLASH_SHORTEN = "splash_shorten"
     private const val KEY_NOTIFY_ENABLED = "notify_enabled"
     private const val KEY_SIGNATURE_REMOVE = "signature_remove"
     private const val KEY_REPORT_ENABLED = "report_enabled"
     private const val KEY_LANG = "lang"

                 
    const val DEFAULT_EXPORT_DIR = "/storage/emulated/0/APKEditor"

                                 
    val TEXT_COLOR_OPTIONS = listOf(
        "青绿荧光" to Color.parseColor("#E8ECFF"),
        "活力橙" to Color.parseColor("#FFB300"),
        "翠绿" to Color.parseColor("#00E676"),
        "玫红" to Color.parseColor("#FF5252"),
        "天蓝" to Color.parseColor("#40C4FF")
    )

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

                    
    fun getExportDir(context: Context): String {
        return prefs(context).getString(KEY_EXPORT_DIR, DEFAULT_EXPORT_DIR) ?: DEFAULT_EXPORT_DIR
    }

                    
    fun setExportDir(context: Context, dir: String) {
        prefs(context).edit().putString(KEY_EXPORT_DIR, dir).apply()
    }

                    
    fun getTextColor(context: Context): Int {
        return prefs(context).getInt(KEY_TEXT_COLOR, Color.parseColor("#E8ECFF"))
    }

                    
fun setTextColor(context: Context, color: Int) {
         prefs(context).edit().putInt(KEY_TEXT_COLOR, color).apply()
     }

     // ── v3.1 处理选项开关（默认值与既有行为一致）──

     fun isParallelEnabled(context: Context): Boolean =
         prefs(context).getBoolean(KEY_PARALLEL_ENABLED, true)

     fun setParallelEnabled(context: Context, enabled: Boolean) =
         prefs(context).edit().putBoolean(KEY_PARALLEL_ENABLED, enabled).apply()

     fun isDropDebugInfo(context: Context): Boolean =
         prefs(context).getBoolean(KEY_DROP_DEBUG_INFO, true)

     fun setDropDebugInfo(context: Context, enabled: Boolean) =
         prefs(context).edit().putBoolean(KEY_DROP_DEBUG_INFO, enabled).apply()

     fun isSplashShorten(context: Context): Boolean =
         prefs(context).getBoolean(KEY_SPLASH_SHORTEN, true)

     fun setSplashShorten(context: Context, enabled: Boolean) =
         prefs(context).edit().putBoolean(KEY_SPLASH_SHORTEN, enabled).apply()

     fun isNotifyEnabled(context: Context): Boolean =
         prefs(context).getBoolean(KEY_NOTIFY_ENABLED, true)

     fun setNotifyEnabled(context: Context, enabled: Boolean) =
         prefs(context).edit().putBoolean(KEY_NOTIFY_ENABLED, enabled).apply()

     fun isSignatureRemove(context: Context): Boolean =
         prefs(context).getBoolean(KEY_SIGNATURE_REMOVE, true)

     fun setSignatureRemove(context: Context, enabled: Boolean) =
         prefs(context).edit().putBoolean(KEY_SIGNATURE_REMOVE, enabled).apply()

     fun isReportEnabled(context: Context): Boolean =
         prefs(context).getBoolean(KEY_REPORT_ENABLED, true)

     fun setReportEnabled(context: Context, enabled: Boolean) =
         prefs(context).edit().putBoolean(KEY_REPORT_ENABLED, enabled).apply()

     /** 界面语言："zh" 或 "en"（默认跟随系统，中文以外显示英文） */
     fun getLang(context: Context): String {
         val saved = prefs(context).getString(KEY_LANG, "auto")
         if (saved == "auto") {
            val locale = context.resources.configuration.locales[0].language
            return if (locale.startsWith("zh")) "zh" else "en"
        }
         return saved ?: "zh"
     }

     fun setLang(context: Context, lang: String) =
         prefs(context).edit().putString(KEY_LANG, lang).apply()
 }
