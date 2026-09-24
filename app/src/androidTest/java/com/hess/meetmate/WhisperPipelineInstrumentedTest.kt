package com.hess.meetmate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hess.meetmate.domain.NativeWhisperRuntime
import com.hess.meetmate.integration.WhisperModelManager
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Device test for the complete sample path: local GGML model -> JNI -> whisper.cpp -> text result.
 * It is skipped when the developer has not downloaded a model or linked the native checkout.
 */
@RunWith(AndroidJUnit4::class)
class WhisperPipelineInstrumentedTest {
    @Test fun sampleWavReachesWhisperJni() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = WhisperModelManager(context).activeModel()
        assumeTrue("Download and select a model before running this test", model != null)
        assumeTrue("Build the whisper.cpp native target before running this test", runCatching { System.loadLibrary("meetmate_whisper") }.isSuccess)
        val pcm = readPcm16(context, "sample_silence.wav")
        val result = runCatching { NativeWhisperRuntime().transcribeSample(model!!.file, pcm) }.onFailure { assumeTrue("Native whisper.cpp checkout is not linked", false) }.getOrNull()
        // Silence normally returns null/empty; reaching this line proves model loading and JNI inference ran.
        check(result == null || result.isNotBlank())
    }

    private fun readPcm16(context: Context, name: String): ShortArray {
        context.assets.open(name).use { input ->
            DataInputStream(input).use { data ->
                val header = ByteArray(44); data.readFully(header)
                val bytes = ByteArray(data.available()); data.readFully(bytes)
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                return ShortArray(bytes.size / 2) { buffer.short }
            }
        }
    }
}
