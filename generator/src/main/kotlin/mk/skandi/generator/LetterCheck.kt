package mk.skandi.generator

/**
 * A quick test of whether a layout can be filled from the word list, used while searching for layouts.
 *
 * It narrows every slot to the words whose letters could still fit, cell by cell, until nothing changes
 * (arc consistency). A slot left with no words means the layout certainly can't be filled; the reverse
 * isn't guaranteed, so [Filler] still does the real fill. Words and letter sets are packed into longs, so
 * a check is a few thousand bit operations.
 */
class LetterCheck(words: List<Word>) {
    private val byLength: Map<Int, List<String>> = words.map { it.answer }.groupBy { it.length }

    /** masks[length][position][letter]: bit i set when word i of that length has that letter there. */
    private val masks: Map<Int, Array<Array<LongArray>>> = byLength.mapValues { (length, list) ->
        Array(length) { pos ->
            Array(LETTERS) { letter ->
                LongArray(longs(list.size)).also { m -> list.forEachIndexed { i, w -> if (w[pos] == MACEDONIAN_LETTERS[letter]) m.set(i) } }
            }
        }
    }

    /** How many slots end up with no word that fits; 0 means the layout passes this check. */
    fun deadSlots(slots: List<Slot>): Int {
        if (slots.isEmpty()) return 0
        if (slots.any { it.length !in byLength }) return slots.count { it.length !in byLength }
        val width = slots.maxOf { maxOf(it.start.col, it.cell(it.length - 1).col) } + 1
        val height = slots.maxOf { maxOf(it.start.row, it.cell(it.length - 1).row) } + 1
        val cellLetters = IntArray(width * height) { ALL_LETTERS } // allowed letters per cell, one bit per letter
        val fits = LongArray(longs(byLength.values.maxOf { it.size }))
        val domains = slots.map { s -> LongArray(longs(byLength.getValue(s.length).size)).also { it.setAll(byLength.getValue(s.length).size) } }
        val dead = BooleanArray(slots.size)
        var changed = true
        while (changed) {
            changed = false
            for ((i, s) in slots.withIndex()) {
                if (dead[i]) continue
                val m = masks.getValue(s.length)
                val d = domains[i]
                // Keep only words whose every letter is still allowed in its cell.
                for (pos in 0 until s.length) {
                    val c = s.cell(pos)
                    val allowed = cellLetters[c.row * width + c.col]
                    if (allowed == ALL_LETTERS) continue
                    fits.fill(0L)
                    for (letter in 0 until LETTERS) if (allowed and (1 shl letter) != 0) fits.or(m[pos][letter])
                    d.and(fits)
                }
                if (d.all { it == 0L }) { dead[i] = true; changed = true; continue }
                // The letters these words can put in each cell narrow the crossing slots next round.
                for (pos in 0 until s.length) {
                    var present = 0
                    for (letter in 0 until LETTERS) if (d.intersects(m[pos][letter])) present = present or (1 shl letter)
                    val c = s.cell(pos)
                    val before = cellLetters[c.row * width + c.col]
                    val after = before and present
                    if (after != before) { cellLetters[c.row * width + c.col] = after; changed = true }
                }
            }
        }
        return dead.count { it }
    }

    private companion object {
        val LETTERS = MACEDONIAN_LETTERS.length
        val ALL_LETTERS = (1 shl LETTERS) - 1
        fun longs(n: Int) = (n + 63) / 64
        fun LongArray.set(i: Int) { this[i / 64] = this[i / 64] or (1L shl (i % 64)) }
        fun LongArray.setAll(n: Int) { for (i in 0 until n) set(i) }
        // `fits` may be longer than the word sets; only the overlapping longs matter.
        fun LongArray.or(o: LongArray) { for (k in o.indices) this[k] = this[k] or o[k] }
        fun LongArray.and(o: LongArray) { for (k in indices) this[k] = this[k] and o[k] }
        fun LongArray.intersects(o: LongArray): Boolean { for (k in indices) if (this[k] and o[k] != 0L) return true; return false }
    }
}
