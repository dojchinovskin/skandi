import { useEffect, useState } from "react";
import type { Action, GameState } from "../game/state";
import type { Model } from "../puzzle/model";

export function ClueBar({ model, state }: { model: Model; state: GameState }) {
  const e = model.entries[state.entry];
  return (
    <div className="cluebar" aria-live="polite">
      <span className="dir">{e.dir === "across" ? "Хоризонтално" : "Вертикално"}</span>
      <span className="txt">{e.clue.text}</span>
    </div>
  );
}

/** The picture's answer, letter by letter, as the player fills the shaded cells. */
export function SolutionStrip({ model, state }: { model: Model; state: GameState }) {
  if (!model.solution) return <div />;
  return (
    <div className="solution">
      <span className="lbl">Решение</span>
      <div className="slots">
        {model.solution.cells.map((k) => {
          const v = state.letters[k] ?? "";
          return (
            <div key={k} className={`slot${v ? " filled" : ""}`}>
              {v}
            </div>
          );
        })}
      </div>
    </div>
  );
}

export function Actions({ state, dispatch }: { state: GameState; dispatch: (a: Action) => void }) {
  return (
    <div className="actions">
      <button className="btn" disabled={state.solved} onClick={() => dispatch({ type: "reveal" })}>
        Откриј буква
      </button>
      <button className="btn" disabled={state.solved} onClick={() => dispatch({ type: "check" })}>
        Провери
      </button>
    </div>
  );
}

/** Shows each new toast for a few seconds. */
export function Toast({ toast }: { toast?: GameState["toast"] }) {
  const [visible, setVisible] = useState(false);
  useEffect(() => {
    if (!toast) return;
    setVisible(true);
    const t = setTimeout(() => setVisible(false), toast.text.length > 40 ? 4000 : 2200);
    return () => clearTimeout(t);
  }, [toast]);
  return (
    <div className={`toast${visible ? " show" : ""}`} role="status">
      {toast?.text}
    </div>
  );
}
