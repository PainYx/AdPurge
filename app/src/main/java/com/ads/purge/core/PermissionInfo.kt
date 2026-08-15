package com.ads.purge.core

   
                 
  
                                 
                   
   
object PermissionInfo {

                    
    data class PermissionItem(
                                                  
        val name: String,
                    
        val label: String,
                            
        val dangerous: Boolean,
                                   
        val group: String
    )

                          
    private val DANGEROUS_LABELS = mapOf(
        "android.permission.READ_EXTERNAL_STORAGE" to "读取外部存储",
        "android.permission.WRITE_EXTERNAL_STORAGE" to "写入外部存储",
        "android.permission.READ_MEDIA_IMAGES" to "读取媒体图片",
        "android.permission.READ_MEDIA_VIDEO" to "读取媒体视频",
        "android.permission.READ_MEDIA_AUDIO" to "读取媒体音频",
        "android.permission.ACCESS_FINE_LOCATION" to "精确位置",
        "android.permission.ACCESS_COARSE_LOCATION" to "大致位置",
        "android.permission.ACCESS_BACKGROUND_LOCATION" to "后台位置",
        "android.permission.CAMERA" to "相机",
        "android.permission.RECORD_AUDIO" to "录音",
        "android.permission.READ_CONTACTS" to "读取联系人",
        "android.permission.WRITE_CONTACTS" to "写入联系人",
        "android.permission.GET_ACCOUNTS" to "获取账户",
        "android.permission.READ_PHONE_STATE" to "读取手机状态",
        "android.permission.READ_PHONE_NUMBERS" to "读取手机号码",
        "android.permission.CALL_PHONE" to "拨打电话",
        "android.permission.READ_CALL_LOG" to "读取通话记录",
        "android.permission.WRITE_CALL_LOG" to "写入通话记录",
        "android.permission.ADD_VOICEMAIL" to "添加语音邮件",
        "android.permission.USE_SIP" to "使用SIP",
        "android.permission.PROCESS_OUTGOING_CALLS" to "监听外拨电话",
        "android.permission.READ_SMS" to "读取短信",
        "android.permission.SEND_SMS" to "发送短信",
        "android.permission.RECEIVE_SMS" to "接收短信",
        "android.permission.RECEIVE_MMS" to "接收彩信",
        "android.permission.RECEIVE_WAP_PUSH" to "接收WAP推送",
        "android.permission.BODY_SENSORS" to "身体传感器",
        "android.permission.ACTIVITY_RECOGNITION" to "运动识别",
        "android.permission.POST_NOTIFICATIONS" to "发送通知",
        "android.permission.BLUETOOTH_SCAN" to "扫描蓝牙设备",
        "android.permission.BLUETOOTH_CONNECT" to "连接蓝牙设备",
        "android.permission.BLUETOOTH_ADVERTISE" to "蓝牙广播",
        "android.permission.NEARBY_WIFI_DEVICES" to "附近WiFi设备",
        "android.permission.READ_CALENDAR" to "读取日历",
        "android.permission.WRITE_CALENDAR" to "写入日历",
        "android.permission.MANAGE_EXTERNAL_STORAGE" to "所有文件访问",
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" to "媒体部分访问"
    )

                          
    private val NORMAL_LABELS = mapOf(
        "android.permission.INTERNET" to "网络访问",
        "android.permission.ACCESS_NETWORK_STATE" to "查看网络状态",
        "android.permission.ACCESS_WIFI_STATE" to "查看WiFi状态",
        "android.permission.CHANGE_WIFI_STATE" to "修改WiFi状态",
        "android.permission.WAKE_LOCK" to "唤醒锁定",
        "android.permission.VIBRATE" to "振动",
        "android.permission.SYSTEM_ALERT_WINDOW" to "悬浮窗",
        "android.permission.REQUEST_INSTALL_PACKAGES" to "安装应用",
        "android.permission.QUERY_ALL_PACKAGES" to "查询全部应用",
        "android.permission.FOREGROUND_SERVICE" to "前台服务",
        "android.permission.FOREGROUND_SERVICE_LOCATION" to "前台定位服务",
        "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" to "前台媒体播放",
        "android.permission.RECEIVE_BOOT_COMPLETED" to "开机启动",
        "android.permission.GET_TASKS" to "获取任务列表",
        "android.permission.REORDER_TASKS" to "重新排序任务",
        "android.permission.CHANGE_NETWORK_STATE" to "修改网络状态",
        "android.permission.CHANGE_WIFI_MULTICAST_STATE" to "多播网络",
        "android.permission.NFC" to "近场通信",
        "android.permission.BLUETOOTH" to "蓝牙",
        "android.permission.MODIFY_AUDIO_SETTINGS" to "修改音频设置",
        "android.permission.USE_FINGERPRINT" to "指纹",
        "android.permission.USE_BIOMETRIC" to "生物识别",
        "android.permission.KILL_BACKGROUND_PROCESSES" to "结束后台进程",
        "android.permission.SET_ALARM" to "设置闹钟",
        "android.permission.DISABLE_KEYGUARD" to "禁用锁屏",
        "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to "忽略电池优化",
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to "无障碍服务",
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" to "通知使用权",
        "android.permission.PACKAGE_USAGE_STATS" to "使用情况访问",
        "com.google.android.gms.permission.AD_ID" to "广告标识",
        "com.android.launcher.permission.INSTALL_SHORTCUT" to "创建桌面快捷方式",
        "android.permission.WRITE_SETTINGS" to "修改系统设置",
        "android.permission.READ_LOGS" to "读取系统日志",
        "android.permission.DUMP" to "系统诊断",
        "android.permission.BROADCAST_PACKAGE_REMOVED" to "应用卸载广播",
        "android.permission.BROADCAST_PACKAGE_ADDED" to "应用安装广播",
        "android.permission.MOUNT_UNMOUNT_FILESYSTEMS" to "挂载文件系统",
        "android.permission.DEVICE_POWER" to "电源管理",
        "android.permission.CHANGE_COMPONENT_ENABLED_STATE" to "修改组件状态",
        "android.permission.SET_WALLPAPER" to "设置壁纸",
        "android.permission.SET_WALLPAPER_HINTS" to "设置壁纸提示",
        "android.permission.EXPAND_STATUS_BAR" to "展开状态栏",
        "android.permission.STATUS_BAR" to "状态栏",
        "android.permission.RESTART_PACKAGES" to "重启应用",
        "android.permission.ACCESS_MEDIA_LOCATION" to "读取媒体位置"
    )

       
                   
                                 
       
    fun resolve(name: String): PermissionItem {
        val trimmed = name.trim()
        DANGEROUS_LABELS[trimmed]?.let {
            return PermissionItem(trimmed, it, dangerous = true, group = "运行时权限")
        }
        NORMAL_LABELS[trimmed]?.let {
            return PermissionItem(trimmed, it, dangerous = false, group = "普通权限")
        }
        return PermissionItem(trimmed, trimmed.substringAfterLast('.'), dangerous = false, group = "其他权限")
    }

       
                      
       
    fun dangerText(name: String): String = if (resolve(name).dangerous) "危险" else "普通"
}
