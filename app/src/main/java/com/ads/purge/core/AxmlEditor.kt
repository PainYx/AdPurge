package com.ads.purge.core

import java.io.ByteArrayOutputStream

   
                            
  
                                              
                                   
                                           
                                        
  
        
                                      
                                               
                    
  
                                            
                     
                            
                             
                                                            
                                                                             
   
object AxmlEditor {

    
    private const val CHUNK_STRING_POOL = 0x0001
    private const val CHUNK_RES_XML = 0x0003
    private const val CHUNK_RESOURCE_MAP = 0x0180
    private const val CHUNK_START_NAMESPACE = 0x0100
    private const val CHUNK_END_NAMESPACE = 0x0101
    private const val CHUNK_START_ELEMENT = 0x0102
    private const val CHUNK_END_ELEMENT = 0x0103
    private const val CHUNK_CDATA = 0x0104

    private const val FLAG_UTF8 = 0x100
    private const val TYPE_STRING = 0x03
    private const val NO_INDEX = 0xFFFFFFFF.toInt()

                                  
    private val COMPONENT_TAGS = setOf(
        "activity", "activity-alias", "service", "receiver", "provider"
    )

    

                         
    data class ElementInfo(
                                                   
        val tag: String,
                                                
        val androidName: String?,
                          
        val offset: Int,
                           
        val size: Int
    ) {
                                        
        val androidNameLower: String get() = androidName?.lowercase() ?: ""
    }

                          
    data class ComponentInfo(
        val tag: String,
        val name: String
    )

    

       
                                           
       
    fun listPermissions(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        parseElements(bytes) { elem ->
            if (elem.tag == "uses-permission") {
                elem.androidName?.let { result.add(it) }
            }
        }
        return result
    }

       
                                                               
       
    fun listComponents(bytes: ByteArray): List<ComponentInfo> {
        val result = mutableListOf<ComponentInfo>()
        parseElements(bytes) { elem ->
            val androidName = elem.androidName
            if (elem.tag in COMPONENT_TAGS && androidName != null) {
                result.add(ComponentInfo(elem.tag, androidName))
            }
        }
        return result
    }

       
                                           
       
    fun listAllDeclarations(bytes: ByteArray): List<ElementInfo> {
        val result = mutableListOf<ElementInfo>()
        parseElements(bytes) { elem ->
            if (elem.tag == "uses-permission" || elem.tag in COMPONENT_TAGS) {
                result.add(elem)
            }
        }
        return result
    }

       
                            
      
                                                                        
                                        
       
    fun removePermissions(bytes: ByteArray, permissionsToRemove: Set<String>): ByteArray {
        if (permissionsToRemove.isEmpty()) return bytes
        val targets = permissionsToRemove.map { it.lowercase() }.toSet()
        return filterElements(bytes) { elem ->
            elem.tag == "uses-permission" && elem.androidNameLower in targets
        }
    }

       
                                                 
      
                                                                   
                                        
       
    fun removeComponents(bytes: ByteArray, componentNames: Set<String>): ByteArray {
        if (componentNames.isEmpty()) return bytes
        val targets = componentNames.map { it.lowercase() }.toSet()
        return filterElements(bytes) { elem ->
            elem.tag in COMPONENT_TAGS && elem.androidNameLower in targets
        }
    }

       
                                                                          
      
                                                                             
                                                      
                                       
                                                         
      
                                                                       
                                                                
                                       
                            
       
    fun removeAdComponents(
        bytes: ByteArray,
        sdkPackages: List<String>,
        adComponents: List<String>,
        removed: MutableList<ComponentInfo> = mutableListOf()
    ): ByteArray {
        if (sdkPackages.isEmpty() && adComponents.isEmpty()) return bytes

        return filterElements(bytes) { elem ->
            val androidName = elem.androidName
            if (elem.tag !in COMPONENT_TAGS || androidName == null) return@filterElements false

            val isAd = isAdComponentName(androidName, sdkPackages, adComponents)
            if (isAd) {
                removed.add(ComponentInfo(elem.tag, androidName))
            }
            isAd
        }
    }

       
                                                                 
      
                                         
                                      
       
