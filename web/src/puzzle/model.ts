import type { Clue, Dir, Picture, Puzzle, Start } from "./types";

// Derived, read-only view of a puzzle: what each cell is and which answers exist.
// Built once per puzzle; the game state (game/state.ts) only stores what the player typed.

export const LETTERS = "АБВГДЃЕЖЗЅИЈКЛЉМНЊОПРСТЌУФХЦЧЏШ";

/** "row,col" — a cheap map key, like a Kotlin data class used as a HashMap key. */
export type Key = `${number},${number}`;
export const keyOf = (row: number, col: number): Key => `${row},${col}`;

export interface Entry {
  index: number;
  clue: Clue;
  dir: Dir;
  start: Start;
  /** Bent arrow: across starting below the clue, or down starting right of it. */
  bent: boolean;
  /** Letter cells of the answer, in reading order. */
  cells: Key[];
  answer: string;
}

// A tagged union — the TypeScript version of a Kotlin sealed class.
export type CellInfo =
  | { kind: "letter"; row: number; col: number; solution: string; across?: number; down?: number; highlight: boolean }
  | { kind: "clue"; row: number; col: number; clues: Entry[] } // 1 or 2 entries ("right" start first)
  | { kind: "filler"; row: number; col: number }
  | { kind: "picture"; row: number; col: number; picture: Picture; isTopLeft: boolean };

export interface Model {
  puzzle: Puzzle;
  width: number;
  height: number;
  cells: CellInfo[][]; // [row][col]
  entries: Entry[];
  letterKeys: Key[]; // every letter cell, reading order
  /** The highlighted (picture) answer, if any. */
  solution?: Entry;
}

const step = (dir: Dir) => (dir === "across" ? [0, 1] : [1, 0]);
export const defaultStart = (dir: Dir): Start => (dir === "across" ? "right" : "below");

export function buildModel(puzzle: Puzzle): Model {
  const { width, height, grid } = puzzle;
  const isLetter = (r: number, c: number) => r >= 0 && c >= 0 && r < height && c < width && LETTERS.includes(grid[r][c]);

  const entries: Entry[] = puzzle.clues.map((clue, index) => {
    const [dr, dc] = step(clue.dir);
    const start = clue.start ?? defaultStart(clue.dir);
    const [r0, c0] = start === "right" ? [clue.row, clue.col + 1] : [clue.row + 1, clue.col];
    const cells: Key[] = [];
    let answer = "";
    for (let r = r0, c = c0; isLetter(r, c); r += dr, c += dc) {
      cells.push(keyOf(r, c));
      answer += grid[r][c];
    }
    return { index, clue, dir: clue.dir, start, bent: start !== defaultStart(clue.dir), cells, answer };
  });

  const across = new Map<Key, number>();
  const down = new Map<Key, number>();
  const highlighted = new Set<Key>();
  for (const e of entries) {
    for (const k of e.cells) (e.dir === "across" ? across : down).set(k, e.index);
    if (e.clue.highlight) e.cells.forEach((k) => highlighted.add(k));
  }

  const pictureAt = (r: number, c: number) =>
    puzzle.pictures.find((p) => r >= p.row && r < p.row + p.height && c >= p.col && c < p.col + p.width);

  const cells: CellInfo[][] = [];
  const letterKeys: Key[] = [];
  for (let r = 0; r < height; r++) {
    const row: CellInfo[] = [];
    for (let c = 0; c < width; c++) {
      const ch = grid[r][c];
      const k = keyOf(r, c);
      if (LETTERS.includes(ch)) {
        letterKeys.push(k);
        row.push({ kind: "letter", row: r, col: c, solution: ch, across: across.get(k), down: down.get(k), highlight: highlighted.has(k) });
      } else if (ch === "#") {
        const here = entries.filter((e) => e.clue.row === r && e.clue.col === c);
        // Two clues in one cell: the one starting right goes on top, the one starting below underneath,
        // so each arrow sits on the edge next to its answer.
        here.sort((a, b) => (a.start === b.start ? 0 : a.start === "right" ? -1 : 1));
        row.push({ kind: "clue", row: r, col: c, clues: here });
      } else if (ch === "*") {
        const picture = pictureAt(r, c)!;
        row.push({ kind: "picture", row: r, col: c, picture, isTopLeft: picture.row === r && picture.col === c });
      } else {
        row.push({ kind: "filler", row: r, col: c });
      }
    }
    cells.push(row);
  }

  return { puzzle, width, height, cells, entries, letterKeys, solution: entries.find((e) => e.clue.highlight) };
}

export function cellAt(model: Model, key: Key): CellInfo {
  const [r, c] = key.split(",").map(Number);
  return model.cells[r][c];
}
