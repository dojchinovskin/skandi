# Skandi — project plan

Working name: **Skandi** (no final name yet; renaming later is cheap).
Repo: https://github.com/dojchinovskin/skandi

## What it is
A website where you solve a Macedonian скандинавка (Scandinavian crossword, like the ones in
Macedonian magazines) in the browser. Clues sit inside grid cells with arrows pointing to the answer.
Puzzles are generated offline by a Kotlin tool and published as JSON files; the website only displays
and plays them.

## Decisions made
| Topic | Decision |
|---|---|
| Purpose | Hobby project. No accounts, ads or backend. |
| Format | Playable web game (phone + desktop). |
| Puzzles | Auto-generated from a Macedonian word + clue list, reviewed before publishing. |
| Generator | Kotlin (JVM command-line tool, Gradle). Owner is fluent in Kotlin and Swift. |
| Website | TypeScript + React + Vite, static site. |
| Contract | Puzzle JSON format is the only link between generator and website. |
| Hosting | GitHub Pages, deployed by `.github/workflows/deploy.yml` on every push to `main` (brought forward from milestone 6 so the owner can test on any device). Needs the repo public (free plan) and Settings → Pages → Source: GitHub Actions. |
| Design | Clean and white, like Wordle / Зборле: square tiles, thin grey borders, bold letters, little colour. |
| Input | On-screen Cyrillic keyboard (31 letters) on mobile, physical keyboard on desktop. |
| Word list | TSV file in the repo: `data/words/words.tsv` (word, clues, optional picture), format in `data/words/README.md`. Claude drafts a starter list; owner reviews and extends it. |
| Clues per word | Several, separated by `\|`, so a common word like ОКО doesn't get the same clue in every puzzle (owner, 2026-11-16). A word appears on only one line. |
| Picture in grid | Yes. A block of cells holds an image that clues one or more answers. Only own or freely licensed images. |
| Picture answer | A normal entry (one whole row or column word) with its own clue cell, e.g. „Што гледаш на сликата?“. Its cells are shaded yellow, like the shaded entries in Macedonian magazines (see „100 Сканди“). Also shown in the **Решение** strip. |
| Pictures per puzzle | One for now; the format should allow more later. |
| Split clue cells | Supported: a clue cell holds up to two clues, as in the magazines. |
| No empty cells | Published puzzles are fully dense: every cell is a letter, a clue or the picture (owner, 2026-11-15). The validator warns on filler cells. |
| Bent arrows | A clue's answer can start in the cell right of it or below it (`start` in the format), as in magazines. Needed for dense grids: with straight arrows only, the rows/columns next to the edge would all have to be unbroken long words. |
| Words | Only real Macedonian words — no loanword slang (e.g. „ама“), no other languages. Applies to answers and clues. |
| Keyboard | Macedonian layout (Љ Њ Е Р Т Ѕ…), not alphabetical. Approved. |
| First grid size | About 7×9, growing as the word list grows. |
| Workflow | Owner directs and reviews; Claude writes the code. One branch + one PR per milestone. |
| Machines | Owner works on two laptops, switching between them. All state lives in this repo (see below). |

## Repo layout
- `format/` — puzzle JSON format v1: `README.md` (fields + grid rules) and `puzzle.schema.json`.
- `generator/` — Kotlin CLI (Gradle 9.1 wrapper, Kotlin 2.2, JDK 17+; CI tests JDK 17 and 25). Commands `generate`, `validate` and `words-check`.
  Uses only the kotlinx-serialization JSON *tree* API (no compiler plugin) and JUnit 4 for tests.
- `web/` — TypeScript + React + Vite site (Node 22.12+). Vitest checks puzzles against the schema.
- `data/site/` — published files served at the site root: `puzzles/NNNN.json`, `pictures/*`.
  The word list lives in `data/words/`, outside the published folder.
- `.github/workflows/build.yml` — builds and tests both parts on every push to `main` and every PR.
- `CLAUDE.md` — project rules for the coding agent. `README.md` — how to run things.

