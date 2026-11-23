package uz.aladdin.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Aladdin xizmati", NotificationManager.IMPORTANCE_LOW)
        )
        Speaker.init(this)
    }

    companion object {
        const val CHANNEL_SERVICE = "aladdin_service"
        lateinit var instance: App
            private set
    }
}
