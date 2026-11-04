import assert from "node:assert/strict";
import { test } from "node:test";
import { createChatDrafts } from "../src/chatDrafts.ts";

test("切换会话后恢复各自草稿，同账号的新登录会话也不继承旧草稿", () => {
  const drafts = createChatDrafts();
  drafts.edit(1, "桂林两人出发");
  drafts.edit(2, "杭州三日游");
  assert.equal(drafts.get(1).content, "桂林两人出发");
  assert.equal(drafts.get(2).content, "杭州三日游");
  assert.equal(createChatDrafts().get(1).content, "");
});
test("消息窗重建后失败重试仍沿用 UUID，修改内容后使用新 UUID", async () => {
  const drafts = createChatDrafts();
  drafts.edit(1, "你好");
  const inputs = [];
  const fail = async (input) => { inputs.push(input); throw Error("offline"); };
  await drafts.send(1, fail);
  assert.equal(drafts.get(1).content, "你好");
  assert.equal(drafts.get(1).error, "offline");
  await drafts.send(1, fail);
  assert.equal(inputs[0].clientId, inputs[1].clientId);
  drafts.edit(1, "你好，请问价格？");
  await drafts.send(1, async (input) => { inputs.push(input); return { id: 7 }; });
  assert.notEqual(inputs[0].clientId, inputs[2].clientId);
  assert.equal(drafts.get(1).content, "");
  assert.equal(drafts.get(1).sending, false);
});
test("离开后发送仍会结算，重复点击不重发，旧响应不清除后来编辑的草稿", async () => {
  const drafts = createChatDrafts();
  drafts.edit(1, "第一条");
  let finish;
  const pending = drafts.send(1, () => new Promise((resolve) => { finish = resolve; }));
  assert.equal(drafts.get(1).sending, true);
  assert.equal(await drafts.send(1, () => assert.fail("duplicate request")), null);
  drafts.edit(1, "下一条");
  finish({ id: 8 });
  assert.deepEqual(await pending, { id: 8 });
  assert.equal(drafts.get(1).content, "下一条");
  assert.equal(drafts.get(1).sending, false);
});
test("订阅只通知目标会话，卸载可解除订阅，空白内容不会发送", async () => {
  const drafts = createChatDrafts();
  let calls = 0;
  const unsubscribe = drafts.subscribe(1, () => calls++);
  drafts.edit(2, "其他会话");
  drafts.edit(1, "  ");
  assert.equal(calls, 1);
  assert.equal(await drafts.send(1, () => assert.fail("blank request")), null);
  unsubscribe();
  drafts.edit(1, "更新");
  assert.equal(calls, 1);
});
