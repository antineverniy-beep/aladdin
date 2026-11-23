package uz.aladdin.assistant

import android.content.Context
import android.content.SharedPreferences

/** Ilova sozlamalari (telefon ichida saqlanadi). */
object Prefs {
    private val sp: SharedPreferences
        get() = App.instance.getSharedPreferences("aladdin", Context.MODE_PRIVATE)

    private fun str(k: String, d: String): String = sp.getString(k, d)?.takeIf { it.isNotBlank() } ?: d
    private fun putStr(k: String, v: String) = sp.edit().putString(k, v.trim()).apply()
    private fun putBool(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    var apiKey: String
        get() = sp.getString("api_key", "")?.trim() ?: ""
        set(v) = putStr("api_key", v)

    /** OpenAI chat modeli. */
    var model: String
        get() = str("model", "gpt-4.1-mini")
        set(v) = putStr("model", v)

    /** OpenAI TTS ovozi: alloy, ash, coral, echo, nova, onyx, sage, shimmer ... */
    var voice: String
        get() = str("voice", "alloy")
        set(v) = putStr("voice", v)

    /** Nutqni tanish tili: uz-UZ yoki ru-RU. */
    var sttLang: String
        get() = str("stt_lang", "uz-UZ")
        set(v) = putStr("stt_lang", v)

    var openAiTts: Boolean
        get() = sp.getBoolean("openai_tts", true)
        set(v) = putBool("openai_tts", v)

    var autoRead: Boolean
        get() = sp.getBoolean("auto_read", true)
        set(v) = putBool("auto_read", v)

    var wakeWord: Boolean
        get() = sp.getBoolean("wake_word", false)
        set(v) = putBool("wake_word", v)
}
