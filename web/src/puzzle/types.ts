// Puzzle format 1 — mirrors format/README.md and format/puzzle.schema.json.
// (For a Kotlin reader: these are like data classes with only `val`s; `type` aliases have no runtime cost.)

export type Dir = "across" | "down";

export interface Clue {
  row: number;
  col: number;
  dir: Dir;
  text: string;
  highlight?: boolean;
}

export interface Picture {
  row: number;
  col: number;
  width: number;
  height: number;
  file: string;
  alt: string;
  credit: string;
}

export interface Puzzle {
  format: 1;
  id: string;
  number: number;
  date: string; // YYYY-MM-DD
  width: number;
  height: number;
  grid: string[];
  clues: Clue[];
  pictures: Picture[];
}

export const SUPPORTED_FORMAT = 1;
