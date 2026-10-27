import assert from "node:assert/strict";
import { test } from "node:test";
import { eventTime, timelineNodes, transitionTitle } from "../src/timeline.ts";

const event = (id, fromStatus, toStatus, extra = {}) => ({
  id,
  bookingId: 7,
  fromStatus,
  toStatus,
  actorId: 3,
  actorName: "王顾问",
  actorRole: "EMPLOYEE",
  note: "",
  createdAt: "2026-10-27T09:05:00",
  ...extra,
});

test("状态变更翻译成客户读得懂的动作", () => {
  assert.equal(transitionTitle(null, "PENDING"), "客户提交预订");
  assert.equal(transitionTitle("PENDING", "ASSIGNED"), "顾问接单");
  assert.equal(transitionTitle("ASSIGNED", "PENDING"), "退回待接池");
  assert.equal(transitionTitle("ASSIGNED", "COMPLETED"), "旅程完成");
  assert.equal(transitionTitle("PENDING", "CANCELLED"), "客户取消预订");
  assert.equal(transitionTitle("ASSIGNED", "CANCELLED"), "客户取消预订");
  // 后端将来新增迁移时不能让界面显示 undefined，必须有兜底文案。
  assert.equal(transitionTitle("COMPLETED", "PENDING"), "订单状态变更");
});

test("时间线按发生时间排序，同一时刻用自增 id 兜底", () => {
  const nodes = timelineNodes([
    event(9, "PENDING", "ASSIGNED", { createdAt: "2026-10-27T09:05:00" }),
    event(4, null, "PENDING", { createdAt: "2026-10-26T18:20:00" }),
    event(12, "ASSIGNED", "COMPLETED", { createdAt: "2026-10-27T09:05:00" }),
  ]);
  assert.deepEqual(
    nodes.map((node) => node.id),
    [4, 9, 12],
  );
  assert.equal(nodes[0].title, "客户提交预订");
  assert.equal(nodes[0].time, "2026.10.26 18:20");
  assert.equal(nodes[2].transition, "服务中 → 已完成");
});

test("每条留痕都带上操作人与角色，界面无需再查用户表", () => {
  const [node] = timelineNodes([
    event(1, null, "PENDING", {
      actorId: 5,
      actorName: "林小满",
      actorRole: "CUSTOMER",
      note: "带两位老人出行",
    }),
  ]);
  assert.equal(node.actor, "林小满");
  assert.equal(node.actorRole, "旅行者");
  assert.equal(node.note, "带两位老人出行");
  assert.equal(node.status, "PENDING");
});

test("缺失操作人姓名或时间时不留空白单元格", () => {
  const [node] = timelineNodes([
    event(2, null, "PENDING", {
      actorName: "",
      createdAt: "",
    }),
  ]);
  assert.equal(node.actor, "系统");
  assert.equal(node.time, "—");
  assert.equal(eventTime("2026-01-02T03:04:05"), "2026.01.02 03:04");
});

test("没有留痕时返回空数组，由组件显示空态而不是渲染空列表", () => {
  assert.deepEqual(timelineNodes([]), []);
  assert.deepEqual(timelineNodes(undefined), []);
});
