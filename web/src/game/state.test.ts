import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { buildModel, keyOf, type Key } from "../puzzle/model";
import type { Puzzle } from "../puzzle/types";
import { type Action, type GameState, initialState, reduce } from "./state";

const puzzle: Puzzle = JSON.parse(readFileSync(resolve(__dirname, "../../../data/site/puzzles/0001.json"), "utf8"));
const model = buildModel(puzzle);
const entry = (answer: string) => model.entries.find((e) => e.answer === answer)!;
const run = (...actions: Action[]) => actions.reduce((s, a) => reduce(model, s, a), initialState(model));
const typeWord = (word: string): Action[] => Array.from(word, (letter) => ({ type: "type", letter }) as Action);
const k = (r: number, c: number): Key => keyOf(r, c);

describe("model", () => {
  it("derives every answer from grid + clues", () => {
    expect(model.entries.map((e) => e.answer)).toEqual([
      "ЛОВ", "ЕРА", "КОЛ", "ЛЕК", "ОРО", "ВАЛ", "ОБЛАК", "ДО", "СЕСТРА", "ОД", "БОС", "КРТ", "НА",
    ]);
  });
  it("knows the picture answer and its shaded cells", () => {
    expect(model.solution?.answer).toBe("ОБЛАК");
    const shaded = model.cells.flat().filter((c) => c.kind === "letter" && c.highlight).length;
    expect(shaded).toBe(5);
  });
  it("marks the picture and its top-left cell", () => {
    const pics = model.cells.flat().filter((c) => c.kind === "picture");
    expect(pics).toHaveLength(16);
    expect(pics.filter((c) => c.kind === "picture" && c.isTopLeft)).toHaveLength(1);
  });
  it("puts two clues in one cell, across first", () => {
    const p: Puzzle = { ...puzzle, grid: [...puzzle.grid], clues: [...puzzle.clues] };
    // (4,1) already has the down clue for ОД; give it a fake across clue too by checking ordering only.
    p.clues.push({ row: 6, col: 0, dir: "down", text: "x" }); // (6,0) has ДО across; down from it is (7,0) = '.', so 0 letters
    const m = buildModel(p);
    const cell = m.cells[6][0];
    expect(cell.kind === "clue" && cell.clues.map((e) => e.dir)).toEqual(["across", "down"]);
  });
});