## Milestones
| # | Milestone | Done when | Agent practice to learn |
|---|---|---|---|
| 0 | Design demo (static page) | Owner approves the look | Visual review in the browser |
| 1 | Repo setup: tooling, CLAUDE.md, puzzle JSON format | Both parts build; example puzzle validates | Project rules, plan before code |
| 2 | Player UI with one hand-made puzzle | Solvable on phone and desktop with the agreed design | Reviewing a PR |
| 3 | Word list: format, starter list, validation | List loads; duplicates and bad rows rejected | Tests as definition of done |
| 4 | Generator: layout, clue cells, fill | Valid ~7×9 grids from the list | Breaking a hard problem down, code review |
| 5 | Picture block in the grid | A picture-clue puzzle generates and renders | Extending a design safely |
| 6 | Puzzle archive + deploy | Site live with several puzzles | CI and deploy automation |
| 7 | Polish: progress saving, check/reveal, larger grids | Agreed later | Parallel sessions, custom skills |

## Open risk
Generator quality depends on the word list. A dense Scandinavian grid needs many short words (2–5
letters). If the starter list can't fill ~7×9 densely, use a smaller grid or add words first.

## Status
- 2026-11-15: Plan agreed. Coding of the real project waits for the owner's green light.
  Design demo (milestone 0) built at `demo/index.html`: a single static file, hand-made 8×8 puzzle,
  picture block plus a solution word, on-screen Macedonian keyboard, light/dark. Waiting for the owner's feedback.
  Demo content is placeholder, hand-made; the real generator will build the grids.
- 2026-11-15: Git installed on laptop 1; everything pushed to GitHub (`main`). Owner continues on the other PC.
- 2026-11-15 (laptop 2): Owner reviewed the demo. Likes the look, the keyboard and the picture + solution idea.
  Changes requested and done: the picture's answer is a whole entry with its own clue (ОБЛАК in row 6,
  where a normal word would be), and only Macedonian words. Demo rebuilt with non-repeating words.
  A private preview of the demo is published on claude.ai for viewing on phone.
  Milestone 0 approved. Owner gave the green light for milestone 1.
- 2026-11-15: Milestone 1 built on branch `m1-repo-setup`: puzzle format v1 + schema, the demo puzzle as
  `data/site/puzzles/0001.json`, Kotlin generator with `validate` + 9 tests, React/Vite site that loads the
  puzzle (placeholder screen), GitHub Actions build. In Claude's cloud workspace Maven/Gradle downloads are
  blocked, so the Kotlin code was compiled and tested there with Gradle's bundled Kotlin compiler; the real
  `./gradlew build` runs in GitHub Actions. Web tests + build pass. PR #1 reviewed (Claude + independent reviewer agent) and merged by the owner.
- 2026-11-15: Added the independent `reviewer` agent (`.claude/agents/reviewer.md`). Its first review of
  milestone 1 found: Gradle 8.14 can't run on JDK 25+, and the Kotlin validator and JSON Schema disagreed on
  several rules (unknown fields, file names, size limits, blank text). Fixed on branch `m1-review-fixes`:
  Gradle 9.1 + Kotlin 2.2 (CI tests JDK 17 and 25), validator matches the schema, shared test data in
  `format/testdata/` checked by both test suites, real-date check, max one highlight per picture, Macedonian
  error messages, `.gitattributes`, two clue fixes in 0001.json.

- 2026-11-15: PR #2 merged. Milestone 2 built on branch `m2-player-ui`: puzzle 0001 is playable in the React
  site with the approved design. Game logic is a pure reducer (`web/src/game/state.ts`); 56 web tests in total;
  components in `web/src/components/`. Supports split clue cells (two clues in one cell). Checked with
  screenshots at 320px, 390px (light + dark) and desktop, playing through to the win. PR #3 reviewed and merged.
- 2026-11-15: Repo made public; GitHub Pages (Source: GitHub Actions) live at
  https://dojchinovskin.github.io/skandi/ — every push to `main` redeploys.

- 2026-11-15: Owner: no empty rows/columns. Added bent arrows (format `start`, validator, player) on branch
  `bent-arrows` and rebuilt puzzle 0001 fully dense (picture top-left, ОБЛАК down column 5, 19 answers)
  with a search script; Claude reviewed the words and clues by hand. PR #4 reviewed (self + independent)
  and merged; the dense grid is live on the site.

