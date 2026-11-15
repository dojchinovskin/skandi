# Skandi

A Macedonian скандинавка (Scandinavian crossword) you solve in the browser. Puzzles are generated
offline by a Kotlin tool and published as JSON; the website only plays them. See `PLAN.md`.

## Folders

| Folder | What |
|---|---|
| `format/` | The puzzle JSON format (`README.md`) and its JSON Schema. The only contract between the two parts. |
| `generator/` | Kotlin command-line tool (Gradle). Today: `validate`. Generating comes in milestone 4. |
| `web/` | The website: TypeScript + React + Vite. |
| `data/site/` | Published files, served as-is by the website: `puzzles/*.json`, `pictures/*`. |
| `demo/` | The approved static design demo (milestone 0). |

## Running it

Needs **JDK 17 or newer** (CI tests 17 and 25) and **Node.js 22.12 or newer**.

```sh
# Generator: compile, run tests, validate every puzzle in data/site/puzzles
cd generator
./gradlew build                                  # Windows: gradlew.bat build
./gradlew run --args="validate data/site/puzzles/0001.json"

# Website
cd web
npm install
npm run dev        # local dev server, opens at http://localhost:5173
npm test           # checks puzzles against the schema
npm run build      # production build in web/dist
```

GitHub Actions runs both builds on every push to `main` and every pull request, and deploys the site to
GitHub Pages on every push to `main` (`.github/workflows/deploy.yml`).
