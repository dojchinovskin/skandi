# Word list

`words.tsv` holds every answer the generator may put in a puzzle, with its clues. It lives outside
`data/site/`, so it is never published with the site.

Check it with `cd generator && ./gradlew run --args="words-check data/words/words.tsv"`.
`./gradlew build` and CI check it too, and fail on any error.

## Format

A UTF-8 text file with one word per line and the columns separated by **tabs**. Edit it in a text editor
(VS Code, IntelliJ), in LibreOffice Calc (save as "Text CSV", field separator Tab, character set UTF-8), or in
Google Sheets (File → Download → Tab-separated values). Avoid saving from Excel: its "Text (Tab delimited)"
loses the Cyrillic letters and its "Unicode Text" isn't UTF-8 (the check fails loudly on both).

```
збор	опис	слика
ОКО	Орган за вид | Јамка во мрежа
РЕКА	Тече низ град | Вардар, на пример
ОБЛАК	Носи дожд | Бел, на небото	oblak.svg
```

| Column | Required | What |
|---|---|---|
| `збор` | yes | The answer, in **capital** Macedonian letters, no spaces: 2 to 30 letters. |
| `опис` | yes | One or more clues, separated by `\|`. Spaces around `\|` are ignored. |
| `слика` | no | A picture in `data/site/pictures/` that shows this word; the word can then be a puzzle's picture answer. |

**Picture answers.** When the generator uses a word as a picture answer, its clue is always the same
(„Што покажува сликата?“) and comes from the generator, not from this file. So `опис` holds only ordinary
clues, which work in any puzzle, with or without the picture.

- The first line that isn't blank or a comment is the header: `збор	опис	слика`, or `збор	опис` for a list
  without pictures (then no line may have a third column).
- Blank lines and lines starting with `#` are ignored, so you can group words under comments.
- The order of the lines doesn't matter.

## Rules the check enforces (errors)

1. Every line has 2 or 3 columns.
2. `збор` uses only the 31 Macedonian capital letters. A Latin look-alike (`A`, `E`, `O`, `J`, `S`…) or a
   letter typed as two characters (`Г` + accent instead of `Ѓ`) is reported with the exact position.
3. Each word appears on only one line. To give a word another clue, add it to the same line after `|`.
4. Every clue has at least one letter, and the same clue isn't listed twice for one word (ignoring case and
   extra spaces). No invisible characters (zero-width spaces and the like, often pasted from the web).
5. A clue doesn't contain its own answer as a whole word (`ОКО` can't have the clue „Око на бура“).
6. Clues use Cyrillic only: no Latin letters (digits and punctuation are fine), and Macedonian quotes „ “
   instead of `"` (spreadsheets add extra `"` around such fields when saving).
7. `слика` is a valid picture file name (lowercase `a-z`, `0-9`, `-`, with `.svg/.png/.jpg/.webp`) and the
   file exists in `data/site/pictures/` with exactly that name.

## Warnings (reported, don't fail the build)

- A clue longer than 30 characters: clue cells are small, and split cells hold two clues.
- A clue word longer than 8 letters: on small phones it breaks mid-word (see `PLAN.md`).

## Report

The check also prints how many words there are of each length. Dense grids need many short
words (2–5 letters), so this shows where the list needs to grow.
