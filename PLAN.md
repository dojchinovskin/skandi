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
| Word list | TSV file in the repo (word, clue, optional picture). Claude drafts a starter list; owner reviews and extends it. |
| Picture in grid | Yes. A block of cells holds an image that clues one or more answers. Only own or freely licensed images. |
| Picture answer | A normal entry (one whole row or column word) with its own clue cell, e.g. „Што гледаш на сликата?“. Its cells are shaded yellow, like the shaded entries in Macedonian magazines (see „100 Сканди“). Also shown in the **Решение** strip. |
| Pictures per puzzle | One for now; the format should allow more later. |
| Later idea | Split clue cells holding two clues (one across, one down), as in the magazines. |
| Words | Only real Macedonian words — no loanword slang (e.g. „ама“), no other languages. Applies to answers and clues. |
| Keyboard | Macedonian layout (Љ Њ Е Р Т Ѕ…), not alphabetical. Approved. |
| First grid size | About 7×9, growing as the word list grows. |
| Workflow | Owner directs and reviews; Claude writes the code. One branch + one PR per milestone. |
| Machines | Owner works on two laptops, switching between them. All state lives in this repo (see below). |

## Repo layout
- `format/` — puzzle JSON format v1: `README.md` (fields + grid rules) and `puzzle.schema.json`.
- `generator/` — Kotlin CLI (Gradle 9.1 wrapper, Kotlin 2.2, JDK 17+; CI tests JDK 17 and 25). Command `validate`; generating comes in milestone 4.
  Uses only the kotlinx-serialization JSON *tree* API (no compiler plugin) and JUnit 4 for tests.
- `web/` — TypeScript + React + Vite site (Node 22.12+). Vitest checks puzzles against the schema.
- `data/site/` — published files served at the site root: `puzzles/NNNN.json`, `pictures/*`.
  The word list will live in `data/words/` (milestone 3), outside the published folder.
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
  screenshots at 320px, 390px (light + dark) and desktop, playing through to the win. PR open, reviews in the PR.

## Notes for later milestones
- Clue length: on small phones (320px) clue cells are ~37px wide, and words longer than ~8 letters break
  mid-word (browsers can't hyphenate Macedonian). The generator (milestone 4) should prefer short clue words,
  or we add soft hyphens to long words in the word list.
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
1. `git pull`, open the folder in Claude, and ask it to read `PLAN.md`.
2. Check the milestone 2 PR: merged?
   Note: in Claude's cloud workspace `gh` works only through the REST API (`gh api ...`), not `gh pr ...`.
3. Then milestone 3 (word list: format, starter list, validation) once the owner gives the green light.
