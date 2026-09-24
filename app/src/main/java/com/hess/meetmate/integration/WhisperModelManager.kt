package com.hess.meetmate.integration

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.io.RandomAccessFile

enum class ModelDownloadState { NOT_DOWNLOADED, DOWNLOADING, READY, INVALID, FAILED }

data class WhisperModelInfo(
    val id: String,
    val label: String,
    val sizeLabel: String,
    val sizeBytes: Long,
    val url: String,
    val file: File,
    val state: ModelDownloadState,
    val progress: Int,
    val active: Boolean,
    val recommended: Boolean,
    val storageWarning: Boolean,
    val downloadId: Long?
) { val downloaded get() = state == ModelDownloadState.READY; val size get() = sizeLabel }

/** Local GGML model lifecycle: download, progress, validation, delete, active selection, and recommendations. */
class WhisperModelManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("whisper_models", Context.MODE_PRIVATE)
    private val directory = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir, "whisper-models").apply { mkdirs() }
    private val downloads get() = appContext.getSystemService(DownloadManager::class.java)

    fun snapshot(): List<WhisperModelInfo> {
        val recommendation = recommendedId()
        if (!preferences.contains(ACTIVE_KEY)) preferences.edit().putString(ACTIVE_KEY, recommendation).apply()
        return specs.map { spec ->
            val file = File(directory, "ggml-${spec.id}.bin")
            val part = File(directory, "ggml-${spec.id}.bin.part")
            val id = preferences.getLong(downloadKey(spec.id), -1L).takeIf { it > 0 }
            val query = id?.let(::queryDownload)
            if (query?.first == DownloadManager.STATUS_SUCCESSFUL && part.exists()) {
                if (isValid(part)) part.renameTo(file) else part.delete()
            }
            val state = when {
                file.exists() && isValid(file) -> ModelDownloadState.READY
                file.exists() -> ModelDownloadState.INVALID
                query?.first == DownloadManager.STATUS_RUNNING || query?.first == DownloadManager.STATUS_PENDING -> ModelDownloadState.DOWNLOADING
                query?.first == DownloadManager.STATUS_FAILED -> ModelDownloadState.FAILED
                part.exists() -> ModelDownloadState.DOWNLOADING
                else -> ModelDownloadState.NOT_DOWNLOADED
            }
            if (state == ModelDownloadState.READY && query?.first == DownloadManager.STATUS_SUCCESSFUL) preferences.edit().remove(downloadKey(spec.id)).apply()
            WhisperModelInfo(spec.id, spec.label, spec.sizeLabel, spec.sizeBytes, spec.url, file, state, query?.second ?: if (state == ModelDownloadState.READY) 100 else 0, preferences.getString(ACTIVE_KEY, null) == spec.id, recommendation == spec.id, availableBytes() < spec.sizeBytes + MIN_FREE_BYTES, id)
        }
    }
    /** Compatibility alias for simple clients; the settings screen uses the live snapshot. */
    fun available(): List<WhisperModelInfo> = snapshot()

    fun activeModel(): WhisperModelInfo? {
        val current = snapshot()
        current.firstOrNull { it.active && it.downloaded }?.let { return it }
        val candidate = current.firstOrNull { it.recommended && it.downloaded } ?: current.firstOrNull { it.downloaded }
        candidate?.let { preferences.edit().putString(ACTIVE_KEY, it.id).apply() }
        return candidate
    }

    fun enqueueDownload(id: String): Long {
        val spec = specs.first { it.id == id }
        require(availableBytes() >= spec.sizeBytes + MIN_FREE_BYTES) { "Not enough storage for ${spec.label}" }
        val destination = File(directory, "ggml-${spec.id}.bin.part")
        if (destination.exists()) destination.delete()
        val request = DownloadManager.Request(Uri.parse(spec.url)).setTitle("MeetMate · ${spec.label} Whisper").setDescription("Downloading offline transcription model").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationUri(Uri.fromFile(destination))
        return downloads.enqueue(request).also { preferences.edit().putLong(downloadKey(id), it).apply() }
    }

    fun delete(id: String) {
        val model = snapshot().first { it.id == id }
        model.downloadId?.let { runCatching { downloads.remove(it) } }
        model.file.delete(); File(directory, "ggml-$id.bin.part").delete()
        preferences.edit().remove(downloadKey(id)).apply()
        if (preferences.getString(ACTIVE_KEY, null) == id) preferences.edit().remove(ACTIVE_KEY).apply()
    }

    fun select(id: String) { require(snapshot().first { it.id == id }.downloaded) { "Download and validate this model first" }; preferences.edit().putString(ACTIVE_KEY, id).apply() }
    fun availableBytes(): Long = runCatching { StatFs(directory.absolutePath).availableBytes }.getOrDefault(0L)
    fun recommendedId(): String {
        val memory = ActivityManager.MemoryInfo().also { appContext.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val ram = memory.totalMem; val cores = Runtime.getRuntime().availableProcessors(); val free = availableBytes()
        // Meeting transcription defaults to Small when the device can sustain it; Base is used on smaller devices.
        return if (ram >= 4L * GB && cores >= 4 && free >= 800L * MB) "small" else "base"
    }

    private fun isValid(file: File): Boolean = runCatching {
        if (file.length() < MIN_VALID_MODEL_BYTES) return false
        RandomAccessFile(file, "r").use { Integer.reverseBytes(it.readInt()) == GGML_MAGIC }
    }.getOrDefault(false)
    private fun queryDownload(id: Long): Pair<Int, Int>? = runCatching { DownloadManager.Query().setFilterById(id).let { query -> downloads.query(query).use { cursor -> if (!cursor.moveToFirst()) null else cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) to cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)).let { done -> val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)); if (total > 0) (done * 100L / total).toInt() else 0 } } } }.getOrNull()
    private fun downloadKey(id: String) = "download_$id"
    private data class Spec(val id: String, val label: String, val sizeLabel: String, val sizeBytes: Long, val url: String)
    private val specs = listOf(
        Spec("tiny", "Tiny", "75 MB", 75L * MB, modelUrl("tiny")), Spec("base", "Base", "142 MB", 142L * MB, modelUrl("base")), Spec("small", "Small", "466 MB", 466L * MB, modelUrl("small")), Spec("medium", "Medium", "1.5 GB", 1500L * MB, modelUrl("medium")), Spec("large-v3", "Large v3", "3.1 GB", 3100L * MB, modelUrl("large-v3"))
    )
    private fun modelUrl(id: String) = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-$id.bin"
    companion object { private const val ACTIVE_KEY = "active_model"; private const val GGML_MAGIC = 0x67676d6c; private const val MB = 1024L * 1024L; private const val GB = 1024L * MB; private const val MIN_FREE_BYTES = 256L * MB; private const val MIN_VALID_MODEL_BYTES = 1024L * 1024L }
}
