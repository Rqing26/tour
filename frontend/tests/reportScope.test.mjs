import assert from "node:assert/strict";
import { test } from "node:test";
import {
  REPORT_WINDOWS,
  canViewReports,
  reportsPath,
  revenueTotal,
  scopeNote,
  scopeTitle,
  windowLabel,
} from "../src/reportQuery.ts";

test("报表路径只有月份一个参数，默认窗口是 6 个月", () => {
  assert.equal(reportsPath(6), "/reports/summary?months=6");
  assert.equal(reportsPath(12), "/reports/summary?months=12");
  assert.equal(reportsPath(24), "/reports/summary?months=24");
  assert.deepEqual([...REPORT_WINDOWS], [6, 12, 24]);
});

test("月份先归一再发出去，不把非法窗口丢给后端", () => {
  assert.equal(reportsPath(0), "/reports/summary?months=1");
  assert.equal(reportsPath(-3), "/reports/summary?months=1");
  assert.equal(reportsPath(99), "/reports/summary?months=24");
  assert.equal(reportsPath(6.4), "/reports/summary?months=6");
});

test("标题与说明必须把口径写清楚，员工不能误以为是全站数字", () => {
  assert.equal(scopeTitle("ADMIN"), "全站经营报表");
  assert.equal(scopeTitle("EMPLOYEE"), "我的服务报表");
  assert.equal(scopeTitle("CUSTOMER"), "我的消费报表");
  assert.notEqual(scopeNote("ADMIN"), scopeNote("EMPLOYEE"));
  assert.match(scopeNote("EMPLOYEE"), /名下/);
  assert.match(scopeNote("ADMIN"), /全站/);
});

test("只有管理员与员工有报表入口，客户看到的是自己的预订列表", () => {
  assert.equal(canViewReports("ADMIN"), true);
  assert.equal(canViewReports("EMPLOYEE"), true);
  assert.equal(canViewReports("CUSTOMER"), false);
});

test("营收合计按月度行累加，空窗口为 0 而不是 NaN", () => {
  assert.equal(revenueTotal([]), 0);
  assert.equal(
    revenueTotal([
      { month: "2026-04", orders: 1, revenue: 1200.5 },
      { month: "2026-05", orders: 0, revenue: 0 },
      { month: "2026-06", orders: 2, revenue: 2400 },
    ]),
    3600.5,
  );
});

test("窗口文案跟着选择走", () => {
  assert.equal(windowLabel(6), "近 6 个月");
  assert.equal(windowLabel(24), "近 24 个月");
});
