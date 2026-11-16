# Skandi

A Macedonian скандинавка (Scandinavian crossword) you solve in the browser. Puzzles are generated
offline by a Kotlin tool and published as JSON; the website only plays them. See `PLAN.md`.

## Folders

| Folder | What |
|---|---|
| `format/` | The puzzle JSON format (`README.md`) and its JSON Schema. The only contract between the two parts. |
| `generator/` | Kotlin command-line tool (Gradle): `generate` builds puzzles from the word list; `validate` and `words-check` check files. |
| `web/` | The website: TypeScript + React + Vite. |
| `data/site/` | Published files, served as-is by the website: `puzzles/*.json`, `pictures/*`. |
| `data/words/` | The word list the generator picks answers and clues from (`words.tsv`, format in its `README.md`). Not published. |
| `demo/` | The approved static design demo (milestone 0). |

## Running it

Needs **JDK 17 or newer** (CI tests 17 and 25) and **Node.js 22.12 or newer**.

```sh
# Generator: compile, run tests, validate every puzzle and the word list
cd generator
./gradlew build                                  # Windows: gradlew.bat build
./gradlew run --args="validate data/site/puzzles/0001.json"
./gradlew run --args="words-check data/words/words.tsv"   # errors, warnings, words per length
./gradlew run --args="generate --seed 11 --number 2"        # preview a new 7×9 puzzle with a picture
./gradlew run --args="generate --seed 11 --number 2 --out data/site/puzzles/0002.json"
./gradlew run --args="help"                                 # all options

# Website
cd web
npm install
npm run dev        # local dev server, opens at http://localhost:5173 (add ?p=0002 for puzzle 0002)
npm test           # checks puzzles against the schema
npm run build      # production build in web/dist
```

GitHub Actions runs both builds on every push to `main` and every pull request, and deploys the site to
GitHub Pages on every push to `main` (`.github/workflows/deploy.yml`).
