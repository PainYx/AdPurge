package com.ads.purge

import android.os.Bundle
import android.util.Log
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ads.purge.core.AdPatternConfig
import com.ads.purge.core.AdPatternConfig.Category
import com.ads.purge.core.AppConfig
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.net.HttpURLConnection
import java.net.URL

   
              
  
      
                      
                 
                                  
                  
            
   
class SettingsActivity : AppCompatActivity() {

    private lateinit var tvConfigPath: TextView
    private lateinit var tvConfigStats: TextView
    private lateinit var btnSave: MaterialButton
    private lateinit var btnReset: MaterialButton
    private lateinit var btnOnlineUpdate: MaterialButton

    private var config: AdPatternConfig.AdPatterns = AdPatternConfig.AdPatterns()

    
    private val categoryCards = mutableMapOf<Category, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_settings)
        } catch (e: Exception) {
            Log.e("SettingsActivity", "布局加载失败", e)
            Toast.makeText(this, "设置界面加载失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        try {
            val toolbar = findViewById<Toolbar>(R.id.toolbar)
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            toolbar.setNavigationOnClickListener { finish() }

            tvConfigPath = findViewById(R.id.tvConfigPath)
            tvConfigStats = findViewById(R.id.tvConfigStats)
            btnSave = findViewById(R.id.btnSave)
            btnReset = findViewById(R.id.btnReset)
            btnOnlineUpdate = findViewById(R.id.btnOnlineUpdate)

            
            val textColor = AppConfig.getTextColor(this)
            findViewById<TextView>(R.id.tvConfigInfoTitle).setTextColor(textColor)
            findViewById<TextView>(R.id.tvCategoryTitle).setTextColor(textColor)
            tvConfigPath.setTextColor(textColor)
            tvConfigStats.setTextColor(textColor)

            
            loadAndDisplayConfig()

            
            btnSave.setOnClickListener {
                val success = AdPatternConfig.saveConfig(config)
                if (success) {
                    Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
                    updateStats()
                } else {
                    Toast.makeText(this, "保存失败，请检查存储权限", Toast.LENGTH_LONG).show()
                }
            }

            
            btnReset.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("重置默认配置")
                    .setMessage("确定要恢复所有广告特征为内置默认值？\n当前自定义修改将丢失。")
                    .setPositiveButton("重置") { _, _ ->
                        config = AdPatternConfig.resetToDefault(this)
                        displayConfig()
                        Toast.makeText(this, "已重置为默认配置", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }

            // ★ v3.1 在线特征库更新
            btnOnlineUpdate.setOnClickListener {
                showOnlineUpdateDialog()
            }
        } catch (e: Exception) {
            Log.e("SettingsActivity", "初始化失败", e)
            Toast.makeText(this, "设置初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

       
               
       
    private fun loadAndDisplayConfig() {
        try {
            config = AdPatternConfig.loadConfig(this)
        } catch (e: Exception) {
            Log.e("SettingsActivity", "加载配置失败，使用默认配置", e)
            config = AdPatternConfig.AdPatterns()
        }
        displayConfig()
    }

    /** ★ v3.1 在线特征库更新：URL 输入对话框 */
    private fun showOnlineUpdateDialog() {
        val input = EditText(this).apply {
            hint = "https://example.com/ad_patterns.json"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle("在线更新特征库")
            .setMessage("输入特征库 JSON 文件的 URL（与 ad_patterns.json 同格式）。\n下载后将与当前配置按分类合并去重，不会覆盖已有条目。")
            .setView(input)
            .setPositiveButton("下载并合并") { _, _ ->
                val url = input.text.toString().trim()
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    Toast.makeText(this, "请输入以 http:// 或 https:// 开头的 URL", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                downloadAndMergePatterns(url)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** ★ v3.1 后台下载 JSON → 解析 → 合并 → 保存 → 刷新 */
    private fun downloadAndMergePatterns(urlStr: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("在线更新特征库")
            .setMessage("正在下载并合并特征库，请稍候…")
            .setCancelable(false)
            .create()
        dialog.show()

        Thread(Runnable {
            try {
                val jsonStr = downloadText(urlStr)
                val imported = AdPatternConfig.parseFromJson(jsonStr)
                if (imported.totalCount() == 0) {
                    runOnUiThread {
                        dialog.dismiss()
                        Toast.makeText(this, "下载成功，但未解析到任何特征条目，请确认 JSON 格式", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val added = AdPatternConfig.mergeConfig(config, imported)
                    val saved = AdPatternConfig.saveConfig(config)
                    runOnUiThread {
                        dialog.dismiss()
                        if (saved) {
                            displayConfig()
                            Toast.makeText(
                                this,
                                "更新完成：导入 ${imported.totalCount()} 条，新增 $added 条（已去重保存）",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(this, "合并成功但保存失败，请检查存储权限", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsActivity", "在线特征库下载失败", e)
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, "下载失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }).start()
    }

    private fun downloadText(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AdPurge/5.0")
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw Exception("HTTP $code")
            }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

       
                  
       
    private fun displayConfig() {
        tvConfigPath.text = AdPatternConfig.getConfigFile().absolutePath
        updateStats()

        
        bindCategoryCard(R.id.cardSdkPackages, Category.SDK_PACKAGES)
        bindCategoryCard(R.id.cardClassKeywords, Category.CLASS_KEYWORDS)
        bindCategoryCard(R.id.cardMethodPatterns, Category.METHOD_PATTERNS)
        bindCategoryCard(R.id.cardUrlPatterns, Category.URL_PATTERNS)
        bindCategoryCard(R.id.cardAdViewNames, Category.AD_VIEW_NAMES)
        bindCategoryCard(R.id.cardAdActivities, Category.AD_ACTIVITIES)
        bindCategoryCard(R.id.cardAdServices, Category.AD_SERVICES)
        bindCategoryCard(R.id.cardAdReceivers, Category.AD_RECEIVERS)
        bindCategoryCard(R.id.cardForceTrueMethods, Category.FORCE_TRUE_METHODS)
        bindCategoryCard(R.id.cardAdKeyStrings, Category.AD_KEY_STRINGS)
        bindCategoryCard(R.id.cardAdAssetFiles, Category.AD_ASSET_FILES)
        bindCategoryCard(R.id.cardAdPermissions, Category.AD_PERMISSIONS)
    }

       
              
       
    private fun updateStats() {
        tvConfigStats.text = "共 ${config.totalCount()} 条特征"
    }

       
                             
       
    private fun bindCategoryCard(cardId: Int, category: Category) {
        try {
            val card = findViewById<View>(cardId) ?: run {
                Log.w("SettingsActivity", "卡片视图未找到: cardId=$cardId")
                return
            }
            categoryCards[category] = card

            val tvName = card.findViewById<TextView>(R.id.tvCategoryName)
            val tvCount = card.findViewById<TextView>(R.id.tvCategoryCount)
            val btnManage = card.findViewById<MaterialButton>(R.id.btnManage)

            if (tvName == null || tvCount == null || btnManage == null) {
                Log.w("SettingsActivity", "卡片子视图未找到: $category")
                return
            }

            tvName.text = category.displayName
            val list = AdPatternConfig.getCategoryList(config, category)
            tvCount.text = "${list.size} 条"

            
            tvName.setTextColor(AppConfig.getTextColor(this))
            tvCount.setTextColor(AppConfig.getTextColor(this))

            btnManage.setOnClickListener {
                showPatternListDialog(category)
            }
        } catch (e: Exception) {
            Log.e("SettingsActivity", "绑定分类卡片失败: $category", e)
        }
    }

       
                      
                            
       
    private fun showPatternListDialog(category: Category) {
        val list = AdPatternConfig.getCategoryList(config, category)

        val dialogView = layoutInflater.inflate(R.layout.dialog_pattern_list, null)
        val rvPatterns = dialogView.findViewById<RecyclerView>(R.id.rvPatterns)
        val etNewPattern = dialogView.findViewById<TextInputEditText>(R.id.etNewPattern)
        val btnAddPattern = dialogView.findViewById<MaterialButton>(R.id.btnAddPattern)
        val tvEmptyHint = dialogView.findViewById<TextView>(R.id.tvEmptyHint)

        
        tvEmptyHint.setTextColor(AppConfig.getTextColor(this))

        val adapter = PatternAdapter(list, object : PatternAdapter.Callback {
            override fun onEdit(position: Int, oldValue: String) {
                showEditDialog(category, oldValue) { newValue ->
                    if (newValue.isNotBlank() && newValue != oldValue) {
                        
                        if (list.any { it.equals(newValue, ignoreCase = true) }) {
                            Toast.makeText(this@SettingsActivity, "该特征已存在", Toast.LENGTH_SHORT).show()
                            return@showEditDialog
                        }
                        list[position] = newValue.trim()
                        rvPatterns.adapter?.notifyItemChanged(position)
                        updateEmptyHint(list, tvEmptyHint)
                        
                        AdPatternConfig.saveConfig(config)
                        updateCategoryCount(category, list.size)
                    }
                }
            }

            override fun onDelete(position: Int) {
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("删除特征")
                    .setMessage("确定删除 \"${list[position].take(50)}\" ？")
                    .setPositiveButton("删除") { _, _ ->
                        list.removeAt(position)
                        rvPatterns.adapter?.notifyItemRemoved(position)
                        rvPatterns.adapter?.notifyItemRangeChanged(position, list.size)
                        updateEmptyHint(list, tvEmptyHint)
                        
                        AdPatternConfig.saveConfig(config)
                        updateCategoryCount(category, list.size)
                        updateStats()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        })

        rvPatterns.layoutManager = LinearLayoutManager(this)
        rvPatterns.adapter = adapter

        
        btnAddPattern.setOnClickListener {
            val text = etNewPattern.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "请输入特征内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (list.any { it.equals(text, ignoreCase = true) }) {
                Toast.makeText(this, "该特征已存在", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            list.add(text)
            rvPatterns.adapter?.notifyItemInserted(list.size - 1)
            rvPatterns.scrollToPosition(list.size - 1)
            etNewPattern.text?.clear()
            updateEmptyHint(list, tvEmptyHint)
            
            AdPatternConfig.saveConfig(config)
            updateCategoryCount(category, list.size)
            updateStats()
            Toast.makeText(this, "已添加", Toast.LENGTH_SHORT).show()
        }

        updateEmptyHint(list, tvEmptyHint)

        AlertDialog.Builder(this)
            .setTitle(category.displayName + " (${list.size} 条)")
            .setView(dialogView)
            .setPositiveButton("关闭", null)
            .setOnDismissListener {
                updateCategoryCount(category, list.size)
                updateStats()
            }
            .show()
    }

       
               
       
    private fun showEditDialog(category: Category, oldValue: String, onSave: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(oldValue)
            setSelection(oldValue.length)
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle("编辑特征")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                onSave(input.text.toString().trim())
            }
            .setNegativeButton("取消", null)
            .show()
    }

       
               
       
    private fun updateEmptyHint(list: List<*>, tvEmptyHint: TextView) {
        tvEmptyHint.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

       
                    
       
    private fun updateCategoryCount(category: Category, count: Int) {
        val card = categoryCards[category] ?: return
        val tvCount = card.findViewById<TextView>(R.id.tvCategoryCount)
        tvCount.text = "$count 条"
    }
}

   
                         
   
class PatternAdapter(
    private val items: MutableList<String>,
    private val callback: Callback
) : RecyclerView.Adapter<PatternAdapter.ViewHolder>() {

    interface Callback {
        fun onEdit(position: Int, oldValue: String)
        fun onDelete(position: Int)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPatternText: TextView = view.findViewById(R.id.tvPatternText)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditItem)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pattern, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvPatternText.text = item
        
        holder.tvPatternText.setTextColor(AppConfig.getTextColor(holder.itemView.context))

        holder.btnEdit.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                callback.onEdit(pos, items[pos])
            }
        }

        holder.btnDelete.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                callback.onDelete(pos)
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
