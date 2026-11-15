package mk.skandi.generator

data class Report(val errors: List<String>, val warnings: List<String>) {
    val ok get() = errors.isEmpty()
}

private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
private val ID = Regex("[a-z0-9-]+")

/** Checks the rules in format/README.md. Errors make a puzzle unusable; warnings are reported only. */
fun validate(p: Puzzle): Report {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    // Header
    if (p.format != 1) errors += "format: expected 1, got ${p.format}"
    if (!ID.matches(p.id)) errors += "id: only a-z, 0-9 and '-' allowed"
    if (p.number < 1) errors += "number: must be 1 or more"
    if (!DATE.matches(p.date)) errors += "date: expected YYYY-MM-DD"

    // Rule 1: grid shape and characters
    if (p.width < 3 || p.height < 3) errors += "width/height: grid must be at least 3×3"
    if (p.grid.size != p.height) errors += "grid: has ${p.grid.size} rows, height is ${p.height}"
    p.grid.forEachIndexed { r, row ->
        if (row.length != p.width) errors += "grid[$r]: has ${row.length} cells, width is ${p.width}"
        row.forEachIndexed { c, ch ->
            if (ch !in MACEDONIAN_LETTERS && ch != CLUE && ch != PICTURE && ch != FILLER) {
                errors += "grid[$r]: '$ch' at column $c is not a Macedonian capital letter, '#', '*' or '.'"
            }
        }
    }
    if (errors.isNotEmpty()) return Report(errors, warnings) // the rest assumes a well-formed grid

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
        if (clue.text.isBlank()) errors += "clues[$i]: text is empty"
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
        if (pic.cell.row < 0 || pic.cell.col < 0 || pic.cell.row + pic.height > p.height || pic.cell.col + pic.width > p.width) {
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

    // Warnings
    entries.groupBy { it.answer }.filter { it.key.length >= 2 && it.value.size > 1 }.forEach { (answer, list) ->
        warnings += "answer $answer is used ${list.size} times"
    }

    return Report(errors.distinct(), warnings)
}
