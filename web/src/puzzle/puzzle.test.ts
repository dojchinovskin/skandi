import { readdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import Ajv2020 from "ajv/dist/2020";
import { describe, expect, it } from "vitest";
import { formatDate } from "./load";
import { SUPPORTED_FORMAT, type Puzzle } from "./types";

const root = resolve(__dirname, "../../..");
const schema = JSON.parse(readFileSync(resolve(root, "format/puzzle.schema.json"), "utf8"));
const validate = new Ajv2020({ allErrors: true }).compile(schema);
const puzzleDir = resolve(root, "data/site/puzzles");

describe("published puzzles", () => {
  const files = readdirSync(puzzleDir).filter((f) => f.endsWith(".json"));

  it("there is at least one", () => expect(files.length).toBeGreaterThan(0));

  for (const file of files) {
    it(`${file} matches the schema`, () => {
      const data = JSON.parse(readFileSync(resolve(puzzleDir, file), "utf8"));
      const ok = validate(data);
      expect(validate.errors ?? []).toEqual([]);
      expect(ok).toBe(true);
      const p = data as Puzzle;
      expect(p.format).toBe(SUPPORTED_FORMAT);
      expect(p.id).toBe(file.replace(".json", ""));
    });
  }
});

describe("formatDate", () => {
  it("writes the month in Macedonian", () => expect(formatDate("2026-11-15")).toBe("15 ноември 2026"));
});
