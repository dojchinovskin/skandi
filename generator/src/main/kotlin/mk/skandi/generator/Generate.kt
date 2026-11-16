package mk.skandi.generator

import kotlinx.serialization.json.JsonPrimitive
import java.util.Random

/** What to build. */
data class GenerateRequest(
    val width: Int,
    val height: Int,
    val seed: Long,
    val number: Int,
    val date: String,
    val id: String = "%04d".format(number),
    /** Layouts to try before giving up; each gets a fresh annealing run and a fill attempt. */
    val attempts: Int = 200,
)

/** A generated puzzle plus how much work it took (shown by the command, useful for tuning). */
data class Generated(val puzzle: Puzzle, val attempt: Int, val layoutsFound: Int)

/** Answer lengths worth building slots for: the list must have at least this many words of a length. */
const val MIN_WORDS_PER_LENGTH = 10

/**
 * Builds a valid, fully dense puzzle (no filler cells, no pictures) from [words], or returns null after
 * [GenerateRequest.attempts] layouts. The same request and words always give the same puzzle.
 */
fun generate(words: List<Word>, request: GenerateRequest): Generated? {
    require(request.width in MIN_SIZE..MAX_SIZE && request.height in MIN_SIZE..MAX_SIZE) { "size must be $MIN_SIZE to $MAX_SIZE" }
    val random = Random(request.seed)
    val filler = Filler(words)
    val lengths = filler.lengths.filterValues { it >= MIN_WORDS_PER_LENGTH }.keys
    require(lengths.isNotEmpty()) { "the word list needs at least $MIN_WORDS_PER_LENGTH words of some length" }
    val goal = LayoutGoal(lengths)

    var layoutsFound = 0
    for (attempt in 1..request.attempts) {
        val layout = searchLayout(request.width, request.height, goal, random) ?: continue
        layoutsFound++
        val fill = filler.fill(layout.slots, random) ?: continue
        val puzzle = toPuzzle(layout, fill, request, random)
        // Belt and braces: never hand back a puzzle the validator would reject.
        val report = validate(puzzle)
        check(report.ok) { "generated an invalid puzzle (a generator bug): ${report.errors}" }
        return Generated(puzzle, attempt, layoutsFound)
    }
    return null
}

/** Puts the words into the grid and picks one clue per word. */
fun toPuzzle(layout: Layout, fill: Map<Slot, Word>, request: GenerateRequest, random: Random): Puzzle {
    val grid = Array(layout.height) { r -> CharArray(layout.width) { c -> if (layout.isClue(Cell(r, c))) CLUE else FILLER } }
    for ((slot, word) in fill) slot.cells.forEachIndexed { i, c -> grid[c.row][c.col] = word.answer[i] }
    val clues = layout.assignment.spots.map { (slot, spot) ->
        val word = fill.getValue(slot)
        Clue(spot.cell, slot.dir, word.clues[random.nextInt(word.clues.size)], start = spot.start)
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
        pictures = emptyList(),
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
