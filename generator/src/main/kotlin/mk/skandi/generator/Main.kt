package mk.skandi.generator

import java.io.File
import java.util.Random
import kotlin.system.exitProcess

private const val USAGE = """Skandi generator

Usage:
  validate <file-or-folder>...   Check puzzle JSON files against format/README.md (a folder: also index.json)
  index <folder>                 Rebuild index.json, the archive list, in a puzzles folder
  words-check <words.tsv>        Check the word list against data/words/README.md
  generate [options]             Build a new puzzle from the word list

Options for generate:
  --width N, --height N   grid size (default 7 × 9)
  --seed N                the same seed always gives the same puzzle (default: random)
  --number N              puzzle number; the id is the number with 4 digits, e.g. 0002
                          (default: from the --out file name, else 1)
  --date YYYY-MM-DD       the day the puzzle comes out (default: the day after the newest puzzle in the
                          --out folder, else today)
  --avoid N               don't reuse answers of 3+ letters from the last N puzzles in the --out folder
                          (default 3)
  --words FILE            word list (default data/words/words.tsv); pictures.tsv is read from the same folder
  --picture FILE|none     picture to show, e.g. oblak.svg (default: a random picture word); none for no picture
  --picture-size WxH      picture block in cells (default: about half the shorter side, e.g. 3x3 in 7×9)
  --out FILE              write the puzzle JSON here, named NNNN.json after --number (never overwrites),
                          and update index.json next to it; without it, only a preview"""

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "validate" -> exitProcess(if (validateCommand(args.drop(1))) 0 else 1)
        "words-check" -> exitProcess(if (wordsCheckCommand(args.drop(1))) 0 else 1)
        "generate" -> exitProcess(if (generateCommand(args.drop(1))) 0 else 1)
        "index" -> exitProcess(if (indexCommand(args.drop(1))) 0 else 1)
        null, "help", "-h", "--help" -> println(USAGE)
        else -> {
            System.err.println("Unknown command: ${args[0]}\n\n$USAGE")
            exitProcess(2)
        }
    }
}

/**
 * Validates each file. A folder means every puzzle in it plus the folder rules (format/README.md,
 * "The archive"), including that index.json is up to date. Returns true when all are valid.
 */
