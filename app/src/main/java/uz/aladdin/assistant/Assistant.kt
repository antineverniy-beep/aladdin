package uz.aladdin.assistant

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tinglash → o'ylash (OpenAI) → gapirish oqimini boshqaradi.
 * Mashina Bluetooth orqali ulangan bo'lsa, butun suhbat magnitola mikrofoni/karnaylari orqali o'tadi.
 */
object Assistant {
    enum class State { IDLE, LISTENING, THINKING, SPEAKING }

    fun interface Observer { fun onState(state: State, text: String) }

    private val observers = CopyOnWriteArrayList<Observer>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stt: Listener? = null
    private var job: Job? = null

    /** Funksiya qo'ng'iroq boshlagan bo'lsa, javobni ovoz chiqarib aytmaymiz. */
    @Volatile var callPlaced = false

    var levelListener: ((Float) -> Unit)? = null

    @Volatile var state = State.IDLE
        private set

    fun addObserver(o: Observer) { observers.add(o) }
    fun removeObserver(o: Observer) { observers.remove(o) }

    private fun set(s: State, text: String) {
        state = s
        observers.forEach { it.onState(s, text) }
    }

    private fun finish(text: String) {
        BtAudio.stop()
        set(State.IDLE, text)
    }

    /** Nutqni tanish xatosini tushunarli qilib aytadi. */
    private fun errorText(code: Int): String = when (code) {
        1, 2, 4 -> "Internet sust yoki yo'q — ovozni taniy olmadim."
        3 -> "Mikrofon ishlamadi. Qaytadan urinib ko'ring."
        8 -> "Mikrofon band. Birozdan keyin urinib ko'ring."
        9 -> "Mikrofonga ruxsat berilmagan."
        12, 13 -> "O'zbek tili nutq paketi topilmadi. Google ilovasini yangilang yoki Sozlamalarda tilni ru-RU qiling."
        -1 -> "Nutqni tanish xizmati topilmadi. Telefonga Google ilovasini o'rnating."
        else -> "Eshitmadim. Qaytadan urinib ko'ring."
    }

    private fun busyWithCall(ctx: Context): Boolean {
        if (!PhoneActions.inCall(ctx)) return false
        set(State.IDLE, "Telefonda gaplashayotganingizda tinglay olmayman.")
        return true
    }

    /** Foydalanuvchini tinglashni boshlaydi. */
    fun listen(ctx: Context) {
        Speaker.stop()
        job?.cancel()
        if (busyWithCall(ctx)) { BtAudio.stop(); return }
        set(State.LISTENING, "Eshityapman…")
        job = scope.launch {
            BtAudio.start()
            val l = stt ?: Listener(ctx.applicationContext).also { stt = it }
            l.listen(
                onPartial = { partial ->
                    val cmd = Text.stripEndWord(partial)
                    if (!cmd.isNullOrBlank()) {
                        // "тезда" eshitildi — tinglashni darhol to'xtatib, buyruqni bajaramiz
                        l.cancel()
                        handle(ctx, cmd)
                    } else {
                        set(State.LISTENING, partial)
                    }
                },
                onLevel = { levelListener?.invoke(it) },
            ) { text ->
                val cmd = text?.let { Text.stripEndWord(it) ?: it }
                if (cmd.isNullOrBlank()) finish(if (text == null) errorText(l.lastError) else "Eshitmadim. Qaytadan urinib ko'ring.")
                else handle(ctx, cmd)
            }
        }
    }

    /** "Привет Аладин" deb chaqirilganda: "Labbay" deydi va tinglaydi. */
    fun greetAndListen(ctx: Context) {
        Speaker.stop()
        job?.cancel()
        if (busyWithCall(ctx)) return
        set(State.SPEAKING, "Labbay")
        job = scope.launch {
            BtAudio.start()
            Speaker.speak("Labbay").join()
            listen(ctx)
        }
    }

    /** Tayyor matnli buyruqni bajaradi. */
    fun handle(ctx: Context, text: String) {
        job?.cancel()
        job = scope.launch {
            set(State.THINKING, "«$text»")
            callPlaced = false
            val reply = Brain.ask(text)
            if (callPlaced) {
                // Qo'ng'iroq ketdi — gapirmaymiz, magnitola qo'ng'iroqqa bo'sh qoladi
                finish(reply)
                return@launch
            }
            set(State.SPEAKING, reply)
            Speaker.speak(reply).join()
            // Savol bilan tugasa — javobni kutib, yana tinglaymiz
            if (reply.trimEnd().endsWith("?")) listen(ctx) else finish(reply)
        }
    }

    fun stop() {
        stt?.cancel()
        Speaker.stop()
        job?.cancel()
        finish("")
    }
}
