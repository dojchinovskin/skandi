package mk.skandi.generator

import java.text.Normalizer

/** One answer the generator may use. [line] is the 1-based line in the file, for messages. */
data class Word(val answer: String, val clues: List<String>, val picture: String?, val line: Int)

/** The parsed list plus everything wrong with it. Words with errors are left out of [words]. */
data class WordList(val words: List<Word>, val report: Report) {
    /** Number of words per answer length, shortest first. */
    fun lengths(): Map<Int, Int> = words.groupingBy { it.answer.length }.eachCount().toSortedMap()
}

const val MIN_WORD = 2
const val MAX_CLUE_CHARS = 30
const val MAX_CLUE_WORD = 8

private val HEADERS = setOf(listOf("збор", "опис"), listOf("збор", "опис", "слика"))

/** Latin letters that look like Macedonian ones, so the message can name the one that was meant. */
private val LOOKALIKES = mapOf(
    'A' to 'А', 'B' to 'В', 'C' to 'С', 'E' to 'Е', 'H' to 'Н', 'I' to 'И', 'J' to 'Ј', 'K' to 'К',
    'M' to 'М', 'O' to 'О', 'P' to 'Р', 'S' to 'Ѕ', 'T' to 'Т', 'X' to 'Х', 'Y' to 'У',
)

// By script, not by range, so accented Latin letters (è, é…) count as Latin too.
private fun isLatin(c: Char) = c.isLetter() && Character.UnicodeScript.of(c.code) == Character.UnicodeScript.LATIN
private fun isCyrillic(c: Char) = c.isLetter() && Character.UnicodeScript.of(c.code) == Character.UnicodeScript.CYRILLIC

private val SPACES = Regex("""\s+""")

/** Invisible characters (zero-width space, joiners, direction marks, BOM…) and control characters. */
private fun isInvisible(c: Char) = Character.getType(c).let { it == Character.FORMAT.toInt() || it == Character.CONTROL.toInt() }

/** Splits text into its words: runs of letters (combining accents included, so they stay attached). */
private val TEXT_WORD = Regex("""[\p{L}\p{M}]+""")

/**
 * Parses and checks a word list (format in data/words/README.md).
 * [pictureNames] are the files in data/site/pictures; null skips the "file exists" check.
 */
fun parseWordList(text: String, pictureNames: Set<String>? = null): WordList {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    val words = mutableListOf<Word>()
    val firstLine = mutableMapOf<String, Int>()

    val lines = text.removePrefix("﻿").split("\n").map { it.removeSuffix("\r") }
    val headerIndex = lines.indexOfFirst { it.isNotBlank() && !it.startsWith("#") }
    if (headerIndex < 0) return WordList(emptyList(), Report(listOf("the file is empty: expected the header збор<TAB>опис<TAB>слика"), emptyList()))
    val header = lines[headerIndex].split("\t").map { it.trim() }
    if (header !in HEADERS) {
        errors += "line ${headerIndex + 1}: header must be збор<TAB>опис<TAB>слика, got \"${lines[headerIndex].replace("\t", "<TAB>")}\""
    }
    // A two-column header means the list has no pictures; otherwise allow the optional third column.
    val maxColumns = if (header.size == 2) 2 else 3

    for (i in headerIndex + 1 until lines.size) {
        val raw = lines[i]
        if (raw.isBlank() || raw.startsWith("#")) continue
        val n = i + 1
        val rowErrors = mutableListOf<String>()
        val cols = raw.split("\t")
        if (cols.size !in 2..maxColumns) {
            val expected = if (maxColumns == 2) "2 columns (the header has no слика)" else "2 or 3 columns"
            errors += "line $n: expected $expected separated by tabs, got ${cols.size}"
            continue
        }
        val answer = cols[0].trim()
        val where = "line $n ($answer)"

        // The answer
        rowErrors += answerProblems(answer).map { "$where: $it" }

        // The clues
        val clues = cols[1].split("|").map { it.trim() }
        if (clues.all { it.isEmpty() }) rowErrors += "$where: no clue"
        else clues.forEachIndexed { k, clue ->
            val label = if (clues.size > 1) "clue ${k + 1}" else "clue"
            if (clue.isEmpty()) { rowErrors += "$where: $label is empty (check for a stray '|')"; return@forEachIndexed }
            rowErrors += clueProblems(clue, answer).map { "$where: $label „$clue“ $it" }
            warnings += clueWarnings(clue).map { "$where: $label „$clue“ $it" }
        }
        clues.filter { it.isNotEmpty() }.groupingBy { it.lowercase().replace(SPACES, " ") }.eachCount().filterValues { it > 1 }
            .keys.forEach { rowErrors += "$where: the clue „$it“ is listed twice" }

        // The picture
        val picture = cols.getOrNull(2)?.trim()?.ifEmpty { null }
        if (picture != null) {
            if (!PICTURE_FILE.matches(picture)) rowErrors += "$where: picture \"$picture\" must be lowercase a-z, 0-9, '-' with .svg/.png/.jpg/.webp"
            else if (pictureNames != null && picture !in pictureNames) rowErrors += "$where: picture file pictures/$picture not found"
        }

        // Duplicates: compare the NFC form, so a letter typed as letter + accent still matches the single letter.
        // (Latin look-alikes don't collide here, but such rows already fail the letter check.)
        val key = Normalizer.normalize(answer, Normalizer.Form.NFC).uppercase()
        val earlier = firstLine[key]
        if (earlier != null) rowErrors += "$where: already on line $earlier — put extra clues on that line after '|'"
        else if (answer.isNotEmpty()) firstLine[key] = n

        errors += rowErrors
        if (rowErrors.isEmpty()) words += Word(answer, clues, picture, n)
    }
    if (words.isEmpty() && errors.isEmpty()) errors += "the list has no words"
    return WordList(words, Report(errors, warnings))
}

