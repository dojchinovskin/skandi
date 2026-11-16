package mk.skandi.generator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Random

/**
 * A small complete layout:
 *   # # #     (0,0) clues the row below with a bent arrow; (0,1) and (0,2) clue the columns below them
 *   - - -
 *   # - -     (2,0) clues the two letters right of it
 */
private val SMALL = Layout.parse("###\n---\n#--")

private fun words(vararg answers: String) = answers.mapIndexed { i, a -> Word(a, listOf("Опис $i"), null, i + 2) }

/** АБВ across row 1, БГ and ВД down, ГД across row 2: the only way to fill [SMALL] from these. */
private val SMALL_WORDS = words("АБВ", "БГ", "ВД", "ГД", "ЖЗ", "ЖЖЖ")

private val realWords by lazy { parseWordList(File(repoRoot, "data/words/words.tsv").readText()).words }
private val realPictures by lazy { parsePictureList(File(repoRoot, "data/words/pictures.tsv").readText()).pictures }

class LayoutTest {
    @Test fun `slots are the runs of 2+ letters`() {
        assertEquals(
            setOf(
                Slot(Cell(1, 0), Dir.ACROSS, 3), Slot(Cell(2, 1), Dir.ACROSS, 2),
                Slot(Cell(1, 1), Dir.DOWN, 2), Slot(Cell(1, 2), Dir.DOWN, 2),
            ),
            SMALL.slots.toSet(),
        )
        assertEquals(0, SMALL.orphans())
    }

    @Test fun `clue spots are left of or above the first letter`() {
        assertEquals(listOf(ClueSpot(Cell(0, 0), Start.BELOW)), SMALL.spotsFor(Slot(Cell(1, 0), Dir.ACROSS, 3)))
        assertEquals(listOf(ClueSpot(Cell(2, 0), Start.RIGHT)), SMALL.spotsFor(Slot(Cell(2, 1), Dir.ACROSS, 2)))
        // A cell with clue cells both left of and above it offers both spots.
        assertEquals(
            listOf(ClueSpot(Cell(1, 0), Start.RIGHT), ClueSpot(Cell(0, 1), Start.BELOW)),
            Layout.parse("###\n#--\n#--").spotsFor(Slot(Cell(1, 1), Dir.ACROSS, 2)),
        )
    }

    @Test fun `complete assignment gives every slot a spot and every clue cell a clue`() {
        val a = SMALL.assignment
        assertTrue(a.complete)
        assertEquals(SMALL.slots.toSet(), a.spots.keys)
        assertEquals(a.spots.size, a.spots.values.toSet().size) // no spot used twice
        assertEquals(SMALL.clueCells.toSet(), a.spots.values.map { it.cell }.toSet())
    }

    @Test fun `unusable clue cell is a fault`() {
        // The bottom-right '#' can't point anywhere.
        assertFalse(Layout.parse("###\n---\n#-#").let { it.assignment.complete && it.orphans() == 0 })
        // (0,0) has nothing to clue: right of it and below it are clue cells.
        assertEquals(1, Layout.parse("###\n#--\n#--").assignment.missing)
    }

    @Test fun `two answers that need the same spot are a fault`() {
        // Row 0 and column 1 both start at (0,1); the only clue cell next to it is (0,0).
        val l = Layout.parse("#--\n#--\n#--")
        assertTrue(l.assignment.missing > 0)
    }

    @Test fun `one clue cell can hold two clues, one each way`() {
        // (0,0) clues row 0 (right) and column 0 (below), as in magazines.
        val l = Layout.parse("#--#\n-#--\n-#--\n#---")
        val spots = l.assignment.spots.values.filter { it.cell == Cell(0, 0) }.map { it.start }.toSet()
        assertEquals(setOf(Start.RIGHT, Start.BELOW), spots)
    }

    @Test fun `picture cells block words and hold no clues`() {
        val l = Layout.parse("**#\n**-\n#--")
        assertTrue(l.isPicture(Cell(0, 0)))
        assertFalse(l.isLetter(Cell(1, 1)) || l.isClue(Cell(1, 1)))
        // (1,2) and (2,1)-(2,2): the picture stops the run at row 1 / column 1.
        assertEquals(setOf(Slot(Cell(1, 2), Dir.DOWN, 2), Slot(Cell(2, 1), Dir.ACROSS, 2)), l.slots.toSet())
        assertEquals("**#\n**-\n#--", l.toString())
        assertThrows(IllegalArgumentException::class.java) { l.flip(Cell(0, 0)) }
    }