fun validateCommand(paths: List<String>): Boolean {
    if (paths.isEmpty()) {
        System.err.println("validate: give at least one file or folder")
        return false
    }
    val folders = mutableListOf<File>()
    val files = paths.flatMap { path ->
        val f = File(path)
        when {
            f.isDirectory -> puzzleFiles(f).also { folders += f }
            f.isFile -> listOf(f)
            else -> { System.err.println("$path: not found"); return false }
        }
    }
    if (files.isEmpty()) {
        System.err.println("validate: no puzzle files found")
        return false
    }
    var allOk = true
    val parsed = HashMap<File, Puzzle>()
    for (file in files) {
        val report = try {
            val puzzle = parsePuzzle(file.readText())
            parsed[file] = puzzle
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
    for (folder in folders) {
        val puzzles = puzzleFiles(folder).mapNotNull { parsed[it] }
        val index = File(folder, INDEX_FILE).takeIf { it.isFile }?.readText()
        val errors = checkArchive(puzzles, index)
        println((if (errors.isEmpty()) "OK    " else "FAIL  ") + "${folder.path} (archive: ${puzzles.size} puzzles, $INDEX_FILE)")
        errors.forEach { println("  error:   $it") }
        allOk = allOk && errors.isEmpty()
    }
    return allOk
}

/** Rebuilds index.json in a puzzles folder. */
fun indexCommand(args: List<String>): Boolean {
    val folder = args.singleOrNull()?.let(::File)
    if (folder == null || !folder.isDirectory) {
        System.err.println("index: give one puzzles folder, e.g. data/site/puzzles")
        return false
    }
    val entries = writeIndex(folder)
    println("Wrote ${File(folder, INDEX_FILE).path} (${entries.size} puzzles)")
    return true
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
    val names = picturesDir.list().orEmpty().toSet()
    val list = parseWordList(file.readText(), names)
    // The picture list sits next to the word list; it's only required once a word has a picture.
    val pictureFile = File(file.absoluteFile.parentFile, "pictures.tsv")
    val reports = mutableListOf(list.report)
    if (pictureFile.isFile) {
        val pictures = parsePictureList(pictureFile.readText(), names)
        reports += pictures.report.let { r -> r.copy(errors = r.errors.map { "pictures.tsv $it" }) }
        reports += crossCheckPictures(list, pictures)
    } else if (list.words.any { it.picture != null }) {
        reports += Report(listOf("words use pictures, but ${pictureFile.path} is missing"), emptyList())
    }
    val report = Report(reports.flatMap { it.errors }, reports.flatMap { it.warnings })
    report.errors.forEach { println("  error:   $it") }
    report.warnings.forEach { println("  warning: $it") }
    val lengths = list.lengths()
    if (lengths.isNotEmpty()) {
        println("Words by length:")
        lengths.forEach { (len, count) -> println("  %2d letters: %4d".format(len, count)) }
        val short = list.words.count { it.answer.length <= 5 }
        println("  total: ${list.words.size} words (${list.words.sumOf { it.clues.size }} clues), $short with 2–5 letters")
    }
    val ok = report.ok
    println(
        (if (ok) "OK    " else "FAIL  ") + file.path +
            " — ${report.errors.size} error(s), ${report.warnings.size} warning(s)",
    )
    return ok
}

/** Parses `--name value` pairs; returns null (after printing why) on a bad, unknown or repeated option. */
private fun options(args: List<String>, known: Set<String>): Map<String, String>? {
    val result = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val name = args[i].removePrefix("--")
        if (!args[i].startsWith("--") || name !in known) { System.err.println("unknown option ${args[i]}\n\n$USAGE"); return null }
        if (name in result) { System.err.println("${args[i]} is given twice"); return null }
        val value = args.getOrNull(i + 1)?.takeUnless { it.startsWith("--") }
            ?: run { System.err.println("${args[i]} needs a value"); return null }
        result[name] = value
        i += 2
    }
    return result
}

/** Builds a puzzle, prints a preview and writes it to --out if given. Returns true on success. */
fun generateCommand(args: List<String>): Boolean {
    val o = options(args, setOf("width", "height", "seed", "number", "date", "words", "out", "picture", "picture-size", "avoid")) ?: return false
    fun fail(message: String): Boolean { System.err.println(message); return false }
    fun int(name: String, default: Int): Int? {
        val v = o[name] ?: return default
        return v.toIntOrNull() ?: run { System.err.println("--$name: expected a number, got \"$v\""); null }
    }
    val width = int("width", 7) ?: return false
    val height = int("height", 9) ?: return false
    val out = o["out"]?.let(::File)
    // Number: --number, else the --out file name (0003.json → 3), else 1.
    val numberFromName = out?.name?.let { Regex("""(\d{4})\.json""").matchEntire(it) }?.groupValues?.get(1)?.toInt()
    val number = int("number", numberFromName ?: 1) ?: return false
    val avoid = int("avoid", 3) ?: return false
    if (avoid < 0) return fail("--avoid: must be 0 or more")
    // The puzzles already in the --out folder: for the default date and for answers to avoid.
    val earlier = out?.absoluteFile?.parentFile?.takeIf { it.isDirectory }?.let(::puzzleFiles).orEmpty()
        .mapNotNull { runCatching { parsePuzzle(it.readText()) }.getOrNull() }
        .filter { it.number < number }.sortedBy { it.number }
    val seed = o["seed"]?.let { it.toLongOrNull() ?: return fail("--seed: expected a number, got \"$it\"") } ?: Random().nextLong()
    // Date: --date, else the day after the newest earlier puzzle (one a day), else today.
    val date = o["date"] ?: earlier.maxOfOrNull { it.date }?.takeIf(::isRealDate)?.let { java.time.LocalDate.parse(it).plusDays(1).toString() }
        ?: java.time.LocalDate.now().toString()
    if (width !in MIN_SIZE..MAX_SIZE || height !in MIN_SIZE..MAX_SIZE) return fail("--width/--height: must be $MIN_SIZE to $MAX_SIZE")
    if (number < 1) return fail("--number: must be 1 or more")
    // Check the date before the slow part, with the same rule as the validator.
    if (!isRealDate(date)) return fail("--date: expected a real date as YYYY-MM-DD, got \"$date\"")
    val picture = o["picture"]?.let { if (it.equals("none", ignoreCase = true)) "none" else it }
    val pictureSize = o["picture-size"]?.let { v ->
        val m = Regex("""(\d{1,3})x(\d{1,3})""").matchEntire(v) ?: return fail("--picture-size: expected WIDTHxHEIGHT like 3x3, got \"$v\"")
        m.groupValues[1].toInt() to m.groupValues[2].toInt()
    }
    if (picture == "none" && pictureSize != null) return fail("--picture-size: there is no picture with --picture none")
    val id = "%04d".format(number)

    if (out != null) {
        if (out.exists()) return fail("${out.path} already exists; not overwriting")
        // A puzzle's id must match its file name (validate checks it), so catch a mismatch now.
        if (out.name != "$id.json") return fail("--out: puzzle number $number must be saved as $id.json, not ${out.name}")
    }

    val wordsFile = File(o["words"] ?: "data/words/words.tsv")
    if (!wordsFile.isFile) return fail("${wordsFile.path}: not found")
    val list = parseWordList(wordsFile.readText())
    if (!list.report.ok) return fail("${wordsFile.path} has errors; run words-check first")
    val picturesFile = File(wordsFile.absoluteFile.parentFile, "pictures.tsv")
    val pictures = if (picturesFile.isFile) parsePictureList(picturesFile.readText()) else null
    if (pictures != null && !pictures.report.ok) return fail("${picturesFile.path} has errors; run words-check first")

    // Variety: leave out answers of 3+ letters (so also their pictures) used in the last few puzzles.
    // 2-letter words (prepositions, pronouns) may repeat; there are too few to avoid.
    val recent = earlier.takeLast(avoid)
    val avoided = recent.flatMap { p -> p.entries().map { it.answer } }.filter { it.length >= 3 }.toSet()
    val words = list.words.filter { it.answer !in avoided }
    if (recent.isNotEmpty()) println("Avoiding ${avoided.size} answers used in ${recent.joinToString { it.id }}")

    val request = GenerateRequest(
        width, height, seed, number, date, id,
        withPicture = picture != "none",
        pictureFile = picture?.takeUnless { it == "none" },
        pictureSize = pictureSize,
    )
    val started = System.nanoTime()
    val result = try {
        generate(words, request, pictures?.pictures.orEmpty())
    } catch (e: GenerateException) {
        return fail("Can't generate: ${e.message}")
    }
    val ms = (System.nanoTime() - started) / 1_000_000
    if (result == null) {
        println("No puzzle found (seed $seed, ${ms} ms). Try another seed, a smaller grid, or more 2–5 letter words.")
        return false
    }
    val p = result.puzzle
    print(preview(p))
    println("seed $seed · layout ${result.attempt} · ${result.fillAttempts} fill attempt(s) · ${p.clues.size} answers · ${ms} ms")
    validate(p).warnings.forEach { println("  warning: $it") }
    if (out != null) {
        out.absoluteFile.parentFile.mkdirs()
        out.writeText(puzzleToJson(p))
        println("Wrote ${out.path}")
        val entries = writeIndex(out.absoluteFile.parentFile)
        println("Updated ${File(out.parentFile, INDEX_FILE).path} (${entries.size} puzzles)")
    }
    return true
}
