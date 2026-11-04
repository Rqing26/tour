export function prepareChatFocus(input: HTMLTextAreaElement | null) {
  if (!input) return;
  const doc = input.ownerDocument;
  const origin = doc.activeElement;
  const view = doc.defaultView!;
  if (!input.form?.contains(origin)) return;
  let moved = false;
  let cancelled = false;
  let frame = 0;
  const interaction = () => {
    moved = true;
  };
  const key = (event: KeyboardEvent) => {
    if (event.key === "Tab") interaction();
  };
  doc.addEventListener("focusin", interaction, true);
  doc.addEventListener("pointerdown", interaction, true);
  doc.addEventListener("keydown", key, true);
  const cleanup = () => {
    doc.removeEventListener("focusin", interaction, true);
    doc.removeEventListener("pointerdown", interaction, true);
    doc.removeEventListener("keydown", key, true);
  };
  return {
    finish() {
      if (cancelled) return;
      frame = view.requestAnimationFrame(() => {
        cleanup();
        if (
          !moved &&
          input.isConnected &&
          !input.disabled &&
          doc.visibilityState === "visible" &&
          (doc.activeElement === origin || doc.activeElement === doc.body)
        ) {
          input.focus({ preventScroll: true });
        }
      });
    },
    cancel() {
      cancelled = true;
      view.cancelAnimationFrame(frame);
      cleanup();
    },
  };
}