    @Test fun `layout search keeps the picture block and makes room for the picture word`() {
        val picture = setOf(Cell(0, 0), Cell(0, 1), Cell(1, 0), Cell(1, 1))
        val goal = LayoutGoal(setOf(2, 3, 4, 5, 6), letters = LetterCheck(realWords), needWord = "ОБЛАК")
        val l = searchLayout(6, 7, goal, Random(4), picture = picture)!!
        assertEquals(picture, l.cells.filter { l.isPicture(it) }.toSet())
        assertTrue(l.slots.any { it.length == 5 })
        assertEquals(0, faults(l, goal))
    }

    @Test fun `layout search stops at the deadline`() {
        val goal = LayoutGoal(setOf(2, 3), letters = LetterCheck(realWords))
        assertNull(searchLayout(30, 30, goal, Random(1), deadline = System.nanoTime()))
    }

    @Test fun `layout search finds usable layouts`() {
        val goal = LayoutGoal(setOf(2, 3, 4, 5, 6), letters = LetterCheck(realWords))
        repeat(3) { seed ->
            val l = searchLayout(7, 9, goal, Random(seed.toLong()))
            assertNotNull("seed $seed", l)
            assertEquals("seed $seed\n$l", 0, faults(l!!, goal))
            assertTrue(l.isClue(Cell(0, 0)))
        }
    }
}

class LetterCheckTest {
    @Test fun `pinned word narrows the check`() {
        val row1 = SMALL.slots.indexOf(Slot(Cell(1, 0), Dir.ACROSS, 3))
        assertEquals(0, LetterCheck(SMALL_WORDS).deadSlots(SMALL.slots, mapOf(row1 to "АБВ")))
        // ЖЖЖ in row 1 needs words starting with Ж going down; there are none.
        assertTrue(LetterCheck(SMALL_WORDS).deadSlots(SMALL.slots, mapOf(row1 to "ЖЖЖ")) > 0)
    }

    @Test fun `fillable layout has no dead slots`() = assertEquals(0, LetterCheck(SMALL_WORDS).deadSlots(SMALL.slots))

    @Test fun `layout that clashes has dead slots`() {
        // Without ГД, row 2 needs Г?Д... nothing fits.
        assertTrue(LetterCheck(words("АБВ", "БГ", "ВД", "ЖЗ")).deadSlots(SMALL.slots) > 0)
        // A slot length the list doesn't have at all.
        assertTrue(LetterCheck(words("БГ", "ВД", "ГД")).deadSlots(SMALL.slots) > 0)
    }
}

class FillerTest {
    @Test fun `fixed word goes where it's pinned, and only there`() {
        val l = Layout.parse("###\n#--\n#--")
        val list = words("АБ", "БВ", "ВГ", "АВ", "БГ", "ГД", "ДЕ")
        val top = Slot(Cell(1, 1), Dir.ACROSS, 2)
        // The only fill with АБ on top: АБ / ВГ across, АВ / БГ down.
        val fill = Filler(list).fill(l.slots, Random(2), fixed = mapOf(top to list[0]))!!
        assertEquals(setOf("АБ", "ВГ", "АВ", "БГ"), fill.values.map { it.answer }.toSet())
        assertEquals("АБ", fill.getValue(top).answer)
        // A word that isn't in the list can't be pinned.
        assertNull(Filler(list).fill(l.slots, Random(2), fixed = mapOf(top to words("ЖЖ")[0])))
    }

    @Test fun `fills crossings consistently`() {
        val fill = Filler(SMALL_WORDS).fill(SMALL.slots, Random(1))
        assertNotNull(fill)
        val byStart = fill!!.mapKeys { (s, _) -> s.start to s.dir }.mapValues { it.value.answer }
        assertEquals("АБВ", byStart[Cell(1, 0) to Dir.ACROSS])
        assertEquals("БГ", byStart[Cell(1, 1) to Dir.DOWN])
        assertEquals("ВД", byStart[Cell(1, 2) to Dir.DOWN])
        assertEquals("ГД", byStart[Cell(2, 1) to Dir.ACROSS])
    }

    @Test fun `returns null when no fill exists`() =
        assertNull(Filler(words("АБВ", "БГ", "ВД", "ЖЗ")).fill(SMALL.slots, Random(1)))

