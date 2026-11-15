# Skandi — notes for Claude

Read `PLAN.md` first: it holds every decision made so far, the milestones and the current status.

## Working rules
- The owner directs and reviews; Claude writes the code. Do not start a milestone's coding until the
  owner gives the green light for it.
- The owner is fluent in Kotlin and Swift; explain TypeScript/React choices in those terms when useful.
- One branch and one PR per milestone.
- Claude reviews every PR before handing it to the owner: read the whole diff, run the builds and
  tests, try edge cases, fix what it finds on the branch, and post the review as a PR comment
  (what was checked, what was found, what was fixed, what is left). The owner still reviews and merges.
- In Claude's cloud workspace `gh` works only through the REST API (`gh api repos/...`).
- The owner switches between two laptops. Keep all project state in this repo (decisions, status,
  TODOs go in `PLAN.md`), commit and push at the end of each session, and pull at the start.
  Don't rely on local-only memory.
- Design: white and minimal like Wordle / Зборле. All UI text in Macedonian Cyrillic.
- Never commit secrets or tokens.
- Only real Macedonian words in puzzles and clues; no loanword slang.

## Commands
- Generator: `cd generator && ./gradlew build` (compiles, tests, validates `data/site/puzzles`).
- Website: `cd web && npm install && npm test && npm run build`; `npm run dev` for a local server.
- The puzzle format lives in `format/README.md`. Change it there first, then in both
  `generator/.../Puzzle.kt` and `web/src/puzzle/types.ts`.
