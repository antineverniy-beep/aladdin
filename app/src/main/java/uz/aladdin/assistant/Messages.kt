package uz.aladdin.assistant

import android.app.Notification
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class InMsg(
    val source: String,
    val sender: String,
    val text: String,
    val time: Long,
    val reply: Notification.Action? = null,
    val smsId: Long = -1,
) {
    @Volatile var read = false
}

/** Kelgan xabarlar xotirasi + avtomatik ovoz chiqarib o'qish. */
object MessageStore {
    private val list = ArrayList<InMsg>()
    private val readSms = HashSet<Long>()

    fun add(m: InMsg) {
        synchronized(list) {
            val dup = list.take(30).firstOrNull { it.source == m.source && it.sender == m.sender && it.text == m.text }
            if (dup != null) return
            list.add(0, m)
            while (list.size > 80) list.removeAt(list.size - 1)
        }
        // Qo'ng'iroq paytida yoki suhbat ketayotganda o'qimaymiz
        if (Prefs.autoRead && Assistant.state == Assistant.State.IDLE && !PhoneActions.inCall(App.instance)) {
            m.read = true
            Speaker.speak("${m.sender} ${from(m.source)} yozdi: ${m.text.take(300)}")
        }
    }

    /** Xabarlarni oladi va o'qilgan deb belgilaydi. */
    fun take(source: String, n: Int, onlyUnread: Boolean): List<InMsg> = synchronized(list) {
        list.filter { (source == "all" || it.source == source) && (!onlyUnread || !it.read) }
            .take(n)
            .onEach { it.read = true }
    }

    fun wasRead(text: String) = synchronized(list) { list.any { it.source == "sms" && it.text == text && it.read } }
    fun markSmsRead(id: Long) { if (id >= 0) synchronized(readSms) { readSms.add(id) } }
    fun isSmsRead(id: Long) = synchronized(readSms) { id in readSms }

    /** Javob yozish mumkin bo'lgan eng oxirgi xabar (ism bo'yicha). */
    fun findForReply(name: String): InMsg? = synchronized(list) {
        val withReply = list.filter { it.reply != null }
        withReply.firstOrNull { Text.norm(it.sender) == Text.norm(name) }
            ?: withReply.maxByOrNull { Text.nameScore(it.sender, name) }?.takeIf { Text.nameScore(it.sender, name) >= 45 }
    }

    fun sendReply(ctx: Context, m: InMsg, text: String): String {
        val action = m.reply ?: return "Bu xabarga javob yozib bo'lmaydi."
        val inputs = action.remoteInputs ?: return "Bu xabarga javob yozib bo'lmaydi."
        return try {
            val intent = Intent()
            val results = Bundle()
            inputs.forEach { results.putCharSequence(it.resultKey, text) }
            RemoteInput.addResultsToIntent(inputs, intent, results)
            action.actionIntent.send(ctx, 0, intent)
            "Javob yuborildi: ${m.sender}"
        } catch (e: Exception) {
            "Javob yuborilmadi (bildirishnoma eskirgan bo'lishi mumkin). SMS yuborishni taklif qil."
        }
    }

    fun app(s: String) = when (s) {
        "telegram" -> "Telegram"
        "whatsapp" -> "WhatsApp"
        else -> "SMS"
    }

    fun from(s: String) = when (s) {
        "telegram" -> "Telegramdan"
        "whatsapp" -> "WhatsAppdan"
        else -> "SMS orqali"
    }
}

/** Kelgan SMS'larni ushlaydi. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        for ((from, parts) in msgs.groupBy { it.originatingAddress ?: "?" }) {
            val text = parts.joinToString("") { it.messageBody ?: "" }
            if (text.isBlank()) continue
            val name = Contacts.nameForNumber(context, from) ?: from
            MessageStore.add(InMsg("sms", name, text, System.currentTimeMillis()))
        }
    }
}

/** Telegram va WhatsApp bildirishnomalarini o'qiydi. */
class NotifListener : NotificationListenerService() {
    private val apps = mapOf(
        "org.telegram.messenger" to "telegram",
        "org.telegram.messenger.web" to "telegram",
        "org.thunderdog.challegram" to "telegram",
        "com.whatsapp" to "whatsapp",
        "com.whatsapp.w4b" to "whatsapp",
    )
    private val summaryRe = Regex("^\\d+ .*(new messages|новых сообщ|новое сообщ|yangi xabar|messages from)", RegexOption.IGNORE_CASE)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val src = apps[sbn.packageName] ?: return
        if (sbn.isOngoing) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras ?: return
        val title = (ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: ex.getCharSequence(Notification.EXTRA_TITLE))?.toString() ?: return

        var sender = title
        var text: String? = null
        @Suppress("DEPRECATION")
        val msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (msgs != null && msgs.isNotEmpty()) {
            val last = msgs.last() as? Bundle
            if (last != null) {
                text = last.getCharSequence("text")?.toString()
                val s = last.getCharSequence("sender")?.toString()
                if (!s.isNullOrBlank() && s != title) sender = "$title guruhida $s"
            }
        }
        if (text.isNullOrBlank()) text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (text.isNullOrBlank() || summaryRe.containsMatchIn(text)) return

        // Javob yozish tugmasi (RemoteInput) — ovoz bilan javob berish uchun
        val reply = n.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
            ?: wearReply(n)

        MessageStore.add(InMsg(src, sender, text, sbn.postTime, reply))
    }

    @Suppress("DEPRECATION")
    private fun wearReply(n: Notification): Notification.Action? = try {
        Notification.WearableExtender(n).actions.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
    } catch (_: Exception) {
        null
    }
}
