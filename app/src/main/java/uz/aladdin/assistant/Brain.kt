package uz.aladdin.assistant

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ApiError(msg: String) : Exception(msg)

/** OpenAI bilan erkin muloqot + funksiyalar (qo'ng'iroq, xabarlar, musiqa...). */
object Brain {
    // Haydovchi uzoq kutmasligi kerak: tunnel/internet yo'q bo'lsa tez xabar beramiz
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    /** Foydalanuvchi gaplari hisoblagichi — SMS tasdig'i faqat keyingi gapda qabul qilinadi. */
    @Volatile var turn = 0
        private set
    private val JSON = "application/json".toMediaType()
    private val history = mutableListOf<JSONObject>()
    private val lock = Mutex()
    private var lastAt = 0L

    private fun systemPrompt(): String {
        val now = SimpleDateFormat("yyyy-MM-dd, EEEE, HH:mm", Locale("uz")).format(Date())
        return """
            Sen — Aladdin, haydovchilar uchun aqlli ovozli yordamchisan.
            Doim o'zbek tilida (lotin yozuvida) javob ber. Foydalanuvchi ruscha gapirsa ham tushun, lekin o'zbekcha javob ber.
            Javobing ovoz chiqarib o'qiladi va haydovchi rul ortida: qisqa bo'lsin (odatda 1–3 gap), markdown, ro'yxat, emoji, havolalar ishlatma.
            Buyruqning ma'nosini o'zing tushun va kerakli funksiyani chaqir: qo'ng'iroq, xabarlarni o'qish, SMS yuborish, Telegram/WhatsApp'ga javob, musiqa, ovoz balandligi, navigator, ilovani ochish.
            Kontakt ismini funksiyaga qo'shimchalarsiz yubor ("Aliga qo'ng'iroq qil" → "Ali", "onamga" → "Onam").
            Agar funksiya bir nechta mos kontakt yoki raqam qaytarsa — hech qachon taxmin qilma: variantlarni qisqa aytib (ism, raqam turi, oxirgi 4 raqam), qaysi biri ekanini so'ra; javobni which parametriga berib funksiyani qayta chaqir.
            Raqamlarni aytganda oxirgi 4 raqamni juft-juft ayt (masalan "qirq besh — oltmish yetti").
            Xabarlarni o'qiganda: kimdan, qayerdan (SMS/Telegram/WhatsApp) va matnini tabiiy qilib aytib ber.
            send_sms va reply_message xabarni yubormaydi, faqat tayyorlaydi. Kimga va qanday matn ketishini aytib "Yuboraymi?" deb so'ra. Foydalanuvchi keyingi gapida rozi bo'lsa ("ha", "yubor", "да") confirm_send ni chaqir, "yo'q" desa cancel_send.
            Foydalanuvchi "ikkinchi SIM" desa, call_contact da sim=2 ber.
            Agar savol aniq bo'lmasa, qisqa aniqlashtiruvchi savol ber (savol belgisi bilan tugat).
            Haydovchiga xavfsizlik birinchi o'rinda: uzun matnlarni qisqartirib ayt.
            Hozirgi sana va vaqt: $now.
        """.trimIndent()
    }

    suspend fun ask(text: String): String = withContext(Dispatchers.IO) {
        if (Prefs.apiKey.isBlank()) {
            return@withContext "OpenAI kaliti kiritilmagan. Sozlamalarga kirib, kalitni yozing."
        }
        lock.withLock {
            turn++
            val now = System.currentTimeMillis()
            if (now - lastAt > 10 * 60_000) history.clear() // 10 daqiqadan keyin yangi suhbat
            lastAt = now
            trim()
            val snapshot = history.size
            history.add(JSONObject().put("role", "user").put("content", text))
            try {
                repeat(6) {
                    val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt()))
                    history.forEach { msgs.put(it) }
                    val req = JSONObject()
                        .put("model", Prefs.model)
                        .put("messages", msgs)
                        .put("tools", Tools.schema())
                    val m = post(req).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                    val content = if (m.isNull("content")) "" else m.optString("content")
                    val calls = m.optJSONArray("tool_calls")
                    val stored = JSONObject().put("role", "assistant").put("content", content)
                    if (calls == null || calls.length() == 0) {
                        history.add(stored)
                        return@withContext clean(content).ifBlank { "Bajarildi." }
                    }
                    stored.put("tool_calls", calls)
                    history.add(stored)
                    for (i in 0 until calls.length()) {
                        val c = calls.getJSONObject(i)
                        val fn = c.getJSONObject("function")
                        val args = try {
                            JSONObject(fn.optString("arguments", "{}").ifBlank { "{}" })
                        } catch (e: Exception) { JSONObject() }
                        val result = try {
                            Tools.run(fn.getString("name"), args)
                        } catch (e: Exception) { "Xato: ${e.message}" }
                        history.add(
                            JSONObject().put("role", "tool").put("tool_call_id", c.getString("id")).put("content", result)
                        )
                    }
                }
                rollback(snapshot)
                "Kechirasiz, buyruqni oxirigacha bajara olmadim."
            } catch (e: CancellationException) {
                // To'xtatildi — chala qolgan qadamlarni olib tashlaymiz, aks holda keyingi so'rov buziladi
                rollback(snapshot)
                throw e
            } catch (e: ApiError) {
                rollback(snapshot)
                e.message ?: "OpenAI xatosi."
            } catch (e: IOException) {
                rollback(snapshot)
                "Internet aloqasi yo'q yoki juda sust. Birozdan keyin qayta urinib ko'ring."
            } catch (e: Exception) {
                rollback(snapshot)
                "Kutilmagan xato: ${e.message}"
            }
        }
    }

    private fun rollback(size: Int) {
        while (history.size > size) history.removeAt(history.size - 1)
    }

    /** Sozlamalardagi "Tekshirish" tugmasi uchun: kalit va model ishlayaptimi. */
    suspend fun ping(): String = withContext(Dispatchers.IO) {
        if (Prefs.apiKey.isBlank()) return@withContext "Kalit kiritilmagan."
        try {
            val msgs = JSONArray().put(JSONObject().put("role", "user").put("content", "Salom"))
            post(JSONObject().put("model", Prefs.model).put("messages", msgs))
            "✓ Kalit va model (${Prefs.model}) ishlayapti."
        } catch (e: ApiError) {
            e.message ?: "OpenAI xatosi."
        } catch (e: IOException) {
            "Internetga ulanib bo'lmadi."
        } catch (e: Exception) {
            "Xato: ${e.message}"
        }
    }

    private fun trim() {
        while (history.size > 30) history.removeAt(0)
        while (history.isNotEmpty() && history[0].optString("role") != "user") history.removeAt(0)
    }

    private fun post(body: JSONObject): JSONObject {
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer ${Prefs.apiKey}")
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(req).execute().use { r ->
            val s = r.body?.string() ?: ""
            if (!r.isSuccessful) {
                throw ApiError(
                    when (r.code) {
                        401 -> "OpenAI kaliti noto'g'ri. Sozlamalarni tekshiring."
                        429 -> "OpenAI limiti tugagan yoki hisobda mablag' yo'q."
                        404 -> "Model topilmadi: ${Prefs.model}. Sozlamalarda boshqa model yozing."
                        else -> "OpenAI xatosi, kod ${r.code}."
                    }
                )
            }
            return JSONObject(s)
        }
    }

    private fun clean(s: String) = s
        .replace("**", "").replace("*", "").replace("#", "").replace("`", "")
        .replace(Regex("\\s+"), " ").trim()
}
