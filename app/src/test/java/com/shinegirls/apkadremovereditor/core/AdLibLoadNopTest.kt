package com.ads.purge.core

import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.immutable.ImmutableClassDef
import org.jf.dexlib2.immutable.ImmutableDexFile
import org.jf.dexlib2.immutable.ImmutableMethod
import org.jf.dexlib2.immutable.ImmutableMethodImplementation
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21c
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction35c
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference
import org.jf.dexlib2.immutable.reference.ImmutableStringReference
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

   
                        
                                                        
                                          
   
class AdLibLoadNopTest {

    private fun writeDex(dir: File, clazz: ImmutableClassDef): File {
        val dexFile = File(dir, "classes.dex")
        DexFileFactory.writeDexFile(dexFile.absolutePath, ImmutableDexFile(Opcodes.getDefault(), listOf(clazz)))
        return dexFile
    }

    private fun loadLibraryClinit(className: String, methodRef: ImmutableMethodReference): ImmutableClassDef {
        
        val impl = ImmutableMethodImplementation(
            2,
            listOf(
                ImmutableInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("ttad")),
                ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 0, 1, 0, 0, 0, methodRef),
                ImmutableInstruction10x(Opcode.RETURN_VOID)
            ),
            emptyList(),
            emptyList()
        )
        val clinit = ImmutableMethod(
            className, "<clinit>", emptyList(), "V", 0x8,
            emptySet(), emptySet(), impl
        )
        return ImmutableClassDef(
            className, 1, "Ljava/lang/Object;",
            emptyList(), null, emptySet(), emptyList(), listOf(clinit)
        )
    }

    @Test
    fun testSystemLoadLibraryForAdLibIsNopd() {
        
        val ref = ImmutableMethodReference(
            "Ljava/lang/System;", "loadLibrary",
            listOf("Ljava/lang/String;"), "V"
        )
        val clazz = loadLibraryClinit("Lcom/test/NativeInit;", ref)
        val dir = Files.createTempDirectory("libnop").toFile()
        val dexFile = writeDex(dir, clazz)

        val result = DexPatcher.patchDex(
            dexFile,
            adPatterns = listOf("com/bytedance"),
            adMethodNames = listOf("showAd"),
            adLibKeywords = listOf("ttad", "pangle"),
            logger = {}
        )
        assertEquals("应 NOP 1 处广告 so 加载调用", 1, result.nopLoadLibrary)

        
        val reloaded = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault())
        val ins = reloaded.classes.first().methods.first().implementation?.instructions?.toList()
        assertEquals("指令数 = const-string + 3×NOP + return-void = 5", 5, ins?.size)
        assertEquals(Opcode.CONST_STRING, ins?.get(0)?.opcode)
        assertEquals(Opcode.NOP, ins?.get(1)?.opcode)
        assertEquals(Opcode.NOP, ins?.get(2)?.opcode)
        assertEquals(Opcode.NOP, ins?.get(3)?.opcode)
        assertEquals(Opcode.RETURN_VOID, ins?.get(4)?.opcode)
    }

    @Test
    fun testRuntimeLoadLibraryForAdLibIsNopd() {
        
        val ref = ImmutableMethodReference(
            "Ljava/lang/Runtime;", "loadLibrary",
            listOf("Ljava/lang/String;"), "V"
        )
        val clazz = loadLibraryClinit("Lcom/test/NativeInit2;", ref)
        val dir = Files.createTempDirectory("libnop").toFile()
        val dexFile = writeDex(dir, clazz)

        val result = DexPatcher.patchDex(
            dexFile,
            adPatterns = listOf("com/bytedance"),
            adMethodNames = listOf(),
            adLibKeywords = listOf("ttad"),
            logger = {}
        )
        assertEquals(1, result.nopLoadLibrary)
    }

    @Test
    fun testNonAdLibLoadLibraryKept() {
        
        val ref = ImmutableMethodReference(
            "Ljava/lang/System;", "loadLibrary",
            listOf("Ljava/lang/String;"), "V"
        )
        
        val impl = ImmutableMethodImplementation(
            2,
            listOf(
                ImmutableInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("crypto")),
                ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 0, 1, 0, 0, 0, ref),
                ImmutableInstruction10x(Opcode.RETURN_VOID)
            ),
            emptyList(),
            emptyList()
        )
        val clinit = ImmutableMethod(
            "Lcom/test/NativeInit;", "<clinit>", emptyList(), "V", 0x8,
            emptySet(), emptySet(), impl
        )
        val clazz = ImmutableClassDef(
            "Lcom/test/NativeInit;", 1, "Ljava/lang/Object;",
            emptyList(), null, emptySet(), emptyList(), listOf(clinit)
        )
        val dir = Files.createTempDirectory("libnop").toFile()
        val dexFile = writeDex(dir, clazz)

        val result = DexPatcher.patchDex(
            dexFile,
            adPatterns = listOf("com/bytedance"),
            adMethodNames = listOf(),
            adLibKeywords = listOf("ttad"),
            logger = {}
        )
        assertEquals("非广告库名不应被 NOP", 0, result.nopLoadLibrary)

        val reloaded = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault())
        val ins = reloaded.classes.first().methods.first().implementation?.instructions?.toList()
        assertEquals("invoke 应保留，指令数仍为 3", 3, ins?.size)
        assertEquals(Opcode.INVOKE_STATIC, ins?.get(1)?.opcode)
    }

    @Test
    fun testCustomClassLoadLibraryKept() {
        
        val ref = ImmutableMethodReference(
            "Lcom/test/MyNativeHelper;", "loadLibrary",
            listOf("Ljava/lang/String;"), "V"
        )
        val clazz = loadLibraryClinit("Lcom/test/NativeInit3;", ref)
        val dir = Files.createTempDirectory("libnop").toFile()
        val dexFile = writeDex(dir, clazz)

        val result = DexPatcher.patchDex(
            dexFile,
            adPatterns = listOf("com/bytedance"),
            adMethodNames = listOf(),
            adLibKeywords = listOf("ttad"),
            logger = {}
        )
        assertEquals("自定义类 loadLibrary 不应被 NOP", 0, result.nopLoadLibrary)
    }
}
