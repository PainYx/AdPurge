package com.ads.purge.core

import java.io.File

   
                           
  
                                                      
                                  
                                                             
  
                                                 
                                                
                                                       
                                 
                                                           
  
          
                                                                
                                                           
   
object AdManifestCleaner {

       
              
       
    data class CleanResult(
        val removedPermissions: List<String> = emptyList(),
        val removedComponents: List<AxmlEditor.ComponentInfo> = emptyList()
    ) {
        val totalRemoved: Int get() = removedPermissions.size + removedComponents.size
    }

       
                                    
      
                                         
                                    
                                                          
                                                             
                                                              
                                                
       
    fun cleanManifest(
        extractDir: File,
        config: AdPatternConfig.AdPatterns,
        removeComponents: Boolean = false
    ): CleanResult {
        val manifestFile = File(extractDir, "AndroidManifest.xml")
        if (!manifestFile.exists()) return CleanResult()

        val bytes = try {
            manifestFile.readBytes()
        } catch (_: Exception) {
            return CleanResult()
        }
        if (!AxmlEditor.isAxml(bytes)) return CleanResult()

        var current = bytes

        
        val removedPermissions = mutableListOf<String>()
        val adPermissions = config.adPermissions
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (adPermissions.isNotEmpty()) {
            val targetSet = adPermissions.toSet()
            val existing = AxmlEditor.listPermissions(bytes).toSet()
            val toRemove = targetSet.intersect(existing)
            if (toRemove.isNotEmpty()) {
                current = AxmlEditor.removePermissions(current, toRemove)
                removedPermissions.addAll(toRemove)
            }
        }

        
        val removedComponents = mutableListOf<AxmlEditor.ComponentInfo>()
        if (removeComponents && current.isNotEmpty()) {
            current = AxmlEditor.removeAdComponents(
                current,
                sdkPackages = config.sdkPackages,
                adComponents = config.adActivities + config.adServices + config.adReceivers,
                removed = removedComponents
            )
        }

        
        if (removedPermissions.isNotEmpty() || removedComponents.isNotEmpty()) {
            try {
                manifestFile.writeBytes(current)
            } catch (_: Exception) {
                
            }
        }

        return CleanResult(removedPermissions, removedComponents)
    }
}
