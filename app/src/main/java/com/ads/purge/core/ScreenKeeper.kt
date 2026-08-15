package com.ads.purge.core

import android.app.Activity
import android.view.WindowManager

   
           
  
                                            
                                      
  
                                  
                                        
                           
   
object ScreenKeeper {

       
              
      
                                             
       
    fun keepOn(activity: Activity) {
        activity.runOnUiThread {
            try {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } catch (_: Exception) {
                
            }
        }
    }

       
                     
      
                                             
       
    fun release(activity: Activity) {
        activity.runOnUiThread {
            try {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } catch (_: Exception) {
                
            }
        }
    }
}