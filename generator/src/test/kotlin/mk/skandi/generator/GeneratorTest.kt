package mk.skandi.generator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    @Test fun `fillable layout has no dead slots`() = assertEquals(0, LetterCheck(SMALL_WORDS).deadSlots(SMALL.slots))

    @Test fun `layout that clashes has dead slots`() {
        // Without ГД, row 2 needs Г?Д... nothing fits.
        assertTrue(LetterCheck(words("АБВ", "БГ", "ВД", "ЖЗ")).deadSlots(SMALL.slots) > 0)
        // A slot length the list doesn't have at all.
        assertTrue(LetterCheck(words("БГ", "ВД", "ГД")).deadSlots(SMALL.slots) > 0)
    }
}

class FillerTest {
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
    private fun request(seed: Long, w: Int = 7, h: Int = 9) = GenerateRequest(w, h, seed, 2, "2026-11-17")

    @Test fun `generated puzzles are valid, dense and use the word list`() {
        val byAnswer = realWords.associateBy { it.answer }
        for (seed in 1L..3L) {
            val g = generate(realWords, request(seed))
            assertNotNull("seed $seed", g)
            val p = g!!.puzzle
            assertEquals("seed $seed", Report(emptyList(), emptyList()), validate(p)) // no errors, no warnings
            assertEquals(7, p.width)
            assertEquals(9, p.height)
            assertTrue("no filler or picture cells", p.grid.all { row -> row.all { it == CLUE || it in MACEDONIAN_LETTERS } })
            val answers = p.entries().map { it.answer }
            assertEquals("no answer twice", answers.size, answers.toSet().size)
            for (e in p.entries()) {
                val word = byAnswer[e.answer]
                assertNotNull("${e.answer} is not in the word list", word)
                assertTrue("„${e.clue.text}“ is not a clue of ${e.answer}", e.clue.text in word!!.clues)
            }
            assertEquals("0002", p.id)
        }
    }

    @Test fun `same seed gives the same puzzle, another seed a different one`() {
        val a = generate(realWords, request(7))!!.puzzle
        assertEquals(a, generate(realWords, request(7))!!.puzzle)
        assertFalse(a.grid == generate(realWords, request(8))!!.puzzle.grid)
    }

    @Test fun `other sizes work`() {
        for ((w, h) in listOf(5 to 5, 6 to 8)) {
            val p = generate(realWords, request(1, w, h))?.puzzle
            assertNotNull("${w}x$h", p)
            assertTrue(validate(p!!).ok)
        }
    }

    @Test fun `gives up within the time limit when the list can't fill a grid`() {
        val started = System.nanoTime()
        val few = realWords.filter { it.answer.length == 3 }.take(12) // one length, a dozen words
        assertNull(generate(few, request(1).copy(timeLimitMillis = 1_500)))
        assertTrue("took too long", System.nanoTime() - started < 15_000_000_000)
    }

    @Test fun `JSON round-trips, including pictures, bent arrows and highlights`() {
        val hand = parsePuzzle(File(repoRoot, "data/site/puzzles/0001.json").readText())
        assertEquals(hand, parsePuzzle(puzzleToJson(hand)))
        val made = generate(realWords, request(3))!!.puzzle
        assertEquals(made, parsePuzzle(puzzleToJson(made)))
        assertFalse("straight arrows leave start out", puzzleToJson(made).contains("\"start\": \"right\", \"text\"") &&
            made.clues.none { it.bent && it.start == Start.RIGHT })
    }

    @Test fun `JSON escapes quotes and backslashes`() {
        val p = generate(realWords, request(3))!!.puzzle
        val tricky = p.copy(clues = p.clues.mapIndexed { i, c -> if (i == 0) c.copy(text = "„Она\" \\ ...“") else c })
        assertEquals(tricky, parsePuzzle(puzzleToJson(tricky)))
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
        val before = out.readText()
        assertFalse(generateCommand(args))
        assertEquals(before, out.readText())
    }

    @Test fun `bad options fail`() {
        assertFalse(generateCommand(listOf("--widht", "7") + wordsArg))
        assertFalse(generateCommand(listOf("--width") + wordsArg))
        assertFalse(generateCommand(listOf("--width", "seven") + wordsArg))
        assertFalse(generateCommand(listOf("--width", "31") + wordsArg))
        assertFalse(generateCommand(listOf("--number", "0") + wordsArg))
        assertFalse(generateCommand(listOf("--words", "/no/such.tsv")))
    }
}
