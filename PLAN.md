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
| Hosting | GitHub Pages. |
| Design | Clean and white, like Wordle / Зборле: square tiles, thin grey borders, bold letters, little colour. |
| Input | On-screen Cyrillic keyboard (31 letters) on mobile, physical keyboard on desktop. |
| Word list | TSV file in the repo (word, clue, optional picture). Claude drafts a starter list; owner reviews and extends it. |
| Picture in grid | Yes. A block of cells holds an image that clues one or more answers. Only own or freely licensed images. |
| Picture answer | Spelled by yellow (marked) cells shown in a **Решение** strip. The marked cells always lie in **one row or one column** (never scattered) and read left-to-right / top-to-bottom. Gaps (clue cells) between them are allowed. |
| Words | Only real Macedonian words — no loanword slang (e.g. „ама“), no other languages. Applies to answers and clues. |
| Keyboard | Macedonian layout (Љ Њ Е Р Т Ѕ…), not alphabetical. Approved. |
| First grid size | About 7×9, growing as the word list grows. |
| Workflow | Owner directs and reviews; Claude writes the code. One branch + one PR per milestone. |
| Machines | Owner works on two laptops, switching between them. All state lives in this repo (see below). |

## Repo layout (planned)
- `generator/` — Kotlin CLI: reads the word list, writes puzzle JSON.
- `web/` — TypeScript + React + Vite site.
- `data/` — word list, pictures, generated puzzles.
- `CLAUDE.md` — project rules for the coding agent.

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
  Changes requested and done: solution cells must sit in one row or column (demo now reads ОБЛАК along row 6),
  and only Macedonian words. Demo puzzle rebuilt with new, non-repeating words (every word appears once).
  A private preview of the demo is published on claude.ai for viewing on phone.
  Milestone 0 is effectively approved; waiting for the owner's green light to start milestone 1.

## Next session
1. Clone (or `git pull`) the repo, open the folder in Claude, and ask it to read `PLAN.md`.
2. If the owner has given the green light, start milestone 1 (repo setup) on its own branch + PR.
