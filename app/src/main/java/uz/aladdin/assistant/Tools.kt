package uz.aladdin.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/** OpenAI chaqira oladigan telefon funksiyalari. */
object Tools {
    private fun p(type: String, desc: String, enum: List<String>? = null) =
        JSONObject().put("type", type).put("description", desc).apply {
            if (enum != null) put("enum", JSONArray(enum))
        }

    private fun fn(name: String, desc: String, props: JSONObject, required: List<String>) =
        JSONObject().put("type", "function").put(
            "function", JSONObject()
                .put("name", name)
                .put("description", desc)
                .put(
                    "parameters", JSONObject()
                        .put("type", "object")
                        .put("properties", props)
                        .put("required", JSONArray(required))
                )
        )

    fun schema(): JSONArray = JSONArray()
        .put(fn("call_contact", "Kontaktga yoki telefon raqamiga qo'ng'iroq qilish",
            JSONObject().put("name", p("string", "Kontakt ismi (qo'shimchasiz) yoki telefon raqami")),
            listOf("name")))
        .put(fn("read_messages", "Oxirgi kelgan xabarlarni olish (SMS, Telegram, WhatsApp)",
            JSONObject()
                .put("source", p("string", "Manba", listOf("all", "sms", "telegram", "whatsapp")))
                .put("count", p("integer", "Nechta xabar, standart 3")),
            listOf()))
        .put(fn("send_sms", "SMS yuborish. Faqat foydalanuvchi tasdiqlagandan keyin chaqir.",
            JSONObject()
                .put("name", p("string", "Kontakt ismi yoki raqam"))
                .put("text", p("string", "Xabar matni")),
            listOf("name", "text")))
        .put(fn("media_control", "Musiqa/pleerni boshqarish",
            JSONObject().put("action", p("string", "Amal", listOf("play", "pause", "play_pause", "next", "previous"))),
            listOf("action")))
        .put(fn("volume", "Musiqa ovoz balandligini o'zgartirish",
            JSONObject()
                .put("action", p("string", "Amal", listOf("up", "down", "set", "mute", "max")))
                .put("level", p("integer", "set uchun 0..100 foiz")),
            listOf("action")))
        .put(fn("navigate", "Navigatorda (Yandex Navigator yoki xarita) manzilga yo'l ochish",
            JSONObject().put("destination", p("string", "Manzil yoki joy nomi")),
            listOf("destination")))
        .put(fn("open_app", "Telefondagi ilovani ochish (Telegram, WhatsApp, YouTube, Yandex Music, xarita...)",
            JSONObject().put("app", p("string", "Ilova nomi")),
            listOf("app")))

    fun run(name: String, a: JSONObject): String {
        val ctx = App.instance
        return when (name) {
            "call_contact" -> callContact(ctx, a.optString("name"))
            "read_messages" -> readMessages(ctx, a.optString("source", "all"), a.optInt("count", 3))
            "send_sms" -> sendSms(ctx, a.optString("name"), a.optString("text"))
            "media_control" -> PhoneActions.media(ctx, a.optString("action", "play_pause"))
            "volume" -> PhoneActions.volume(ctx, a.optString("action", "set"), a.optInt("level", 50))
            "navigate" -> PhoneActions.navigate(ctx, a.optString("destination"))
            "open_app" -> PhoneActions.openApp(ctx, a.optString("app"))
            else -> "Noma'lum funksiya: $name"
        }
    }

    private fun granted(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    /** Ism yoki raqamdan kontaktni topadi. Xato bo'lsa — matnli izoh qaytaradi. */
    private fun resolve(ctx: Context, who: String): Pair<Contacts.C?, String?> {
        val digits = who.filter { it.isDigit() || it == '+' }
        if (digits.count { it.isDigit() } >= 5) return Contacts.C(digits, digits) to null
        if (!granted(ctx, Manifest.permission.READ_CONTACTS)) return null to "Kontaktlarni o'qishga ruxsat berilmagan."
        val found = Contacts.find(ctx, who)
        if (found.isEmpty()) return null to "\"$who\" nomli kontakt topilmadi."
        val top = found.first()
        val same = found.filter { it.score == top.score && it.c.name != top.c.name }
        if (same.isNotEmpty()) {
            val names = (listOf(top) + same).take(4).joinToString(", ") { it.c.name }
            return null to "Bir nechta kontakt mos keldi: $names. Foydalanuvchidan qaysi biri ekanini so'ra."
        }
        return top.c to null
    }

    private fun callContact(ctx: Context, who: String): String {
        val (c, err) = resolve(ctx, who)
        if (c == null) return err ?: "Topilmadi"
        return if (PhoneActions.call(ctx, c.number)) "Qo'ng'iroq qilinmoqda: ${c.name}"
        else "Qo'ng'iroq qilib bo'lmadi (ruxsat berilmagan bo'lishi mumkin)."
    }

    private fun sendSms(ctx: Context, who: String, text: String): String {
        if (text.isBlank()) return "Xabar matni bo'sh."
        val (c, err) = resolve(ctx, who)
        if (c == null) return err ?: "Topilmadi"
        return PhoneActions.sendSms(ctx, c.number, text, c.name)
    }

    private fun readMessages(ctx: Context, source: String, count: Int): String {
        val n = count.coerceIn(1, 10)
        val list = ArrayList(MessageStore.latest(source, n))
        if ((source == "sms" || source == "all") && granted(ctx, Manifest.permission.READ_SMS)) {
            for (m in smsInbox(ctx, n)) {
                if (list.none { it.source == "sms" && it.text == m.text }) list.add(m)
            }
        }
        val res = list.sortedByDescending { it.time }.take(n)
        if (res.isEmpty()) return "Yangi xabar yo'q."
        val now = System.currentTimeMillis()
        return res.joinToString("\n") { m ->
            val mins = ((now - m.time) / 60_000).coerceAtLeast(0)
            val ago = when {
                mins < 1 -> "hozirgina"
                mins < 60 -> "$mins daqiqa oldin"
                mins < 1440 -> "${mins / 60} soat oldin"
                else -> "${mins / 1440} kun oldin"
            }
            "[${MessageStore.label(m.source)}] ${m.sender} ($ago): ${m.text.take(500)}"
        }
    }

    private fun smsInbox(ctx: Context, n: Int): List<InMsg> = try {
        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null, null, "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            val out = ArrayList<InMsg>()
            while (c.moveToNext() && out.size < n) {
                val addr = c.getString(0) ?: ""
                out.add(InMsg("sms", Contacts.nameForNumber(ctx, addr) ?: addr, c.getString(1) ?: "", c.getLong(2)))
            }
            out
        } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
