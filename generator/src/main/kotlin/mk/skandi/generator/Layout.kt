package mk.skandi.generator

import java.util.Random
import kotlin.math.abs
import kotlin.math.exp

/** A place for one answer: a maximal run of 2+ letter cells, across or down. */
data class Slot(val start: Cell, val dir: Dir, val length: Int) {
    fun cell(i: Int) = Cell(start.row + i * dir.dRow, start.col + i * dir.dCol)
    val cells get() = List(length) { cell(it) }
}

/** Where a slot's clue goes: in clue cell [cell], with its answer starting [start] of it. */
data class ClueSpot(val cell: Cell, val start: Start)

/**
 * A grid of clue cells and letter cells, with no pictures and no filler: the shape of a puzzle before
 * the words go in. Immutable; [flip] returns a copy.
 */
class Layout(val width: Int, val height: Int, private val clue: BooleanArray) {
    init {
        require(clue.size == width * height)
    }

    fun inside(c: Cell) = c.row in 0 until height && c.col in 0 until width
    fun isClue(c: Cell) = inside(c) && clue[c.row * width + c.col]
    fun isLetter(c: Cell) = inside(c) && !clue[c.row * width + c.col]
    val cells: List<Cell> get() = List(width * height) { Cell(it / width, it % width) }
    val clueCells: List<Cell> get() = cells.filter { isClue(it) }

    fun flip(c: Cell): Layout = Layout(width, height, clue.copyOf().also { it[c.row * width + c.col] = !it[c.row * width + c.col] })

    /** Every maximal run of 2+ letters, across and down; these are the answers. */
    val slots: List<Slot> by lazy {
        buildList {
            for (dir in Dir.entries) for (c in cells) {
                if (!isLetter(c) || isLetter(c.prev(dir))) continue
                val length = generateSequence(c) { it.next(dir) }.takeWhile { isLetter(it) }.count()
                if (length >= 2) add(Slot(c, dir, length))
            }
        }
    }

    /** Letter cells that are in no slot (alone both across and down). The format forbids them. */
    fun orphans(): Int {
        val covered = slots.flatMap { it.cells }.toSet()
        return cells.count { isLetter(it) && it !in covered }
    }

    /**
     * The clue spots a slot could use: the clue cell left of its first letter (spot RIGHT) or above it
     * (spot BELOW). Straight or bent arrow, whichever fits; see format/README.md.
     */
    fun spotsFor(s: Slot): List<ClueSpot> = buildList {
        val left = Cell(s.start.row, s.start.col - 1)
        val above = Cell(s.start.row - 1, s.start.col)
        if (isClue(left)) add(ClueSpot(left, Start.RIGHT))
        if (isClue(above)) add(ClueSpot(above, Start.BELOW))
    }

    /** The best clue assignment; see [ClueAssignment]. */
    val assignment: ClueAssignment by lazy { assignClues(this) }

    /** The grid as text, '#' for clue cells and '-' for letters (for tests and debugging). */
    override fun toString() = (0 until height).joinToString("\n") { r ->
        (0 until width).joinToString("") { c -> if (clue[r * width + c]) "#" else "-" }
    }

    companion object {
        fun parse(text: String): Layout {
            val rows = text.trim().lines().map { it.trim() }
            return Layout(rows[0].length, rows.size, rows.joinToString("").map { it == '#' }.toBooleanArray())
        }
    }
}

/**
 * Which clue spot each slot uses. Complete when every slot has a spot (one clue per answer) and every clue
 * cell holds at least one clue; [missing] counts how far from that the layout is.
 */
data class ClueAssignment(val spots: Map<Slot, ClueSpot>, val missing: Int) {
    val complete get() = missing == 0
}

/**
 * Assigns clue spots as a flow with lower bounds:
 * source → slot (exactly 1) → spot (at most 1 slot per spot) → clue cell (1 or 2 clues) → sink.
 * A spot is a (cell, start) pair, so a cell never holds two clues starting the same way.
 */
