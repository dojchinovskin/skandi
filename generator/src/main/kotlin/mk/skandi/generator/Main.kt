package mk.skandi.generator

import java.io.File
import java.util.Random
import kotlin.system.exitProcess

private const val USAGE = """Skandi generator

Usage:
  validate <file-or-folder>...   Check puzzle JSON files against format/README.md
  words-check <words.tsv>        Check the word list against data/words/README.md
  generate [options]             Build a new puzzle from the word list

Options for generate:
  --width N, --height N   grid size (default 7 × 9)
  --seed N                the same seed always gives the same puzzle (default: random)
  --number N              puzzle number (default 1); the id is the number with 4 digits, e.g. 0002
  --date YYYY-MM-DD       date shown on the puzzle (default: today)
  --words FILE            word list (default data/words/words.tsv)
  --out FILE              write the puzzle JSON here (never overwrites); without it, only a preview"""

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "validate" -> exitProcess(if (validateCommand(args.drop(1))) 0 else 1)
        "words-check" -> exitProcess(if (wordsCheckCommand(args.drop(1))) 0 else 1)
        "generate" -> exitProcess(if (generateCommand(args.drop(1))) 0 else 1)
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

/** Parses `--name value` pairs; returns null (after printing why) on a bad or unknown option. */
private fun options(args: List<String>, known: Set<String>): Map<String, String>? {
    val result = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val name = args[i].removePrefix("--")
        if (!args[i].startsWith("--") || name !in known) { System.err.println("unknown option ${args[i]}\n\n$USAGE"); return null }
        val value = args.getOrNull(i + 1) ?: run { System.err.println("${args[i]} needs a value"); return null }
        result[name] = value
        i += 2
    }
    return result
}

/** Builds a puzzle, prints a preview and writes it to --out if given. Returns true on success. */
fun generateCommand(args: List<String>): Boolean {
    val o = options(args, setOf("width", "height", "seed", "number", "date", "words", "out")) ?: return false
    fun int(name: String, default: Int): Int? = o[name]?.let { v -> v.toIntOrNull() ?: run { System.err.println("--$name: expected a number, got \"$v\""); null } } ?: default
    val width = int("width", 7) ?: return false
    val height = int("height", 9) ?: return false
    val number = int("number", 1) ?: return false
    val seed = o["seed"]?.let { it.toLongOrNull() ?: run { System.err.println("--seed: expected a number, got \"$it\""); return false } }
        ?: Random().nextLong()
    val date = o["date"] ?: java.time.LocalDate.now().toString()
    if (width !in MIN_SIZE..MAX_SIZE || height !in MIN_SIZE..MAX_SIZE) { System.err.println("--width/--height: must be $MIN_SIZE to $MAX_SIZE"); return false }
    if (number < 1) { System.err.println("--number: must be 1 or more"); return false }

    val wordsFile = File(o["words"] ?: "data/words/words.tsv")
    if (!wordsFile.isFile) { System.err.println("${wordsFile.path}: not found"); return false }
    val list = parseWordList(wordsFile.readText())
    if (!list.report.ok) {
        System.err.println("${wordsFile.path} has errors; run words-check first")
        return false
    }
    val out = o["out"]?.let(::File)
    if (out != null && out.exists()) { System.err.println("${out.path} already exists; not overwriting"); return false }

    val request = GenerateRequest(width, height, seed, number, date)
    val started = System.nanoTime()
    val result = generate(list.words, request)
    val ms = (System.nanoTime() - started) / 1_000_000
    if (result == null) {
        println("No puzzle after ${request.attempts} layouts (seed $seed, ${ms} ms). Try another seed, a smaller grid, or more short words.")
        return false
    }
    val p = result.puzzle
    // The grid must pass the same check as hand-made puzzles, including the id and date fields.
    val report = validate(p)
    if (!report.ok) { report.errors.forEach { System.err.println("  error: $it") }; return false }
    print(preview(p))
    println("seed $seed · layout ${result.attempt} (${result.layoutsFound} filled tries) · ${p.clues.size} answers · ${ms} ms")
    report.warnings.forEach { println("  warning: $it") }
    if (out != null) {
        out.absoluteFile.parentFile.mkdirs()
        out.writeText(puzzleToJson(p))
        println("Wrote ${out.path}")
    }
    return true
}
