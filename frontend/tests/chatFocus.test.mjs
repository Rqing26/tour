import assert from "node:assert/strict";
import { test } from "node:test";
import { prepareChatFocus } from "../src/chatFocus.ts";

function fixture() {
  const doc = Object.assign(new EventTarget(), { body: {}, visibilityState: "visible" });
  const jobs = new Map();
  doc.defaultView = {
    requestAnimationFrame(fn) { jobs.set(1, fn); return 1; },
    cancelAnimationFrame(id) { jobs.delete(id); },
  };
  const input = {
    ownerDocument: doc, isConnected: true, disabled: false, calls: 0,
    focus(options) { this.calls++; this.options = options; doc.activeElement = this; },
    form: { contains: (element) => element === input || element === button },
  };
  const button = {};
  doc.activeElement = input;
  return { doc, input, button, tick() { for (const fn of jobs.values()) fn(); jobs.clear(); } };
}

test("键盘或按钮发送后在渲染完成时回到输入框，避免滚动跳动", () => {
  for (const source of ["input", "button"]) {
    const f = fixture();
    f.doc.activeElement = f[source];
    const job = prepareChatFocus(f.input);
    f.input.disabled = true;
    f.doc.activeElement = f.doc.body;
    job.finish();
    f.input.disabled = false;
    f.tick();
    assert.equal(f.doc.activeElement, f.input);
    assert.deepEqual(f.input.options, { preventScroll: true });
  }
});

test("等待响应及渲染期间用户改选焦点或点击空白时不抢回输入框", () => {
  for (const late of [false, true]) {
    for (const event of ["focusin", "pointerdown", "keydown"]) {
      const f = fixture();
      const job = prepareChatFocus(f.input);
      if (late) job.finish();
      const interaction = new Event(event);
      Object.defineProperty(interaction, "key", { value: "Tab" });
      f.doc.dispatchEvent(interaction);
      f.doc.activeElement = f.doc.body;
      if (!late) job.finish();
      f.tick();
      assert.equal(f.input.calls, 0, `${event}, late=${late}`);
    }
  }
});

test("卸载、禁用、页面隐藏或非输入区触发时不自动聚焦", () => {
  for (const state of ["disconnected", "disabled", "hidden", "outside"]) {
    const f = fixture();
    if (state === "outside") f.doc.activeElement = {};
    const job = prepareChatFocus(f.input);
    if (state === "disconnected") f.input.isConnected = false;
    if (state === "disabled") f.input.disabled = true;
    if (state === "hidden") f.doc.visibilityState = "hidden";
    job?.finish();
    f.tick();
    assert.equal(f.input.calls, 0, state);
  }
  assert.equal(prepareChatFocus(null), undefined);
});

test("会话卸载取消恢复任务，已排队的回调也不会重新聚焦", () => {
  const f = fixture();
  const job = prepareChatFocus(f.input);
  job.finish();
  job.cancel();
  job.finish();
  f.tick();
  assert.equal(f.input.calls, 0);
});