    @Test fun `never uses a word twice`() {
        // Four 2-letter slots in a square; ББ fits them all, but may be used only once.
        val square = Layout.parse("###\n#--\n#--")
        assertEquals(4, square.slots.size)
        assertNull(Filler(words("ББ")).fill(square.slots, Random(1)))
    }
}

class GenerateTest {
    private fun request(seed: Long, w: Int = 7, h: Int = 9, picture: Boolean = true) =
        GenerateRequest(w, h, seed, 2, "2026-11-17", withPicture = picture)

    private fun make(r: GenerateRequest) = generate(realWords, r, realPictures)

    /** Every check a generated puzzle must pass, picture or not. */
    private fun assertGood(p: Puzzle, label: String) {
        assertEquals(label, Report(emptyList(), emptyList()), validate(p)) // no errors, no warnings
        assertTrue("$label: no filler cells", p.grid.all { row -> row.all { it == CLUE || it == PICTURE || it in MACEDONIAN_LETTERS } })
        val answers = p.entries().map { it.answer }
        assertEquals("$label: no answer twice", answers.size, answers.toSet().size)
        val byAnswer = realWords.associateBy { it.answer }
        for (e in p.entries()) {
            val word = byAnswer[e.answer]
            assertNotNull("$label: ${e.answer} is not in the word list", word)
            if (e.clue.highlight) {
                assertEquals(PICTURE_CLUE, e.clue.text)
                assertEquals("$label: the picture shows the highlighted word", word!!.picture, p.pictures.single().file)
            } else {
                assertTrue("$label: „${e.clue.text}“ is not a clue of ${e.answer}", e.clue.text in word!!.clues)
            }
        }
    }

    @Test fun `picture puzzles are valid, with the picture word highlighted`() {
        for (seed in 1L..3L) {
            val p = make(request(seed))?.puzzle
            assertNotNull("seed $seed", p)
            assertGood(p!!, "seed $seed")
            val pic = p.pictures.single()
            assertEquals(3 to 3, pic.width to pic.height) // default for 7×9
            assertTrue("in a corner", pic.cell.row in setOf(0, 9 - 3) && pic.cell.col in setOf(0, 7 - 3))
            assertEquals(realPictures.getValue(pic.file).alt, pic.alt)
            assertEquals(1, p.clues.count { it.highlight })
        }
    }

    @Test fun `puzzles without a picture`() {
        val p = make(request(1, picture = false))!!.puzzle
        assertGood(p, "no picture")
        assertTrue(p.pictures.isEmpty() && p.clues.none { it.highlight })
        assertTrue(p.grid.none { PICTURE in it })
    }

    @Test fun `a chosen picture and size are used`() {
        val p = make(request(2).copy(pictureFile = "mak.svg", pictureSize = 2 to 3))!!.puzzle
        assertGood(p, "mak")
        assertEquals("mak.svg", p.pictures.single().file)
        assertEquals(2 to 3, p.pictures.single().let { it.width to it.height })
        assertEquals("МАК", p.entries().single { it.clue.highlight }.answer)
    }

    @Test fun `impossible requests explain themselves`() {
        assertThrows(GenerateException::class.java) { make(request(1).copy(pictureFile = "nema.svg")) }
        assertThrows(GenerateException::class.java) { make(request(1).copy(pictureSize = 7 to 2)) } // as wide as the grid
        assertThrows(GenerateException::class.java) { generate(realWords, request(1), emptyMap()) } // no picture info
        assertThrows(GenerateException::class.java) { generate(realWords.take(5), request(1, picture = false)) }
    }

    @Test fun `same seed gives the same puzzle, another seed a different one`() {
        val a = make(request(7))!!.puzzle
        assertEquals(a, make(request(7))!!.puzzle)
        assertFalse(a.grid == make(request(8))!!.puzzle.grid)
    }

    @Test fun `other sizes work`() {
        for ((w, h) in listOf(4 to 4, 5 to 5, 6 to 8)) {
            val p = make(request(1, w, h))?.puzzle
            assertNotNull("${w}x$h", p)
            assertGood(p!!, "${w}x$h")
        }
    }

    @Test fun `gives up within the time limit when the list can't fill a grid`() {
        val started = System.nanoTime()
        val few = realWords.filter { it.answer.length == 3 }.take(12) // one length, a dozen words
        assertNull(generate(few, request(1, picture = false).copy(timeLimitMillis = 1_500)))
        assertTrue("took too long", System.nanoTime() - started < 5_000_000_000)
    }

