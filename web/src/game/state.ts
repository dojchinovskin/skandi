import { cellAt, type Entry, type Key, type Model, keyOf } from "../puzzle/model";

// Game state + a pure reducer: (state, action) -> new state. No React in here, so it is easy to test.
// For a Kotlin reader: `Action` is a sealed class, `reduce` is a `when (action)` returning a copy.

export interface GameState {
  letters: Readonly<Record<Key, string>>; // what the player typed
  cursor: Key;
  entry: number; // index of the active answer (model.entries)
  wrong: ReadonlySet<Key>; // marked by "Провери", cleared when the cell changes
  revealed: ReadonlySet<Key>; // filled by "Откриј буква"
  solved: boolean;
  toast?: { text: string; seq: number }; // seq changes every time, so the same text shows again
}

export type Arrow = "left" | "right" | "up" | "down";

export type Action =
  | { type: "select"; key: Key } // tap/click a letter cell
  | { type: "selectEntry"; index: number } // tap a clue
  | { type: "type"; letter: string }
  | { type: "backspace" }
  | { type: "move"; arrow: Arrow }
  | { type: "nextEntry"; step: 1 | -1 }
  | { type: "check" }
  | { type: "reveal" }
  | { type: "help" };

export function initialState(model: Model): GameState {
  const first = model.entries[0];
  return { letters: {}, cursor: first.cells[0], entry: 0, wrong: new Set(), revealed: new Set(), solved: false };
}

const without = (set: ReadonlySet<Key>, key: Key) => {
  if (!set.has(key)) return set;
  const next = new Set(set);
  next.delete(key);
  return next;
};

// The only non-pure bit: a counter so each toast is "new" even with the same text. React may call the
// reducer twice in development (StrictMode); that just skips a number, which is harmless.
let toastSeq = 0;
const toast = (text: string) => ({ text, seq: ++toastSeq });

export const HELP_TEXT = "Допри поле и пишувај. Допри го истото поле пак за да ја смениш насоката. Жолтите полиња се одговорот на сликата.";

function letterCell(model: Model, key: Key) {
  const cell = cellAt(model, key);
  if (cell.kind !== "letter") throw new Error(`not a letter cell: ${key}`);
  return cell;
}

/** First empty cell of an entry, or its first cell when it is full. */
function startOf(entry: Entry, letters: GameState["letters"]): Key {
  return entry.cells.find((k) => !letters[k]) ?? entry.cells[0];
}

/** The next entry (in clue order) that still has an empty cell; with skipFull=false, simply the next one. */
function nextEntry(model: Model, from: number, step: 1 | -1, letters: GameState["letters"], skipFull: boolean): Entry | undefined {
  const n = model.entries.length;
  for (let i = 1; i <= n; i++) {
    const e = model.entries[(((from + step * i) % n) + n) % n];
    if (!skipFull || e.cells.some((k) => !letters[k])) return e;
  }
  return undefined;
}

function isComplete(model: Model, letters: GameState["letters"]) {
  return model.letterKeys.every((k) => letters[k] === letterCell(model, k).solution);
}

function winIfComplete(model: Model, s: GameState): GameState {
  if (!isComplete(model, s.letters)) return s;
  const text = model.solution ? `Браво! Решението е ${model.solution.answer}.` : "Браво! Ја решивте скандинавката.";
  return { ...s, solved: true, wrong: new Set(), toast: toast(text) };
}

export function reduce(model: Model, s: GameState, a: Action): GameState {
  if (a.type === "help") return { ...s, toast: toast(HELP_TEXT) };
  if (s.solved) return s; // the finished grid is read-only

  switch (a.type) {
    case "select": {
      const cell = letterCell(model, a.key);
      const current = model.entries[s.entry].dir;
      // Tapping the active cell again flips direction (when the cell is in both an across and a down answer).
      if (a.key === s.cursor && cell.across !== undefined && cell.down !== undefined) {
        return { ...s, entry: current === "across" ? cell.down : cell.across };
      }
      const keep = current === "across" ? cell.across : cell.down;
      return { ...s, cursor: a.key, entry: keep ?? cell.across ?? cell.down! };
    }

    case "selectEntry": {
      const e = model.entries[a.index];
      return { ...s, entry: a.index, cursor: startOf(e, s.letters) };
    }

    case "type": {
      const k = s.cursor;
      const letters = { ...s.letters, [k]: a.letter };
      let next: GameState = { ...s, letters, wrong: without(s.wrong, k), revealed: without(s.revealed, k) };
      const cells = model.entries[s.entry].cells;
      const at = cells.indexOf(k);
      if (at < cells.length - 1) {
        next = { ...next, cursor: cells[at + 1] };
      } else {
        // End of the answer: jump to the next answer that still has gaps.
        const e = nextEntry(model, s.entry, 1, letters, true);
        if (e) next = { ...next, entry: e.index, cursor: startOf(e, letters) };
      }
      return winIfComplete(model, next);
    }

    case "backspace": {
      const k = s.cursor;
      if (s.letters[k]) {
        const { [k]: _, ...letters } = s.letters;
        return { ...s, letters, wrong: without(s.wrong, k), revealed: without(s.revealed, k) };
      }
      // Empty cell: step back and clear the previous one.
      const cells = model.entries[s.entry].cells;
      const at = cells.indexOf(k);
      if (at <= 0) return s;
      const prev = cells[at - 1];
      const { [prev]: _, ...letters } = s.letters;
      return { ...s, letters, cursor: prev, wrong: without(s.wrong, prev), revealed: without(s.revealed, prev) };
    }

    case "move": {
      const [dr, dc] = { left: [0, -1], right: [0, 1], up: [-1, 0], down: [1, 0] }[a.arrow];
      let [r, c] = s.cursor.split(",").map(Number);
      // Skip over clue, filler and picture cells to the next letter in that direction.
      do {
        r += dr;
        c += dc;
      } while (r >= 0 && c >= 0 && r < model.height && c < model.width && model.cells[r][c].kind !== "letter");
      if (r < 0 || c < 0 || r >= model.height || c >= model.width) return s;
      const key = keyOf(r, c);
      const cell = letterCell(model, key);
      const wanted = a.arrow === "left" || a.arrow === "right" ? cell.across : cell.down;
      return { ...s, cursor: key, entry: wanted ?? cell.across ?? cell.down! };
    }

    case "nextEntry": {
      const e = nextEntry(model, s.entry, a.step, s.letters, false)!;
      return { ...s, entry: e.index, cursor: startOf(e, s.letters) };
    }

    case "check": {
      const wrong = new Set<Key>();
      let empty = 0;
      for (const k of model.letterKeys) {
        const v = s.letters[k];
        if (!v) empty++;
        else if (v !== letterCell(model, k).solution) wrong.add(k);
      }
      const text =
        wrong.size === 1 ? "Има 1 погрешна буква." :
        wrong.size > 1 ? `Има ${wrong.size} погрешни букви.` :
        empty > 0 ? "Сè е точно засега." : "Сè е точно.";
      return { ...s, wrong, toast: toast(text) };
    }

    case "reveal": {
      const k = s.cursor;
      const letters = { ...s.letters, [k]: letterCell(model, k).solution };
      const revealed = new Set(s.revealed).add(k);
      return winIfComplete(model, { ...s, letters, revealed, wrong: without(s.wrong, k) });
    }
  }
}
