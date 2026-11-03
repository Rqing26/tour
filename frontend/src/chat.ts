import type { ChatMessage } from "./types";

export const mergeMessages = (...pages: ChatMessage[][]) =>
  [
    ...new Map(pages.flat().map((message) => [message.id, message])).values(),
  ].sort((a, b) => a.id - b.id);

// Reconnects must fill every missed page before advancing the read cursor.
export async function reconcileMessages(
  fetchPage: (beforeId?: number) => Promise<ChatMessage[]>,
  lastId: number,
) {
  const latest = await fetchPage();
  const collected = [...latest];
  let batch = latest;
  while (lastId > 0 && batch.length === 50 && batch[0].id > lastId) {
    const beforeId = batch[0].id;
    batch = await fetchPage(beforeId);
    if (batch.some((message) => message.id >= beforeId))
      throw new Error("聊天记录顺序异常，请重新加载。");
    collected.push(...batch);
  }
  return { messages: mergeMessages(collected), hasOlder: latest.length === 50 };
}

export function sendInput(
  draft: string,
  retry: { content: string; clientId: string } | null,
) {
  const content = draft.trim();
  return retry?.content === content
    ? retry
    : { content, clientId: crypto.randomUUID() };
}