    private fun isAdComponentName(
        name: String,
        sdkPackages: List<String>,
        adComponents: List<String>
    ): Boolean {
        val lower = name.trim().lowercase()
        if (lower.isEmpty()) return false

        val dot = lower.lastIndexOf('.')
        val simpleName = if (dot >= 0 && dot < lower.length - 1) lower.substring(dot + 1) else lower
        val pkg = if (dot > 0) lower.substring(0, dot) else lower

        
        val sdkPrefixes = sdkPackages
            .map { it.trim().lowercase().removeSuffix(".") }
            .filter { p -> p.isNotEmpty() && !p.startsWith(".") && !p.endsWith(".") && p.contains('.') }
        for (sdk in sdkPrefixes) {
            if (pkg == sdk) return true
            if (pkg.startsWith("$sdk.")) return true
        }

        
        val keywords = adComponents
            .map { it.trim().lowercase() }
            .filter { it.length >= 4 }
            .toSet()
        for (kw in keywords) {
            if (simpleName == kw) return true
            if (simpleName.length > kw.length && simpleName.endsWith(kw)) return true
        }
        return false
    }

    

       
                                                 
      
                                                    
                                                
                                 
       
    fun filterElements(
        bytes: ByteArray,
        predicate: (ElementInfo) -> Boolean
    ): ByteArray {
        if (!isAxml(bytes)) return bytes

        val pool = locateStringPool(bytes)
        val out = ByteArrayOutputStream(bytes.size)
        val dropStack = ArrayDeque<Boolean>()
        var offset = 0
        val length = bytes.size
        var removedCount = 0

        
        
        
        if (length >= 8) {
            val headerType = u16(bytes, 0)
            val headerSize = u16(bytes, 2)
            if (headerType == CHUNK_RES_XML) {
                val copyLen = minOf(headerSize, length)
                out.write(bytes, 0, copyLen)
                offset = copyLen
            }
        }

        while (offset < length) {
            if (length - offset < 8) {
                out.write(bytes, offset, length - offset)
                break
            }
            val type = u16(bytes, offset)
            val chunkSize = u32(bytes, offset + 4)
            if (chunkSize < 8 || offset + chunkSize > length) {
                
                out.write(bytes, offset, length - offset)
                break
            }

            when (type) {
                CHUNK_START_ELEMENT -> {
                    val elem = parseStartElement(bytes, offset, chunkSize, pool.strings)
                    val parentDrop = dropStack.lastOrNull() == true
                    val drop = if (parentDrop) true else {
                        try {
                            predicate(elem)
                        } catch (_: Exception) {
                            false
                        }
                    }
                    if (drop) removedCount++
                    dropStack.add(drop)
                    if (!drop) out.write(bytes, offset, chunkSize)
                }

                CHUNK_END_ELEMENT -> {
                    val drop = dropStack.removeLastOrNull() ?: false
                    if (!drop) out.write(bytes, offset, chunkSize)
                }

                CHUNK_CDATA -> {
                    
                    if (dropStack.lastOrNull() != true) out.write(bytes, offset, chunkSize)
                }

                else -> {
                    
                    if (dropStack.lastOrNull() != true) out.write(bytes, offset, chunkSize)
                }
            }
            offset += chunkSize
        }

        return if (removedCount > 0) {
            val result = out.toByteArray()
            
            
            
            if (result.size >= 8 && u16(result, 0) == CHUNK_RES_XML) {
                val total = result.size
                result[4] = (total and 0xFF).toByte()
                result[5] = ((total shr 8) and 0xFF).toByte()
                result[6] = ((total shr 16) and 0xFF).toByte()
                result[7] = ((total shr 24) and 0xFF).toByte()
            }
            result
        } else {
            bytes
        }
    }

    

       
                                                 
       
    private fun parseElements(bytes: ByteArray, visitor: (ElementInfo) -> Unit) {
        if (!isAxml(bytes)) return
        val pool = locateStringPool(bytes)
        var offset = 0
        val length = bytes.size

        
        if (length >= 8 && u16(bytes, 0) == CHUNK_RES_XML) {
            offset = minOf(u16(bytes, 2), length)
        }

        while (offset < length) {
            if (length - offset < 8) break
            val type = u16(bytes, offset)
            val chunkSize = u32(bytes, offset + 4)
            if (chunkSize < 8 || offset + chunkSize > length) break

            if (type == CHUNK_START_ELEMENT) {
                try {
                    val elem = parseStartElement(bytes, offset, chunkSize, pool.strings)
                    visitor(elem)
                } catch (_: Exception) {
                    
                }
            }
            offset += chunkSize
        }
    }

    

    private data class StringPool(
        val strings: MutableList<String>,
        val isUtf8: Boolean,
                               
        val poolOffset: Int
    ) {
        companion object {
            val EMPTY = StringPool(mutableListOf(), false, -1)
        }
    }

       
                                 
                              
       
    private fun locateStringPool(bytes: ByteArray): StringPool {
        if (!isAxml(bytes)) return StringPool.EMPTY
        val poolOffset = u16(bytes, 2) 
        if (poolOffset + 8 > bytes.size) return StringPool.EMPTY
        val type = u16(bytes, poolOffset)
        if (type != CHUNK_STRING_POOL) return StringPool.EMPTY
        val poolSize = u32(bytes, poolOffset + 4)
        if (poolSize < 8 || poolOffset + poolSize > bytes.size) return StringPool.EMPTY
        return parseStringPool(bytes, poolOffset, poolSize)
    }

       
                            
                              
       
