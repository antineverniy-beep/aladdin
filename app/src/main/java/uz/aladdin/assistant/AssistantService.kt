package uz.aladdin.assistant

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Fonda ishlash: doimiy bildirishnoma ("GAPIRISH" tugmasi bilan)
 * va ixtiyoriy "Aladdin" deb ovoz bilan chaqirish.
 */
class AssistantService : Service() {
    private lateinit var wake: Listener
    private val h = Handler(Looper.getMainLooper())
    private val loop = Runnable { runWake() }
    private val obs = Assistant.Observer { s, _ ->
        if (s == Assistant.State.IDLE) scheduleWake(1200)
        else { h.removeCallbacks(loop); wake.cancel() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wake = Listener(this)
        if (!startFg()) { stopSelf(); return }
        Assistant.addObserver(obs)
        scheduleWake(800)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TALK -> Assistant.listen(this)
            ACTION_STOP -> { Assistant.stop(); stopSelf() }
            ACTION_REFRESH -> { startFg(); wake.cancel(); scheduleWake(300) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Assistant.removeObserver(obs)
        h.removeCallbacks(loop)
        wake.cancel()
        super.onDestroy()
    }

    private fun startFg(): Boolean {
        val imm = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), imm)
        val talk = PendingIntent.getService(this, 1, Intent(this, AssistantService::class.java).setAction(ACTION_TALK), imm)
        val stop = PendingIntent.getService(this, 2, Intent(this, AssistantService::class.java).setAction(ACTION_STOP), imm)
        val n = NotificationCompat.Builder(this, App.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Aladdin ishlayapti")
            .setContentText(if (Prefs.wakeWord) "«Привет Аладин» deb chaqiring yoki GAPIRISH ni bosing" else "Gapirish uchun GAPIRISH ni bosing")
            .setContentIntent(open)
            .addAction(0, "GAPIRISH", talk)
            .addAction(0, "O'chirish", stop)
            .setOngoing(true)
            .build()
        return try {
            val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            ServiceCompat.startForeground(this, 7, n, type)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun scheduleWake(delayMs: Long) {
        h.removeCallbacks(loop)
        if (Prefs.wakeWord) h.postDelayed(loop, delayMs)
    }

    private fun runWake() {
        if (!Prefs.wakeWord || Assistant.state != Assistant.State.IDLE) return
        // Telefonda gaplashayotganda mikrofonni band qilmaymiz
        if (PhoneActions.inCall(this)) { scheduleWake(3000); return }
        wake.listen(lang = Prefs.wakeLang) { text ->
            if (text.isNullOrBlank() || !Text.isWake(text)) {
                // Jiddiy xatolarda (mikrofon band, ruxsat yo'q, xizmat yo'q) batareyani tejash uchun sekinlashamiz
                val serious = wake.lastError in setOf(-1, -2, 3, 5, 8, 9, 11, 12, 13)
                scheduleWake(if (serious) 5000 else 300)
                return@listen
            }
            // "Привет Аладин" → "Labbay" va buyruqni tinglash
            Assistant.greetAndListen(this)
        }
    }

    companion object {
        const val ACTION_TALK = "uz.aladdin.TALK"
        const val ACTION_STOP = "uz.aladdin.STOP"
        const val ACTION_REFRESH = "uz.aladdin.REFRESH"

        fun start(ctx: Context, action: String? = null) {
            val i = Intent(ctx, AssistantService::class.java)
            if (action != null) i.action = action
            try { ContextCompat.startForegroundService(ctx, i) } catch (_: Exception) {}
        }
    }
}
