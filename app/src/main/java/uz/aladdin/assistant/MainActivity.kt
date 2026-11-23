package uz.aladdin.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var btn: TextView
    private lateinit var status: TextView
    private lateinit var reply: TextView
    private lateinit var banner: TextView
    private var pendingTalk = false

    private val obs = Assistant.Observer { s, t -> runOnUiThread { render(s, t) } }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onPerms() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        btn = findViewById(R.id.talk)
        status = findViewById(R.id.status)
        reply = findViewById(R.id.reply)
        banner = findViewById(R.id.banner)

        btn.setOnClickListener {
            if (Assistant.state == Assistant.State.IDLE) talk() else Assistant.stop()
        }
        findViewById<View>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        banner.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        Assistant.levelListener = { lvl ->
            if (Assistant.state == Assistant.State.LISTENING) {
                val s = 1f + (lvl.coerceIn(0f, 10f) / 40f)
                btn.scaleX = s; btn.scaleY = s
            }
        }
        pendingTalk = isAssistIntent(intent)
        askPerms()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isAssistIntent(intent)) talk()
    }

    override fun onStart() {
        super.onStart()
        Assistant.addObserver(obs)
        render(Assistant.state, "")
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        banner.visibility = if (enabled) View.GONE else View.VISIBLE
        if (Prefs.apiKey.isBlank()) status.text = "Avval Sozlamalarda OpenAI kalitini kiriting"
    }

    override fun onStop() {
        Assistant.removeObserver(obs)
        super.onStop()
    }

    override fun onDestroy() {
        Assistant.levelListener = null
        super.onDestroy()
    }

    private fun isAssistIntent(i: Intent?) =
        i?.action == Intent.ACTION_ASSIST || i?.action == Intent.ACTION_VOICE_COMMAND

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun askPerms() {
        val need = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
        )
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = need.filter { !granted(it) }
        if (missing.isEmpty()) onPerms() else permLauncher.launch(missing.toTypedArray())
    }

    private fun onPerms() {
        if (!granted(Manifest.permission.RECORD_AUDIO)) {
            status.text = "Mikrofonga ruxsat kerak"
            return
        }
        AssistantService.start(this)
        if (pendingTalk) { pendingTalk = false; talk() }
    }

    private fun talk() {
        if (!granted(Manifest.permission.RECORD_AUDIO)) { pendingTalk = true; askPerms(); return }
        if (Prefs.apiKey.isBlank()) {
            Toast.makeText(this, "OpenAI kalitini kiriting", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        Assistant.listen(this)
    }

    private fun render(s: Assistant.State, t: String) {
        val (label, color, st) = when (s) {
            Assistant.State.IDLE -> Triple("GAPIRISH", "#2E7D32", "Tayyor. Tugmani bosing.")
            Assistant.State.LISTENING -> Triple("TINGLAYAPMAN", "#C62828", "Gapiring…")
            Assistant.State.THINKING -> Triple("O'YLAYAPMAN", "#1565C0", "Javob tayyorlanmoqda…")
            Assistant.State.SPEAKING -> Triple("TO'XTATISH", "#6A1B9A", "Gapiryapman…")
        }
        btn.text = label
        btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor(color))
        if (s != Assistant.State.LISTENING) { btn.scaleX = 1f; btn.scaleY = 1f }
        status.text = st
        if (t.isNotBlank()) reply.text = t
    }
}
