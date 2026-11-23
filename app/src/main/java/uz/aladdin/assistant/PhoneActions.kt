package uz.aladdin.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat

/** Telefonni boshqarish amallari. */
object PhoneActions {

    private fun granted(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun call(ctx: Context, number: String): Boolean {
        if (!granted(ctx, Manifest.permission.CALL_PHONE)) return false
        val uri = Uri.fromParts("tel", number, null)
        return try {
            ctx.getSystemService(TelecomManager::class.java).placeCall(uri, Bundle())
            true
        } catch (e: Exception) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    fun sendSms(ctx: Context, number: String, text: String, name: String): String {
        if (!granted(ctx, Manifest.permission.SEND_SMS)) return "SMS yuborishga ruxsat berilmagan."
        return try {
            val sms: SmsManager = if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            }
            val parts = sms.divideMessage(text)
            sms.sendMultipartTextMessage(number, null, parts, null, null)
            "SMS yuborildi: $name"
        } catch (e: Exception) {
            "SMS yuborilmadi: ${e.message}"
        }
    }

    fun media(ctx: Context, action: String): String {
        val am = ctx.getSystemService(AudioManager::class.java)
        val code = when (action) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return "Bajarildi: $action"
    }

    fun volume(ctx: Context, action: String, level: Int): String {
        val am = ctx.getSystemService(AudioManager::class.java)
        val s = AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(s)
        when (action) {
            "up" -> repeat(2) { am.adjustStreamVolume(s, AudioManager.ADJUST_RAISE, 0) }
            "down" -> repeat(2) { am.adjustStreamVolume(s, AudioManager.ADJUST_LOWER, 0) }
            "mute" -> am.setStreamVolume(s, 0, 0)
            "max" -> am.setStreamVolume(s, max, 0)
            else -> am.setStreamVolume(s, (level.coerceIn(0, 100) * max + 50) / 100, 0)
        }
        val cur = am.getStreamVolume(s)
        return "Ovoz balandligi: ${if (max > 0) cur * 100 / max else 0}%"
    }

    fun navigate(ctx: Context, dest: String): String {
        if (dest.isBlank()) return "Manzil aytilmadi."
        val pm = ctx.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("yandexnavi://map_search?text=" + Uri.encode(dest)))
                .setPackage("ru.yandex.yandexnavi"),
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(dest)))
                .setPackage("com.google.android.apps.maps"),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(dest)))
        )
        for (i in intents) {
            try {
                if (i.resolveActivity(pm) != null || i.`package` == null) {
                    ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return "Navigator ochildi: $dest"
                }
            } catch (_: Exception) {}
        }
        return "Navigator ilovasi topilmadi."
    }

    private val known = mapOf(
        "telegram" to "org.telegram.messenger",
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "yandex music" to "ru.yandex.music",
        "spotify" to "com.spotify.music",
        "yandex navigator" to "ru.yandex.yandexnavi",
        "navigator" to "ru.yandex.yandexnavi",
        "yandex maps" to "ru.yandex.yandexmaps",
        "google maps" to "com.google.android.apps.maps",
        "xarita" to "com.google.android.apps.maps",
        "yandex go" to "ru.yandex.taxi",
    )

    fun openApp(ctx: Context, app: String): String {
        val pm = ctx.packageManager
        val q = Contacts.norm(app)
        val pkgCandidates = known.filterKeys { Contacts.norm(it) == q || q.contains(Contacts.norm(it)) }.values
        for (pkg in pkgCandidates) {
            pm.getLaunchIntentForPackage(pkg)?.let {
                ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Ochildi: $app"
            }
        }
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = pm.queryIntentActivities(launcher, 0).firstOrNull {
            val label = Contacts.norm(it.loadLabel(pm).toString())
            label.isNotBlank() && (label == q || label.contains(q) || q.contains(label))
        }
        if (match != null) {
            pm.getLaunchIntentForPackage(match.activityInfo.packageName)?.let {
                ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Ochildi: ${match.loadLabel(pm)}"
            }
        }
        return "\"$app\" ilovasi topilmadi."
    }
}
