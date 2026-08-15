package com.ads.purge.core

import org.json.JSONObject
import java.io.File

/**
 * 断点续传管理器：每个处理阶段完成后保存 checkpoint，失败后可从中断点恢复。
 *
 * 阶段定义：
 *   EXTRACTED  - APK已解包
 *   SCANNED    - 广告SDK已识别
 *   DEX_PATCHED- DEX修补完成
 *   ASSETS_CLEANED - assets清理完成
 *   MANIFEST_CLEANED - Manifest清理完成
 *   PACKAGED   - 已打包
 *   SIGNED     - 已签名
 */
enum class PipelineStage {
    EXTRACTED, SCANNED, DEX_PATCHED, ASSETS_CLEANED, MANIFEST_CLEANED, PACKAGED, SIGNED
}

class CheckpointManager(private val workDir: File) {

    private val checkpointFile = File(workDir, ".apkad_checkpoint.json")

    fun save(stage: PipelineStage, extra: Map<String, String> = emptyMap()) {
        try {
            val json = JSONObject()
            json.put("stage", stage.name)
            json.put("timestamp", System.currentTimeMillis())
            val extraObj = JSONObject()
            for ((k, v) in extra) extraObj.put(k, v)
            json.put("extra", extraObj)
            checkpointFile.writeText(json.toString())
        } catch (_: Exception) {}
    }

    fun load(): Pair<PipelineStage?, Map<String, String>> {
        return try {
            if (!checkpointFile.exists()) return Pair(null, emptyMap())
            val json = JSONObject(checkpointFile.readText())
            val stage = try { PipelineStage.valueOf(json.getString("stage")) } catch (_: Exception) { null }
            val extraObj = json.optJSONObject("extra") ?: JSONObject()
            val extra = mutableMapOf<String, String>()
            for (key in extraObj.keys()) extra[key] = extraObj.optString(key, "")
            Pair(stage, extra)
        } catch (_: Exception) {
            Pair(null, emptyMap())
        }
    }

    fun clear() {
        try { checkpointFile.delete() } catch (_: Exception) {}
    }

    fun exists(): Boolean = checkpointFile.exists()
}