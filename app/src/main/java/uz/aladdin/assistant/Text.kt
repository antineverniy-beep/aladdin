package uz.aladdin.assistant

/*
 * Android'ga bog'liq bo'lmagan sof mantiq: matnni solishtirish, "тезда", "Привет Аладин",
 * kontaktni tanlash. Shu sababli bu yerdagi hamma narsa unit-testlar bilan tekshiriladi.
 */

object Text {
    private val cyr = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
        'ж' to "j", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sh", 'ъ' to "",
        'ы' to "i", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya", 'ў' to "o", 'қ' to "k",
        'ғ' to "g", 'ҳ' to "h"
    )

    /** Kirill/lotin, o'/g' belgilari, x/h, q/k farqlarini yo'qotadi. */
    fun norm(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) sb.append(cyr[ch] ?: ch.toString())
        return sb.toString()
            .replace(Regex("([og])['‘’ʻʼ`]"), "$1")
            .replace("kh", "h").replace('x', 'h').replace('q', 'k').replace('w', 'v')
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun digits(s: String) = s.filter { it.isDigit() }

    /** Raqamni solishtirish uchun: oxirgi 9 raqam (+998 bilan yoki usiz bir xil). */
    fun numKey(s: String): String = digits(s).takeLast(9)

    /** "…45-67" ko'rinishida oxirgi 4 raqam. */
    fun tail(number: String): String {
        val d = digits(number)
        if (d.length <= 4) return d
        val t = d.takeLast(4)
        return "${t.take(2)}-${t.takeLast(2)}"
    }

    /** Buyruq "тезда" bilan tugasa — shu so'zsiz buyruq, aks holda null. */
    fun stripEndWord(text: String): String? {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
        if (words.isEmpty()) return null
        val last = norm(words.last())
        val prev = if (words.size >= 2) norm(words[words.size - 2]) else ""
        when {
            last == "tezda" -> words.removeAt(words.size - 1)
            last == "da" && prev == "tez" -> { words.removeAt(words.size - 1); words.removeAt(words.size - 1) }
            else -> return null
        }
        return words.joinToString(" ").trim(' ', ',', '.', '!', '?')
    }

    private val wakeRe = Regex("al+[aoe]d+[iye]n")

    /** "Привет Аладин", "Привет, Аладдин!", "Salom Aladdin" ... */
    fun isWake(text: String): Boolean = wakeRe.containsMatchIn(norm(text).replace(" ", ""))

    // O'zbekcha kelishik qo'shimchalari (norm ko'rinishida, uzunidan qisqasiga)
    private val suffixes = listOf(
        "lariga", "laridan", "larga", "larni", "ning", "akaga", "opaga", "dagi",
        "dan", "ga", "ka", "ni", "da", "ta"
    )

    /** "Sanjarga" → "sanjar", "Onamga" → "onam". */
    fun stripSuffix(q: String): String {
        val n = norm(q)
        val words = n.split(" ").toMutableList()
        val w = words.last()
        for (s in suffixes) {
            if (w.endsWith(s) && w.length - s.length >= 3) {
                words[words.size - 1] = w.dropLast(s.length)
                return words.joinToString(" ")
            }
        }
        return n
    }

    fun sim(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return 1.0 - lev(a, b).toDouble() / maxOf(a.length, b.length)
    }

    private fun lev(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** Ism so'rovga qanchalik mos: 0 — mos emas, 100 — aynan. */
    fun nameScore(name: String, query: String): Int {
        val n = norm(name)
        val q = norm(query)
        if (n.isBlank() || q.isBlank()) return 0
        val nt = n.split(" ")
        val qt = q.split(" ")
        return when {
            n == q -> 100
            n.startsWith("$q ") -> 85
            qt.all { w -> nt.any { it == w } } -> 80
            n.startsWith(q) -> 70
            qt.all { w -> nt.any { it.startsWith(w) } } -> 65
            n.contains(q) -> 55
            qt.any { w -> w.length >= 3 && nt.any { it == w } } -> 45
            else -> {
                val best = maxOf(sim(n, q), nt.maxOf { t -> qt.maxOf { w -> sim(t, w) } })
                if (best >= 0.75) (best * 50).toInt() else 0
            }
        }
    }

    /** "birinchisi" → 0, "ikkinchi" → 1, "третий" → 2 ... */
    fun ordinal(hint: String): Int? {
        val h = norm(hint)
        if (digits(h).length >= 2) return null
        return when {
            h.contains("birinchi") || h.contains("perv") || h == "1" || h.startsWith("1 ") -> 0
            h.contains("ikkinchi") || h.contains("vtor") || h == "2" || h.startsWith("2 ") -> 1
            h.contains("uchinchi") || h.contains("tret") || h == "3" || h.startsWith("3 ") -> 2
            h.contains("tortinchi") || h.contains("chetv") || h == "4" || h.startsWith("4 ") -> 3
            h.contains("beshinchi") || h.contains("pyat") || h == "5" || h.startsWith("5 ") -> 4
            else -> null
        }
    }

    /** "mobil" / "ish" / "uy" — raqam turi. */
    fun phoneType(hint: String): String? {
        val h = norm(hint)
        return when {
            listOf("mobil", "mobile", "uyali", "sotov", "mobilniy").any { h.contains(it) } -> "mobil"
            listOf("ish", "work", "rabo", "ofis", "office").any { h.contains(it) } -> "ish"
            listOf("uy", "home", "dom").any { h == it || h.startsWith("$it ") || h.contains(" $it") } -> "uy"
            else -> null
        }
    }
}

data class PhoneNum(val number: String, val type: String, val primary: Boolean = false)
data class Person(val id: Long, val name: String, val phones: List<PhoneNum>)

