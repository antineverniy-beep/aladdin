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
    /** Yuborishni kutayotgan xabar. Faqat keyingi foydalanuvchi gapidan keyin yuboriladi. */
    private class Pending(val desc: String, val turn: Int, val at: Long, val send: () -> String)

    @Volatile private var pending: Pending? = null

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

    private val whichDesc = "Bir nechta mos kontakt yoki raqam bo'lsa — foydalanuvchining aniqlashtirishi: " +
        "tartib raqami (birinchisi/ikkinchisi), raqamning oxirgi raqamlari (faqat raqamlar bilan, masalan 4567), " +
        "raqam turi (mobil/ish/uy) yoki familiya. Birinchi chaqiruvda bo'sh qoldir."

    fun schema(): JSONArray = JSONArray()
        .put(fn("call_contact", "Kontaktga yoki telefon raqamiga qo'ng'iroq qilish",
            JSONObject()
                .put("name", p("string", "Kontakt ismi (qo'shimchasiz) yoki telefon raqami"))
                .put("which", p("string", whichDesc))
                .put("sim", p("integer", "Qaysi SIM orqali: 1 yoki 2. Foydalanuvchi aytmasa berma.")),
            listOf("name")))
        .put(fn("read_messages", "Kelgan xabarlarni olish (SMS, Telegram, WhatsApp)",
            JSONObject()
                .put("source", p("string", "Manba", listOf("all", "sms", "telegram", "whatsapp")))
                .put("count", p("integer", "Nechta xabar, standart 3"))
                .put("only_unread", p("boolean", "true — faqat hali o'qilmaganlari (standart). Foydalanuvchi 'oxirgi xabarlar'ni qayta so'rasa false.")),
            listOf()))
        .put(fn("send_sms", "SMS tayyorlash. Darhol YUBORMAYDI — tasdiqdan keyin confirm_send yuboradi.",
            JSONObject()
                .put("name", p("string", "Kontakt ismi yoki raqam"))
                .put("text", p("string", "Xabar matni"))
                .put("which", p("string", whichDesc)),
            listOf("name", "text")))
        .put(fn("reply_message", "Telegram yoki WhatsApp'dagi xabarga javob tayyorlash. Darhol YUBORMAYDI — tasdiqdan keyin confirm_send yuboradi.",
            JSONObject()
                .put("to", p("string", "Kimga (xabar yuborgan odam yoki guruh nomi)"))
                .put("text", p("string", "Javob matni")),
            listOf("to", "text")))
        .put(fn("confirm_send", "Tayyorlangan SMS yoki javobni yuborish. Faqat foydalanuvchi tasdiqlagandan keyin ('ha', 'yubor', 'да') chaqir.",
            JSONObject(), listOf()))
        .put(fn("cancel_send", "Tayyorlangan xabarni bekor qilish (foydalanuvchi 'yo'q' desa).",
            JSONObject(), listOf()))
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
            "call_contact" -> callContact(ctx, a.optString("name"), a.optString("which"), a.optInt("sim", 0))
            "read_messages" -> readMessages(ctx, a.optString("source", "all"), a.optInt("count", 3), a.optBoolean("only_unread", true))
            "send_sms" -> sendSms(ctx, a.optString("name"), a.optString("text"), a.optString("which"))
            "reply_message" -> replyMessage(ctx, a.optString("to"), a.optString("text"))
            "confirm_send" -> confirmSend()
            "cancel_send" -> { pending = null; "Bekor qilindi." }
            "media_control" -> PhoneActions.media(ctx, a.optString("action", "play_pause"))
            "volume" -> PhoneActions.volume(ctx, a.optString("action", "set"), a.optInt("level", 50))
            "navigate" -> PhoneActions.navigate(ctx, a.optString("destination"))
            "open_app" -> PhoneActions.openApp(ctx, a.optString("app"))
            else -> "Noma'lum funksiya: $name"
        }
    }

    private fun granted(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    private fun pick(ctx: Context, who: String, which: String): Pick {
        val people = if (granted(ctx, Manifest.permission.READ_CONTACTS)) Contacts.people(ctx) else emptyList()
        return Resolver.pick(people, who, which.ifBlank { null })
    }

    private fun notFound(ctx: Context, who: String) =
        if (!granted(ctx, Manifest.permission.READ_CONTACTS)) "Kontaktlarni o'qishga ruxsat berilmagan."
        else "\"$who\" nomli kontakt topilmadi. Ismni qaytadan so'ra."

    private fun label(o: Pick.One) =
        if (o.person.id < 0) o.phone.number else "${o.person.name} (${o.phone.type}, oxiri ${Text.tail(o.phone.number)})"

    private fun callContact(ctx: Context, who: String, which: String, sim: Int): String =
        when (val r = pick(ctx, who, which)) {
            is Pick.Ask -> r.question
            Pick.None -> notFound(ctx, who)
            is Pick.One -> {
                if (PhoneActions.call(ctx, r.phone.number, sim)) {
                    Assistant.callPlaced = true
                    "Qo'ng'iroq qilinmoqda: ${label(r)}"
                } else "Qo'ng'iroq qilib bo'lmadi (qo'ng'iroq qilishga ruxsat berilmagan bo'lishi mumkin)."
            }
        }

    private fun stage(desc: String, send: () -> String): String {
        pending = Pending(desc, Brain.turn, System.currentTimeMillis(), send)
        return "TAYYOR, LEKIN HALI YUBORILMADI. Foydalanuvchiga ayt: \"$desc. Yuboraymi?\" " +
            "Faqat u keyingi gapida rozi bo'lsa confirm_send ni chaqir, 'yo'q' desa cancel_send."
    }

    private fun sendSms(ctx: Context, who: String, text: String, which: String): String {
        if (text.isBlank()) return "Xabar matni bo'sh. Foydalanuvchidan matnni so'ra."
        return when (val r = pick(ctx, who, which)) {
            is Pick.Ask -> r.question
            Pick.None -> notFound(ctx, who)
            is Pick.One -> {
                val number = r.phone.number
                val name = label(r)
                stage("$name ga SMS: «$text»") { PhoneActions.sendSms(ctx, number, text, name) }
            }
        }
    }

    private fun replyMessage(ctx: Context, to: String, text: String): String {
        if (text.isBlank()) return "Javob matni bo'sh. Foydalanuvchidan matnni so'ra."
        val m = MessageStore.findForReply(to)
            ?: return "\"$to\" dan javob yozish mumkin bo'lgan Telegram/WhatsApp xabari topilmadi. " +
                "(Javob faqat bildirishnomasi hali turgan xabarga yoziladi.) SMS yuborishni taklif qil."
        return stage("${m.sender} ga ${MessageStore.app(m.source)} orqali javob: «$text»") {
            MessageStore.sendReply(ctx, m, text)
        }
    }

    private fun confirmSend(): String {
        val p = pending ?: return "Yuborishga tayyor xabar yo'q."
        if (System.currentTimeMillis() - p.at > 3 * 60_000) {
            pending = null
            return "Tasdiq vaqti o'tib ketdi (3 daqiqa). Xabarni qaytadan tayyorlash kerak."
        }
        if (p.turn == Brain.turn) {
            return "Foydalanuvchi hali tasdiqlamadi. Avval undan so'ra va javobini kut."
        }
        pending = null
        return p.send()
    }

    private fun readMessages(ctx: Context, source: String, count: Int, onlyUnread: Boolean): String {
        val n = count.coerceIn(1, 10)
        val list = ArrayList(MessageStore.take(source, n, onlyUnread))
        if ((source == "sms" || source == "all") && list.size < n && granted(ctx, Manifest.permission.READ_SMS)) {
            for (m in smsInbox(ctx, n, onlyUnread)) {
                if (list.none { it.source == "sms" && it.text == m.text }) {
                    list.add(m)
                    MessageStore.markSmsRead(m.smsId)
                }
            }
        }
        val res = list.sortedByDescending { it.time }.take(n)
        if (res.isEmpty()) {
            return if (onlyUnread) "Yangi (o'qilmagan) xabar yo'q." else "Xabarlar topilmadi."
        }
        val now = System.currentTimeMillis()
        return res.joinToString("\n") { m ->
            val mins = ((now - m.time) / 60_000).coerceAtLeast(0)
            val ago = when {
                mins < 1 -> "hozirgina"
                mins < 60 -> "$mins daqiqa oldin"
                mins < 1440 -> "${mins / 60} soat oldin"
                else -> "${mins / 1440} kun oldin"
            }
            "[${MessageStore.app(m.source)}] ${m.sender} ($ago): ${m.text.take(500)}"
        }
    }

    private fun smsInbox(ctx: Context, n: Int, onlyUnread: Boolean): List<InMsg> = try {
        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            if (onlyUnread) "${Telephony.Sms.READ} = 0" else null,
            null, "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            val out = ArrayList<InMsg>()
            while (c.moveToNext() && out.size < n) {
                val id = c.getLong(0)
                val body = c.getString(2) ?: ""
                if (onlyUnread && (MessageStore.isSmsRead(id) || MessageStore.wasRead(body))) continue
                val addr = c.getString(1) ?: ""
                out.add(InMsg("sms", Contacts.nameForNumber(ctx, addr) ?: addr, body, c.getLong(3), smsId = id))
            }
            out
        } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
