package uz.aladdin.assistant

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {
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
            AssistantService.start(this, AssistantService.ACTION_REFRESH)
            Toast.makeText(this, "Saqlandi", Toast.LENGTH_SHORT).show()
            finish()
        }
        findViewById<Button>(R.id.test).setOnClickListener {
            Prefs.apiKey = key.text.toString()
            Prefs.voice = voice.text.toString()
            Prefs.openAiTts = tts.isChecked
            Speaker.speak("Assalomu alaykum! Men Aladdin, sizning yo'ldagi yordamchingizman.")
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
