package com.ads.purge.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

   
                         
  
                                          
                                     
  
                                                         
  
                                         
    
                                                         
                                                     
                                                  
                                                          
                                                  
                                                                    
                                                          
                                                         
                                                                
                                            
                                                                         
    
   
object AdPatternConfig {

    private const val CONFIG_DIR = "/storage/emulated/0/APKEditor"
    private const val CONFIG_FILE = "ad_patterns.json"

    
    private const val KEY_SDK_PACKAGES = "sdk_packages"
    private const val KEY_CLASS_KEYWORDS = "class_keywords"
    private const val KEY_METHOD_PATTERNS = "method_patterns"
    private const val KEY_URL_PATTERNS = "url_patterns"
    private const val KEY_AD_VIEW_NAMES = "ad_view_names"
    private const val KEY_AD_ACTIVITIES = "ad_activities"
    private const val KEY_AD_SERVICES = "ad_services"
    private const val KEY_AD_RECEIVERS = "ad_receivers"
    private const val KEY_FORCE_TRUE_METHODS = "force_true_methods"
    private const val KEY_AD_KEY_STRINGS = "ad_key_strings"
    private const val KEY_AD_ASSET_FILES = "ad_asset_files"
    private const val KEY_AD_PERMISSIONS = "ad_permissions"
    // ★ v3.1.1 原作者新特征库 6 类
    private const val KEY_AD_ASSET_PATHS = "ad_asset_paths"
    private const val KEY_LIB_FILE_KEYWORDS = "lib_file_keywords"
    private const val KEY_ASSET_KEYWORDS = "asset_keywords"
    private const val KEY_METHOD_NEUTRALIZE_KEYWORDS = "method_neutralize_keywords"
    private const val KEY_ROOT_FILE_KEYWORDS = "root_file_keywords"
    private const val KEY_RES_LAYOUT_KEYWORDS = "res_layout_keywords"

       
                 
       
    data class AdPatterns(
        val sdkPackages: MutableList<String> = mutableListOf(),
        val classKeywords: MutableList<String> = mutableListOf(),
        val methodPatterns: MutableList<String> = mutableListOf(),
        val urlPatterns: MutableList<String> = mutableListOf(),
        val adViewNames: MutableList<String> = mutableListOf(),
        val adActivities: MutableList<String> = mutableListOf(),
        val adServices: MutableList<String> = mutableListOf(),
        val adReceivers: MutableList<String> = mutableListOf(),
        val forceTrueMethodNames: MutableList<String> = mutableListOf(),
        val adKeyStrings: MutableList<String> = mutableListOf(),
        val adAssetFiles: MutableList<String> = mutableListOf(),
        val adPermissions: MutableList<String> = mutableListOf(),
        // ★ v3.1.1 原作者新特征库 6 类
        val adAssetPaths: MutableList<String> = mutableListOf(),
        val libFileKeywords: MutableList<String> = mutableListOf(),
        val assetKeywords: MutableList<String> = mutableListOf(),
        val methodNeutralizeKeywords: MutableList<String> = mutableListOf(),
        val rootFileKeywords: MutableList<String> = mutableListOf(),
        val resLayoutKeywords: MutableList<String> = mutableListOf()
    ) {
           
                                   
                                                                        
           
        fun allAdPatterns(): List<String> {
            val result = mutableListOf<String>()
            
            result.addAll(sdkPackages.map { it.replace('.', '/') })
            result.addAll(classKeywords)
            result.addAll(adActivities)
            result.addAll(adServices)
            result.addAll(adReceivers)
            result.addAll(adViewNames)
            return result
        }

           
                
           
        fun totalCount(): Int =
            sdkPackages.size + classKeywords.size +
            methodPatterns.size + urlPatterns.size + adViewNames.size +
            adActivities.size + adServices.size + adReceivers.size +
            forceTrueMethodNames.size + adKeyStrings.size + adAssetFiles.size +
            adPermissions.size + adAssetPaths.size + libFileKeywords.size +
            assetKeywords.size + methodNeutralizeKeywords.size +
            rootFileKeywords.size + resLayoutKeywords.size
    }

       
                                                               
      
                      
                                         
                                                                                                    
                                      
                                                      
      
                                                                
                                   
       