private fun answerProblems(answer: String): List<String> {
    if (answer.isEmpty()) return listOf("the word is empty")
    val problems = mutableListOf<String>()
    if (Normalizer.normalize(answer, Normalizer.Form.NFC) != answer) {
        problems += "a letter is typed as a letter plus an accent mark; type the single letter (Ѓ or Ќ) instead"
    }
    answer.forEachIndexed { i, c ->
        when {
            c in MACEDONIAN_LETTERS -> {}
            c.isWhitespace() -> problems += "letter ${i + 1} is a space; answers are one word"
            LOOKALIKES[c.uppercaseChar()] != null -> problems += "letter ${i + 1} '$c' is Latin; use Cyrillic '${LOOKALIKES[c.uppercaseChar()]}'"
            c.uppercaseChar() in MACEDONIAN_LETTERS -> problems += "letter ${i + 1} '$c' is lowercase; write answers in capitals"
            Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> {} // reported above as an accent mark
            else -> problems += "letter ${i + 1} '$c' is not a Macedonian letter"
        }
    }
    if (answer.length < MIN_WORD) problems += "must have at least $MIN_WORD letters"
    if (answer.length > MAX_SIZE) problems += "longer than $MAX_SIZE letters, the widest grid"
    return problems
}

private fun clueProblems(clue: String, answer: String): List<String> {
    val problems = mutableListOf<String>()
    if (clue.none { it.isLetter() }) problems += "has no letters"
    if (clue.any(::isInvisible)) problems += "has an invisible character (often pasted from the web); retype it"
    val latin = clue.filter(::isLatin)
    if (latin.isNotEmpty()) problems += "has Latin letters (${latin.toSet().joinToString("")}); use Cyrillic only"
    if (Normalizer.normalize(clue, Normalizer.Form.NFC) != clue) problems += "has a letter typed as a letter plus an accent mark"
    // Spreadsheets wrap fields that contain " in more quotes when saving TSV, so use „ “ instead.
    if ('"' in clue) problems += "has a straight quote \"; use Macedonian quotes „ “"
    if (clue.any { it.isLetter() && !isLatin(it) && !isCyrillic(it) }) problems += "has letters that are neither Cyrillic nor Latin"
    val needle = Normalizer.normalize(answer, Normalizer.Form.NFC).lowercase()
    if (needle.isNotEmpty() && TEXT_WORD.findAll(Normalizer.normalize(clue, Normalizer.Form.NFC)).any { it.value.lowercase() == needle }) {
        problems += "contains the answer"
    }
    return problems
}

private fun clueWarnings(clue: String): List<String> {
    val warnings = mutableListOf<String>()
    if (clue.length > MAX_CLUE_CHARS) warnings += "is ${clue.length} characters (over $MAX_CLUE_CHARS may not fit the clue cell)"
    TEXT_WORD.findAll(clue).map { it.value }.filter { it.length > MAX_CLUE_WORD }.distinct()
        .forEach { warnings += "has the long word „$it“ (${it.length} letters; may break on small phones)" }
    return warnings
}