    @Test fun `JSON round-trips, including pictures, bent arrows and highlights`() {
        val hand = parsePuzzle(File(repoRoot, "data/site/puzzles/0001.json").readText())
        assertEquals(hand, parsePuzzle(puzzleToJson(hand)))
        val made = make(request(3))!!.puzzle
        assertEquals(made, parsePuzzle(puzzleToJson(made)))
        assertTrue(made.clues.any { it.bent } && made.pictures.isNotEmpty())
    }

    @Test fun `JSON escapes quotes and backslashes`() {
        val p = make(request(3))!!.puzzle
        val tricky = p.copy(clues = p.clues.mapIndexed { i, c -> if (i == 0) c.copy(text = "„Она\" \\ ...“") else c })
        assertEquals(tricky, parsePuzzle(puzzleToJson(tricky)))
    }

    @Test fun `published puzzle 0002 is what the generator makes for its seed`() {
        // 0002.json was written by: generate --seed 31 --number 2 --date 2026-11-17
        val made = make(GenerateRequest(7, 9, 31, 2, "2026-11-17"))!!.puzzle
        assertEquals(parsePuzzle(File(repoRoot, "data/site/puzzles/0002.json").readText()), made)
    }
}

class GenerateCliTest {
    private fun tmp() = Files.createTempDirectory("skandi").toFile()
    private val wordsArg = listOf("--words", File(repoRoot, "data/words/words.tsv").path)

    @Test fun `writes a valid puzzle and never overwrites`() {
        val out = File(tmp(), "puzzles/0002.json")
        val args = listOf("--seed", "5", "--number", "2", "--date", "2026-11-17", "--out", out.path) + wordsArg
        assertTrue(generateCommand(args))
        val p = parsePuzzle(out.readText())
        assertTrue(validate(p).ok)
        assertEquals("0002", p.id)
        assertEquals("2026-11-17", p.date)
        assertEquals(1, p.pictures.size)
        val before = out.readText()
        assertFalse(generateCommand(args))
        assertEquals(before, out.readText())
    }

    @Test fun `picture options`() {
        assertTrue(generateCommand(listOf("--seed", "1", "--picture", "none") + wordsArg))
        assertTrue(generateCommand(listOf("--seed", "1", "--picture", "kluch.svg", "--picture-size", "2x2") + wordsArg))
        assertFalse(generateCommand(listOf("--seed", "1", "--picture", "nema.svg") + wordsArg))
        assertFalse(generateCommand(listOf("--picture-size", "3") + wordsArg))
        assertFalse(generateCommand(listOf("--picture-size", "9x9") + wordsArg))
    }

    @Test fun `bad options fail before generating`() {
        assertFalse(generateCommand(listOf("--widht", "7") + wordsArg))
        assertFalse(generateCommand(listOf("--width") + wordsArg))
        assertFalse(generateCommand(listOf("--width", "seven") + wordsArg))
        assertFalse(generateCommand(listOf("--width", "31") + wordsArg))
        assertFalse(generateCommand(listOf("--number", "0") + wordsArg))
        assertFalse(generateCommand(listOf("--seed", "1", "--seed", "2") + wordsArg))
        assertFalse(generateCommand(listOf("--words", "/no/such.tsv")))
        // Impossible dates are caught up front, not by a crash after generating.
        val started = System.nanoTime()
        assertFalse(generateCommand(listOf("--date", "2026-02-30") + wordsArg))
        assertFalse(generateCommand(listOf("--date", "sutra") + wordsArg))
        assertTrue("checked before generating", System.nanoTime() - started < 1_000_000_000)
    }

    @Test fun `out file name must match the puzzle number`() {
        val out = File(tmp(), "0005.json")
        assertFalse(generateCommand(listOf("--seed", "1", "--number", "2", "--out", out.path) + wordsArg))
        assertFalse(out.exists())
    }

    @Test fun `a word list that is too small gives a message, not a crash`() {
        val dir = tmp()
        val list = File(dir, "words.tsv").apply { writeText("збор\tопис\nОКО\tОрган за вид\nРЕКА\tТече\n") }
        assertFalse(generateCommand(listOf("--seed", "1", "--picture", "none", "--words", list.path)))
    }
}
