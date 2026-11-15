import { type CSSProperties, useCallback, useEffect, useMemo, useReducer, useState } from "react";
import { Grid } from "./components/Grid";
import { Header } from "./components/Header";
import { Keyboard, usePhysicalKeyboard } from "./components/Keyboard";
import { Actions, ClueBar, SolutionStrip, Toast } from "./components/Parts";
import { type Action, initialState, reduce } from "./game/state";
import { formatDate, loadPuzzle } from "./puzzle/load";
import { buildModel } from "./puzzle/model";
import type { Puzzle } from "./puzzle/types";

const PUZZLE_ID = "0001"; // the archive comes in milestone 6

export default function App() {
  const [puzzle, setPuzzle] = useState<Puzzle | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Runs once after the first render — like LaunchedEffect(Unit) in Compose.
  useEffect(() => {
    loadPuzzle(PUZZLE_ID).then(setPuzzle, (e: Error) => setError(e.message));
  }, []);

  return (
    <div className="app">
      {puzzle && puzzle.clues.length > 0 ? (
        <Game puzzle={puzzle} />
      ) : (
        <>
          <Header onHelp={() => {}} />
          <p className="note">{error ?? (puzzle ? "Оваа сложувалка нема прашања." : "Се вчитува…")}</p>
        </>
      )}
    </div>
  );
}

function Game({ puzzle }: { puzzle: Puzzle }) {
  const model = useMemo(() => buildModel(puzzle), [puzzle]);
  // useReducer = state + a pure reduce function (game/state.ts); dispatch(action) produces the next state.
  const [state, rawDispatch] = useReducer((s: ReturnType<typeof initialState>, a: Action) => reduce(model, s, a), model, initialState);
  const dispatch = useCallback((a: Action) => rawDispatch(a), []);
  usePhysicalKeyboard(dispatch);

  return (
    <div className="game" style={{ "--cols": model.width } as CSSProperties}>
      <Header onHelp={() => dispatch({ type: "help" })} />
      <div className="meta">
        Бр. {puzzle.number} · {formatDate(puzzle.date)}
      </div>
      <ClueBar model={model} state={state} />
      <Grid model={model} state={state} dispatch={dispatch} />
      <div className="below">
        <SolutionStrip model={model} state={state} />
        <Actions state={state} dispatch={dispatch} />
      </div>
      <Keyboard dispatch={dispatch} />
      <Toast toast={state.toast} />
    </div>
  );
}
