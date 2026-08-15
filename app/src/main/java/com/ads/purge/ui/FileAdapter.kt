package com.ads.purge.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ads.purge.R
import java.io.File

class FileAdapter(
    private var files: List<File>,
    private val onItemClick: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    fun updateFiles(newFiles: List<File>) {
        files = newFiles.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(files[position])
    }

    override fun getItemCount(): Int = files.size

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.iconView)
        private val nameView: TextView = itemView.findViewById(R.id.nameView)
        private val infoView: TextView = itemView.findViewById(R.id.infoView)

        fun bind(file: File) {
            nameView.text = file.name

            if (file.isDirectory) {
                iconView.setImageResource(android.R.drawable.ic_menu_view)
                val childCount = file.listFiles()?.size ?: 0
                infoView.text = "$childCount 项"
            } else {
                iconView.setImageResource(getFileIcon(file))
                infoView.text = formatFileSize(file.length())
            }

            itemView.setOnClickListener {
                onItemClick(file)
            }
        }

        private fun getFileIcon(file: File): Int {
            return when {
                file.name.endsWith(".dex", ignoreCase = true) -> android.R.drawable.ic_menu_sort_by_size
                file.name.endsWith(".xml", ignoreCase = true) -> android.R.drawable.ic_menu_edit
                file.name.endsWith(".smali", ignoreCase = true) -> android.R.drawable.ic_menu_agenda
                file.name.endsWith(".png", ignoreCase = true) ||
                file.name.endsWith(".jpg", ignoreCase = true) -> android.R.drawable.ic_menu_gallery
                else -> android.R.drawable.ic_menu_info_details
            }
        }

        private fun formatFileSize(size: Long): String {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
                else -> "${size / (1024 * 1024 * 1024)} GB"
            }
        }
    }
}
