package uz.aladdin.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** Honor / MagicOS fondagi ilovani o'chirib qo'ymasligi uchun. */
object Honor {
    fun ignoringBattery(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    @SuppressLint("BatteryLife")
    fun askBattery(ctx: Context, force: Boolean = false) {
        if (ignoringBattery(ctx) && !force) return
        if (!force && Prefs.batteryAsked) return
        Prefs.batteryAsked = true
        try {
            val i = if (ignoringBattery(ctx)) Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            else Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
            ctx.startActivity(i)
        } catch (e: Exception) {
            try { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) {}
        }
    }
}