- 2026-11-16: Owner gave the green light for milestone 3 and chose several clues per word. Built on branch
  `m3-word-list`: format in `data/words/README.md`; Kotlin `parseWordList` + `words-check` command (errors:
  columns, Macedonian capitals only with Latin look-alikes named, letter+accent typed as two characters,
  duplicates, empty/repeated clues, clue containing its answer, Latin letters or `"` in clues, picture name/file;
  warnings: clue over 30 characters or with a word over 8 letters; report of words per length). Runs in
  `./gradlew build` (task `checkWords`) and CI. 26 new tests (57 Kotlin tests in total).
  Starter list: 378 words, 399 clues, 359 of them 2–5 letters; includes 12 Macedonian place names in their own
  section, easy to delete. PR #5. Independent review found wrong clues (ГРАВ, СРНА, ВРАНА „грачи“, КАЈ as a
  question word) and a format gap: fixed. Picture answers get their clue („Што покажува сликата?“) from the
  generator, so `опис` holds only ordinary clues. Waiting for the owner's word-by-word review in the PR.

- 2026-11-17: PR #5 merged (owner kept the short words, verbs and place names). Owner gave the green light for
  milestone 4. Built on branch `m4-generator`: `generate` command (`Layout.kt`, `LetterCheck.kt`, `Fill.kt`,
  `Generate.kt`). How it works:
  1. Layout: simulated annealing flips cells between clue and letter until there are no orphan letters,
     every word has a length the list can fill, and a clue assignment exists. The assignment (which clue cell
     and arrow each word uses) is an exact max-flow with lower bounds: every word gets one clue spot, every
     clue cell gets 1–2 clues, never two the same way.
  2. Letter check (inside the annealing): arc consistency over the word list. Without it 55 of 60 layouts
     were unfillable because of simple letter clashes; with it 1 in 2–3 layouts fills.
  3. Fill: backtracking, most constrained slot first, bit sets per (length, position, letter), no word twice.
  4. Pick a random clue per word, run `validate` (must pass), write JSON in the 0001 style; never overwrites.
  Tuned on the starter list (7×9): clue cells ~30%, a penalty on 2-letter answers (46% → ~32%), average answer
  3.2 letters, ~1.6 s a puzzle (6×8 0.7 s, 9×11 8 s). Same seed → same puzzle. 77 Kotlin tests.

- 2026-11-17: PR #6 merged. The independent review of #6 (after merge) found three input bugs in `generate`:
  an impossible `--date` crashed after generating, `--out` with a file name not matching the id wrote a file
  `validate` rejects, a too-small word list crashed with a stack trace. Fixed on the milestone 5 branch, with
  repeated options rejected and the time limit also checked inside the layout search.
