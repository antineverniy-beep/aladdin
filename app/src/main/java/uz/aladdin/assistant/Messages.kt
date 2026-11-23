package uz.aladdin.assistant

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

data class InMsg(val source: String, val sender: String, val text: String, val time: Long)

/** Kelgan xabarlar xotirasi + avtomatik ovoz chiqarib o'qish. */
object MessageStore {
    private val list = ArrayList<InMsg>()

    fun add(m: InMsg) {
        synchronized(list) {
            if (list.take(20).any { it.source == m.source && it.sender == m.sender && it.text == m.text }) return
            list.add(0, m)
            while (list.size > 60) list.removeAt(list.size - 1)
        }
        if (Prefs.autoRead && Assistant.state == Assistant.State.IDLE) {
            Speaker.speak("${m.sender} ${label(m.source)} yozdi: ${m.text.take(400)}")
        }
    }

    fun latest(source: String, n: Int): List<InMsg> = synchronized(list) {
        list.filter { source == "all" || it.source == source }.take(n)
    }

    fun label(s: String) = when (s) {
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
        if (text.isNullOrBlank()) return
        // "5 ta yangi xabar" kabi umumiy bildirishnomalarni o'tkazib yuboramiz
        if (Regex("^\\d+ .*(new messages|новых сообщ|yangi xabar)", RegexOption.IGNORE_CASE).containsMatchIn(text)) return
        MessageStore.add(InMsg(src, sender, text, sbn.postTime))
    }
}
