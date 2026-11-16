package mk.skandi.generator

import java.io.File
import kotlin.system.exitProcess

private const val USAGE = """Skandi generator

Usage:
  validate <file-or-folder>...   Check puzzle JSON files against format/README.md
  words-check <words.tsv>        Check the word list against data/words/README.md

Generating puzzles comes in milestone 4."""

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "validate" -> exitProcess(if (validateCommand(args.drop(1))) 0 else 1)
        "words-check" -> exitProcess(if (wordsCheckCommand(args.drop(1))) 0 else 1)
        null, "help", "-h", "--help" -> println(USAGE)
        else -> {
            System.err.println("Unknown command: ${args[0]}\n\n$USAGE")
            exitProcess(2)
        }
    }
}

/** Validates each file (folders: every *.json inside). Returns true when all are valid. */
fun validateCommand(paths: List<String>): Boolean {
    if (paths.isEmpty()) {
        System.err.println("validate: give at least one file or folder")
        return false
    }
    val files = paths.flatMap { path ->
        val f = File(path)
        when {
            f.isDirectory -> f.listFiles { x -> x.extension == "json" }.orEmpty().sortedBy { it.name }
            f.isFile -> listOf(f)
            else -> { System.err.println("$path: not found"); return false }
        }
    }
    if (files.isEmpty()) {
        System.err.println("validate: no .json files found")
        return false
    }
    var allOk = true
    for (file in files) {
        val report = try {
            val puzzle = parsePuzzle(file.readText())
            val r = validate(puzzle)
            val extra = mutableListOf<String>()
            if (puzzle.id != file.nameWithoutExtension) extra += "id \"${puzzle.id}\" must match the file name \"${file.nameWithoutExtension}\""
            // Picture files live next to the puzzles folder: data/site/pictures/.
            // absoluteFile, so a bare name like "0001.json" still has parent folders.
            // Compare against the real file names, so a wrong-case name fails here too (macOS and Windows
            // ignore case on disk, GitHub Pages doesn't).
            val pictureNames = File(file.absoluteFile.parentFile.parentFile, "pictures").list().orEmpty().toSet()
            puzzle.pictures.map { it.file }.filter { it !in pictureNames }
                .forEach { extra += "picture file pictures/$it not found" }
            r.copy(errors = r.errors + extra)
        } catch (e: FormatException) {
            Report(listOf(e.message ?: "unreadable"), emptyList())
        }
        if (report.ok) println("OK    ${file.path}") else println("FAIL  ${file.path}")
        report.errors.forEach { println("  error:   $it") }
        report.warnings.forEach { println("  warning: $it") }
        allOk = allOk && report.ok
    }
    return allOk
}

/**
 * Checks a word list and prints its errors, warnings and how many words there are of each length.
 * Pictures are looked up in data/site/pictures next to the list's data/words folder.
 */
fun wordsCheckCommand(args: List<String>): Boolean {
    if (args.size != 1) {
        System.err.println("words-check: give exactly one .tsv file")
        return false
    }
    val file = File(args[0])
    if (!file.isFile) {
        System.err.println("${args[0]}: not found")
        return false
    }
    val picturesDir = File(file.absoluteFile.parentFile.parentFile, "site/pictures")
    val list = parseWordList(file.readText(), picturesDir.list().orEmpty().toSet())
    list.report.errors.forEach { println("  error:   $it") }
    list.report.warnings.forEach { println("  warning: $it") }
    val lengths = list.lengths()
    if (lengths.isNotEmpty()) {
        println("Words by length:")
        lengths.forEach { (len, count) -> println("  %2d letters: %4d".format(len, count)) }
        val short = list.words.count { it.answer.length <= 5 }
        println("  total: ${list.words.size} words (${list.words.sumOf { it.clues.size }} clues), $short with 2–5 letters")
    }
    val ok = list.report.ok
    println(
        (if (ok) "OK    " else "FAIL  ") + file.path +
            " — ${list.report.errors.size} error(s), ${list.report.warnings.size} warning(s)",
    )
    return ok
}
