package com.ads.purge.core

import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.immutable.ImmutableClassDef
import org.jf.dexlib2.immutable.ImmutableDexFile
import org.jf.dexlib2.immutable.ImmutableMethod
import org.jf.dexlib2.immutable.ImmutableMethodImplementation
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21s
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11x
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11n
import org.jf.dexlib2.Opcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

   
                                       
   
class DexReturnInstructionTest {

    @Test
    fun testVoidReturnsVoid() {
        val ins = DexPatcher.createReturnInstructions("V")
        assertEquals(1, ins.size)
        assertTrue(ins[0] is ImmutableInstruction10x)
        assertEquals(Opcode.RETURN_VOID, ins[0].opcode)
    }

    @Test
    fun testBooleanReturnsZero() {
        val ins = DexPatcher.createReturnInstructions("Z")
        assertEquals(2, ins.size)
        assertTrue(ins[0] is ImmutableInstruction11n)
        assertEquals(Opcode.CONST_4, ins[0].opcode)
        assertTrue(ins[1] is ImmutableInstruction11x)
        assertEquals(Opcode.RETURN, ins[1].opcode)
    }

    @Test
    fun testLongReturnsWideWithCorrectFormats() {
        
        val ins = DexPatcher.createReturnInstructions("J")
        assertEquals(2, ins.size)
        assertTrue("CONST_WIDE_16 必须用 21s 格式类", ins[0] is ImmutableInstruction21s)
        assertEquals(Opcode.CONST_WIDE_16, ins[0].opcode)
        assertTrue("RETURN_WIDE 必须用 11x 格式类", ins[1] is ImmutableInstruction11x)
        assertEquals(Opcode.RETURN_WIDE, ins[1].opcode)
    }

    @Test
    fun testDoubleReturnsWideWithCorrectFormats() {
        val ins = DexPatcher.createReturnInstructions("D")
        assertEquals(2, ins.size)
        assertTrue(ins[0] is ImmutableInstruction21s)
        assertEquals(Opcode.CONST_WIDE_16, ins[0].opcode)
        assertTrue(ins[1] is ImmutableInstruction11x)
        assertEquals(Opcode.RETURN_WIDE, ins[1].opcode)
    }

    @Test
    fun testFloatReturnsZero() {
        val ins = DexPatcher.createReturnInstructions("F")
        assertEquals(2, ins.size)
        assertTrue(ins[0] is ImmutableInstruction11n)
        assertEquals(Opcode.CONST_4, ins[0].opcode)
        assertTrue(ins[1] is ImmutableInstruction11x)
        assertEquals(Opcode.RETURN, ins[1].opcode)
    }

    @Test
    fun testObjectReturnsNull() {
        val ins = DexPatcher.createReturnInstructions("Lcom/example/AdView;")
        assertEquals(2, ins.size)
        assertTrue(ins[1] is ImmutableInstruction11x)
        assertEquals(Opcode.RETURN_OBJECT, ins[1].opcode)
    }

    @Test
    fun testReferenceReturnTypeDetection() {
        assertTrue(DexPatcher.isReferenceReturnType("Lcom/example/AdView;"))
        assertTrue(DexPatcher.isReferenceReturnType("[Ljava/lang/String;"))
        assertFalse(DexPatcher.isReferenceReturnType("V"))
        assertFalse(DexPatcher.isReferenceReturnType("Z"))
        assertFalse(DexPatcher.isReferenceReturnType("I"))
        assertFalse(DexPatcher.isReferenceReturnType("J"))
        assertFalse(DexPatcher.isReferenceReturnType(""))
    }

    @Test
    fun testAllGeneratedInstructionsAreImmutable() {
        val types = listOf("V", "Z", "B", "S", "C", "I", "J", "F", "D", "Ljava/lang/Object;", "[I")
        for (t in types) {
            val ins = DexPatcher.createReturnInstructions(t)
            ins.forEach { assertTrue("指令应均为 ImmutableInstruction 实例", it is ImmutableInstruction) }
        }
    }

       
                    
                                                 
                                                   
                                           
       
    @Test
    fun testPatchDexOnlyNeutralizesVoidAdMethods() {
        val dir = Files.createTempDirectory("dexrt").toFile()

        
        val showImpl = ImmutableMethodImplementation(
            1,
            listOf(ImmutableInstruction10x(Opcode.RETURN_VOID)),
            emptyList(),
            emptyList()
        )
        val showMethod = ImmutableMethod(
            "Lcom/example/Ad;", "showAd", emptyList(), "V", 1,
            emptySet(), emptySet(), showImpl
        )

        
        val posImpl = ImmutableMethodImplementation(
            2,
            listOf(
                ImmutableInstruction21s(Opcode.CONST_WIDE_16, 0, 5),
                ImmutableInstruction11x(Opcode.RETURN_WIDE, 0)
            ),
            emptyList(),
            emptyList()
        )
        val posMethod = ImmutableMethod(
            "Lcom/example/Ad;", "getAdPosition", emptyList(), "J", 1,
            emptySet(), emptySet(), posImpl
        )
        val clazz = ImmutableClassDef(
            "Lcom/example/Ad;", 1, "Ljava/lang/Object;",
            emptyList(), null, emptySet(), emptyList(), listOf(showMethod, posMethod)
        )
        val dexFile = File(dir, "classes.dex")
        DexFileFactory.writeDexFile(dexFile.absolutePath, ImmutableDexFile(Opcodes.getDefault(), listOf(clazz)))

        val result = DexPatcher.patchDex(
            dexFile,
            adPatterns = listOf("com/example/Ad"),
            adMethodNames = listOf("showAd", "getAdPosition"),
            logger = {}
        )
        assertEquals("仅 void 广告方法被置空，应恰好 1 个", 1, result.neutralizedMethods)

        
        val reloaded = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault())
        val patchedClass = reloaded.classes.first()

        val showIns = patchedClass.methods.first { it.name == "showAd" }.implementation?.instructions?.toList()
        assertEquals("showAd 应置空为单条 return-void", 1, showIns?.size)
        assertEquals(Opcode.RETURN_VOID, showIns?.get(0)?.opcode)

        val posIns = patchedClass.methods.first { it.name == "getAdPosition" }.implementation?.instructions?.toList()
        assertEquals("getAdPosition 应保留原 2 条指令", 2, posIns?.size)
        assertEquals(Opcode.CONST_WIDE_16, posIns?.get(0)?.opcode)
        assertEquals(Opcode.RETURN_WIDE, posIns?.get(1)?.opcode)
    }
}
