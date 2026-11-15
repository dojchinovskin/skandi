import type { CSSProperties } from "react";
import type { Action, GameState } from "../game/state";
import { type CellInfo, type Entry, keyOf, type Model } from "../puzzle/model";

interface Props {
  model: Model;
  state: GameState;
  dispatch: (a: Action) => void;
}

export function Grid({ model, state, dispatch }: Props) {
  const active = model.entries[state.entry];
  const inActive = new Set(active.cells);
  // Order in which cells flip green after a win.
  const flipOrder = new Map(model.letterKeys.map((k, i) => [k, i]));

  return (
    <div
      className="grid"
      role="grid"
      aria-label="Скандинавка"
    >
      {model.cells.flat().map((cell) => {
        const id = keyOf(cell.row, cell.col);
        switch (cell.kind) {
          case "picture":
            return cell.isTopLeft ? <PictureCell key={id} cell={cell} /> : null;
          case "filler":
            return <div key={id} className="cell filler" />;
          case "clue":
            return <ClueCell key={id} clues={cell.clues} active={active} dispatch={dispatch} />;
          case "letter": {
            const value = state.letters[id] ?? "";
            const classes = ["cell", "letter"];
            if (cell.highlight) classes.push("mark");
            if (state.solved) classes.push("solved");
            else {
              if (inActive.has(id)) classes.push("word");
              if (id === state.cursor) classes.push("active");
            }
            if (state.wrong.has(id)) classes.push("wrong");
            if (state.revealed.has(id)) classes.push("revealed");
            return (
              <div
                key={id}
                role="gridcell"
                data-cell={id}
                aria-label={value ? `Буква ${value}` : "Празно поле"}
                aria-selected={id === state.cursor}
                className={classes.join(" ")}
                style={state.solved ? ({ "--flip": flipOrder.get(id) } as CSSProperties) : undefined}
                onClick={() => dispatch({ type: "select", key: id })}
              >
                {/* key={value} restarts the pop animation each time the letter changes */}
                <span className="ch" key={value}>{value}</span>
              </div>
            );
          }
        }
      })}
    </div>
  );
}

function PictureCell({ cell }: { cell: Extract<CellInfo, { kind: "picture" }> }) {
  const p = cell.picture;
  return (
    <div
      className="cell pic"
      style={{ gridRow: `${p.row + 1} / span ${p.height}`, gridColumn: `${p.col + 1} / span ${p.width}` }}
    >
      <img src={`${import.meta.env.BASE_URL}pictures/${p.file}`} alt={p.alt} title={p.credit} draggable={false} />
    </div>
  );
}

/** One clue, or two stacked (the one starting right on top, the one starting below underneath), like the
 *  split cells in magazine скандинавки. Each arrow sits on the edge where its answer starts. */
function ClueCell({ clues, active, dispatch }: { clues: Entry[]; active: Entry; dispatch: (a: Action) => void }) {
  return (
    <div className={`cell clue${clues.length > 1 ? " split" : ""}`}>
      {clues.map((e) => (
        <button
          key={e.index}
          className={`clue-part${e.index === active.index ? " hot" : ""}`}
          onClick={() => dispatch({ type: "selectEntry", index: e.index })}
          aria-label={`${e.dir === "across" ? "Хоризонтално" : "Вертикално"}: ${e.clue.text}`}
        >
          <span className="clue-text">{e.clue.text}</span>
          <Arrow entry={e} />
        </button>
      ))}
    </div>
  );
}

/** ▸ / ▾ for straight arrows; a small bent arrow (↳ or ⤵) for answers that start beside the clue and turn. */
function Arrow({ entry }: { entry: Entry }) {
  if (!entry.bent) return <span className={`arr ${entry.start === "right" ? "r" : "d"}`} aria-hidden="true" />;
  // Bent: drawn on the edge where the answer starts, turning toward the direction it runs.
  const path = entry.start === "below" ? "M2 1 V6 H9 M6.5 3.5 L9 6 L6.5 8.5" : "M1 2 H6 V9 M3.5 6.5 L6 9 L8.5 6.5";
  return (
    <svg className={`bent ${entry.start === "below" ? "b" : "r"}`} viewBox="0 0 10 10" aria-hidden="true">
      <path d={path} fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
