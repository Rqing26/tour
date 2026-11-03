import assert from "node:assert/strict";
import { after, test } from "node:test";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { createServer } from "vite";

const server = await createServer({ server: { middlewareMode: true, hmr: false } });
after(() => server.close());
const { default: Thread } = await server.ssrLoadModule("/src/components/ChatThread.tsx");
const chat = { revision: 0, refresh: async () => {} };
const thread = { id: 1, peer: { displayName: "<顾问>", enabled: true }, unreadCount: 0 };
test("聊天窗对用户文本转义，发送前提供可访问的标签与空草稿限制", () => {
  const html = renderToStaticMarkup(createElement(Thread, {
    user: { id: 2 }, thread, chat, onBack() {},
  }));
  assert.ok(html.includes("&lt;顾问&gt;"));
  assert.ok(html.includes('for="chat-draft"'));
  assert.match(html, /<button[^>]*disabled=""[^>]*>发送/);
});
test("停用账号只能读历史，不能输入或发送", () => {
  const html = renderToStaticMarkup(createElement(Thread, {
    user: { id: 2 }, thread: { ...thread, peer: { ...thread.peer, enabled: false } }, chat, onBack() {},
  }));
  assert.ok(html.includes("账号已停用，仅可查看历史"));
  assert.match(html, /<textarea[^>]*disabled=""/);
});
