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
import org.jf.dexlib2.immutable.reference.ImmutableStringReference
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

   
                                           
                                      
                                                                      
                                   
   
class AdCallbackPreserveTest {

    private fun methodWithBody(className: String, name: String): ImmutableMethod {
        val impl = ImmutableMethodImplementation(
            1,
            listOf(
                ImmutableInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("ad_unit")),
                ImmutableInstruction10x(Opcode.RETURN_VOID)
            ),
            emptyList(),
            emptyList()
        )
        return ImmutableMethod(
            className, name, emptyList(), "V", 0x1,
            emptySet(), emptySet(), impl
        )
    }

    private fun adManagerClass(vararg methods: ImmutableMethod): ImmutableClassDef {
        return ImmutableClassDef(
            "Lcom/bytedance/sdk/AdManager;", 1, "Ljava/lang/Object;",
            emptyList(), null, emptySet(), emptyList(), methods.toList()
        )
    }

    @Test
    fun testAdCallbackAndListenerMethodsKept() {
        val clazz = adManagerClass(
            methodWithBody("Lcom/bytedance/sdk/AdManager;", "showAd"),
            methodWithBody("Lcom/bytedance/sdk/AdManager;", "onAdLoaded"),
            methodWithBody("Lcom/bytedance/sdk/AdManager;", "setRewardedVideoAdListener"),
            methodWithBody("Lcom/bytedance/sdk/AdManager;", "setAdListener")
        )
        val dir = Files.createTempDirectory("adcb").toFile()
        val dexFile = File(dir, "classes.dex")
        DexFileFactory.writeDexFile(
            dexFile.absolutePath,
            ImmutableDexFile(Opcodes.getDefault(), listOf(clazz))
        )

        val result = DexPatcher.patchDex(
            dexFile,
            adPatterns = listOf("com/bytedance"),
            adMethodNames = emptyList(),
            logger = {}
        )
        
        
        assertEquals("仅 showAd 应被置空", 1, result.neutralizedMethods)

        val reloaded = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault())
        val methods = reloaded.classes.first().methods.associateBy { it.name }

        
        val showIns = methods.getValue("showAd").implementation?.instructions?.toList()
        assertEquals("showAd 指令数 = 1", 1, showIns?.size)
        assertEquals(Opcode.RETURN_VOID, showIns?.get(0)?.opcode)

        
        for (kept in listOf("onAdLoaded", "setRewardedVideoAdListener", "setAdListener")) {
            val ins = methods.getValue(kept).implementation?.instructions?.toList()
            assertEquals("$kept 应保留原方法体（2 条指令）", 2, ins?.size)
            assertEquals(Opcode.CONST_STRING, ins?.get(0)?.opcode)
            assertEquals(Opcode.RETURN_VOID, ins?.get(1)?.opcode)
        }
    }
}
