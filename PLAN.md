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
  Demo content is placeholder: each 3×3 block is a symmetric word square, so every word appears twice.
  The real generator won't do that.
- 2026-11-15: Git installed on laptop 1; everything pushed to GitHub (`main`). Owner continues on the other PC.

## Next session
1. Clone (or `git pull`) the repo, open the folder in Claude, and ask it to read `PLAN.md`.
2. Collect the owner's design feedback on the demo (open `demo/index.html` in a browser), then start milestone 1
   once the owner gives the green light.
