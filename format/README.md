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
  "grid": ["****.###", "****#ЛОВ", "..."],
  "clues": [
    { "row": 1, "col": 4, "dir": "across", "text": "Гонење дивеч" },
    { "row": 5, "col": 0, "dir": "across", "text": "Што гледаш на сликата?", "highlight": true }
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
| `id` | File name without `.json`. |
| `number`, `date` | Shown in the header („Бр. 1 · 15 ноември 2026“). Date is `YYYY-MM-DD`. |
| `width`, `height` | Grid size in cells. |
| `grid` | `height` strings of `width` characters each, top row first. |
| `clues` | Clue texts, each placed in a `#` cell. |
| `pictures` | Rectangles of `*` cells that show an image from `data/site/pictures/`. |

Grid characters:

| Char | Cell |
|---|---|
| `А`–`Ш` | A letter cell; the character is the solution. Only the 31 uppercase Macedonian letters. |
| `#` | A clue cell. Holds one or two clues (at most one `across` and one `down`). |
| `*` | Part of a picture. |
| `.` | An empty filler cell (plain grey, no clue). |

## Rules

A file is valid when all of these hold (the generator's `validate` command checks them):

1. `grid` has `height` rows of exactly `width` characters, using only the characters above.
2. Each clue sits on a `#` cell; one cell has at most one clue per direction.
3. The cell right of an `across` clue (below a `down` clue) is a letter. The clue's answer is the run of
   letter cells starting there and going right (down) until a non-letter or the edge. Answers are at least
   2 letters.
4. Every run of 2+ letters, across or down, has a clue in the cell just before it. No unclued words.
5. Every letter cell is part of at least one answer, and every `#` cell holds at least one clue.
6. Pictures stay inside the grid, don't overlap, and together cover exactly the `*` cells.
7. `highlight: true` marks an answer whose cells are shaded yellow (the picture's answer). It is a normal
   answer with its own clue.

Answers are not stored separately: both sides derive them from `grid` + `clues`, so they can never disagree.

Warnings (allowed, but the generator reports them): the same answer used twice.

## Changes

New versions bump `format`. The website refuses formats it doesn't know.
