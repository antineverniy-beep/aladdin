package uz.aladdin.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * O'zbekcha ovoz: avval OpenAI TTS (ravon o'zbekcha), u ishlamasa telefonning o'z TTS'i.
 * Gapirayotganda musiqa ovozi pasaytiriladi (ducking).
 */
object Speaker {
    private const val TAG = "Speaker"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()
    private val jobs = CopyOnWriteArrayList<Job>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private var player: MediaPlayer? = null

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun init(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            val t = tts
            if (status == TextToSpeech.SUCCESS && t != null) {
                val r = t.setLanguage(Locale("uz", "UZ"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // O'zbekcha yo'q bo'lsa — turkcha lotin harflarini yaqin talaffuz qiladi
                    val r2 = t.setLanguage(Locale("tr", "TR"))
                    if (r2 == TextToSpeech.LANG_MISSING_DATA || r2 == TextToSpeech.LANG_NOT_SUPPORTED) {
                        t.setLanguage(Locale("ru", "RU"))
                    }
                }
                t.setSpeechRate(1.0f)
                ttsReady = true
            }
        }
    }

    /** Matnni navbat bilan o'qiydi. Qaytgan Job tugashini kutish mumkin. */
    fun speak(text: String): Job {
        val j = scope.launch { mutex.withLock { doSay(text) } }
        jobs.add(j)
        j.invokeOnCompletion { jobs.remove(j) }
        return j
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        try { player?.release() } catch (_: Exception) {}
        player = null
        tts?.stop()
    }

    private suspend fun doSay(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val am = App.instance.getSystemService(AudioManager::class.java)
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .build()
        am.requestAudioFocus(focus)
        try {
            var ok = false
            if (Prefs.openAiTts && Prefs.apiKey.isNotBlank()) {
                ok = try {
                    playOpenAi(clean); true
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "OpenAI TTS ishlamadi: ${e.message}"); false
                }
            }
            if (!ok) speakLocal(clean)
        } finally {
            am.abandonAudioFocusRequest(focus)
        }
    }

    private suspend fun playOpenAi(text: String) {
        val file = withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("model", "gpt-4o-mini-tts")
                .put("input", text)
                .put("voice", Prefs.voice)
                .put("instructions", "Gapir o'zbek tilida: tabiiy, ravon, aniq o'zbekcha talaffuz bilan, xotirjam va do'stona ohangda.")
                .put("response_format", "mp3")
                .toString()
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("https://api.openai.com/v1/audio/speech")
                .header("Authorization", "Bearer ${Prefs.apiKey}")
                .post(body)
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("TTS ${resp.code}: ${resp.body?.string()?.take(200)}")
                val f = File(App.instance.cacheDir, "tts_${System.nanoTime()}.mp3")
                f.outputStream().use { out -> resp.body!!.byteStream().copyTo(out) }
                f
            }
        }
        try {
            playFile(file)
        } finally {
            file.delete()
        }
    }

    private suspend fun playFile(f: File) = suspendCancellableCoroutine<Unit> { cont ->
        val mp = MediaPlayer()
        player = mp
        fun done() {
            try { mp.release() } catch (_: Exception) {}
            if (player === mp) player = null
            if (cont.isActive) cont.resume(Unit)
        }
        mp.setAudioAttributes(attrs)
        mp.setOnCompletionListener { done() }
        mp.setOnErrorListener { _, _, _ -> done(); true }
        cont.invokeOnCancellation { try { mp.release() } catch (_: Exception) {} }
        try {
            mp.setDataSource(f.absolutePath)
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            done()
        }
    }

    private suspend fun speakLocal(text: String) {
        val t = tts ?: return
        var waited = 0
        while (!ttsReady && waited < 30) { delay(100); waited++ }
        if (!ttsReady) return
        suspendCancellableCoroutine<Unit> { cont ->
            val id = UUID.randomUUID().toString()
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id && cont.isActive) cont.resume(Unit)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id && cont.isActive) cont.resume(Unit)
                }
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (utteranceId == id && cont.isActive) cont.resume(Unit)
                }
            })
            t.setAudioAttributes(attrs)
            t.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
            cont.invokeOnCancellation { t.stop() }
        }
    }
}
