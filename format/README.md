# Puzzle format, version 1

One puzzle is one JSON file, e.g. `data/site/puzzles/0001.json`. The generator writes it; the website only
reads it. This file and `puzzle.schema.json` are the contract between the two.

```json
{
  "format": 1,
  "id": "0001",
  "number": 1,
  "date": "2026-11-15",
  "width": 8,
  "height": 8,
  "grid": ["****##В#", "****О#ИМ", "..."],
  "clues": [
    { "row": 1, "col": 5, "dir": "across", "text": "Кратка заменка: „... дадов“" },
    { "row": 0, "col": 5, "dir": "down", "start": "right", "text": "Црвено или бело, во чаша" },
    { "row": 0, "col": 4, "dir": "down", "text": "Што покажува сликата?", "highlight": true }
  ],
  "pictures": [
    { "row": 0, "col": 0, "width": 4, "height": 4, "file": "oblak.svg", "alt": "Облак и сонце", "credit": "Skandi" }
  ]
}
```

## Fields

| Field | Meaning |
|---|---|
| `format` | Always `1` for this version. |
| `id` | File name without `.json`; only `a-z`, `0-9` and `-`. |
| `number`, `date` | Shown in the header („Бр. 1 · 15 ноември 2026“). `number` ≥ 1; `date` is a real date, `YYYY-MM-DD`. |
| `width`, `height` | Grid size in cells, 3 to 30 each. |
| `grid` | `height` strings of `width` characters each, top row first. |
| `clues` | `row`, `col` (0-based), `dir` (`across`/`down`), `text` (not blank), optional `start` (`right`/`below`), optional `highlight`. |
| `pictures` | `row`, `col`, `width`, `height`, `file`, `alt` and `credit` (both not blank). `file` is a name in `data/site/pictures/`: lowercase `a-z`, `0-9`, `-`, ending in `.svg`, `.png`, `.jpg` or `.webp`. |

No other fields are allowed anywhere, so a typo like `"higlight"` is an error rather than silently ignored.

Grid characters:

| Char | Cell |
|---|---|
| `А Б В Г Д Ѓ Е Ж З Ѕ И Ј К Л Љ М Н Њ О П Р С Т Ќ У Ф Х Ц Ч Џ Ш` | A letter cell; the character is the solution. Only these 31 uppercase Macedonian letters (not Й, not Latin look-alikes). |
| `#` | A clue cell. Holds one or two clues. |
| `*` | Part of a picture. |
| `.` | An empty filler cell (plain grey, no clue). Allowed, but published puzzles should have none (the generator warns). |

### Arrows: where an answer starts

A clue's answer starts in the cell to the **right** of the clue cell or in the cell **below** it, and then
runs in its `dir`. `start` says which; leaving it out means the straight arrow:

| `dir` | `start` | Arrow | Answer |
|---|---|---|---|
| `across` | `right` (default) | ▸ | starts right of the clue, runs right |
| `down` | `below` (default) | ▾ | starts below the clue, runs down |
| `across` | `below` | ↳ bent | starts below the clue, runs right |
| `down` | `right` | ⤵ bent | starts right of the clue, runs down |

Bent arrows are what magazine скандинавки use to fill the grid with no empty cells (for example a clue in
the top row whose answer runs across the row below it).

## Rules

A file is valid when the fields above are correct and all of these hold. The generator's `validate`
command checks everything. `puzzle.schema.json` checks the fields' form (not, for example, that a date
really exists) and is what the website's tests use.

1. `grid` has `height` rows of exactly `width` characters, using only the characters above.
2. Each clue sits on a `#` cell. A cell holds at most 2 clues, and not two with the same `start`.
3. The clue's answer is the run of letter cells from its start cell in its `dir`, until a non-letter or the
   edge. It must be at least 2 letters and must begin a word (the cell before the start cell, in `dir`, is
   not a letter).
4. Every run of 2+ letters, across or down, is the answer of exactly one clue. No unclued words, and no
   word with two clues.
5. Every letter cell is part of at least one answer, and every `#` cell holds at least one clue.
6. Pictures stay inside the grid, don't overlap, and together cover exactly the `*` cells.
7. `highlight: true` marks an answer whose cells are shaded yellow (the picture's answer). It is a normal
   answer with its own clue. A puzzle has at most one highlighted answer per picture.

Answers are not stored separately: both sides derive them from `grid` + `clues`, so they can never disagree.

Warnings (allowed, but the generator reports them): the same answer used twice, and filler (`.`) cells.

## Changes

New versions bump `format`. The website refuses formats it doesn't know.
