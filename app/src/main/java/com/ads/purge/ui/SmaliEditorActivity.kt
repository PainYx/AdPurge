package com.ads.purge.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.ads.purge.R
import java.io.File

class SmaliEditorActivity : AppCompatActivity() {

    private lateinit var editText: EditText
    private var smaliFilePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        editText = findViewById(R.id.editText)
        smaliFilePath = intent.getStringExtra("file_path")

        smaliFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                editText.setText(file.readText())
                title = file.name
            }
        }
    }

    private fun saveFile() {
        val path = smaliFilePath ?: return
        try {
            File(path).writeText(editText.text.toString())
            Toast.makeText(this, "Smali文件已保存", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.editor_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                saveFile()
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