    fun isAdComponentName(name: String, config: AdPatterns): Boolean {
        val lower = name.trim().lowercase()
        if (lower.isEmpty()) return false

        
        val dot = lower.lastIndexOf('.')
        val simpleName = if (dot >= 0 && dot < lower.length - 1) lower.substring(dot + 1) else lower
        val pkg = if (dot > 0) lower.substring(0, dot) else lower

        
        val sdkPrefixes = config.sdkPackages
            .map { it.trim().lowercase().removeSuffix(".") }
            .filter { p -> p.isNotEmpty() && !p.startsWith(".") && !p.endsWith(".") && p.contains('.') }
        for (sdk in sdkPrefixes) {
            if (pkg == sdk) return true
            if (pkg.startsWith("$sdk.")) return true
        }

        
        val keywords = (config.adActivities + config.adServices + config.adReceivers)
            .map { it.trim().lowercase() }
            .filter { it.length >= 4 }
            .toSet()
        if (keywords.isEmpty()) return false
        for (kw in keywords) {
            if (simpleName == kw) return true
            if (simpleName.length > kw.length && simpleName.endsWith(kw)) return true
        }
        return false
    }

       
                        
       
    enum class Category(val key: String, val displayName: String) {
        SDK_PACKAGES(KEY_SDK_PACKAGES, "广告SDK包名"),
        CLASS_KEYWORDS(KEY_CLASS_KEYWORDS, "广告类名关键词"),
        METHOD_PATTERNS(KEY_METHOD_PATTERNS, "广告方法名"),
        URL_PATTERNS(KEY_URL_PATTERNS, "广告URL/域名"),
        AD_VIEW_NAMES(KEY_AD_VIEW_NAMES, "广告View类名"),
        AD_ACTIVITIES(KEY_AD_ACTIVITIES, "广告Activity"),
        AD_SERVICES(KEY_AD_SERVICES, "广告Service"),
        AD_RECEIVERS(KEY_AD_RECEIVERS, "广告Receiver"),
        FORCE_TRUE_METHODS(KEY_FORCE_TRUE_METHODS, "强制返回true的方法名"),
        AD_KEY_STRINGS(KEY_AD_KEY_STRINGS, "广告关键字符串"),
        AD_ASSET_FILES(KEY_AD_ASSET_FILES, "广告SDK配置文件"),
        AD_PERMISSIONS(KEY_AD_PERMISSIONS, "广告相关权限"),
        // ★ v3.1.1 原作者新特征库 6 类
        AD_ASSET_PATHS(KEY_AD_ASSET_PATHS, "广告SDK资产路径"),
        LIB_FILE_KEYWORDS(KEY_LIB_FILE_KEYWORDS, "广告SO库文件名"),
        ASSET_KEYWORDS(KEY_ASSET_KEYWORDS, "广告assets文件名"),
        METHOD_NEUTRALIZE_KEYWORDS(KEY_METHOD_NEUTRALIZE_KEYWORDS, "方法中性化关键词"),
        ROOT_FILE_KEYWORDS(KEY_ROOT_FILE_KEYWORDS, "根目录文件名"),
        RES_LAYOUT_KEYWORDS(KEY_RES_LAYOUT_KEYWORDS, "广告布局文件名")
    }

       
                
       
    fun getConfigFile(): File {
        return File(CONFIG_DIR, CONFIG_FILE)
    }

       
                
       
    private fun ensureConfigDir(): File {
        val dir = File(CONFIG_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

       
                         
                                        
       
    fun loadConfig(context: Context): AdPatterns {
        val configFile = getConfigFile()
        if (!configFile.exists()) {
            val defaults = getDefaultConfig(context)
            saveConfig(defaults)
            return defaults
        }

        return try {
            val jsonStr = configFile.readText(Charsets.UTF_8)
            val json = JSONObject(jsonStr)

            AdPatterns(
                sdkPackages = jsonToStringList(json, KEY_SDK_PACKAGES),
                classKeywords = jsonToStringList(json, KEY_CLASS_KEYWORDS),
                methodPatterns = jsonToStringList(json, KEY_METHOD_PATTERNS),
                urlPatterns = jsonToStringList(json, KEY_URL_PATTERNS),
                adViewNames = jsonToStringList(json, KEY_AD_VIEW_NAMES),
                adActivities = jsonToStringList(json, KEY_AD_ACTIVITIES),
                adServices = jsonToStringList(json, KEY_AD_SERVICES),
                adReceivers = jsonToStringList(json, KEY_AD_RECEIVERS),
                forceTrueMethodNames = jsonToStringList(json, KEY_FORCE_TRUE_METHODS),
                adKeyStrings = jsonToStringList(json, KEY_AD_KEY_STRINGS),
                adAssetFiles = jsonToStringList(json, KEY_AD_ASSET_FILES),
                adPermissions = jsonToStringList(json, KEY_AD_PERMISSIONS),
                adAssetPaths = jsonToStringList(json, KEY_AD_ASSET_PATHS),
                libFileKeywords = jsonToStringList(json, KEY_LIB_FILE_KEYWORDS),
                assetKeywords = jsonToStringList(json, KEY_ASSET_KEYWORDS),
                methodNeutralizeKeywords = jsonToStringList(json, KEY_METHOD_NEUTRALIZE_KEYWORDS),
                rootFileKeywords = jsonToStringList(json, KEY_ROOT_FILE_KEYWORDS),
                resLayoutKeywords = jsonToStringList(json, KEY_RES_LAYOUT_KEYWORDS)
            )
        } catch (_: Exception) {
            
            val defaults = getDefaultConfig(context)
            saveConfig(defaults)
            defaults
        }
    }

       
                         
       
    fun saveConfig(config: AdPatterns): Boolean {
        return try {
            ensureConfigDir()
            val json = JSONObject()

            json.put(KEY_SDK_PACKAGES, listToJsonArray(config.sdkPackages))
            json.put(KEY_CLASS_KEYWORDS, listToJsonArray(config.classKeywords))
            json.put(KEY_METHOD_PATTERNS, listToJsonArray(config.methodPatterns))
            json.put(KEY_URL_PATTERNS, listToJsonArray(config.urlPatterns))
            json.put(KEY_AD_VIEW_NAMES, listToJsonArray(config.adViewNames))
            json.put(KEY_AD_ACTIVITIES, listToJsonArray(config.adActivities))
            json.put(KEY_AD_SERVICES, listToJsonArray(config.adServices))
            json.put(KEY_AD_RECEIVERS, listToJsonArray(config.adReceivers))
            json.put(KEY_FORCE_TRUE_METHODS, listToJsonArray(config.forceTrueMethodNames))
            json.put(KEY_AD_KEY_STRINGS, listToJsonArray(config.adKeyStrings))
            json.put(KEY_AD_ASSET_FILES, listToJsonArray(config.adAssetFiles))
            json.put(KEY_AD_PERMISSIONS, listToJsonArray(config.adPermissions))
            json.put(KEY_AD_ASSET_PATHS, listToJsonArray(config.adAssetPaths))
            json.put(KEY_LIB_FILE_KEYWORDS, listToJsonArray(config.libFileKeywords))
            json.put(KEY_ASSET_KEYWORDS, listToJsonArray(config.assetKeywords))
            json.put(KEY_METHOD_NEUTRALIZE_KEYWORDS, listToJsonArray(config.methodNeutralizeKeywords))
            json.put(KEY_ROOT_FILE_KEYWORDS, listToJsonArray(config.rootFileKeywords))
            json.put(KEY_RES_LAYOUT_KEYWORDS, listToJsonArray(config.resLayoutKeywords))

            getConfigFile().writeText(json.toString(2), Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

       
               
       
    fun resetToDefault(context: Context): AdPatterns {
        val defaults = getDefaultConfig(context)
        saveConfig(defaults)
        return defaults
    }

       
                     
       
    fun getCategoryList(config: AdPatterns, category: Category): MutableList<String> {
        return when (category) {
            Category.SDK_PACKAGES -> config.sdkPackages
            Category.CLASS_KEYWORDS -> config.classKeywords
            Category.METHOD_PATTERNS -> config.methodPatterns
            Category.URL_PATTERNS -> config.urlPatterns
            Category.AD_VIEW_NAMES -> config.adViewNames
            Category.AD_ACTIVITIES -> config.adActivities
            Category.AD_SERVICES -> config.adServices
            Category.AD_RECEIVERS -> config.adReceivers
            Category.FORCE_TRUE_METHODS -> config.forceTrueMethodNames
            Category.AD_KEY_STRINGS -> config.adKeyStrings
            Category.AD_ASSET_FILES -> config.adAssetFiles
            Category.AD_PERMISSIONS -> config.adPermissions
            Category.AD_ASSET_PATHS -> config.adAssetPaths
            Category.LIB_FILE_KEYWORDS -> config.libFileKeywords
            Category.ASSET_KEYWORDS -> config.assetKeywords
            Category.METHOD_NEUTRALIZE_KEYWORDS -> config.methodNeutralizeKeywords
            Category.ROOT_FILE_KEYWORDS -> config.rootFileKeywords
            Category.RES_LAYOUT_KEYWORDS -> config.resLayoutKeywords
        }
    }

    /** ★ v3.1 在线特征库：从 JSON 字符串解析 AdPatterns（与 ad_patterns.json 同格式） */
    fun parseFromJson(jsonStr: String): AdPatterns {
        val json = JSONObject(jsonStr)
        return AdPatterns(
            sdkPackages = jsonToStringList(json, KEY_SDK_PACKAGES),
            classKeywords = jsonToStringList(json, KEY_CLASS_KEYWORDS),
            methodPatterns = jsonToStringList(json, KEY_METHOD_PATTERNS),
            urlPatterns = jsonToStringList(json, KEY_URL_PATTERNS),
            adViewNames = jsonToStringList(json, KEY_AD_VIEW_NAMES),
            adActivities = jsonToStringList(json, KEY_AD_ACTIVITIES),
            adServices = jsonToStringList(json, KEY_AD_SERVICES),
            adReceivers = jsonToStringList(json, KEY_AD_RECEIVERS),
            forceTrueMethodNames = jsonToStringList(json, KEY_FORCE_TRUE_METHODS),
            adKeyStrings = jsonToStringList(json, KEY_AD_KEY_STRINGS),
            adAssetFiles = jsonToStringList(json, KEY_AD_ASSET_FILES),
            adPermissions = jsonToStringList(json, KEY_AD_PERMISSIONS),
            adAssetPaths = jsonToStringList(json, KEY_AD_ASSET_PATHS),
            libFileKeywords = jsonToStringList(json, KEY_LIB_FILE_KEYWORDS),
            assetKeywords = jsonToStringList(json, KEY_ASSET_KEYWORDS),
            methodNeutralizeKeywords = jsonToStringList(json, KEY_METHOD_NEUTRALIZE_KEYWORDS),
            rootFileKeywords = jsonToStringList(json, KEY_ROOT_FILE_KEYWORDS),
            resLayoutKeywords = jsonToStringList(json, KEY_RES_LAYOUT_KEYWORDS)
        )
    }

    /** ★ v3.1 在线特征库：将 imported 合并进 base（就地修改，大小写不敏感去重），返回新增条数 */
    fun mergeConfig(base: AdPatterns, imported: AdPatterns): Int {
        var added = 0

        fun merge(list: MutableList<String>, newItems: List<String>) {
            val existing = list.map { it.trim().lowercase() }.toMutableSet()
            for (item in newItems) {
                val trimmed = item.trim()
                if (trimmed.isEmpty()) continue
                if (existing.add(trimmed.lowercase())) {
                    list.add(trimmed)
                    added++
                }
            }
        }

        merge(base.sdkPackages, imported.sdkPackages)
        merge(base.classKeywords, imported.classKeywords)
        merge(base.methodPatterns, imported.methodPatterns)
        merge(base.urlPatterns, imported.urlPatterns)
        merge(base.adViewNames, imported.adViewNames)
        merge(base.adActivities, imported.adActivities)
        merge(base.adServices, imported.adServices)
        merge(base.adReceivers, imported.adReceivers)
        merge(base.forceTrueMethodNames, imported.forceTrueMethodNames)
        merge(base.adKeyStrings, imported.adKeyStrings)
        merge(base.adAssetFiles, imported.adAssetFiles)
        merge(base.adPermissions, imported.adPermissions)
        merge(base.adAssetPaths, imported.adAssetPaths)
        merge(base.libFileKeywords, imported.libFileKeywords)
        merge(base.assetKeywords, imported.assetKeywords)
        merge(base.methodNeutralizeKeywords, imported.methodNeutralizeKeywords)
        merge(base.rootFileKeywords, imported.rootFileKeywords)
        merge(base.resLayoutKeywords, imported.resLayoutKeywords)
        return added
    }

    

    private fun jsonToStringList(json: JSONObject, key: String): MutableList<String> {
        val result = mutableListOf<String>()
        if (!json.has(key)) return result
        val arr = json.getJSONArray(key)
        for (i in 0 until arr.length()) {
            result.add(arr.getString(i))
        }
        return result
    }

    private fun listToJsonArray(list: List<String>): JSONArray {
        val arr = JSONArray()
        for (item in list) {
            arr.put(item)
        }
        return arr
    }

    

       
                                     
                                            
                                                
                                                       
       
    fun getDefaultConfig(context: Context): AdPatterns {
        return try {
            context.assets.open("ad_patterns_default.json").use { inputStream ->
                val jsonStr = inputStream.bufferedReader().readText()
                val json = JSONObject(jsonStr)
                AdPatterns(
                    sdkPackages = jsonToStringList(json, KEY_SDK_PACKAGES),
                    classKeywords = jsonToStringList(json, KEY_CLASS_KEYWORDS),
                    methodPatterns = jsonToStringList(json, KEY_METHOD_PATTERNS),
                    urlPatterns = jsonToStringList(json, KEY_URL_PATTERNS),
                    adViewNames = jsonToStringList(json, KEY_AD_VIEW_NAMES),
                    adActivities = jsonToStringList(json, KEY_AD_ACTIVITIES),
                    adServices = jsonToStringList(json, KEY_AD_SERVICES),
                    adReceivers = jsonToStringList(json, KEY_AD_RECEIVERS),
                    forceTrueMethodNames = jsonToStringList(json, KEY_FORCE_TRUE_METHODS),
                    adKeyStrings = jsonToStringList(json, KEY_AD_KEY_STRINGS),
                    adAssetFiles = jsonToStringList(json, KEY_AD_ASSET_FILES),
                    adPermissions = jsonToStringList(json, KEY_AD_PERMISSIONS),
                    adAssetPaths = jsonToStringList(json, KEY_AD_ASSET_PATHS),
                    libFileKeywords = jsonToStringList(json, KEY_LIB_FILE_KEYWORDS),
                    assetKeywords = jsonToStringList(json, KEY_ASSET_KEYWORDS),
                    methodNeutralizeKeywords = jsonToStringList(json, KEY_METHOD_NEUTRALIZE_KEYWORDS),
                    rootFileKeywords = jsonToStringList(json, KEY_ROOT_FILE_KEYWORDS),
                    resLayoutKeywords = jsonToStringList(json, KEY_RES_LAYOUT_KEYWORDS)
                )
            }
        } catch (e: Exception) {
            
            AdPatterns(
                sdkPackages = mutableListOf("com.google.android.gms.ads"),
                classKeywords = mutableListOf("AdView", "AdActivity")
            )
        }
    }
}