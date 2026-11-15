package mk.skandi.generator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Repo root, found by walking up from the working directory (Gradle runs tests in generator/). */
internal val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "format/README.md").isFile }

/** format/testdata is shared with the web tests, which check the same files against the JSON Schema. */
class FormatTestDataTest {
    private fun files(folder: String) =
        File(repoRoot, "format/testdata/$folder").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }

    private fun problems(file: File): List<String> = try {
        validate(parsePuzzle(file.readText())).errors
    } catch (e: FormatException) {
        listOf(e.message ?: "unreadable")
    }

    /** What each invalid file's error must mention, so a file can't pass by failing for another reason. */
    private val EXPECTED = mapOf(
        "bad-date" to "date",
        "bad-dir" to "dir",
        "bad-id" to "id:",
        "bad-start" to "start: must be right or below",
        "blank-alt" to "alt is empty",
        "blank-clue" to "text is empty",
        "blank-credit" to "credit is empty",
        "format-2" to "format",
        "huge-picture" to "outside the grid",
        "latin-letter" to "'O'",
        "missing-field" to "clues: missing",
        "picture-path" to "file \"../",
        "picture-uppercase" to "file \"Oblak",
        "too-wide" to "3 to 30",
        "unknown-field" to "unknown field \"higlight\"",
        "unknown-picture-field" to "unknown field \"note\"",
        "unknown-top-field" to "unknown field \"extra\"",
    )

    @Test fun `every invalid file is rejected for its own defect`() {
        val invalid = files("invalid")
        assertEquals("EXPECTED must list every file", EXPECTED.keys.sorted(), invalid.map { it.nameWithoutExtension })
        for (file in invalid) {
            val expected = EXPECTED.getValue(file.nameWithoutExtension)
            val errors = problems(file)
            assertTrue("${file.name}: expected an error mentioning <$expected>, got $errors", errors.any { expected in it })
        }
    }

    @Test fun `every valid file is accepted`() {
        val valid = files("valid")
        assertTrue(valid.isNotEmpty())
        valid.forEach { assertEquals(it.name, emptyList<String>(), problems(it)) }
    }
}

class CliTest {
    /** Builds a throwaway data/site folder with one puzzle and returns the puzzle file. */
    private fun site(id: String = "0001", fileName: String = "0001.json", picture: String? = "oblak.svg"): File {
        val dir = Files.createTempDirectory("skandi").toFile()
        File(dir, "puzzles").mkdirs()
        File(dir, "pictures").mkdirs()
        if (picture != null) File(dir, "pictures/$picture").writeText("<svg/>")
        val json = File(repoRoot, "data/site/puzzles/0001.json").readText().replace("\"id\": \"0001\"", "\"id\": \"$id\"")
        return File(dir, "puzzles/$fileName").apply { writeText(json) }
    }

    @Test fun `valid puzzle passes`() = assertTrue(validateCommand(listOf(site().path)))

    @Test fun `folder of puzzles passes`() = assertTrue(validateCommand(listOf(site().parent)))

    @Test fun `id must match file name`() = assertFalse(validateCommand(listOf(site(id = "0002").path)))

    @Test fun `missing picture fails`() = assertFalse(validateCommand(listOf(site(picture = null).path)))

    @Test fun `picture name must match case exactly`() = assertFalse(validateCommand(listOf(site(picture = "Oblak.svg").path)))

    @Test fun `missing path and empty arguments fail`() {
        assertFalse(validateCommand(listOf("/no/such/file.json")))
        assertFalse(validateCommand(emptyList()))
    }
}
