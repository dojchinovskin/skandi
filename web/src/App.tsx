import { type CSSProperties, useCallback, useEffect, useMemo, useReducer, useState } from "react";
import { Grid } from "./components/Grid";
import { Header } from "./components/Header";
import { Keyboard, usePhysicalKeyboard } from "./components/Keyboard";
import { Actions, ClueBar, SolutionStrip, Toast } from "./components/Parts";
import { type Action, initialState, reduce } from "./game/state";
import { formatDate, loadPuzzle, puzzleIdFrom } from "./puzzle/load";
import { buildModel, type Model } from "./puzzle/model";
import type { Puzzle } from "./puzzle/types";

// `?p=0002` opens another puzzle; the archive (milestone 6) replaces this.
const PUZZLE_ID = puzzleIdFrom(window.location.search);

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
        // key: a different puzzle gets a fresh Game (and fresh state), like a new ViewModel per screen.
        <Game key={puzzle.id} puzzle={puzzle} />
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
  const model = useMemo(() => {
    try {
      return buildModel(puzzle);
    } catch {
      return null; // a broken file should show a message, not a blank page
    }
  }, [puzzle]);
  if (!model) {
    return (
      <>
        <Header onHelp={() => {}} />
        <p className="note">Сложувалката {puzzle.id} е оштетена.</p>
      </>
    );
  }
  return <Board puzzle={puzzle} model={model} />;
}

function Board({ puzzle, model }: { puzzle: Puzzle; model: Model }) {
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
      {/* The win message waits for the green flip to finish, as in the demo. */}
      <Toast toast={state.toast} delay={state.solved ? model.letterKeys.length * 35 + 450 : 0} />
    </div>
  );
}
