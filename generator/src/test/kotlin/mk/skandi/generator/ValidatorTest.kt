package mk.skandi.generator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ValidatorTest {
    /** A tiny valid puzzle: ДОМ across, ДА down, ОД down. */
    private fun small(
        grid: List<String> = listOf(
            ".##",
            "#ДО",
            ".АД",
        ),
        clues: List<Clue> = listOf(
            Clue(Cell(1, 0), Dir.ACROSS, "Предлог: ... утре"),
            Clue(Cell(0, 1), Dir.DOWN, "Потврден одговор"),
            Clue(Cell(0, 2), Dir.DOWN, "Предлог за потекло"),
        ),
        pictures: List<Picture> = emptyList(),
    ) = Puzzle(1, "test", 1, "2026-11-15", grid[0].length, grid.size, grid, clues, pictures)

    private fun errorsOf(p: Puzzle) = validate(p).errors

    @Test fun `small puzzle is valid`() {
        // Row 2 "АД" is a 2-letter run without a clue, so give it one by making (2,0) a clue cell.
        val p = small(
            grid = listOf(".##", "#ДО", "#АД"),
            clues = small().clues + Clue(Cell(2, 0), Dir.ACROSS, "Потврда и предлог"),
        )
        assertEquals(emptyList<String>(), errorsOf(p))
        assertEquals(listOf("ДО", "ДА", "ОД", "АД"), p.entries().map { it.answer })
    }

    @Test fun `unclued word is an error`() {
        assertTrue(errorsOf(small()).any { "(2,1) going across" in it && "no clue" in it })
    }

    @Test fun `wrong row length and bad characters are errors`() {
        val e = errorsOf(small(grid = listOf(".##", "#ДO", ".А")))
        assertTrue(e.any { "grid[1]" in it && "'O'" in it }) // Latin O, not Cyrillic О
        assertTrue(e.any { "grid[2]: has 2 cells" in it })
    }

    @Test fun `clue must be on a hash cell and point at 2+ letters`() {
        val p = small(
            grid = listOf(".##", "#ДО", "#А#"),
            clues = small().clues + Clue(Cell(2, 0), Dir.ACROSS, "Една буква") + Clue(Cell(1, 1), Dir.ACROSS, "На буква"),
        )
        val e = errorsOf(p)
        assertTrue(e.any { "at least 2 letters" in it })
        assertTrue(e.any { "(1,1) is 'Д'" in it })
    }

    @Test fun `empty hash cell and duplicate clue are errors`() {
        val p = small(
            grid = listOf("###", "#ДО", "#АД"),
            clues = small().clues + Clue(Cell(2, 0), Dir.ACROSS, "А") + Clue(Cell(2, 0), Dir.ACROSS, "Б"),
        )
        val e = errorsOf(p)
        assertTrue(e.any { "(0,0) holds no clue" in it })
        assertTrue(e.any { "a second across clue in (2,0)" in it })
    }

    @Test fun `pictures must cover exactly the star cells`() {
        val grid = listOf("**#", "**Д", "#НЕ")
        val clues = listOf(Clue(Cell(0, 2), Dir.DOWN, "Не, обратно"), Clue(Cell(2, 0), Dir.ACROSS, "Одрекување"))
        val pic = Picture(Cell(0, 0), 2, 2, "x.svg", "x", "x")
        assertEquals(emptyList<String>(), errorsOf(small(grid, clues, listOf(pic))))
        assertTrue(errorsOf(small(grid, clues, emptyList())).any { "not covered" in it })
        assertTrue(errorsOf(small(grid, clues, listOf(pic.copy(width = 3)))).any { "instead of '*'" in it })
    }

    @Test fun `repeated answer is only a warning`() {
        val p = small(
            grid = listOf(".##", "#ДА", "#АД"),
            clues = listOf(
                Clue(Cell(1, 0), Dir.ACROSS, "Потврда"),
                Clue(Cell(2, 0), Dir.ACROSS, "Потврда и предлог"),
                Clue(Cell(0, 1), Dir.DOWN, "Потврда"),
                Clue(Cell(0, 2), Dir.DOWN, "Потврда и предлог"),
            ),
        )
        val r = validate(p)
        assertTrue(r.ok)
        assertTrue(r.warnings.any { "ДА is used 2 times" in it })
    }

    @Test fun `parser reports the path of a bad field`() {
        val e = runCatching { parsePuzzle("""{"format":1,"id":"x","number":"one"}""") }.exceptionOrNull()
        assertTrue(e is FormatException && e.message == "number: expected a whole number")
    }

    @Test fun `date must be a real date`() {
        assertTrue(errorsOf(small().copy(date = "2026-13-45")).any { "date" in it })
        assertTrue(errorsOf(small().copy(date = "2026-02-30")).any { "date" in it })
    }

    @Test fun `header error does not hide grid errors`() {
        val e = errorsOf(small().copy(date = "x"))
        assertTrue(e.any { "date" in it })
        assertTrue(e.any { "no clue" in it })
    }

    @Test fun `clue outside the grid is an error`() {
        val p = small(clues = small().clues + Clue(Cell(99, 0), Dir.ACROSS, "Никаде"))
        assertTrue(errorsOf(p).any { "(99,0) is outside the grid" in it })
    }

    @Test fun `pictures out of bounds, negative or overlapping are errors`() {
        val grid = listOf("**#", "**Д", "#НЕ")
        val clues = listOf(Clue(Cell(0, 2), Dir.DOWN, "А"), Clue(Cell(2, 0), Dir.ACROSS, "Б"))
        val pic = Picture(Cell(0, 0), 2, 2, "x.svg", "x", "x")
        assertTrue(errorsOf(small(grid, clues, listOf(pic.copy(cell = Cell(-1, 0))))).any { "outside the grid" in it })
        assertTrue(errorsOf(small(grid, clues, listOf(pic.copy(height = Int.MAX_VALUE)))).any { "outside the grid" in it })
        assertTrue(errorsOf(small(grid, clues, listOf(pic, pic.copy(width = 1)))).any { "overlap" in it })
    }

    @Test fun `at most one highlighted answer per picture`() {
        val p = small(
            grid = listOf(".##", "#ДО", "#АД"),
            clues = small().clues.map { it.copy(highlight = true) } + Clue(Cell(2, 0), Dir.ACROSS, "Б"),
        )
        assertTrue(errorsOf(p).any { "highlighted answers but only 0 picture" in it })
    }

    @Test fun `size limits`() {
        val row = "." .repeat(31)
        assertTrue(errorsOf(small(grid = List(3) { row }, clues = emptyList())).any { "3 to 30" in it })
    }

    @Test fun `parser rejects unknown fields and malformed JSON`() {
        val ok = File(repoRoot, "data/site/puzzles/0001.json").readText()
        val typo = runCatching { parsePuzzle(ok.replace("\"highlight\"", "\"higlight\"")) }.exceptionOrNull()
        assertTrue(typo is FormatException && "unknown field \"higlight\"" in typo.message!!)
        assertTrue(runCatching { parsePuzzle("{ not json") }.exceptionOrNull() is FormatException)
        assertTrue(runCatching { parsePuzzle(ok.replace("\"down\"", "\"left\"")) }.exceptionOrNull() is FormatException)
    }

    @Test fun `example puzzle in the repo is valid`() {
        val p = parsePuzzle(File(repoRoot, "data/site/puzzles/0001.json").readText())
        assertEquals(emptyList<String>(), validate(p).errors)
        val solution = p.entries().single { it.clue.highlight }.answer
        assertEquals("ОБЛАК", solution)
    }
}
