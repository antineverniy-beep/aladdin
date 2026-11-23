package uz.aladdin.assistant

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/** Tinglash → o'ylash (OpenAI) → gapirish oqimini boshqaradi. */
object Assistant {
    enum class State { IDLE, LISTENING, THINKING, SPEAKING }

    fun interface Observer { fun onState(state: State, text: String) }

    private val observers = CopyOnWriteArrayList<Observer>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stt: Listener? = null
    private var job: Job? = null

    var levelListener: ((Float) -> Unit)? = null

    @Volatile var state = State.IDLE
        private set

    fun addObserver(o: Observer) { observers.add(o) }
    fun removeObserver(o: Observer) { observers.remove(o) }

    private fun set(s: State, text: String) {
        state = s
        observers.forEach { it.onState(s, text) }
    }

    /** Foydalanuvchini tinglashni boshlaydi. */
    fun listen(ctx: Context) {
        Speaker.stop()
        job?.cancel()
        val l = stt ?: Listener(ctx.applicationContext).also { stt = it }
        set(State.LISTENING, "Eshityapman…")
        l.listen(
            onPartial = { set(State.LISTENING, it) },
            onLevel = { levelListener?.invoke(it) },
        ) { text ->
            if (text.isNullOrBlank()) set(State.IDLE, "Eshitmadim. Qaytadan urinib ko'ring.")
            else handle(ctx, text)
        }
    }

    /** "Aladdin" deb chaqirilganda: "Labbay" deydi va tinglaydi. */
    fun greetAndListen(ctx: Context) {
        Speaker.stop()
        job?.cancel()
        job = scope.launch {
            set(State.SPEAKING, "Labbay")
            Speaker.speak("Labbay").join()
            listen(ctx)
        }
    }

    /** Tayyor matnli buyruqni bajaradi. */
    fun handle(ctx: Context, text: String) {
        job?.cancel()
        job = scope.launch {
            set(State.THINKING, "«$text»")
            val reply = Brain.ask(text)
            set(State.SPEAKING, reply)
            Speaker.speak(reply).join()
            set(State.IDLE, reply)
            // Savol bilan tugasa — javobni kutib, yana tinglaymiz
            if (reply.trimEnd().endsWith("?")) listen(ctx)
        }
    }

    fun stop() {
        stt?.cancel()
        Speaker.stop()
        job?.cancel()
        set(State.IDLE, "")
    }
}
