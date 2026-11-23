package uz.aladdin.assistant

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup

/** Kontaktlarni ism bo'yicha qidirish (kirill/lotin farqisiz, xatoga chidamli). */
object Contacts {
    data class C(val name: String, val number: String)
    data class Match(val c: C, val score: Int)

    fun all(ctx: Context): List<C> {
        val out = ArrayList<C>()
        try {
            ctx.contentResolver.query(
                Phone.CONTENT_URI, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val n = cur.getString(0) ?: continue
                    val num = cur.getString(1) ?: continue
                    out.add(C(n, num))
                }
            }
        } catch (_: Exception) {}
        return out.distinctBy { it.name }
    }

    fun find(ctx: Context, query: String): List<Match> {
        val q = norm(query)
        if (q.isBlank()) return emptyList()
        val qt = q.split(" ")
        return all(ctx).mapNotNull { c ->
            val n = norm(c.name)
            if (n.isBlank()) return@mapNotNull null
            val nt = n.split(" ")
            val s = when {
                n == q -> 100
                n.startsWith(q) -> 85
                qt.all { w -> nt.any { it.startsWith(w) } } -> 75
                n.contains(q) -> 65
                qt.any { w -> w.length >= 3 && nt.any { it == w } } -> 50
                else -> {
                    val best = maxOf(sim(n, q), nt.maxOf { t -> qt.maxOf { w -> sim(t, w) } })
                    if (best >= 0.75) (best * 60).toInt() else 0
                }
            }
            if (s > 0) Match(c, s) else null
        }.sortedByDescending { it.score }
    }

    fun nameForNumber(ctx: Context, number: String): String? = try {
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        ctx.contentResolver.query(uri, arrayOf(PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    private val cyr = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
        'ж' to "j", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sh", 'ъ' to "",
        'ы' to "i", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya", 'ў' to "o", 'қ' to "k",
        'ғ' to "g", 'ҳ' to "h"
    )

    /** Matnni solishtirish uchun bir xil ko'rinishga keltiradi. */
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

    private fun sim(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val d = lev(a, b)
        return 1.0 - d.toDouble() / maxOf(a.length, b.length)
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
}
