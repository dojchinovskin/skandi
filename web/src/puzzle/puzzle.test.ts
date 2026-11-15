import { readdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import Ajv2020 from "ajv/dist/2020";
import { afterEach, describe, expect, it, vi } from "vitest";
import { formatDate, loadPuzzle } from "./load";
import { SUPPORTED_FORMAT, type Puzzle } from "./types";

const root = resolve(__dirname, "../../..");
const schema = JSON.parse(readFileSync(resolve(root, "format/puzzle.schema.json"), "utf8"));
const validate = new Ajv2020({ allErrors: true }).compile(schema);

const jsonFiles = (dir: string) => readdirSync(resolve(root, dir)).filter((f) => f.endsWith(".json"));
const read = (dir: string, file: string) => JSON.parse(readFileSync(resolve(root, dir, file), "utf8"));

describe("published puzzles", () => {
  const dir = "data/site/puzzles";
  const files = jsonFiles(dir);

  it("there is at least one", () => expect(files.length).toBeGreaterThan(0));

  for (const file of files) {
    it(`${file} matches the schema`, () => {
      const data = read(dir, file);
      validate(data);
      expect(validate.errors ?? []).toEqual([]);
      const p = data as Puzzle;
      expect(p.format).toBe(SUPPORTED_FORMAT);
      expect(p.id).toBe(file.replace(".json", ""));
    });
  }
});

// Shared with the Kotlin tests (FormatTestDataTest.kt), so the schema and the validator can't drift apart.
describe("format/testdata", () => {
  for (const file of jsonFiles("format/testdata/invalid")) {
    it(`rejects invalid/${file}`, () => expect(validate(read("format/testdata/invalid", file))).toBe(false));
  }
  for (const file of jsonFiles("format/testdata/valid")) {
    it(`accepts valid/${file}`, () => {
      validate(read("format/testdata/valid", file));
      expect(validate.errors ?? []).toEqual([]);
    });
  }
});

describe("loadPuzzle", () => {
  afterEach(() => vi.unstubAllGlobals());
  const respond = (body: unknown, ok = true) =>
    vi.stubGlobal("fetch", vi.fn(async () => ({ ok, json: async () => body }) as Response));

  it("returns a supported puzzle", async () => {
    respond(read("data/site/puzzles", "0001.json"));
    expect((await loadPuzzle("0001")).number).toBe(1);
  });
  it("refuses an unknown format, in Macedonian", async () => {
    respond({ format: 2 });
    await expect(loadPuzzle("0001")).rejects.toThrow("понова верзија");
  });
  it("reports a missing puzzle in Macedonian", async () => {
    respond(null, false);
    await expect(loadPuzzle("9999")).rejects.toThrow("не е пронајдена");
  });
  it("reports an empty (null) puzzle in Macedonian", async () => {
    respond(null);
    await expect(loadPuzzle("0001")).rejects.toThrow("оштетена");
  });
  it("reports a network failure in Macedonian", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => { throw new TypeError("Failed to fetch"); }));
    await expect(loadPuzzle("0001")).rejects.toThrow("Нема врска");
  });
});

describe("formatDate", () => {
  it("writes the month in Macedonian", () => expect(formatDate("2026-11-15")).toBe("15 ноември 2026"));
  it("never shows undefined", () => expect(formatDate("2026-13-45")).toBe("2026-13-45"));
});
