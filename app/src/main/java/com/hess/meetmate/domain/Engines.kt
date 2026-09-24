package com.hess.meetmate.domain

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
import com.hess.meetmate.integration.WhisperModelManager
import java.io.File
import java.util.Locale

/** Whisper is the only speech-to-text engine in the app. */
enum class SpeechEngine(val label: String, val detail: String) {
    WHISPER("Whisper offline", "Private, on-device transcription with the selected local model")
}

interface SpeechToTextEngine {
    fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit)
    fun stop()
}

/** Native boundary for whisper.cpp or another bundled Whisper runtime. */
interface WhisperRuntime {
    fun start(model: File, onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit)
    fun stop()
}

/**
 * JNI-backed microphone loop for whisper.cpp. The external functions are the small contract an AAR must
 * implement; when that checkout is absent, the app reports a native setup error without crashing.
 */
class NativeWhisperRuntime : WhisperRuntime {
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private var handle = 0L
    override fun start(model: File, onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit) {
        try {
            WhisperNativeBindings.load()
            handle = WhisperNativeBindings.init(model.absolutePath)
            val minBuffer = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, 16_000 * 2))
            recorder?.startRecording()
            val audio = recorder ?: error("Could not open microphone")
            worker = Thread {
                // Four-second windows keep latency bounded while giving Whisper enough acoustic context.
                val samples = ShortArray(16_000 * 4)
                while (!Thread.currentThread().isInterrupted) {
                    val count = audio.read(samples, 0, samples.size)
                    if (count > 0) {
                        WhisperNativeBindings.transcribe(handle, samples, count)?.takeIf { it.isNotBlank() }?.let {
                            // Emit an intermediate segment immediately, then commit it to the transcript.
                            onPartial(it)
                            onFinal(it)
                        }
                    }
                }
            }.also { it.start() }
        } catch (failure: Throwable) {
            stop(); onError("Whisper runtime unavailable: ${failure.message ?: "add the whisper.cpp Android AAR"}")
        }
    }
    override fun stop() { worker?.interrupt(); worker = null; runCatching { recorder?.stop() }; recorder?.release(); recorder = null; if (handle != 0L) runCatching { WhisperNativeBindings.release(handle) }; handle = 0L }

    /** Test and file-import entry point using the same loaded model and JNI inference path as microphone audio. */
    fun transcribeSample(model: File, pcm: ShortArray): String? {
        stop()
        WhisperNativeBindings.load()
        handle = WhisperNativeBindings.init(model.absolutePath)
        return try { WhisperNativeBindings.transcribe(handle, pcm, pcm.size) } finally { stop() }
    }
}

private object WhisperNativeBindings {
    private var loaded = false
    fun load() { if (!loaded) { System.loadLibrary("meetmate_whisper"); loaded = true } }
    @JvmStatic external fun init(modelPath: String): Long
    @JvmStatic external fun transcribe(handle: Long, samples: ShortArray, count: Int): String?
    @JvmStatic external fun release(handle: Long)
}

class WhisperEngine(context: Context, private val runtime: WhisperRuntime = NativeWhisperRuntime()) : SpeechToTextEngine {
    private val models = WhisperModelManager(context)
    override fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit) {
        val model = models.activeModel() ?: models.snapshot().firstOrNull { it.downloaded }
        if (model == null) onError("Download a Whisper model in Settings to use offline transcription") else runtime.start(model.file, onPartial, onFinal, onError)
    }
    override fun stop() = runtime.stop()
}

typealias WhisperSpeechEngine = WhisperEngine

/** Optional spoken playback. The persian_tts repository belongs behind this TTS boundary, never STT. */
interface TextToSpeechEngine { fun speak(text: String); fun stop() }

class AndroidPersianTtsEngine(context: Context) : TextToSpeechEngine {
    private var ready = false
    private lateinit var tts: TextToSpeech
    init { tts = TextToSpeech(context) { status -> ready = status == TextToSpeech.SUCCESS; if (ready) tts.language = Locale("fa", "IR") } }
    override fun speak(text: String) { if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "meetmate-summary") }
    override fun stop() { tts.stop(); tts.shutdown() }
}

class MeetingRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var currentPath: String? = null; private set
    fun start(): Boolean = runCatching { val file = File(context.filesDir, "meeting_${System.currentTimeMillis()}.m4a"); currentPath = file.absolutePath; @Suppress("DEPRECATION") val nativeRecorder = MediaRecorder(); recorder = nativeRecorder.apply { setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setOutputFile(file.absolutePath); prepare(); start() } }.isSuccess
    fun pause() { recorder?.pause() }
    fun resume() { recorder?.resume() }
    fun stop() { runCatching { recorder?.stop() }; recorder?.release(); recorder = null }
}
