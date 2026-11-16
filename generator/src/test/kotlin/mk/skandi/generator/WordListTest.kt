package mk.skandi.generator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WordListTest {
    private val header = "збор\tопис\tслика\n"

    private fun parse(body: String, pictures: Set<String>? = setOf("oblak.svg")) = parseWordList(header + body, pictures)

    /** Asserts the list is rejected with an error containing [expected]. */
    private fun assertError(body: String, expected: String) {
        val errors = parse(body).report.errors
        assertTrue("expected an error mentioning <$expected>, got $errors", errors.any { expected in it })
    }

    @Test fun `good list loads`() {
        val list = parse(
            """
            # Кратки зборови
            ОКО	Орган за вид | Јамка во мрежа
            РЕКА	Тече низ град

            ОБЛАК	Што гледаш на сликата?	oblak.svg
            """.trimIndent(),
        )
        assertEquals(emptyList<String>(), list.report.errors)
        assertEquals(listOf("ОКО", "РЕКА", "ОБЛАК"), list.words.map { it.answer })
        assertEquals(listOf("Орган за вид", "Јамка во мрежа"), list.words[0].clues)
        assertEquals("oblak.svg", list.words[2].picture)
        assertEquals(null, list.words[0].picture)
        assertEquals(3, list.words[0].line) // header is line 1, comment line 2
        assertEquals(mapOf(3 to 1, 4 to 1, 5 to 1), list.lengths())
    }

    @Test fun `two-column header, BOM and Windows line endings are accepted`() {
        val list = parseWordList("﻿збор\tопис\r\nОКО\tОрган за вид\r\n")
        assertEquals(emptyList<String>(), list.report.errors)
        assertEquals(listOf("ОКО"), list.words.map { it.answer })
    }

    @Test fun `all 31 letters are accepted`() {
        val half = MACEDONIAN_LETTERS.length / 2
        val list = parse("${MACEDONIAN_LETTERS.take(half)}\tПрва половина\n${MACEDONIAN_LETTERS.drop(half)}\tВтора половина")
        assertEquals(emptyList<String>(), list.report.errors)
    }

    @Test fun `bad header`() {
        assertTrue(parseWordList("word\tclue\nОКО\tОрган за вид").report.errors.any { "line 1: header" in it })
        assertTrue(parseWordList("").report.errors.any { "empty" in it })
        assertTrue(parseWordList("збор\tопис\n# само коментар\n").report.errors.any { "no words" in it })
    }

    @Test fun `wrong number of columns`() {
        assertError("ОКО", "line 2: expected 2 or 3 columns")
        assertError("ОКО\tОрган за вид\toblak.svg\tвишок", "got 4")
        assertError("ОКО Орган за вид", "got 1") // spaces instead of a tab
    }

    @Test fun `Latin look-alike is named with its Cyrillic letter`() {
        assertError("OКО\tОрган за вид", "letter 1 'O' is Latin; use Cyrillic 'О'")
        assertTrue(parse("ЅЈАЅ\tТест").report.errors.isEmpty()) // Cyrillic Ѕ and Ј are fine…
        assertError("SЈА\tТест", "'S' is Latin; use Cyrillic 'Ѕ'") // …Latin S and J are not
        assertError("ЅJА\tТест", "'J' is Latin; use Cyrillic 'Ј'")
    }

    @Test fun `lowercase, spaces, digits and non-Macedonian Cyrillic are rejected`() {
        assertError("око\tОрган за вид", "'о' is lowercase")
        assertError("ДВА ОКА\tТест", "letter 4 is a space")
        assertError("ОК0\tТест", "'0' is not a Macedonian letter")
        assertError("ЙОД\tТест", "'Й' is not a Macedonian letter") // Russian letter
        assertError("ЁЖ\tТест", "'Ё' is not a Macedonian letter")
    }

    @Test fun `letter typed as two characters is rejected`() {
        assertError("ЃОЛ\tТест", "letter plus an accent mark") // Г + combining acute, not Ѓ
    }

    @Test fun `length limits`() {
        assertError("А\tТест", "at least 2 letters")
        assertError("${"А".repeat(31)}\tТест", "longer than 30")
        assertTrue(parse("${"А".repeat(30)}\tТест").report.errors.isEmpty())
    }

    @Test fun `duplicates are rejected and point to the first line`() {
        assertError("ОКО\tОрган за вид\nРЕКА\tТече\nОКО\tЈамка во мрежа", "line 4 (ОКО): already on line 2")
        val list = parse("ОКО\tОрган за вид\nОКО\tЈамка во мрежа")
        assertEquals("only the first copy is kept", listOf(2), list.words.map { it.line })
    }

    @Test fun `missing, empty and repeated clues`() {
        assertError("ОКО\t", "no clue")
        assertError("ОКО\t   ", "no clue")
        assertError("ОКО\tОрган за вид |", "clue 2 is empty")
        assertError("ОКО\tОрган за вид | орган за вид", "listed twice")
        assertError("\tОрган за вид", "the word is empty")
    }

    @Test fun `clue must not contain the answer as a whole word`() {
        assertError("ОКО\tОко на бура", "contains the answer")
        assertError("ОКО\tОрган за вид | Сокол, ОКО?", "clue 2")
        // Inside a longer word is fine: СОКОЛ contains ОКО.
        assertTrue(parse("ОКО\tКако на сокол").report.errors.isEmpty())
    }

    @Test fun `clue with Latin letters is rejected, digits are fine`() {
        assertError("ОКО\tOрган за вид", "Latin letters (O)")
        assertError("РЕКА\tТече низ Skopje", "Latin letters")
        assertError("СЕ\tСè и сешто", "Latin letters (è)") // Latin è instead of Cyrillic ѐ
        assertTrue(parse("ВЕК\t100 години").report.errors.isEmpty())
    }

    @Test fun `straight quotes are rejected, Macedonian quotes are fine`() {
        assertError("ЛЕБ\t\"Се пече во фурна\"", "straight quote")
        assertTrue(parse("ДЕКА\tСврзник: „Знам ... е тука“").report.ok)
    }

    @Test fun `picture name and existence`() {
        assertError("ОБЛАК\tНа небо\tOblak.svg", "must be lowercase")
        assertError("ОБЛАК\tНа небо\t../oblak.svg", "must be lowercase")
        assertError("ОБЛАК\tНа небо\tsonce.svg", "pictures/sonce.svg not found")
        assertTrue("null skips the existence check", parse("ОБЛАК\tНа небо\tsonce.svg", pictures = null).report.ok)
        assertTrue("empty third column means no picture", parse("ОКО\tОрган за вид\t").report.ok)
    }

    @Test fun `long clues give warnings, not errors`() {
        val list = parse("ОКО\tОрган со кој гледаме сите работи околу нас | Неупотребливост")
        assertTrue(list.report.ok)
        assertTrue(list.report.warnings.any { "characters" in it })
        assertTrue(list.report.warnings.any { "„Неупотребливост“" in it })
    }

    @Test fun `rows with errors are left out, good rows stay`() {
        val list = parse("ОКО\tОрган за вид\nOKO\tЛошо\nРЕКА\tТече")
        assertFalse(list.report.ok)
        assertEquals(listOf("ОКО", "РЕКА"), list.words.map { it.answer })
    }
}

