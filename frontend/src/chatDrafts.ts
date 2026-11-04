import { errorMessage } from "./api.ts";
import { sendInput } from "./chat.ts";
import type { ChatMessage } from "./types";

type Input = ReturnType<typeof sendInput>;
type Draft = {
  content: string;
  retry: Input | null;
  sending: boolean;
  error: string;
};
const empty: Draft = { content: "", retry: null, sending: false, error: "" };

// One store per authenticated app session; nothing is written to browser storage.
export function createChatDrafts() {
  const drafts = new Map<number, Draft>();
  const listeners = new Map<number, Set<() => void>>();
  const get = (id: number) => drafts.get(id) ?? empty;
  const write = (id: number, draft: Draft) => {
    drafts.set(id, draft);
    listeners.get(id)?.forEach((listener) => listener());
  };
  return {
    get,
    subscribe(id: number, listener: () => void) {
      if (!listeners.has(id)) listeners.set(id, new Set());
      listeners.get(id)!.add(listener);
      return () => {
        listeners.get(id)?.delete(listener);
      };
    },
    edit(id: number, content: string) {
      write(id, { ...get(id), content, error: "" });
    },
    async send(id: number, deliver: (input: Input) => Promise<ChatMessage>) {
      const before = get(id);
      if (!before.content.trim() || before.sending) return null;
      const input = sendInput(before.content, before.retry);
      write(id, { ...before, retry: input, sending: true, error: "" });
      try {
        const message = await deliver(input);
        const current = get(id);
        write(id, {
          ...empty,
          content: current.content === before.content ? "" : current.content,
        });
        return message;
      } catch (error) {
        write(id, { ...get(id), sending: false, error: errorMessage(error) });
        return null;
      }
    },
  };
}
