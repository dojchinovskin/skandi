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

/** The parts of a KeyboardEvent the mapping needs, so it can be tested without a browser. */
export interface KeyPress {
  key: string;
  code: string;
  shiftKey?: boolean;
  ctrlKey?: boolean;
  metaKey?: boolean;
  altKey?: boolean;
  onButton?: boolean; // focus is on a button
}

/** Maps a physical key press to a game action, or undefined to let the browser handle it. */
export function actionForKey(e: KeyPress): Action | undefined {
  if (e.ctrlKey || e.metaKey || e.altKey) return undefined;
  // Tab always moves focus (keyboard users must reach the buttons). On a focused button, Enter/Space press it.
  if (e.key === "Tab") return undefined;
  if (e.onButton && (e.key === "Enter" || e.key === " ")) return undefined;
  const upper = e.key.length === 1 ? e.key.toUpperCase() : "";
  const letter = LETTERS.has(upper) ? upper : BY_POSITION[e.code];
  if (letter) return { type: "type", letter };
  switch (e.key) {
    case "Backspace": return { type: "backspace" };
    case "Delete": return { type: "clear" };
    case "ArrowLeft": return { type: "move", arrow: "left" };
    case "ArrowRight": return { type: "move", arrow: "right" };
    case "ArrowUp": return { type: "move", arrow: "up" };
    case "ArrowDown": return { type: "move", arrow: "down" };
    case "Enter": return { type: "nextEntry", step: e.shiftKey ? -1 : 1 };
  }
  return undefined;
}

/** Desktop keyboard. Like registering a key listener in onAppear and removing it in onDisappear. */
export function usePhysicalKeyboard(dispatch: (a: Action) => void) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const onButton = e.target instanceof HTMLElement && e.target.closest("button") !== null;
      const action = actionForKey({ key: e.key, code: e.code, shiftKey: e.shiftKey, ctrlKey: e.ctrlKey, metaKey: e.metaKey, altKey: e.altKey, onButton });
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
