package mk.skandi.generator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.File

/** The archive list the website reads (format/README.md, "The archive"). */
const val INDEX_FILE = "index.json"

/** One line of index.json. */
data class IndexEntry(val id: String, val number: Int, val date: String)

fun indexOf(puzzles: List<Puzzle>): List<IndexEntry> =
    puzzles.sortedBy { it.number }.map { IndexEntry(it.id, it.number, it.date) }

fun indexToJson(entries: List<IndexEntry>): String {
    fun q(s: String) = JsonPrimitive(s).toString()
    val lines = entries.joinToString(",\n") { "    { \"id\": ${q(it.id)}, \"number\": ${it.number}, \"date\": ${q(it.date)} }" }
    return "{\n  \"format\": 1,\n  \"puzzles\": " + (if (lines.isEmpty()) "[]" else "[\n$lines\n  ]") + "\n}\n"
}

/** Reads index.json; throws [FormatException] if it isn't in the expected shape. */
fun parseIndex(text: String): List<IndexEntry> {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
        ?: throw FormatException("$INDEX_FILE: not a JSON object")
    if ((root["format"] as? JsonPrimitive)?.intOrNull != 1) throw FormatException("$INDEX_FILE: format must be 1")
    if (root.keys != setOf("format", "puzzles")) throw FormatException("$INDEX_FILE: only \"format\" and \"puzzles\" allowed")
    val list = root["puzzles"] as? JsonArray ?: throw FormatException("$INDEX_FILE: puzzles must be an array")
    return list.mapIndexed { i, e ->
        val o = e as? JsonObject ?: throw FormatException("$INDEX_FILE: puzzles[$i] must be an object")
        if (o.keys != setOf("id", "number", "date")) throw FormatException("$INDEX_FILE: puzzles[$i] needs exactly id, number, date")
        val id = (o["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val number = (o["number"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        val date = (o["date"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (id == null || number == null || date == null) throw FormatException("$INDEX_FILE: puzzles[$i] has a wrong type")
        IndexEntry(id, number, date)
    }
}

/**
 * The folder rules in format/README.md: ids are numbers with four digits, numbers and dates are unique,
 * dates go up with numbers, and [indexText] (null if the file is missing) lists exactly these puzzles.
 */
fun checkArchive(puzzles: List<Puzzle>, indexText: String?): List<String> {
    val errors = mutableListOf<String>()
    for (p in puzzles) if (p.id != "%04d".format(p.number)) errors += "${p.id}: id must be the number with four digits (\"${"%04d".format(p.number)}\")"
    puzzles.groupBy { it.number }.filter { it.value.size > 1 }.forEach { (n, list) -> errors += "number $n is used by ${list.joinToString { it.id }}" }
    puzzles.groupBy { it.date }.filter { it.value.size > 1 }.forEach { (d, list) -> errors += "date $d is used by ${list.joinToString { it.id }}; one puzzle a day" }
    indexOf(puzzles).zipWithNext().forEach { (a, b) ->
        if (a.date >= b.date) errors += "${b.id} (${b.date}) must be dated after ${a.id} (${a.date})"
    }
    val expected = indexOf(puzzles)
    when {
        indexText == null -> errors += "$INDEX_FILE is missing; run: generate writes it, or `index <folder>`"
        else -> try {
            val actual = parseIndex(indexText)
            if (actual != expected) errors += "$INDEX_FILE doesn't match the puzzle files; rebuild it with `index <folder>`"
        } catch (e: FormatException) {
            errors += e.message ?: "$INDEX_FILE is unreadable"
        }
    }
    return errors
}

/** The puzzle files in a folder (every *.json except index.json), by name. */
fun puzzleFiles(folder: File): List<File> =
    folder.listFiles { f -> f.extension == "json" && f.name != INDEX_FILE }.orEmpty().sortedBy { it.name }

/** Rewrites [folder]/index.json from the puzzle files; unreadable files are skipped (validate reports them). */
fun writeIndex(folder: File): List<IndexEntry> {
    val puzzles = puzzleFiles(folder).mapNotNull { runCatching { parsePuzzle(it.readText()) }.getOrNull() }
    val entries = indexOf(puzzles)
    File(folder, INDEX_FILE).writeText(indexToJson(entries))
    return entries
}
