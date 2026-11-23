package uz.aladdin.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTest {
    @Test fun kirillVaLotinBirXil() {
        assertEquals(Text.norm("Sanjar"), Text.norm("Санжар"))
        assertEquals(Text.norm("O‘tkir"), Text.norm("Ўткир"))
        assertEquals(Text.norm("Xurshid"), Text.norm("Хуршид"))
        assertEquals(Text.norm("Qodir"), Text.norm("Қодир"))
    }

    @Test fun tezdaOxirida() {
        assertEquals("Санжарга телефон қил", Text.stripEndWord("Санжарга телефон қил тезда"))
        assertEquals("Sanjarga telefon qil", Text.stripEndWord("Sanjarga telefon qil tezda."))
        assertEquals("musiqani to'xtat", Text.stripEndWord("musiqani to'xtat тез да"))
        assertEquals("", Text.stripEndWord("тезда"))
        assertNull(Text.stripEndWord("тезда Санжарга қўнғироқ қил"))
        assertNull(Text.stripEndWord("Санжарга телефон қил"))
    }

    @Test fun chaqiruvIborasi() {
        assertTrue(Text.isWake("привет Аладин"))
        assertTrue(Text.isWake("Привет, Аладдин!"))
        assertTrue(Text.isWake("привет алладин"))
        assertTrue(Text.isWake("Salom Aladdin"))
        assertTrue(Text.isWake("привет ала дин"))
        assertFalse(Text.isWake("привет мама"))
        assertFalse(Text.isWake("ладно давай"))
    }

    @Test fun qoshimchalar() {
        assertEquals("sanjar", Text.stripSuffix("Sanjarga"))
        assertEquals("onam", Text.stripSuffix("Onamga"))
        assertEquals("ol", Text.stripSuffix("Ol").take(2))
    }

    @Test fun tartibRaqami() {
        assertEquals(0, Text.ordinal("birinchisi"))
        assertEquals(1, Text.ordinal("ikkinchisiga"))
        assertEquals(1, Text.ordinal("второй"))
        assertEquals(2, Text.ordinal("3"))
        assertNull(Text.ordinal("4567"))
    }
}

class ResolverTest {
    private fun person(id: Long, name: String, vararg nums: Pair<String, String>) =
        Person(id, name, nums.map { PhoneNum(it.first, it.second) })

    @Test fun birXilIsmliIkkiOdam_soraydi() {
        val people = listOf(
            person(1, "Sanjar", "+998901114567" to "mobil"),
            person(2, "Sanjar", "+998935551234" to "mobil"),
        )
        val r = Resolver.pick(people, "Sanjar")
        assertTrue(r is Pick.Ask)
        val q = (r as Pick.Ask).question
        assertTrue(q.contains("45-67"))
        assertTrue(q.contains("12-34"))
    }

    @Test fun birXilIsm_oxirgiRaqamBilanTanlanadi() {
        val people = listOf(
            person(1, "Sanjar", "+998901114567" to "mobil"),
            person(2, "Sanjar", "+998935551234" to "mobil"),
        )
        val r = Resolver.pick(people, "Sanjar", "1234")
        assertTrue(r is Pick.One)
        assertEquals("+998935551234", (r as Pick.One).phone.number)
    }

    @Test fun birXilIsm_tartibBilanTanlanadi() {
        val people = listOf(
            person(5, "Sanjar", "+998901114567" to "mobil"),
            person(3, "Sanjar", "+998935551234" to "mobil"),
        )
        // Tartib barqaror: id bo'yicha — 3, keyin 5
        val r = Resolver.pick(people, "Sanjar", "ikkinchisi")
        assertTrue(r is Pick.One)
        assertEquals("+998901114567", (r as Pick.One).phone.number)
    }

    @Test fun birOdamIkkiMartaSaqlangan_birlashadi() {
        val people = Resolver.mergeDuplicates(
            listOf(
                person(1, "Sanjar", "+998 90 111 45 67" to "mobil"),
                person(2, "Sanjar", "901114567" to "mobil"),
            )
        )
        assertEquals(1, people.size)
        val r = Resolver.pick(people, "Sanjar")
        assertTrue(r is Pick.One)
    }

    @Test fun ikkitaRaqam_soraydi_keyinTurBilanTanlanadi() {
        val people = listOf(person(1, "Sanjar", "+998901114567" to "mobil", "+998712001234" to "ish"))
        // Bitta mobil bor — avtomatik mobilga
        assertTrue(Resolver.pick(people, "Sanjar") is Pick.One)
        val two = listOf(person(1, "Sanjar", "+998901114567" to "mobil", "+998935551234" to "mobil"))
        assertTrue(Resolver.pick(two, "Sanjar") is Pick.Ask)
        val r = Resolver.pick(listOf(person(1, "Sanjar", "+998901114567" to "uy", "+998712001234" to "ish")), "Sanjar", "ish")
        assertEquals("+998712001234", (r as Pick.One).phone.number)
    }

    @Test fun aniqIsmUstun() {
        val people = listOf(
            person(1, "Ali", "+998901110000" to "mobil"),
            person(2, "Alisher", "+998902220000" to "mobil"),
            person(3, "Ali Valiyev", "+998903330000" to "mobil"),
        )
        val r = Resolver.pick(people, "Ali")
        assertEquals("Ali", (r as Pick.One).person.name)
    }

    @Test fun qoshimchaliIsm() {
        val people = listOf(person(1, "Sanjar", "+998901114567" to "mobil"), person(2, "Onam", "+998907770000" to "mobil"))
        assertEquals("Sanjar", (Resolver.pick(people, "Sanjarga") as Pick.One).person.name)
        assertEquals("Onam", (Resolver.pick(people, "Onamga") as Pick.One).person.name)
    }

    @Test fun kirillchaIsm() {
        val people = listOf(person(1, "Sanjar Aka", "+998901114567" to "mobil"))
        assertTrue(Resolver.pick(people, "Санжар ака") is Pick.One)
    }

    @Test fun favqulodaRaqam() {
        val r = Resolver.pick(emptyList(), "112")
        assertEquals("112", (r as Pick.One).phone.number)
    }

    @Test fun topilmadi() {
        assertTrue(Resolver.pick(listOf(person(1, "Sanjar", "+998901114567" to "mobil")), "Bobur") is Pick.None)
    }
}
