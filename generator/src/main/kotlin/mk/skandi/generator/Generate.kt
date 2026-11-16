package mk.skandi.generator

import kotlinx.serialization.json.JsonPrimitive
import java.util.Random

/** The clue of every picture answer; the word list holds only ordinary clues (data/words/README.md). */
const val PICTURE_CLUE = "Што е на сликата?"

/** What to build. */
data class GenerateRequest(
    val width: Int,
    val height: Int,
    val seed: Long,
    val number: Int,
    val date: String,
    val id: String = "%04d".format(number),
    /** Put a picture block in the grid (milestone 5); false gives letters and clues only. */
    val withPicture: Boolean = true,
    /** Picture file to use (a word's `слика`); null picks one of the picture words at random. */
    val pictureFile: String? = null,
    /** Picture block size in cells; null picks one from the grid size, see [defaultPictureSize]. */
    val pictureSize: Pair<Int, Int>? = null,
    /** Layouts to try before giving up; each gets a fresh annealing run and a fill attempt. */
    val attempts: Int = 200,
    /** Give up after this long too, e.g. when the word list is too small. */
    val timeLimitMillis: Long = 60_000,
)

/** A generated puzzle plus how much work it took (shown by the command, useful for tuning). */
data class Generated(val puzzle: Puzzle, val attempt: Int, val fillAttempts: Int)

/** Answer lengths worth building slots for: the list must have at least this many words of a length. */
const val MIN_WORDS_PER_LENGTH = 10

/** A square block about half the grid's shorter side: 3×3 in 7×9, 4×4 in 8×8 (as in puzzle 0001). */
fun defaultPictureSize(width: Int, height: Int): Pair<Int, Int> = (minOf(width, height) / 2).coerceAtLeast(1).let { it to it }

/** Thrown for requests that can't work at all (no usable picture, list too small); the message says why. */
class GenerateException(message: String) : Exception(message)

/**
 * Builds a valid, fully dense puzzle (no filler cells) from [words], with a picture block when
 * [GenerateRequest.withPicture] is set, or returns null after [GenerateRequest.attempts] layouts or
 * [GenerateRequest.timeLimitMillis]. The same request and words always give the same puzzle (only giving
 * up depends on the clock). [pictures] is data/words/pictures.tsv, for the picture's description and credit.
 */
fun generate(words: List<Word>, request: GenerateRequest, pictures: Map<String, PictureInfo> = emptyMap()): Generated? {
    if (request.width !in MIN_SIZE..MAX_SIZE || request.height !in MIN_SIZE..MAX_SIZE) throw GenerateException("size must be $MIN_SIZE to $MAX_SIZE")
    val random = Random(request.seed)
    val filler = Filler(words)
    val lengths = filler.lengths.filterValues { it >= MIN_WORDS_PER_LENGTH }.keys
    if (lengths.isEmpty()) throw GenerateException("the word list needs at least $MIN_WORDS_PER_LENGTH words of some length")

    // Words that can be the picture's answer: they have a picture with a description, and a usable length.
    val pictureWords = if (!request.withPicture) emptyList() else {
        val (pw, ph) = request.pictureSize ?: defaultPictureSize(request.width, request.height)
        if (pw < 1 || ph < 1 || pw >= request.width || ph >= request.height) {
            throw GenerateException("picture block ${pw}×$ph doesn't fit a ${request.width}×${request.height} grid with room for words")
        }
        val usable = words.filter { it.picture != null && it.picture in pictures && it.answer.length in lengths && it.answer.length <= maxOf(request.width, request.height) }
        val chosen = if (request.pictureFile == null) usable else usable.filter { it.picture == request.pictureFile }
        if (chosen.isEmpty()) {
            throw GenerateException(
                if (request.pictureFile == null) "no word has a picture listed in pictures.tsv"
                else "no usable word has the picture ${request.pictureFile} (check words.tsv and pictures.tsv)",
            )
        }
        chosen
    }
    val letters = LetterCheck(words)

    var fillAttempts = 0
    val deadline = System.nanoTime() + request.timeLimitMillis * 1_000_000
    for (attempt in 1..request.attempts) {
        if (System.nanoTime() > deadline) break
        val placed = if (pictureWords.isEmpty()) null else placePicture(request, pictureWords, pictures, random)
        val goal = LayoutGoal(lengths, letters = letters, needWord = placed?.word?.answer)
        val layout = searchLayout(request.width, request.height, goal, random, picture = placed?.cells.orEmpty(), deadline = deadline) ?: continue
        // Pin the picture word to one of the slots of its length (trying them in random order).
        val targets = if (placed == null) listOf(null) else layout.slots.indices
            .filter { layout.slots[it].length == placed.word.answer.length && letters.deadSlots(layout.slots, mapOf(it to placed.word.answer)) == 0 }
            .map { layout.slots[it] }.shuffled(random)
        for (target in targets) {
            fillAttempts++
            val fixed = if (target == null) emptyMap() else mapOf(target to placed!!.word)
            val fill = filler.fill(layout.slots, random, fixed = fixed) ?: continue
            val puzzle = toPuzzle(layout, fill, request, random, placed?.let { it.picture to target!! })
            // Belt and braces: never hand back a puzzle the validator would reject.
            val report = validate(puzzle)
            check(report.ok) { "generated an invalid puzzle (a generator bug): ${report.errors}" }
            return Generated(puzzle, attempt, fillAttempts)
        }
    }
    return null
}

