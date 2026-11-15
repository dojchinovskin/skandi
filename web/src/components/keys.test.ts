import { describe, expect, it } from "vitest";
import { actionForKey } from "./Keyboard";

describe("physical keyboard", () => {
  it("Macedonian layout letters type directly", () =>
    expect(actionForKey({ key: "љ", code: "KeyQ" })).toEqual({ type: "type", letter: "Љ" }));
  it("Latin keys map by position", () => {
    expect(actionForKey({ key: "q", code: "KeyQ" })).toEqual({ type: "type", letter: "Љ" });
    expect(actionForKey({ key: "[", code: "BracketLeft" })).toEqual({ type: "type", letter: "Ш" });
    expect(actionForKey({ key: "'", code: "Quote" })).toEqual({ type: "type", letter: "Ќ" });
  });
  it("shortcuts with Ctrl/Cmd/Alt are left to the browser", () => {
    expect(actionForKey({ key: "c", code: "KeyC", ctrlKey: true })).toBeUndefined();
    expect(actionForKey({ key: "r", code: "KeyR", metaKey: true })).toBeUndefined();
  });
  it("Enter goes to the next answer, Shift+Enter back; Tab is left to the browser", () => {
    expect(actionForKey({ key: "Enter", code: "Enter" })).toEqual({ type: "nextEntry", step: 1 });
    expect(actionForKey({ key: "Enter", code: "Enter", shiftKey: true })).toEqual({ type: "nextEntry", step: -1 });
    expect(actionForKey({ key: "Tab", code: "Tab" })).toBeUndefined();
  });
  it("on a focused button, Tab, Enter and Space keep their normal meaning", () => {
    for (const key of ["Tab", "Enter", " "]) expect(actionForKey({ key, code: key, onButton: true })).toBeUndefined();
    expect(actionForKey({ key: "л", code: "KeyL", onButton: true })).toEqual({ type: "type", letter: "Л" });
  });
  it("Backspace steps back, Delete only clears, arrows move", () => {
    expect(actionForKey({ key: "Backspace", code: "Backspace" })).toEqual({ type: "backspace" });
    expect(actionForKey({ key: "Delete", code: "Delete" })).toEqual({ type: "clear" });
    expect(actionForKey({ key: "ArrowUp", code: "ArrowUp" })).toEqual({ type: "move", arrow: "up" });
  });
  it("other keys are ignored", () => expect(actionForKey({ key: "1", code: "Digit1" })).toBeUndefined());
});
