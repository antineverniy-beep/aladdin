package uz.aladdin.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Ovozni matnga aylantirish (telefonning Google nutq tanish xizmati, o'zbek tili). */
class Listener(private val ctx: Context) {
    private var sr: SpeechRecognizer? = null
    private var session = 0
    private val main = Handler(Looper.getMainLooper())

    fun listen(
        lang: String = Prefs.sttLang,
        onPartial: (String) -> Unit = {},
        onLevel: (Float) -> Unit = {},
        onResult: (String?) -> Unit,
    ) {
        cancel()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onResult(null); return
        }
        val my = ++session
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        sr = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { if (my == session) onLevel(rmsdB) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (my != session) return
                release(); onResult(null)
            }
            override fun onResults(results: Bundle?) {
                if (my != session) return
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                release(); onResult(t)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                if (my != session) return
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onPartial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        }
        try {
            r.startListening(i)
        } catch (e: Exception) {
            release(); onResult(null)
        }
    }

    private fun release() {
        session++
        val r = sr ?: return
        sr = null
        main.post { try { r.destroy() } catch (_: Exception) {} }
    }

    fun cancel() {
        session++
        val r = sr ?: return
        sr = null
        try { r.cancel(); r.destroy() } catch (_: Exception) {}
    }
}
