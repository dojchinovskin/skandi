package mk.skandi.generator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/** The 31 letters of the Macedonian alphabet, in alphabetical order. */
const val MACEDONIAN_LETTERS = "АБВГДЃЕЖЗЅИЈКЛЉМНЊОПРСТЌУФХЦЧЏШ"

const val CLUE = '#'
const val PICTURE = '*'
const val FILLER = '.'

enum class Dir(val json: String, val dRow: Int, val dCol: Int) {
    ACROSS("across", 0, 1),
    DOWN("down", 1, 0),
}

data class Cell(val row: Int, val col: Int) {
    fun next(dir: Dir) = Cell(row + dir.dRow, col + dir.dCol)
    fun prev(dir: Dir) = Cell(row - dir.dRow, col - dir.dCol)
    override fun toString() = "($row,$col)"
}

data class Clue(val cell: Cell, val dir: Dir, val text: String, val highlight: Boolean = false)

data class Picture(
    val cell: Cell,
    val width: Int,
    val height: Int,
    val file: String,
    val alt: String,
    val credit: String,
) {
    fun contains(c: Cell) = c.row in cell.row until cell.row + height && c.col in cell.col until cell.col + width
}

/** A clue's answer: the run of letter cells after the clue cell. */
data class Entry(val clue: Clue, val cells: List<Cell>, val answer: String)

/** One puzzle, format 1. See format/README.md. */
data class Puzzle(
    val format: Int,
    val id: String,
    val number: Int,
    val date: String,
    val width: Int,
    val height: Int,
    val grid: List<String>,
    val clues: List<Clue>,
    val pictures: List<Picture>,
) {
    fun inside(c: Cell) = c.row in 0 until height && c.col in 0 until width && c.col < grid.getOrElse(c.row) { "" }.length
    fun charAt(c: Cell): Char? = if (inside(c)) grid[c.row][c.col] else null
    fun isLetter(c: Cell) = charAt(c)?.let { it in MACEDONIAN_LETTERS } == true

    /** Letter cells starting right after [clue], until a non-letter or the edge. */
    fun entryOf(clue: Clue): Entry {
        val cells = generateSequence(clue.cell.next(clue.dir)) { it.next(clue.dir) }.takeWhile { isLetter(it) }.toList()
        return Entry(clue, cells, cells.map { charAt(it)!! }.joinToString(""))
    }

    fun entries(): List<Entry> = clues.map { entryOf(it) }
}

class FormatException(message: String) : Exception(message)

/** Reads a puzzle from JSON text. Throws [FormatException] with the path of the first problem. */
fun parsePuzzle(text: String): Puzzle {
    val root = try {
        Json.parseToJsonElement(text)
    } catch (e: Exception) {
        throw FormatException("not valid JSON: ${e.message?.lineSequence()?.first()}")
    }
    val o = root.obj("puzzle")
    return Puzzle(
        format = o.int("format"),
        id = o.str("id"),
        number = o.int("number"),
        date = o.str("date"),
        width = o.int("width"),
        height = o.int("height"),
        grid = o.arr("grid").mapIndexed { i, e -> e.str("grid[$i]") },
        clues = o.arr("clues").mapIndexed { i, e ->
            val c = e.obj("clues[$i]")
            val p = "clues[$i]"
            val dirName = c.str("dir", p)
            Clue(
                cell = Cell(c.int("row", p), c.int("col", p)),
                dir = Dir.entries.find { it.json == dirName } ?: throw FormatException("$p.dir: must be across or down, got \"$dirName\""),
                text = c.str("text", p),
                highlight = c.optBool("highlight", p) ?: false,
            )
        },
        pictures = o.arr("pictures").mapIndexed { i, e ->
            val c = e.obj("pictures[$i]")
            val p = "pictures[$i]"
            Picture(
                cell = Cell(c.int("row", p), c.int("col", p)),
                width = c.int("width", p),
                height = c.int("height", p),
                file = c.str("file", p),
                alt = c.str("alt", p),
                credit = c.str("credit", p),
            )
        },
    )
}

// ---- small helpers for reading the JSON tree with readable error messages

private fun JsonElement.obj(path: String) = this as? JsonObject ?: throw FormatException("$path: expected an object")

private fun JsonElement.str(path: String): String =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw FormatException("$path: expected a string")

private fun JsonObject.field(name: String, parent: String?): Pair<JsonElement, String> {
    val path = if (parent == null) name else "$parent.$name"
    return (this[name] ?: throw FormatException("$path: missing")) to path
}

private fun JsonObject.str(name: String, parent: String? = null): String = field(name, parent).let { (e, p) -> e.str(p) }

private fun JsonObject.int(name: String, parent: String? = null): Int = field(name, parent).let { (e, p) ->
    (e as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: throw FormatException("$p: expected a whole number")
}

private fun JsonObject.arr(name: String, parent: String? = null): JsonArray = field(name, parent).let { (e, p) ->
    e as? JsonArray ?: throw FormatException("$p: expected an array")
}

private fun JsonObject.optBool(name: String, parent: String): Boolean? {
    val e = this[name] ?: return null
    return (e as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: throw FormatException("$parent.$name: expected true or false")
}