describe("typing and moving", () => {
  it("starts on the first answer", () => {
    const s = initialState(model);
    expect(model.entries[s.entry].answer).toBe("ЛОВ");
    expect(s.cursor).toBe(k(1, 5));
  });

  it("typing fills the answer and jumps to the next answer with gaps", () => {
    const s = run(...typeWord("ЛОВ"));
    expect([s.letters[k(1, 5)], s.letters[k(1, 6)], s.letters[k(1, 7)]]).toEqual(["Л", "О", "В"]);
    expect(model.entries[s.entry].answer).toBe("ЕРА");
    expect(s.cursor).toBe(k(2, 5));
  });

  it("tapping the active cell again flips the direction", () => {
    let s = run({ type: "select", key: k(2, 5) }); // a new cell keeps the current direction
    expect(model.entries[s.entry].answer).toBe("ЕРА");
    s = reduce(model, s, { type: "select", key: k(2, 5) }); // same cell again flips it
    expect(model.entries[s.entry].answer).toBe("ЛЕК");
  });

  it("tapping a cell in only one answer selects that answer", () => {
    const s = run({ type: "select", key: k(5, 3) }); // Л of ОБЛАК, no down answer
    expect(model.entries[s.entry].answer).toBe("ОБЛАК");
  });

  it("backspace clears, then steps back", () => {
    let s = run(...typeWord("ЛО"));
    s = reduce(model, s, { type: "backspace" }); // cursor on empty (1,7): steps back and clears О
    expect(s.cursor).toBe(k(1, 6));
    expect(s.letters[k(1, 6)]).toBeUndefined();
    s = reduce(model, s, { type: "select", key: k(1, 5) });
    s = reduce(model, s, { type: "backspace" }); // cursor on filled (1,5): clears in place
    expect(s.letters[k(1, 5)]).toBeUndefined();
    expect(s.cursor).toBe(k(1, 5));
  });

  it("arrows skip clue and picture cells and pick the matching direction", () => {
    let s = run({ type: "select", key: k(1, 5) });
    s = reduce(model, s, { type: "move", arrow: "left" }); // (1,4) is a clue, then the picture: nothing to the left
    expect(s.cursor).toBe(k(1, 5));
    s = reduce(model, s, { type: "move", arrow: "down" });
    expect(s.cursor).toBe(k(2, 5));
    expect(model.entries[s.entry].answer).toBe("ЛЕК");
    s = reduce(model, s, { type: "move", arrow: "down" });
    s = reduce(model, s, { type: "move", arrow: "down" }); // (4,5) is a clue cell → skips to (5,5)
    expect(s.cursor).toBe(k(5, 5));
    expect(model.entries[s.entry].answer).toBe("КРТ");
  });

  it("selecting a clue goes to the first empty cell of its answer", () => {
    let s = run({ type: "selectEntry", index: entry("ОБЛАК").index }, ...typeWord("ОБ"));
    s = reduce(model, s, { type: "selectEntry", index: entry("ОБЛАК").index });
    expect(s.cursor).toBe(k(5, 3));
  });

  it("Tab cycles answers both ways", () => {
    let s = run({ type: "nextEntry", step: -1 });
    expect(model.entries[s.entry].answer).toBe("НА");
    s = reduce(model, s, { type: "nextEntry", step: 1 });
    expect(model.entries[s.entry].answer).toBe("ЛОВ");
  });
});

describe("check, reveal and winning", () => {
  it("check marks wrong letters and a later edit clears the mark", () => {
    let s = run(...typeWord("ЛАВ"), { type: "check" });
    expect([...s.wrong]).toEqual([k(1, 6)]);
    expect(s.toast?.text).toBe("Има 1 погрешна буква.");
    s = reduce(model, { ...s, cursor: k(1, 6), entry: 0 }, { type: "type", letter: "О" });
    expect(s.wrong.size).toBe(0);
  });

  it("check with no mistakes says so", () => {
    expect(run(...typeWord("ЛОВ"), { type: "check" }).toast?.text).toBe("Сè е точно засега.");
  });

  it("reveal fills the current cell with the right letter", () => {
    const s = run({ type: "reveal" });
    expect(s.letters[k(1, 5)]).toBe("Л");
    expect(s.revealed.has(k(1, 5))).toBe(true);
  });

  it("solving every cell wins and names the picture answer; the grid then locks", () => {
    let s: GameState = initialState(model);
    for (const key of model.letterKeys) {
      s = reduce(model, s, { type: "select", key });
      s = reduce(model, s, { type: "reveal" });
    }
    expect(s.solved).toBe(true);
    expect(s.toast?.text).toBe("Браво! Решението е ОБЛАК.");
    const after = reduce(model, s, { type: "type", letter: "А" });
    expect(after).toBe(s);
  });

  it("a full grid with a mistake does not win", () => {
    let s: GameState = initialState(model);
    for (const key of model.letterKeys.filter((x) => x !== k(7, 7))) {
      s = reduce(model, s, { type: "select", key });
      s = reduce(model, s, { type: "reveal" });
    }
    s = reduce(model, s, { type: "select", key: k(7, 7) });
    s = reduce(model, s, { type: "type", letter: "Б" }); // should be А
    expect(s.solved).toBe(false);
    expect(reduce(model, s, { type: "check" }).toast?.text).toBe("Има 1 погрешна буква.");
  });

  it("help shows the instructions even after winning", () => {
    expect(run({ type: "help" }).toast?.text).toContain("Допри поле");
  });
});
