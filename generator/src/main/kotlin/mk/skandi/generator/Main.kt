package mk.skandi.generator

import java.io.File
import kotlin.system.exitProcess

private const val USAGE = """Skandi generator

Usage:
  validate <file-or-folder>...   Check puzzle JSON files against format/README.md

Generating puzzles comes in milestone 4."""

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "validate" -> exitProcess(if (validateCommand(args.drop(1))) 0 else 1)
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
            val siteDir = file.absoluteFile.parentFile.parentFile
            puzzle.pictures.map { it.file }.filter { !File(siteDir, "pictures/$it").isFile }
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
