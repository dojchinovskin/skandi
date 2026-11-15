# Format test data

Shared by both test suites, so the Kotlin validator and `puzzle.schema.json` can't drift apart:
`generator/.../FormatTestDataTest.kt` and `web/src/puzzle/puzzle.test.ts` both check every file here.

`invalid/`: each file is puzzle 0001 with exactly one defect. Both sides must reject it.

| File | Defect |
|---|---|
| `unknown-field.json` | clue has `higlight` (typo) |
| `unknown-top-field.json` | extra top-level field |
| `picture-path.json` | picture `file` tries to leave the pictures folder |
| `picture-uppercase.json` | picture `file` with capitals and a space |
| `huge-picture.json` | picture height 2147483647 |
| `blank-alt.json` | picture `alt` is only spaces |
| `blank-clue.json` | clue `text` is only spaces |
| `bad-id.json` | `id` with capitals and underscore |
| `bad-date.json` | `date` not YYYY-MM-DD |
| `bad-dir.json` | clue `dir` is `left` |
| `latin-letter.json` | grid uses Latin `O` instead of Cyrillic `О` |
| `format-2.json` | unknown `format` version |
| `too-wide.json` | width 31 (limit is 30) |
| `missing-field.json` | no `clues` field |

`valid/`: both sides must accept.

| File | Why it's here |
|---|---|
| `whole-number-floats.json` | `1.0` and `8.0` are whole numbers in JSON |
