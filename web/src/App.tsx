import { useEffect, useState } from "react";
import { formatDate, loadPuzzle } from "./puzzle/load";
import type { Puzzle } from "./puzzle/types";

// Placeholder screen for milestone 1: proves the site builds and reads the puzzle JSON.
// The playable grid arrives in milestone 2.
export default function App() {
  // useState is like a Compose `mutableStateOf`: changing it re-renders the component.
  const [puzzle, setPuzzle] = useState<Puzzle | null>(null);
  const [error, setError] = useState<string | null>(null);

  // useEffect with [] runs once after the first render — like LaunchedEffect(Unit).
  useEffect(() => {
    loadPuzzle("0001").then(setPuzzle, (e: Error) => setError(e.message));
  }, []);

  return (
    <div className="app">
      <header>
        <h1>Скандинавка</h1>
      </header>
      {error && <p className="note">{error}</p>}
      {puzzle && (
        <>
          <div className="meta">
            Бр. {puzzle.number} · {formatDate(puzzle.date)}
          </div>
          <p className="note">
            Сложувалката е вчитана: {puzzle.width}×{puzzle.height} полиња, {puzzle.clues.length} прашања.
            <br />
            Играта доаѓа наскоро.
          </p>
        </>
      )}
    </div>
  );
}