sealed class Pick {
    data class One(val person: Person, val phone: PhoneNum) : Pick()
    data class Ask(val question: String) : Pick()
    object None : Pick()
}

/** Kontaktni xavfsiz tanlash: noaniq bo'lsa hech qachon taxmin qilmaydi — so'raydi. */
object Resolver {

    /** Bir odam bir necha marta saqlangan bo'lsa (SIM + Google), bittaga birlashtiradi. */
    fun mergeDuplicates(people: List<Person>): List<Person> {
        val out = ArrayList<Person>()
        for (p in people) {
            val phones = dedupePhones(p.phones)
            if (phones.isEmpty()) continue
            val keys = phones.map { Text.numKey(it.number) }.toSet()
            val idx = out.indexOfFirst { o ->
                Text.norm(o.name) == Text.norm(p.name) && o.phones.any { Text.numKey(it.number) in keys }
            }
            if (idx >= 0) {
                val o = out[idx]
                out[idx] = o.copy(phones = dedupePhones(o.phones + phones))
            } else {
                out.add(p.copy(phones = phones))
            }
        }
        return out
    }

    private fun dedupePhones(list: List<PhoneNum>): List<PhoneNum> {
        val seen = LinkedHashMap<String, PhoneNum>()
        for (ph in list) {
            val k = Text.numKey(ph.number)
            if (k.isEmpty()) continue
            val old = seen[k]
            if (old == null || (!old.primary && ph.primary)) seen[k] = ph
        }
        // Tartib barqaror: asosiy raqam, keyin mobil, keyin boshqalar
        return seen.values.sortedWith(compareBy<PhoneNum>({ !it.primary }, { it.type != "mobil" }, { Text.numKey(it.number) }))
    }

    private fun describe(ph: PhoneNum) = "${ph.type}, oxiri ${Text.tail(ph.number)}"

    fun pick(people: List<Person>, query: String, which: String? = null): Pick {
        val q = query.trim()
        // To'g'ridan-to'g'ri raqam (shu jumladan 101, 102, 103, 112)
        val qd = Text.digits(q)
        if (qd.length >= 3 && q.none { it.isLetter() }) {
            val num = if (q.trim().startsWith("+")) "+$qd" else qd
            return Pick.One(Person(-1, num, listOf(PhoneNum(num, "raqam"))), PhoneNum(num, "raqam"))
        }
        if (q.isBlank()) return Pick.None

        var scored = people.map { it to Text.nameScore(it.name, q) }.filter { it.second > 0 }
        val stripped = Text.stripSuffix(q)
        if (stripped != Text.norm(q)) {
            val alt = people.map { it to Text.nameScore(it.name, stripped) }.filter { it.second > 0 }
            if ((alt.maxOfOrNull { it.second } ?: 0) > (scored.maxOfOrNull { it.second } ?: 0)) scored = alt
        }
        if (scored.isEmpty()) return Pick.None

        val top = scored.maxOf { it.second }
        var cands = scored.filter { it.second == top }.map { it.first }
            .sortedWith(compareBy<Person>({ Text.norm(it.name) }, { it.id }))

        val w = which?.trim().orEmpty()
        var ordinalUsed = false
        if (cands.size > 1 && w.isNotEmpty()) {
            val ord = Text.ordinal(w)
            val hd = Text.digits(w)
            val narrowed = when {
                ord != null -> { ordinalUsed = true; cands.getOrNull(ord)?.let { listOf(it) } ?: emptyList() }
                hd.length >= 2 -> cands.filter { p -> p.phones.any { Text.digits(it.number).endsWith(hd) } }
                else -> {
                    val toks = Text.norm(w).split(" ").filter { it.length >= 2 }
                    cands.filter { p -> toks.isNotEmpty() && toks.all { Text.norm(p.name).contains(it) } }
                }
            }
            if (narrowed.isNotEmpty()) cands = narrowed
        }

        if (cands.size > 1) {
            val list = cands.take(5).mapIndexed { i, p ->
                "${i + 1}) ${p.name} — " + p.phones.joinToString(" yoki ") { describe(it) }
            }.joinToString("; ")
            return Pick.Ask(
                "Bir nechta kontakt mos keldi: $list. Foydalanuvchiga qisqa aytib, qaysi biri ekanini so'ra " +
                    "(tartib raqami, raqamning oxirgi raqamlari yoki familiyasi), keyin javobni which ga berib qayta chaqir."
            )
        }

        val p = cands[0]
        val phones = p.phones
        if (phones.size == 1) return Pick.One(p, phones[0])

        if (w.isNotEmpty()) {
            val hd = Text.digits(w)
            val byHint = when {
                hd.length >= 2 -> phones.filter { Text.digits(it.number).endsWith(hd) }.singleOrNull()
                !ordinalUsed && Text.ordinal(w) != null -> phones.getOrNull(Text.ordinal(w)!!)
                Text.phoneType(w) != null -> phones.filter { it.type == Text.phoneType(w) }.singleOrNull()
                else -> null
            }
            if (byHint != null) return Pick.One(p, byHint)
        }
        phones.firstOrNull { it.primary }?.let { return Pick.One(p, it) }
        phones.filter { it.type == "mobil" }.singleOrNull()?.let { return Pick.One(p, it) }

        val list = phones.mapIndexed { i, ph -> "${i + 1}) ${describe(ph)}" }.joinToString("; ")
        return Pick.Ask(
            "${p.name}ning ${phones.size} ta raqami bor: $list. Qaysi biriga ekanini so'ra " +
                "(mobil/ish/uy yoki oxirgi raqamlari), keyin javobni which ga berib qayta chaqir."
        )
    }
}
