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

    /** Foydalanuvchini tinglashni boshlaydi. */
    fun listen(ctx: Context) {
        Speaker.stop()
        job?.cancel()
        set(State.LISTENING, "Eshityapman…")
        job = scope.launch {
            BtAudio.start()
            val l = stt ?: Listener(ctx.applicationContext).also { stt = it }
            l.listen(
                onPartial = { set(State.LISTENING, it) },
                onLevel = { levelListener?.invoke(it) },
            ) { text ->
                if (text.isNullOrBlank()) finish("Eshitmadim. Qaytadan urinib ko'ring.")
                else handle(ctx, text)
            }
        }
    }

    /** "Привет Аладин" deb chaqirilganda: "Labbay" deydi va tinglaydi. */
    fun greetAndListen(ctx: Context) {
        Speaker.stop()
        job?.cancel()
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
                // Qo'ng'iroq ketdi — magnitolani telefon suhbatiga bo'shatamiz
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
