import { SUPPORTED_FORMAT, type Puzzle } from "./types";

/** Fetches a puzzle published under data/site/puzzles/. Like a suspend fun returning Puzzle or throwing. */
export async function loadPuzzle(id: string): Promise<Puzzle> {
  const res = await fetch(`${import.meta.env.BASE_URL}puzzles/${id}.json`);
  if (!res.ok) throw new Error(`Сложувалката ${id} не е пронајдена.`);
  const data = (await res.json()) as Puzzle;
  if (data.format !== SUPPORTED_FORMAT) throw new Error("Оваа сложувалка бара понова верзија на страницата.");
  return data;
}

const MONTHS = [
  "јануари", "февруари", "март", "април", "мај", "јуни",
  "јули", "август", "септември", "октомври", "ноември", "декември",
];

/** "2026-11-15" → "15 ноември 2026" */
export function formatDate(iso: string): string {
  const [y, m, d] = iso.split("-").map(Number);
  return `${d} ${MONTHS[m - 1]} ${y}`;
}
