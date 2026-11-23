package uz.aladdin.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private val ui = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onDestroy() {
        ui.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val key = findViewById<EditText>(R.id.apiKey)
        val model = findViewById<EditText>(R.id.model)
        val voice = findViewById<EditText>(R.id.voice)
        val lang = findViewById<EditText>(R.id.lang)
        val tts = findViewById<SwitchCompat>(R.id.openaiTts)
        val auto = findViewById<SwitchCompat>(R.id.autoRead)
        val wake = findViewById<SwitchCompat>(R.id.wakeWord)
        val car = findViewById<SwitchCompat>(R.id.carMic)
        car.isChecked = Prefs.carMic
        findViewById<Button>(R.id.battery).setOnClickListener { Honor.askBattery(this, force = true) }
        findViewById<Button>(R.id.overlay).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        key.setText(Prefs.apiKey)
        model.setText(Prefs.model)
        voice.setText(Prefs.voice)
        lang.setText(Prefs.sttLang)
        tts.isChecked = Prefs.openAiTts
        auto.isChecked = Prefs.autoRead
        wake.isChecked = Prefs.wakeWord

        findViewById<Button>(R.id.save).setOnClickListener {
            Prefs.apiKey = key.text.toString()
            Prefs.model = model.text.toString()
            Prefs.voice = voice.text.toString()
            Prefs.sttLang = lang.text.toString()
            Prefs.openAiTts = tts.isChecked
            Prefs.autoRead = auto.isChecked
            Prefs.wakeWord = wake.isChecked
            Prefs.carMic = car.isChecked
            AssistantService.start(this, AssistantService.ACTION_REFRESH)
            Toast.makeText(this, "Saqlandi", Toast.LENGTH_SHORT).show()
            finish()
        }
        findViewById<Button>(R.id.test).setOnClickListener {
            Prefs.apiKey = key.text.toString()
            Prefs.voice = voice.text.toString()
            Prefs.openAiTts = tts.isChecked
            Prefs.model = model.text.toString()
            Toast.makeText(this, "Tekshirilmoqda…", Toast.LENGTH_SHORT).show()
            ui.launch {
                val res = Brain.ping()
                Toast.makeText(this@SettingsActivity, res, Toast.LENGTH_LONG).show()
                Speaker.speak("Assalomu alaykum! Men Aladdin, sizning yo'ldagi yordamchingizman.")
            }
        }
        findViewById<Button>(R.id.notifAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.defaultAssistant).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }
}
