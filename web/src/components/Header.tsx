import { useEffect, useState } from "react";

type Theme = "light" | "dark";

/** A manual light/dark choice, remembered in this browser. Until the player picks one, the page follows
 *  the system setting through CSS (no data-theme attribute), including live changes. */
function useTheme(): [Theme, () => void] {
  const [chosen, setChosen] = useState<Theme | null>(() => {
    try {
      const saved = localStorage.getItem("theme");
      if (saved === "light" || saved === "dark") return saved;
    } catch { /* storage can be unavailable (private mode) */ }
    return null;
  });
  useEffect(() => {
    if (chosen) document.documentElement.dataset.theme = chosen;
  }, [chosen]);
  // Follow live system changes so the button label stays right while no theme is chosen.
  const DARK = "(prefers-color-scheme: dark)";
  const [systemDark, setSystemDark] = useState(() => matchMedia(DARK).matches);
  useEffect(() => {
    const query = matchMedia(DARK);
    const onChange = (e: MediaQueryListEvent) => setSystemDark(e.matches);
    query.addEventListener("change", onChange);
    return () => query.removeEventListener("change", onChange);
  }, []);
  const current = (): Theme => chosen ?? (systemDark ? "dark" : "light");
  const toggle = () => {
    const next = current() === "dark" ? "light" : "dark";
    setChosen(next);
    try { localStorage.setItem("theme", next); } catch { /* ignore */ }
  };
  return [current(), toggle];
}

export function Header({ onHelp }: { onHelp: () => void }) {
  const [theme, toggleTheme] = useTheme();
  return (
    <header>
      <div className="bar">
        <button className="icon-btn" onClick={onHelp} aria-label="Како се игра">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            <circle cx="12" cy="12" r="9.5" />
            <path d="M9.6 9.3a2.5 2.5 0 1 1 3.4 2.3c-.7.3-1 .9-1 1.6v.6" />
            <circle cx="12" cy="17" r=".6" fill="currentColor" />
          </svg>
        </button>
        <h1>Скандинавка</h1>
        <button className="icon-btn" onClick={toggleTheme} aria-label={theme === "dark" ? "Светла тема" : "Темна тема"}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" />
          </svg>
        </button>
      </div>
    </header>
  );
}
