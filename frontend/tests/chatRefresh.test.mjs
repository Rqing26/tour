import assert from "node:assert/strict";
import { test } from "node:test";
import { createChatRefresh } from "../src/chatRefresh.ts";

test("通知合并为串行刷新，所有调用方都等待补充刷新完成", async () => {
  const finish = [];
  const queue = createChatRefresh(() => new Promise((resolve) => finish.push(resolve)), assert.fail);
  let settled = false;
  const first = queue.refresh().then(() => { settled = true; });
  await Promise.resolve();
  const second = queue.refresh();
  assert.equal(queue.refresh(), second);
  assert.equal(finish.length, 1);
  finish[0]();
  await Promise.resolve();
  await Promise.resolve();
  assert.equal(finish.length, 2);
  assert.equal(settled, false);
  finish[1]();
  await Promise.all([first, second]);
  assert.equal(settled, true);
});
test("失败不会阻塞后续刷新，排队的实时通知仍会得到处理", async () => {
  const errors = [];
  let reject;
  let calls = 0;
  const queue = createChatRefresh(() => {
    calls++;
    return calls === 1 ? new Promise((_, fail) => { reject = fail; }) : Promise.resolve();
  }, (e) => errors.push(e.message));
  const pending = queue.refresh();
  await Promise.resolve();
  queue.refresh();
  reject(Error("offline"));
  await pending;
  assert.deepEqual(errors, ["offline"]);
  assert.equal(calls, 2);
  await queue.refresh();
  assert.equal(calls, 3);
});
test("卸载时丢弃排队请求，之后不再发起刷新", async () => {
  let finish;
  let calls = 0;
  const queue = createChatRefresh(() => {
    calls++;
    return new Promise((resolve) => { finish = resolve; });
  }, assert.fail);
  const pending = queue.refresh();
  await Promise.resolve();
  queue.refresh();
  queue.dispose();
  finish();
  await pending;
  await queue.refresh();
  assert.equal(calls, 1);
});
test("同步抛错也能结算，下一次可正常刷新", async () => {
  let calls = 0;
  const errors = [];
  const queue = createChatRefresh(() => { calls++; throw Error("sync"); }, (e) => errors.push(e.message));
  await queue.refresh();
  await queue.refresh();
  assert.equal(calls, 2);
  assert.deepEqual(errors, ["sync", "sync"]);
});
