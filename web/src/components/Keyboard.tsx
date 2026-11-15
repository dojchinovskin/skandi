import { useEffect } from "react";
import type { Action } from "../game/state";

// Macedonian keyboard layout (approved in milestone 0), 31 letters.
const ROWS = ["ЉЊЕРТЅУИОПШЃ", "АСДФГХЈКЛЧЌЖ", "ЗЏЦВБНМ"];
const LETTERS = new Set(Array.from(ROWS.join("")));

// Physical keys → Macedonian letters by position, so a desktop keyboard works without the
// Macedonian layout installed. Typing with the Macedonian layout on works too (event.key).
const BY_POSITION: Record<string, string> = {
  KeyQ: "Љ", KeyW: "Њ", KeyE: "Е", KeyR: "Р", KeyT: "Т", KeyY: "Ѕ", KeyU: "У", KeyI: "И", KeyO: "О", KeyP: "П",
  BracketLeft: "Ш", BracketRight: "Ѓ", KeyA: "А", KeyS: "С", KeyD: "Д", KeyF: "Ф", KeyG: "Г", KeyH: "Х",
  KeyJ: "Ј", KeyK: "К", KeyL: "Л", Semicolon: "Ч", Quote: "Ќ", Backslash: "Ж", KeyZ: "З", KeyX: "Џ",
  KeyC: "Ц", KeyV: "В", KeyB: "Б", KeyN: "Н", KeyM: "М",
};

/** Desktop keyboard. Like registering a key listener in onAppear and removing it in onDisappear. */
export function usePhysicalKeyboard(dispatch: (a: Action) => void) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      const upper = e.key.length === 1 ? e.key.toUpperCase() : "";
      const letter = LETTERS.has(upper) ? upper : BY_POSITION[e.code];
      let action: Action | undefined;
      if (letter) action = { type: "type", letter };
      else if (e.key === "Backspace" || e.key === "Delete") action = { type: "backspace" };
      else if (e.key === "ArrowLeft") action = { type: "move", arrow: "left" };
      else if (e.key === "ArrowRight") action = { type: "move", arrow: "right" };
      else if (e.key === "ArrowUp") action = { type: "move", arrow: "up" };
      else if (e.key === "ArrowDown") action = { type: "move", arrow: "down" };
      else if (e.key === "Tab" || e.key === "Enter") action = { type: "nextEntry", step: e.shiftKey ? -1 : 1 };
      if (action) {
        e.preventDefault();
        dispatch(action);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [dispatch]);
}

export function Keyboard({ dispatch }: { dispatch: (a: Action) => void }) {
  return (
    <div className="kb" role="group" aria-label="Тастатура">
      {ROWS.map((row, i) => (
        <div className="row" key={row}>
          {Array.from(row).map((l) => (
            <button key={l} className="key" onClick={() => dispatch({ type: "type", letter: l })}>
              {l}
            </button>
          ))}
          {i === ROWS.length - 1 && (
            <button className="key wide" onClick={() => dispatch({ type: "backspace" })} aria-label="Избриши">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round">
                <path d="M21 5H9l-6 7 6 7h12z" />
                <path d="m11.5 9.5 5 5m0-5-5 5" />
              </svg>
            </button>
          )}
        </div>
      ))}
    </div>
  );
}