class WordsCheckCliTest {
    private fun list(body: String, picture: String? = "oblak.svg"): File {
        val data = Files.createTempDirectory("skandi").toFile()
        File(data, "words").mkdirs()
        File(data, "site/pictures").mkdirs()
        if (picture != null) File(data, "site/pictures/$picture").writeText("<svg/>")
        return File(data, "words/words.tsv").apply { writeText("збор\tопис\tслика\n$body") }
    }

    @Test fun `good list passes`() = assertTrue(wordsCheckCommand(listOf(list("ОБЛАК\tНа небо\toblak.svg").path)))

    @Test fun `bad list fails`() = assertFalse(wordsCheckCommand(listOf(list("OKO\tТест").path)))

    @Test fun `picture is looked up next to the list`() =
        assertFalse(wordsCheckCommand(listOf(list("ОБЛАК\tНа небо\toblak.svg", picture = null).path)))

    @Test fun `wrong arguments fail`() {
        assertFalse(wordsCheckCommand(emptyList()))
        assertFalse(wordsCheckCommand(listOf("/no/such/words.tsv")))
        assertFalse(wordsCheckCommand(listOf("a.tsv", "b.tsv")))
    }

    @Test fun `the real word list passes`() =
        assertTrue(wordsCheckCommand(listOf(File(repoRoot, "data/words/words.tsv").path)))
}