fun assignClues(layout: Layout): ClueAssignment {
    val slots = layout.slots
    val clueCells = layout.clueCells
    val spots = slots.flatMap { layout.spotsFor(it) }.distinct()
    val cellIndex = clueCells.withIndex().associate { it.value to it.index }

    // Nodes: S, T, slots, spots, cells, then S' and T' for the lower bounds.
    val s = 0
    val t = 1
    val slot0 = 2
    val spot0 = slot0 + slots.size
    val cell0 = spot0 + spots.size
    val s2 = cell0 + clueCells.size
    val t2 = s2 + 1
    val flow = Flow(t2 + 1)
    val excess = IntArray(t2 + 1)
    fun edge(u: Int, v: Int, lower: Int, upper: Int): Int {
        excess[v] += lower
        excess[u] -= lower
        return flow.add(u, v, upper - lower)
    }

    slots.indices.forEach { edge(s, slot0 + it, 1, 1) }
    val spotIndex = spots.withIndex().associate { it.value to it.index }
    val slotEdges = slots.mapIndexed { i, slot ->
        layout.spotsFor(slot).map { spot -> spot to edge(slot0 + i, spot0 + spotIndex.getValue(spot), 0, 1) }
    }
    spots.forEachIndexed { i, spot -> edge(spot0 + i, cell0 + cellIndex.getValue(spot.cell), 0, 1) }
    clueCells.indices.forEach { edge(cell0 + it, t, 1, 2) }
    edge(t, s, 0, Int.MAX_VALUE / 2)

    var needed = 0
    for (v in excess.indices) {
        if (excess[v] > 0) { flow.add(s2, v, excess[v]); needed += excess[v] }
        if (excess[v] < 0) flow.add(v, t2, -excess[v])
    }
    val got = flow.max(s2, t2)

    val assigned = HashMap<Slot, ClueSpot>()
    slots.forEachIndexed { i, slot ->
        slotEdges[i].firstOrNull { (_, e) -> flow.used(e) > 0 }?.let { (spot, _) -> assigned[slot] = spot }
    }
    return ClueAssignment(assigned, needed - got)
}

/** A small max-flow (Edmonds–Karp); graphs here have about a hundred nodes. */
private class Flow(n: Int) {
    private val adj = Array(n) { mutableListOf<Int>() }
    private val to = mutableListOf<Int>()
    private val cap = mutableListOf<Int>()
    private val original = mutableListOf<Int>()

    /** Adds u → v with capacity [c]; returns the edge id. */
    fun add(u: Int, v: Int, c: Int): Int {
        adj[u] += to.size; to += v; cap += c; original += c
        adj[v] += to.size; to += u; cap += 0; original += 0
        return to.size - 2
    }

    fun used(e: Int) = original[e] - cap[e]

    fun max(s: Int, t: Int): Int {
        var total = 0
        while (true) {
            val via = IntArray(adj.size) { -1 }
            val queue = ArrayDeque(listOf(s))
            while (queue.isNotEmpty() && via[t] < 0) {
                val u = queue.removeFirst()
                for (e in adj[u]) {
                    val v = to[e]
                    if (cap[e] > 0 && via[v] < 0 && v != s) { via[v] = e; queue += v }
                }
            }
            if (via[t] < 0) return total
            var push = Int.MAX_VALUE
            var v = t
            while (v != s) { push = minOf(push, cap[via[v]]); v = to[via[v] xor 1] }
            v = t
            while (v != s) { cap[via[v]] -= push; cap[via[v] xor 1] += push; v = to[via[v] xor 1] }
            total += push
        }
    }
}

/** What the layout search aims for. */
data class LayoutGoal(
    /** Answer lengths the word list can fill; a slot of any other length counts as a fault. */
    val lengths: Set<Int>,
    /** Share of cells that are clue cells; magazine grids have about a third. */
    val clueShare: Double = 0.33,
)

/**
 * How far a layout is from usable: 0 means every rule in format/README.md holds (no orphan letters,
 * every slot has a fillable length, a complete clue assignment). Clue-cell count is only a preference,
 * so it adds a fraction and never decides between usable and not.
 */
fun faults(layout: Layout, goal: LayoutGoal): Int =
    layout.orphans() + layout.slots.count { it.length !in goal.lengths } + layout.assignment.missing

private fun cost(layout: Layout, goal: LayoutGoal): Double {
    val target = goal.clueShare * layout.width * layout.height
    return 3.0 * faults(layout, goal) + 0.25 * abs(layout.clueCells.size - target)
}

/**
 * Searches for a usable layout by simulated annealing: start from random cells, flip one cell at a time,
 * keep changes that lower [cost] (and sometimes ones that raise it, less often as it cools).
 * Returns null if [steps] flips don't reach a usable layout; the caller then tries another seed.
 */
fun searchLayout(width: Int, height: Int, goal: LayoutGoal, random: Random, steps: Int = 30_000): Layout? {
    var current = Layout(width, height, BooleanArray(width * height) { random.nextDouble() < goal.clueShare })
    // The top-left cell is always a clue: a letter there could have no clue next to it.
    if (!current.isClue(Cell(0, 0))) current = current.flip(Cell(0, 0))
    var currentCost = cost(current, goal)
    val startTemp = 2.0
    val endTemp = 0.05
    for (step in 0 until steps) {
        if (faults(current, goal) == 0) return current
        val temp = startTemp * Math.pow(endTemp / startTemp, step.toDouble() / steps)
        val cell = Cell(random.nextInt(height), random.nextInt(width))
        if (cell == Cell(0, 0)) continue
        val next = current.flip(cell)
        val nextCost = cost(next, goal)
        if (nextCost <= currentCost || random.nextDouble() < exp((currentCost - nextCost) / temp)) {
            current = next
            currentCost = nextCost
        }
    }
    return if (faults(current, goal) == 0) current else null
}