/** A picture block for one attempt: which word it shows and which cells it covers. */
private data class PlacedPicture(val word: Word, val picture: Picture, val cells: Set<Cell>)

/** Picks a picture word and puts its block in a random corner of the grid. */
private fun placePicture(request: GenerateRequest, words: List<Word>, info: Map<String, PictureInfo>, random: Random): PlacedPicture {
    val word = words[random.nextInt(words.size)]
    val (w, h) = request.pictureSize ?: defaultPictureSize(request.width, request.height)
    val row = if (random.nextBoolean()) 0 else request.height - h
    val col = if (random.nextBoolean()) 0 else request.width - w
    val meta = info.getValue(word.picture!!)
    val picture = Picture(Cell(row, col), w, h, meta.file, meta.alt, meta.credit)
    val cells = (row until row + h).flatMap { r -> (col until col + w).map { c -> Cell(r, c) } }.toSet()
    return PlacedPicture(word, picture, cells)
}

/**
 * Puts the words into the grid and picks one clue per word. With [picture], its cells become '*' and the
 * given slot's answer is the highlighted picture answer with [PICTURE_CLUE].
 */
fun toPuzzle(layout: Layout, fill: Map<Slot, Word>, request: GenerateRequest, random: Random, picture: Pair<Picture, Slot>? = null): Puzzle {
    val grid = Array(layout.height) { r ->
        CharArray(layout.width) { c ->
            when {
                layout.isClue(Cell(r, c)) -> CLUE
                layout.isPicture(Cell(r, c)) -> PICTURE
                else -> FILLER
            }
        }
    }
    for ((slot, word) in fill) slot.cells.forEachIndexed { i, c -> grid[c.row][c.col] = word.answer[i] }
    // Go through slots in layout order, not map order: Slot's hash includes an enum, whose hash differs
    // between JVM runs, and the random clue choices must happen in the same order for the same seed.
    val clues = layout.slots.map { slot ->
        val spot = layout.assignment.spots.getValue(slot)
        val word = fill.getValue(slot)
        if (slot == picture?.second) Clue(spot.cell, slot.dir, PICTURE_CLUE, highlight = true, start = spot.start)
        else Clue(spot.cell, slot.dir, word.clues[random.nextInt(word.clues.size)], start = spot.start)
    }.sortedWith(compareBy({ it.cell.row }, { it.cell.col }, { it.start }))
    return Puzzle(
        format = 1,
        id = request.id,
        number = request.number,
        date = request.date,
        width = layout.width,
        height = layout.height,
        grid = grid.map { String(it) },
        clues = clues,
        pictures = listOfNotNull(picture?.first),
    )
}

/** Writes a puzzle as JSON in the same layout as the hand-made 0001.json: one clue per line. */
fun puzzleToJson(p: Puzzle): String {
    fun q(s: String) = JsonPrimitive(s).toString() // quoted and escaped
    val clues = p.clues.joinToString(",\n") { c ->
        buildString {
            append("    { \"row\": ${c.cell.row}, \"col\": ${c.cell.col}, \"dir\": ${q(c.dir.json)}")
            if (c.bent) append(", \"start\": ${q(c.start.json)}")
            append(", \"text\": ${q(c.text)}")
            if (c.highlight) append(", \"highlight\": true")
            append(" }")
        }
    }
    val pictures = p.pictures.joinToString(",\n") { pic ->
        "    { \"row\": ${pic.cell.row}, \"col\": ${pic.cell.col}, \"width\": ${pic.width}, \"height\": ${pic.height}, " +
            "\"file\": ${q(pic.file)}, \"alt\": ${q(pic.alt)}, \"credit\": ${q(pic.credit)} }"
    }
    fun list(body: String) = if (body.isEmpty()) "[]" else "[\n$body\n  ]"
    return """
        |{
        |  "format": ${p.format},
        |  "id": ${q(p.id)},
        |  "number": ${p.number},
        |  "date": ${q(p.date)},
        |  "width": ${p.width},
        |  "height": ${p.height},
        |  "grid": ${list(p.grid.joinToString(",\n") { "    ${q(it)}" })},
        |  "clues": ${list(clues)},
        |  "pictures": ${list(pictures)}
        |}
        |
    """.trimMargin()
}

/** A plain-text view of a puzzle for the terminal: the grid, then every answer with its clue. */
fun preview(p: Puzzle): String = buildString {
    p.grid.forEach { row -> appendLine(row.toCharArray().joinToString(" ")) }
    appendLine()
    for (e in p.entries()) {
        val arrow = when {
            e.clue.dir == Dir.ACROSS && !e.clue.bent -> "▸"
            e.clue.dir == Dir.DOWN && !e.clue.bent -> "▾"
            e.clue.dir == Dir.ACROSS -> "↳"
            else -> "⤵"
        }
        appendLine("  ${e.clue.cell} $arrow ${e.answer.padEnd(7)} ${e.clue.text}")
    }
}
