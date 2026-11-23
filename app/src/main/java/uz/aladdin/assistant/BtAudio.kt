package uz.aladdin.assistant

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.delay

/**
 * Mashina magnitolasi (masalan Pioneer) Bluetooth orqali ulangan bo'lsa:
 * suhbat vaqtida magnitola mikrofoni va karnaylari ishlatiladi (Bluetooth SCO / "qo'l bo'sh" rejimi).
 * Suhbat tugagach, oddiy musiqa rejimiga (A2DP) qaytadi.
 */
object BtAudio {
    @Volatile var active = false
        private set

    private val am: AudioManager
        get() = App.instance.getSystemService(AudioManager::class.java)

    private fun scoDevice(): AudioDeviceInfo? =
        if (Build.VERSION.SDK_INT >= 31) {
            am.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        } else {
            am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        }

    /** Mashina (yoki Bluetooth garnitura) mikrofoni bilan ulanganmi. */
    fun carConnected(): Boolean = try { scoDevice() != null } catch (_: Exception) { false }

    fun carName(): String? = try { scoDevice()?.productName?.toString() } catch (_: Exception) { null }

    /** Mashina mikrofonini yoqadi. true — muvaffaqiyatli. */
    suspend fun start(): Boolean {
        if (active) return true
        if (!Prefs.carMic) return false
        val dev = scoDevice() ?: return false
        return try {
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= 31) {
                if (!am.setCommunicationDevice(dev)) {
                    am.mode = AudioManager.MODE_NORMAL
                    return false
                }
                var w = 0
                while (am.communicationDevice?.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO && w < 25) { delay(100); w++ }
            } else {
                @Suppress("DEPRECATION")
                am.startBluetoothSco()
                @Suppress("DEPRECATION")
                am.isBluetoothScoOn = true
                @Suppress("DEPRECATION")
                run {
                    var w = 0
                    while (!am.isBluetoothScoOn && w < 25) { delay(100); w++ }
                }
            }
            active = true
            delay(700) // magnitola "qo'ng'iroq" rejimiga o'tib olishi uchun
            true
        } catch (e: Exception) {
            active = true
            stop()
            false
        }
    }

    fun stop() {
        if (!active) return
        active = false
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                am.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                am.stopBluetoothSco()
                @Suppress("DEPRECATION")
                am.isBluetoothScoOn = false
            }
            am.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}
    }
}