- 2026-11-17: Owner gave the green light for milestone 5. Built on branch `m5-picture`:
  - `data/words/pictures.tsv` (file, description, credit), checked by `words-check` against the word list.
  - Five new pictures drawn by Claude (сонце, риба, мак, клуч, чаша), simple SVGs like oblak.svg.
  - Generator: by default every puzzle gets a picture block in a random corner (3×3 in 7×9; about half the
    shorter side), a random picture word, pinned to one slot of its length; its clue is „Што е на сликата?“
    (shorter than 0001's text, which needed three lines in a split clue cell) and it's highlighted. The letter
    check tests that the picture word fits during the layout search: 7×9 ~1.5 s, usually the first layout.
    Options `--picture FILE|none`, `--picture-size WxH`.
  - Puzzle 0002 (seed 31, picture bottom-right) is published; open it with `?p=0002` (stopgap until the
    archive in milestone 6). Played to the win in Chrome at 390px; screenshots at 320px and dark mode.
  - 95 Kotlin tests, 68 web tests. Independent review: merge after fixes; fixed a test that compared 0002 with a
    fresh run (would break CI on the first new word), a stack trace on a huge `--picture-size`, `--picture-size`
    silently ignored with `--picture none`, `NONE` not accepted, README example that overwrote 0002.

- 2026-11-16 (late): PR #7 merged. Owner gave the green light for milestone 6 and chose: **one puzzle per day**
  (the site shows puzzles dated today or earlier, Skopje time, and opens today's) and **7 new puzzles**
  (0003–0009, dated 2026-11-18 to 11-24). Work in progress on branch `m6-archive` (not yet compiled or
  tested; stopped mid-way at the owner's request):
  - Done: `format/README.md` documents `data/site/puzzles/index.json` and the folder rules (id = number with 4
    digits, unique numbers and dates, dates go up, index matches the files). `Archive.kt` (index model,
    JSON, `checkArchive`, `puzzleFiles` excluding index.json, `writeIndex`). `validate <folder>` checks the
    archive; new `index <folder>` command. `generate`: number from the `--out` file name, date defaults to
    the day after the newest puzzle, `--avoid N` (default 3) skips 3+ letter answers of the last N puzzles,
    and index.json is rewritten after writing.

## Notes for later milestones
- Generator quality grows with the word list: more 3–5 letter words mean fewer 2-letter answers (prepositions,
  short pronouns) and faster generation. `words-check` prints the counts per length.
- Split clue cells fit about two short lines per clue. Long clues there overflow onto the arrow; the
  generator could prefer short clues for cells holding two clues (milestone 7).
- Picture position: only the four corners so far; magazines also put pictures along an edge.
- Bigger grids are slow near the time limit (9×11 ~9 s here, 10×12 ~30 s); the limit is 60 s, so a slower
  laptop may give up where this machine doesn't. Try another seed.
- Variety across puzzles (milestone 6): avoid answers used in the last few puzzles.
- Clue length: on small phones (320px) clue cells are ~37px wide, and words longer than ~8 letters break
  mid-word (browsers can't hyphenate Macedonian). The generator (milestone 4) should prefer short clue words,
  or we add soft hyphens to long words in the word list. `words-check` warns about such words; it rejects
  invisible characters, so allow U+00AD there first if we choose soft hyphens.
- The demo picture is light even in dark mode; fine for photos, revisit for drawn pictures.
- Small phones (375×667 and smaller): the bottom keyboard row needs a little scrolling, same as the approved
  demo. Consider a compact layout in milestone 7.
- Accessibility: the grid has no ARIA rows and letter cells aren't focusable; screen-reader support is for
  milestone 7.

## Changes from the demo (agreed in review, milestone 2)
- Keyboard: Tab is left to the browser (so keyboard users can reach the buttons); Enter / Shift+Enter move to
  the next / previous answer; Delete clears only the current cell.
- Dropped the small numbers in the shaded cells and Решение slots, and the „Слика“ tag: the solution is now a
  normal answer read left to right, so they added nothing.

## Next session
1. `git pull`, `git checkout m6-archive`, open the folder in Claude, and ask it to read `PLAN.md`.
2. Finish milestone 6 on `m6-archive`:
   - Compile and fix; update tests: existing `CliTest` folder tests need an index.json; add tests for
     `Archive.kt`, `index`, `validate` archive errors, `generate` defaults (number from file name, next date,
     `--avoid`, index rewritten).
   - Run `index data/site/puzzles` to create index.json for 0001 and 0002.
   - Generate 0003–0009 (`generate --out data/site/puzzles/000N.json --seed …`), review words and clues.
   - Web: load index.json; pure helpers for today in Europe/Skopje, visible puzzles, which one to open
     (`?p=` for a specific one; a future one says when it comes out); „Архива“ button in the header and an
     archive screen listing visible puzzles (newest first, „Бр. N · date“, links to `?p=`). The web test
     that reads every .json in data/site/puzzles must skip index.json; add a schema/test for the index.
   - Screenshots (phone, dark, 320px), play one to the win, self-review, PR, independent reviewer.
   Note: in Claude's cloud workspace `gh` works only through the REST API (`gh api ...`), not `gh pr ...`.
   Maven/Gradle downloads are blocked there too: compile and test the Kotlin code with the Kotlin compiler
   bundled in /opt/gradle/lib (kotlin-compiler-embeddable, kotlinx-serialization-json, junit, annotations jars);
   the real `./gradlew build` runs in GitHub Actions.
