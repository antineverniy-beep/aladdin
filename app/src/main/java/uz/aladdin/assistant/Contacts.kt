package uz.aladdin.assistant

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup

/** Telefon kontaktlarini o'qish. Tanlash mantig'i — Resolver (Text.kt). */
object Contacts {

    fun people(ctx: Context): List<Person> {
        val byId = LinkedHashMap<Long, Pair<String, MutableList<PhoneNum>>>()
        try {
            ctx.contentResolver.query(
                Phone.CONTENT_URI,
                arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.IS_SUPER_PRIMARY),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1) ?: continue
                    val num = c.getString(2) ?: continue
                    val type = when (c.getInt(3)) {
                        Phone.TYPE_MOBILE -> "mobil"
                        Phone.TYPE_HOME -> "uy"
                        Phone.TYPE_WORK, Phone.TYPE_WORK_MOBILE -> "ish"
                        else -> "boshqa"
                    }
                    val primary = c.getInt(4) != 0
                    byId.getOrPut(id) { name to ArrayList() }.second.add(PhoneNum(num, type, primary))
                }
            }
        } catch (_: Exception) {}
        return Resolver.mergeDuplicates(byId.map { (id, v) -> Person(id, v.first, v.second) })
    }

    fun nameForNumber(ctx: Context, number: String): String? = try {
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        ctx.contentResolver.query(uri, arrayOf(PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    fun norm(s: String) = Text.norm(s)
}
