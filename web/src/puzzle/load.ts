import { SUPPORTED_FORMAT, type Puzzle } from "./types";

/** Fetches a puzzle published under data/site/puzzles/. Like a suspend fun returning Puzzle or throwing. */
export async function loadPuzzle(id: string): Promise<Puzzle> {
  let res: Response;
  try {
    res = await fetch(`${import.meta.env.BASE_URL}puzzles/${id}.json`);
  } catch {
    // Browser errors ("Failed to fetch") are in English; the UI must stay Macedonian.
    throw new Error("Нема врска. Провери го интернетот и обиди се повторно.");
  }
  if (!res.ok) throw new Error(`Сложувалката ${id} не е пронајдена.`);
  let data: Puzzle;
  try {
    data = (await res.json()) as Puzzle;
  } catch {
    throw new Error(`Сложувалката ${id} е оштетена.`);
  }
  if (typeof data !== "object" || data === null) throw new Error(`Сложувалката ${id} е оштетена.`);
  if (data.format !== SUPPORTED_FORMAT) throw new Error("Оваа сложувалка бара понова верзија на страницата.");
  return data;
}

const MONTHS = [
  "јануари", "февруари", "март", "април", "мај", "јуни",
  "јули", "август", "септември", "октомври", "ноември", "декември",
];

/** "2026-11-15" → "15 ноември 2026". Anything unexpected is shown as-is rather than as "undefined". */
export function formatDate(iso: string): string {
  const [y, m, d] = iso.split("-").map(Number);
  const month = MONTHS[m - 1];
  return month && d >= 1 && d <= 31 ? `${d} ${month} ${y}` : iso;
}