    private fun parseStringPool(bytes: ByteArray, offset: Int, size: Int): StringPool {
        val stringCount = u32(bytes, offset + 8)
        val flags = u32(bytes, offset + 16)
        val stringsStart = u32(bytes, offset + 20)
        val isUtf8 = (flags and FLAG_UTF8) != 0

        val strings = mutableListOf<String>()
        if (stringCount == 0 || stringCount > 1_000_000) return StringPool(strings, isUtf8, offset)
        if (stringCount.toLong() * 4 + offset + 28 > bytes.size) return StringPool(strings, isUtf8, offset)

        var poolBase = offset + 28
        val poolEnd = offset + size
        val dataBase = offset + stringsStart

        for (i in 0 until stringCount) {
            val strOffset = u32(bytes, poolBase + i * 4)
            if (strOffset < 0) {
                strings.add("")
                continue
            }
            val pos = dataBase + strOffset
            if (pos < 0 || pos >= poolEnd) {
                strings.add("")
                continue
            }
            try {
                strings.add(
                    if (isUtf8) readUtf8String(bytes, pos, poolEnd)
                    else readUtf16String(bytes, pos, poolEnd)
                )
            } catch (_: Exception) {
                strings.add("")
            }
        }
        return StringPool(strings, isUtf8, offset)
    }

                                            
    private fun readUtf8String(bytes: ByteArray, pos: Int, poolEnd: Int): String {
        var p = pos
        var byteLen = bytes[p].toInt() and 0xFF
        p++
        if (byteLen and 0x80 != 0) {
            byteLen = ((byteLen and 0x7F) shl 8) or (bytes[p].toInt() and 0xFF)
            p++
        }
        
        val charLen = bytes[p].toInt() and 0xFF
        p++
        if (charLen and 0x80 != 0) {
            p++
        }
        if (p + byteLen > poolEnd || p + byteLen > bytes.size) return ""
        if (byteLen <= 0) return ""
        return String(bytes, p, byteLen, Charsets.UTF_8)
    }

                        
    private fun readUtf16String(bytes: ByteArray, pos: Int, poolEnd: Int): String {
        var p = pos
        var len = u16(bytes, p) and 0x7FFF
        p += 2
        if ((u16(bytes, p - 2) and 0x8000) != 0) {
            
            len = ((u16(bytes, p - 2) and 0x7FFF) shl 16) or u16(bytes, p)
            p += 2
        }
        if (p + len * 2 > poolEnd || p + len * 2 > bytes.size) return ""
        if (len <= 0) return ""
        return String(bytes, p, len * 2, Charsets.UTF_16LE)
    }

    

       
                                                            
               
                                          
                                     
                          
                                                                                                        
                                              
                           
                                           
                                                            
       
    private fun parseStartElement(
        bytes: ByteArray,
        offset: Int,
        chunkSize: Int,
        strings: List<String>
    ): ElementInfo {
        val elemNameIdx = u32(bytes, offset + 20)
        val name = getString(strings, elemNameIdx)
        if (name.isNullOrEmpty()) return ElementInfo("", null, offset, chunkSize)

        var androidName: String? = null

        val attrStart = u16(bytes, offset + 24)
        val attrSize = u16(bytes, offset + 26)
        val attrCount = u16(bytes, offset + 28)

        val attrBase = offset + 16 + attrStart
        if (attrSize >= 20 && attrCount > 0 && attrBase + attrCount * attrSize <= offset + chunkSize) {
            for (i in 0 until attrCount) {
                val ab = attrBase + i * attrSize
                val attrNameIdx = u32(bytes, ab + 4)
                val attrName = getString(strings, attrNameIdx) ?: continue
                if (attrName == "name") {
                    val rawIdx = u32(bytes, ab + 8)
                    val typedType = bytes[ab + 15].toInt() and 0xFF
                    val typedData = u32(bytes, ab + 16)
                    
                    val valueIdx = if (rawIdx != NO_INDEX && rawIdx >= 0 && rawIdx < strings.size) {
                        rawIdx
                    } else if (typedType == TYPE_STRING && typedData != NO_INDEX && typedData >= 0 && typedData < strings.size) {
                        typedData
                    } else {
                        NO_INDEX
                    }
                    androidName = getString(strings, valueIdx)
                    break
                }
            }
        }

        return ElementInfo(name, androidName, offset, chunkSize)
    }

    private fun getString(strings: List<String>, idx: Int): String? {
        return if (idx != NO_INDEX && idx >= 0 && idx < strings.size) strings[idx] else null
    }

    

                                      
    fun isAxml(bytes: ByteArray): Boolean {
        return bytes.size >= 8 && u16(bytes, 0) == CHUNK_RES_XML
    }

                     
    private fun u16(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

                     
    private fun u32(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 4 > bytes.size) return NO_INDEX
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}
