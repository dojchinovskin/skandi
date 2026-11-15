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

    @Test fun `every invalid file is rejected`() {
        val invalid = files("invalid")
        assertTrue("no test files found", invalid.size >= 10)
        val accepted = invalid.filter { problems(it).isEmpty() }.map { it.name }
        assertEquals("accepted but should be rejected", emptyList<String>(), accepted)
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
