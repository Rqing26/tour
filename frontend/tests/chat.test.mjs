import assert from "node:assert/strict";
import { test } from "node:test";
import { mergeMessages, reconcileMessages, sendInput } from "../src/chat.ts";

const messages = Array.from({ length: 123 }, (_, i) => ({ id: i + 1 }));
test("重连补齐超过两页的漏消息，按 ID 去重并保持正序", async () => {
  const cursors = [];
  const result = await reconcileMessages(async (before) => {
    cursors.push(before);
    return messages.filter((m) => !before || m.id < before).slice(-50);
  }, 10);
  assert.deepEqual(cursors, [undefined, 74, 24]);
  assert.deepEqual(result.messages, messages);
  assert.equal(result.hasOlder, true);
  assert.equal(mergeMessages(messages.slice(0, 30), result.messages).length, 123);
});
test("初次打开仅取最新一页，空会话也正常返回", async () => {
  let calls = 0;
  const result = await reconcileMessages(async () => {
    calls++;
    return messages.slice(-50);
  }, 0);
  assert.equal(calls, 1);
  assert.equal(result.messages[0].id, 74);
  assert.deepEqual(await reconcileMessages(async () => [], 0), {
    messages: [], hasOlder: false,
  });
});
test("补齐失败不返回残缺结果，错误分页不会无限循环", async () => {
  await assert.rejects(reconcileMessages(async (before) => {
    if (before) throw new Error("offline");
    return messages.slice(-50);
  }, 1), /offline/);
  await assert.rejects(reconcileMessages(async () => messages.slice(-50), 1), /顺序异常/);
});
test("重发保留客户端 ID，编辑正文后生成新 ID", () => {
  const first = sendInput("  你好  ", null);
  assert.equal(first.content, "你好");
  assert.equal(sendInput("你好", first).clientId, first.clientId);
  assert.notEqual(sendInput("另一条消息", first).clientId, first.clientId);
});
