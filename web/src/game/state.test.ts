import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { buildModel, keyOf, type Key } from "../puzzle/model";
import type { Puzzle } from "../puzzle/types";
import { type Action, type GameState, initialState, reduce } from "./state";

// A fixed copy of the first hand-made puzzle (straight arrows only), so content changes to the published
// puzzles never break these rule tests.
const puzzle: Puzzle = JSON.parse(readFileSync(resolve(__dirname, "fixtures/classic.json"), "utf8"));
const model = buildModel(puzzle);
const entry = (answer: string) => model.entries.find((e) => e.answer === answer)!;
const run = (...actions: Action[]) => actions.reduce((s, a) => reduce(model, s, a), initialState(model));
const typeWord = (word: string): Action[] => Array.from(word, (letter) => ({ type: "type", letter }) as Action);
const k = (r: number, c: number): Key => keyOf(r, c);

describe("model (classic fixture)", () => {
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
});

// A tiny valid puzzle with a split clue cell: (0,0) holds ДО across and НА down.
//                                          # Д О
//                                          Н . .
//                                          А . .
const split: Puzzle = {
  format: 1, id: "split", number: 1, date: "2026-11-15", width: 3, height: 3,
  grid: ["#ДО", "Н..", "А.."],
  clues: [
    { row: 0, col: 0, dir: "down", text: "Предлог: ... маса" },
    { row: 0, col: 0, dir: "across", text: "Предлог за правец" },
  ],
  pictures: [],
};

describe("published puzzle 0001 (dense, bent arrows)", () => {
  const p: Puzzle = JSON.parse(readFileSync(resolve(__dirname, "../../../data/site/puzzles/0001.json"), "utf8"));
  const m = buildModel(p);
  it("has no empty cells", () => expect(m.cells.flat().filter((c) => c.kind === "filler")).toHaveLength(0));
  it("derives bent answers from their start cell", () => {
    const bent = m.entries.filter((e) => e.bent).map((e) => `${e.answer}:${e.dir}:${e.start}`);
    expect(bent).toEqual(["ВИНО:down:right", "СОЛ:across:below", "ТАБАН:across:below"]);
  });
  it("stacks a split cell with the right-starting clue on top", () => {
    const cell = m.cells[6][0];
    expect(cell.kind === "clue" && cell.clues.map((e) => e.answer)).toEqual(["ДЕН", "ТАБАН"]);
  });
  it("can be solved, and names the picture answer", () => {
    let s = initialState(m);
    for (const key of m.letterKeys) s = reduce(m, reduce(m, s, { type: "select", key }), { type: "reveal" });
    expect(s.solved).toBe(true);
    expect(s.toast?.text).toBe("Браво! Решението е ОБЛАК.");
  });
});

// Independent of the published puzzles: one cell holding a bent down clue (starts right) and a bent across
// clue (starts below).   # Д .
//                        Н А .
//                        . . .
const bentPuzzle: Puzzle = {
  format: 1, id: "bent", number: 1, date: "2026-11-15", width: 3, height: 3,
  grid: ["#Д.", "НА.", "..."],
  clues: [
    { row: 0, col: 0, dir: "across", start: "below", text: "Предлог: ... маса" },
    { row: 0, col: 0, dir: "down", start: "right", text: "Потврда" },
  ],
  pictures: [],
};

describe("bent arrows (fixture)", () => {
  const m = buildModel(bentPuzzle);
  it("derive answers from the start cell", () => {
    expect(m.entries.map((e) => `${e.answer}:${e.bent}`)).toEqual(["НА:true", "ДА:true"]);
  });
  it("stack the right-starting clue on top, whatever its direction", () => {
    const cell = m.cells[0][0];
    expect(cell.kind === "clue" && cell.clues.map((e) => `${e.dir}:${e.start}`)).toEqual(["down:right", "across:below"]);
  });
  it("play like any other answer", () => {
    let s = reduce(m, initialState(m), { type: "selectEntry", index: 1 }); // ДА, down from (0,1)
    s = reduce(m, reduce(m, s, { type: "type", letter: "Д" }), { type: "type", letter: "А" });
    s = reduce(m, s, { type: "selectEntry", index: 0 }); // НА: А is already there, so the cursor goes to (1,0)
    expect(s.cursor).toBe(keyOf(1, 0));
    s = reduce(m, s, { type: "type", letter: "Н" });
    expect(s.solved).toBe(true);
  });
});

describe("split clue cell", () => {
  const m = buildModel(split);
  const go = (...actions: Action[]) => actions.reduce((s, a) => reduce(m, s, a), initialState(m));

  it("shows both clues in one cell, across on top", () => {
    const cell = m.cells[0][0];
    expect(cell.kind === "clue" && cell.clues.map((e) => e.answer)).toEqual(["ДО", "НА"]);
  });
  it("each half selects its own answer, and both can be solved", () => {
    let s = go({ type: "selectEntry", index: 1 }, { type: "type", letter: "Д" }, { type: "type", letter: "О" });
    expect(s.solved).toBe(false);
    s = reduce(m, s, { type: "selectEntry", index: 0 });
    expect(m.entries[s.entry].answer).toBe("НА");
    s = reduce(m, reduce(m, s, { type: "type", letter: "Н" }), { type: "type", letter: "А" });
    expect(s.solved).toBe(true);
    expect(s.toast?.text).toBe("Браво! Ја реши скандинавката."); // no picture answer in this puzzle
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

  it("Enter / Shift+Enter cycle answers both ways", () => {
    let s = run({ type: "nextEntry", step: -1 });
    expect(model.entries[s.entry].answer).toBe("НА");
    s = reduce(model, s, { type: "nextEntry", step: 1 });
    expect(model.entries[s.entry].answer).toBe("ЛОВ");
  });
});

describe("editing edge cases", () => {
  it("backspace on the first cell of an empty answer does nothing", () => {
    const s = initialState(model);
    expect(reduce(model, s, { type: "backspace" })).toBe(s);
  });
  it("Delete clears only the current cell and never moves", () => {
    let s = run(...typeWord("Л"), { type: "select", key: k(1, 6) });
    expect(reduce(model, s, { type: "clear" })).toBe(s); // empty: nothing to do
    s = reduce(model, s, { type: "select", key: k(1, 5) });
    s = reduce(model, s, { type: "clear" });
    expect(s.letters[k(1, 5)]).toBeUndefined();
    expect(s.cursor).toBe(k(1, 5));
  });
  it("typing over a revealed letter removes the revealed mark", () => {
    let s = run({ type: "reveal" });
    s = reduce(model, s, { type: "select", key: k(1, 5) });
    s = reduce(model, s, { type: "type", letter: "Л" });
    expect(s.revealed.has(k(1, 5))).toBe(false);
  });
  it("with every cell filled but one wrong, typing at the end of an answer keeps the cursor", () => {
    let s: GameState = initialState(model);
    for (const key of model.letterKeys) {
      s = reduce(model, s, { type: "select", key });
      s = reduce(model, s, { type: "reveal" });
      if (key === k(7, 6)) break; // everything before the last cell
    }
    s = reduce(model, s, { type: "select", key: k(7, 7) });
    s = reduce(model, s, { type: "type", letter: "Б" });
    expect(s.solved).toBe(false);
    expect(s.cursor).toBe(k(7, 7));
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
