package com.ads.purge.utils

import java.io.*
import java.util.zip.*

object ZipUtils {

    fun unzip(zipFile: File, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()

        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val outFile = File(destDir, entry.name)

                if (entry.isDirectory) {
                    outFile.mkdirs()
                    continue
                }

                outFile.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    fun zip(sourceDir: File, outputZip: File, filter: ((File) -> Boolean)? = null) {
        if (outputZip.exists()) outputZip.delete()

        ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isDirectory) return@forEach
                if (filter != null && !filter(file)) return@forEach

                val relativePath = sourceDir.toURI().relativize(file.toURI()).path
                val entry = ZipEntry(relativePath)
                zos.putNextEntry(entry)
                file.inputStream().use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }
}
