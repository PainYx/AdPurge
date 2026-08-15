package com.ads.purge.core

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

   
                                                        
  
        
                              
                                                             
                                        
                      
                   
   
class ApkProcessor {

    companion object {
           
                                                                      
          
                                       
                                     
                                                            
                                
                                           
          
                                            
                                                  
                                                             
           
        private const val ALIGNMENT_FIELD_ID = 0xd935

                                                                     
        private const val ALIGN_4 = 4

                                                    
        private const val ALIGN_PAGE = 4096

                                    
        // 注意：
        // - "arsc" 必须保持 STORED：Android 11+（targetSdk>=30）要求 resources.arsc
        //   未压缩存储且 4 字节对齐，压缩 arsc 会导致安装报 -124
        //   （Failed parse during installPackageLI）
        // - "dex" 已移除：DEX 可正常 DEFLATED 压缩，与原始 APK 体积一致
        private val STORED_EXTENSIONS = setOf(
            "arsc",    
            "so",      
            "png",     
            "jpg",     
            "jpeg",    
            "gif",     
            "webp",    
            "ttf",     
            "otf",     
            "wav",     
            "mp3",     
            "mp4",     
            "ogg"      
        )
    }

       
                         
                                      
       
    fun extractApk(apkFile: File, outputDir: File) {
        if (!outputDir.exists()) outputDir.mkdirs()

        ZipFile(apkFile).use { zipFile ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val entryName = entry.name

                
                
                
                
                if (entryName.startsWith("META-INF/") && (
                        entryName.endsWith(".SF") ||
                        entryName.endsWith(".RSA") ||
                        entryName.endsWith(".DSA") ||
                        entryName.endsWith(".EC")
                    )) {
                    continue
                }

                val outFile = File(outputDir, entryName)

                
                if (!outFile.canonicalPath.startsWith(outputDir.canonicalPath + File.separator) &&
                    outFile.canonicalPath != outputDir.canonicalPath) {
                    continue
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                    continue
                }

                outFile.parentFile?.mkdirs()
                zipFile.getInputStream(entry).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

       
                          
      
            
                                           
                                            
                             
      
                                            
                                           
      
                           
                                 
                            
       
    fun buildApk(sourceDir: File, outputApk: File, logger: Logger? = null) {
        val log = logger ?: {}
        if (outputApk.exists()) outputApk.delete()
        outputApk.parentFile?.mkdirs()

        var entryCount = 0
        var totalUncompressed = 0L
        var totalCompressed = 0L

        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        FileOutputStream(outputApk).use { fos ->
            ZipOutputStream(fos, StandardCharsets.UTF_8).use { zos ->
                
                zos.setLevel(Deflater.DEFAULT_COMPRESSION) // 标准压缩级别，平衡体积与速度

                sourceDir.walkTopDown().forEach { file ->
                    if (file.isDirectory) return@forEach

                    val relativePath = sourceDir.toURI().relativize(file.toURI()).path

                    
                    if (relativePath.startsWith("smali_")) return@forEach
                    
                    if (file.extension.equals("apk", ignoreCase = true)) return@forEach
                    
                    if (file.name.endsWith(".tmp")) return@forEach
                    
                    
                    
                    if (relativePath.startsWith("META-INF/") && (
                            relativePath.endsWith(".SF") ||
                            relativePath.endsWith(".RSA") ||
                            relativePath.endsWith(".DSA") ||
                            relativePath.endsWith(".EC")
                        )) {
                        return@forEach
                    }

                    val ext = file.extension.lowercase()
                    val isSo = ext == "so"
                    val shouldStore = ext in STORED_EXTENSIONS

                    val entry = ZipEntry(relativePath)
                    
                    val align = if (!shouldStore) 0 else if (isSo) ALIGN_PAGE else ALIGN_4

                    if (shouldStore) {
                        
                        entry.method = ZipEntry.STORED
                        entry.size = file.length()
                        entry.compressedSize = file.length()
                        entry.crc = calculateCrc32(file)

                        
                        if (align > 0) {
                            // 先 flush 内部缓冲：确保 fos.channel.position() 是
                            // local header 的真实起始位置（ZipOutputStream 有 512B 内部缓冲）
                            zos.flush()
                            val nameBytes = relativePath.toByteArray(StandardCharsets.UTF_8)
                            
                            val localHeaderStart = fos.channel.position()
                            
                            val fieldSize = 6
                            val base = localHeaderStart + 30 + nameBytes.size
                            val pad = ((align - (base + fieldSize) % align) % align).toInt()
                            entry.extra = buildAlignExtra(pad, align)
                        }
                    } else {
                        
                        entry.method = ZipEntry.DEFLATED
                    }

                    
                    entry.time = file.lastModified()

                    zos.putNextEntry(entry)
                    file.inputStream().use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()

                    entryCount++
                    totalUncompressed += file.length()
                }
            }
        }

        totalCompressed = outputApk.length()

        log("  打包完成: $entryCount 个条目")
        log("  未压缩大小: ${formatSize(totalUncompressed)}")
        log("  打包后大小: ${formatSize(totalCompressed)}")
        if (totalUncompressed > 0) {
            val ratio = (1.0 - totalCompressed.toDouble() / totalUncompressed) * 100
            log("  压缩率: ${String.format("%.1f", ratio)}%")
        }
    }

       
                    
       
    fun getApkInfo(apkFile: File): Map<String, String> {
        val info = mutableMapOf<String, String>()
        ZipFile(apkFile).use { zip ->
            val manifestEntry = zip.getEntry("AndroidManifest.xml")
            if (manifestEntry != null) {
                info["has_manifest"] = "true"
            }

            val entries = zip.entries()
            var dexCount = 0
            var resCount = 0
            var libCount = 0
            var assetsCount = 0
            var totalSize = 0L

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".dex")) dexCount++
                if (entry.name.startsWith("res/")) resCount++
                if (entry.name.startsWith("lib/")) libCount++
                if (entry.name.startsWith("assets/")) assetsCount++
                totalSize += entry.size
            }
            info["dex_count"] = dexCount.toString()
            info["res_count"] = resCount.toString()
            info["lib_count"] = libCount.toString()
            info["assets_count"] = assetsCount.toString()
            info["total_size"] = totalSize.toString()
            info["file_size"] = apkFile.length().toString()
        }
        return info
    }

       
                                  
       
    private fun calculateCrc32(file: File): Long {
        val crc = java.util.zip.CRC32()
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                crc.update(buffer, 0, read)
            }
        }
        return crc.value
    }

       
                                                    
      
                
                                  
                                   
                                   
                                  
      
                                     
                                     
       
    private fun buildAlignExtra(pad: Int, align: Int): ByteArray {
        val dataSize = 2 + pad
        val xtr = ByteArray(6 + pad)
        var i = 0
        
        xtr[i++] = (ALIGNMENT_FIELD_ID and 0xff).toByte()
        xtr[i++] = ((ALIGNMENT_FIELD_ID shr 8) and 0xff).toByte()
        
        xtr[i++] = (dataSize and 0xff).toByte()
        xtr[i++] = ((dataSize shr 8) and 0xff).toByte()
        
        xtr[i++] = (align and 0xff).toByte()
        xtr[i] = ((align shr 8) and 0xff).toByte()
        
        return xtr
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
        }
    }
}
