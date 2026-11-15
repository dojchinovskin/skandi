package mk.skandi.generator

data class Report(val errors: List<String>, val warnings: List<String>) {
    val ok get() = errors.isEmpty()
}

// Limits and patterns shared with format/puzzle.schema.json — keep them in sync.
const val MIN_SIZE = 3
const val MAX_SIZE = 30
private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
private val ID = Regex("[a-z0-9-]+")
private val PICTURE_FILE = Regex("""[a-z0-9-]+\.(svg|png|jpg|webp)""")

private fun isRealDate(s: String) = DATE.matches(s) && runCatching { java.time.LocalDate.parse(s) }.isSuccess

/** Checks the rules in format/README.md. Errors make a puzzle unusable; warnings are reported only. */
fun validate(p: Puzzle): Report {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    // Header
    if (p.format != 1) errors += "format: expected 1, got ${p.format}"
    if (!ID.matches(p.id)) errors += "id: only a-z, 0-9 and '-' allowed"
    if (p.number < 1) errors += "number: must be 1 or more"
    if (!isRealDate(p.date)) errors += "date: expected a real date as YYYY-MM-DD, got \"${p.date}\""
    p.clues.forEachIndexed { i, clue -> if (clue.text.isBlank()) errors += "clues[$i]: text is empty" }
    p.pictures.forEachIndexed { i, pic ->
        if (!PICTURE_FILE.matches(pic.file)) errors += "pictures[$i]: file \"${pic.file}\" must be lowercase a-z, 0-9, '-' with .svg/.png/.jpg/.webp"
        if (pic.alt.isBlank()) errors += "pictures[$i]: alt is empty"
        if (pic.credit.isBlank()) errors += "pictures[$i]: credit is empty"
    }

    // Rule 1: grid shape and characters
    val gridErrors = mutableListOf<String>()
    if (p.width !in MIN_SIZE..MAX_SIZE || p.height !in MIN_SIZE..MAX_SIZE) {
        gridErrors += "width/height: must be $MIN_SIZE to $MAX_SIZE, got ${p.width}×${p.height}"
    }
    if (p.grid.size != p.height) gridErrors += "grid: has ${p.grid.size} rows, height is ${p.height}"
    p.grid.forEachIndexed { r, row ->
        if (row.length != p.width) gridErrors += "grid[$r]: has ${row.length} cells, width is ${p.width}"
        row.forEachIndexed { c, ch ->
            if (ch !in MACEDONIAN_LETTERS && ch != CLUE && ch != PICTURE && ch != FILLER) {
                gridErrors += "grid[$r]: '$ch' at column $c is not a Macedonian capital letter, '#', '*' or '.'"
            }
        }
    }
    errors += gridErrors
    if (gridErrors.isNotEmpty()) return Report(errors, warnings) // the rest assumes a well-formed grid

    val cells = (0 until p.height).flatMap { r -> (0 until p.width).map { c -> Cell(r, c) } }

    // Rule 2: clues sit on '#' cells, at most one per direction
    val seen = mutableSetOf<Pair<Cell, Dir>>()
    p.clues.forEachIndexed { i, clue ->
        val ch = p.charAt(clue.cell)
        when {
            ch == null -> errors += "clues[$i]: ${clue.cell} is outside the grid"
            ch != CLUE -> errors += "clues[$i]: ${clue.cell} is '$ch', clues must be on '#' cells"
        }
        if (!seen.add(clue.cell to clue.dir)) errors += "clues[$i]: a second ${clue.dir.json} clue in ${clue.cell}"
    }

    // Rule 3: each clue has an answer of 2+ letters
    val entries = p.entries()
    entries.forEachIndexed { i, e ->
        if (p.charAt(e.clue.cell) == CLUE && e.cells.size < 2) {
            errors += "clues[$i] „${e.clue.text}“: answer must be at least 2 letters, found ${e.cells.size}"
        }
    }

    // Rule 4: every run of 2+ letters has a clue right before it
    val clued = p.clues.map { it.cell to it.dir }.toSet()
    for (dir in Dir.entries) for (cell in cells) {
        if (!p.isLetter(cell) || p.isLetter(cell.prev(dir))) continue // not the start of a run
        val length = generateSequence(cell) { it.next(dir) }.takeWhile { p.isLetter(it) }.count()
        if (length >= 2 && (cell.prev(dir) to dir) !in clued) {
            errors += "word starting at $cell going ${dir.json} ($length letters) has no clue before it"
        }
    }

    // Rule 5: no orphan letters, no empty clue cells
    val covered = entries.flatMap { it.cells }.toSet()
    for (cell in cells) {
        if (p.isLetter(cell) && cell !in covered) errors += "letter at $cell is not part of any answer"
        if (p.charAt(cell) == CLUE && p.clues.none { it.cell == cell }) errors += "'#' at $cell holds no clue (use '.' for filler)"
    }

    // Rule 6: pictures cover exactly the '*' cells
    p.pictures.forEachIndexed { i, pic ->
        if (pic.width < 1 || pic.height < 1) errors += "pictures[$i]: width and height must be 1 or more"
        // Compare without adding first, so huge sizes can't overflow Int.
        if (pic.cell.row < 0 || pic.cell.col < 0 || pic.height > p.height - pic.cell.row || pic.width > p.width - pic.cell.col) {
            errors += "pictures[$i]: goes outside the grid"
        }
    }
    for (cell in cells) {
        val owners = p.pictures.count { it.contains(cell) }
        val star = p.charAt(cell) == PICTURE
        if (owners > 1) errors += "pictures overlap at $cell"
        if (star && owners == 0) errors += "'*' at $cell is not covered by any picture"
        if (!star && owners > 0) errors += "picture covers $cell, which is '${p.charAt(cell)}' instead of '*'"
    }

    // Rule 7: at most one highlighted (picture) answer per picture
    val highlights = p.clues.count { it.highlight }
    if (highlights > p.pictures.size) errors += "$highlights highlighted answers but only ${p.pictures.size} picture(s)"

    // Warnings
    entries.groupBy { it.answer }.filter { it.key.length >= 2 && it.value.size > 1 }.forEach { (answer, list) ->
        warnings += "answer $answer is used ${list.size} times"
    }

    return Report(errors.distinct(), warnings)
}
