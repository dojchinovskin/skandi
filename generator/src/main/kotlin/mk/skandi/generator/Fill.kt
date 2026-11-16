package mk.skandi.generator

import java.util.BitSet
import java.util.Random

/**
 * Fills a layout's slots with words so that crossing letters agree and no word is used twice.
 *
 * Backtracking search: always fill the slot with the fewest words left that still fit (it's the most
 * likely to fail, so fail early), try its words in random order, and after each choice narrow the
 * crossing slots to the words that agree with the new letters. Words are kept as bit sets per
 * (length, position, letter), so narrowing is one AND per crossing.
 */
class Filler(words: List<Word>) {
    private val byLength: Map<Int, List<Word>> = words.groupBy { it.answer.length }

    /** index[length][position][letter] = words of that length with that letter at that position. */
    private val index: Map<Int, Array<Array<BitSet>>> = byLength.mapValues { (length, list) ->
        Array(length) { pos ->
            Array(MACEDONIAN_LETTERS.length) { letter ->
                BitSet(list.size).apply { list.forEachIndexed { i, w -> if (w.answer[pos] == MACEDONIAN_LETTERS[letter]) set(i) } }
            }
        }
    }

    val lengths: Map<Int, Int> get() = byLength.mapValues { it.value.size }

    /**
     * Returns a word for every slot, or null if there is none within [maxSteps] tries
     * (the caller then tries another layout). [fixed] slots must get exactly the given word.
     */
    fun fill(slots: List<Slot>, random: Random, maxSteps: Int = 20_000, fixed: Map<Slot, Word> = emptyMap()): Map<Slot, Word>? {
        if (slots.any { it.length !in byLength }) return null
        val n = slots.size
        // crossings[i] = (position in slot i, other slot, position in the other slot)
        val at = HashMap<Cell, MutableList<Pair<Int, Int>>>()
        slots.forEachIndexed { i, s -> s.cells.forEachIndexed { pos, c -> at.getOrPut(c) { mutableListOf() } += i to pos } }
        val crossings = Array(n) { mutableListOf<Triple<Int, Int, Int>>() }
        for (list in at.values) for ((a, pa) in list) for ((b, pb) in list) if (a != b) crossings[a] += Triple(pa, b, pb)

        val domains = Array(n) { i -> BitSet().apply { set(0, byLength.getValue(slots[i].length).size) } }
        // Fixed slots (the picture's answer) start with just their word, so they're filled first.
        for ((slot, word) in fixed) {
            val i = slots.indexOf(slot)
            val w = byLength[slot.length]?.indexOfFirst { it.answer == word.answer } ?: -1
            if (i < 0 || w < 0) return null
            domains[i].clear()
            domains[i].set(w)
        }
        val chosen = IntArray(n) { -1 }
        var steps = 0

        fun search(filled: Int): Boolean {
            if (filled == n) return true
            if (++steps > maxSteps) return false
            // Most constrained open slot first; ties go to the longer slot (more crossings to check).
            var slot = -1
            for (i in 0 until n) {
                if (chosen[i] >= 0) continue
                if (slot < 0 || domains[i].cardinality() < domains[slot].cardinality() ||
                    (domains[i].cardinality() == domains[slot].cardinality() && slots[i].length > slots[slot].length)
                ) slot = i
            }
            val list = byLength.getValue(slots[slot].length)
            val candidates = domains[slot].stream().toArray().also { shuffle(it, random) }
            for (w in candidates) {
                val word = list[w].answer
                val saved = ArrayList<Pair<Int, BitSet>>()
                var ok = true
                chosen[slot] = w
                for ((pos, other, otherPos) in crossings[slot]) {
                    if (chosen[other] >= 0) continue
                    val keep = index.getValue(slots[other].length)[otherPos][MACEDONIAN_LETTERS.indexOf(word[pos])]
                    saved += other to domains[other].clone() as BitSet
                    domains[other].and(keep)
                    if (domains[other].isEmpty) { ok = false; break }
                }
                // No word twice: take this one out of the other open slots of the same length.
                if (ok) for (other in 0 until n) {
                    if (chosen[other] >= 0 || slots[other].length != slots[slot].length || !domains[other].get(w)) continue
                    saved += other to domains[other].clone() as BitSet
                    domains[other].clear(w)
                    if (domains[other].isEmpty) { ok = false; break }
                }
                if (ok && search(filled + 1)) return true
                for ((other, old) in saved.asReversed()) domains[other] = old
                chosen[slot] = -1
                if (steps > maxSteps) return false
            }
            return false
        }

        if (!search(0)) return null
        return slots.indices.associate { slots[it] to byLength.getValue(slots[it].length)[chosen[it]] }
    }

    private fun shuffle(a: IntArray, random: Random) {
        for (i in a.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val t = a[i]; a[i] = a[j]; a[j] = t
        }
    }
}
