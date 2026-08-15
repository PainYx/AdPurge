package com.ads.purge.core

import org.jf.baksmali.Baksmali
import org.jf.baksmali.BaksmaliOptions
import org.jf.smali.Smali
import org.jf.smali.SmaliOptions
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.DexFile
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import java.io.File

   
                                                                                   
  
                             
                                                                  
                                                                                    
                                                                                             
                                                
   
object DexHandler {

    private const val JOBS = 6

       
                                 
       
    fun decompileDex(dexFile: File, outputDir: File) {
        if (!outputDir.exists()) outputDir.mkdirs()

        val dex: DexBackedDexFile = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault())

        val options = BaksmaliOptions().apply {
            deodex = false
            parameterRegisters = true
            localsDirective = false
            sequentialLabels = false
            debugInfo = true
            codeOffsets = false
            accessorComments = true
            normalizeVirtualMethods = false
            implicitReferences = false
            registerInfo = 0
        }

        Baksmali.disassembleDexFile(dex, outputDir, JOBS, options)
    }

       
                                
       
    fun compileSmali(smaliDir: File, outputDex: File) {
        if (outputDex.exists()) outputDex.delete()
        outputDex.parentFile?.mkdirs()

        val options = SmaliOptions().apply {
            outputDexFile = outputDex.absolutePath
            verboseErrors = true
            allowOdexOpcodes = false
        }

        val smaliFiles = mutableListOf<String>()
        smaliDir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension.equals("smali", ignoreCase = true)) {
                smaliFiles.add(file.absolutePath)
            }
        }

        if (smaliFiles.isEmpty()) return

        Smali.assemble(options, smaliFiles)
    }

       
                      
       
    fun getDexInfo(dexFile: File): String {
        val dex: DexFile = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault())

        val sb = StringBuilder()
        sb.appendLine("文件: ${dexFile.name}")
        sb.appendLine("大小: ${dexFile.length()} bytes")

        val classes = dex.classes
        sb.appendLine("类数量: ${classes.size}")

        var methodCount = 0
        var fieldCount = 0
        for (classDef in classes) {
            methodCount += classDef.methods.count()
            fieldCount += classDef.fields.count()
        }
        sb.appendLine("方法数量: $methodCount")
        sb.appendLine("字段数量: $fieldCount")

        val packageMap = mutableMapOf<String, Int>()
        for (classDef in classes) {
            val className = classDef.type
            
            val packageName = className.substring(1).substringBeforeLast('/', "default")
            packageMap[packageName] = packageMap.getOrDefault(packageName, 0) + 1
        }

        sb.appendLine("\n包分布:")
        packageMap.entries.sortedByDescending { it.value }.take(10).forEach {
            sb.appendLine("  ${it.key}: ${it.value} 个类")
        }

        return sb.toString()
    }
}
