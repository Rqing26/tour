import assert from "node:assert/strict";
import { test } from "node:test";
import {
  CHART_HEIGHT,
  CHART_PAD_X,
  CHART_PAD_Y,
  CHART_WIDTH,
  areaPath,
  axisTicks,
  barRects,
  compactAmount,
  hasData,
  linePath,
  linePoints,
  monthLabel,
  niceMax,
} from "../src/chart.ts";

test("Y 轴上限至少为 1，峰值向上取到整刻度", () => {
  // 全 0 时不能返回 0：后面每一处都要拿它当除数。
  assert.equal(niceMax([]), 1);
  assert.equal(niceMax([0, 0, 0]), 1);
  assert.equal(niceMax([1]), 1);
  assert.equal(niceMax([3]), 5);
  assert.equal(niceMax([7]), 10);
  assert.equal(niceMax([12]), 20);
  assert.equal(niceMax([100]), 100);
});

test("刻度从上限到 0 等分，中间的取整", () => {
  assert.deepEqual(axisTicks(5), [5, 3, 0]);
  assert.deepEqual(axisTicks(10), [10, 5, 0]);
  assert.deepEqual(axisTicks(1), [1, 1, 0]);
});

test("折线首尾贴住绘图区左右边界，最高点贴顶、0 值贴底", () => {
  const points = linePoints([0, 2, 4], 4);
  assert.equal(points.length, 3);
  assert.equal(points[0].x, CHART_PAD_X);
  assert.equal(points[2].x, CHART_WIDTH - CHART_PAD_X);
  assert.equal(points[1].x, CHART_WIDTH / 2);
  assert.equal(points[0].y, CHART_HEIGHT - CHART_PAD_Y);
  assert.equal(points[2].y, CHART_PAD_Y);
  // 中点的值是一半，纵坐标也落在绘图区正中间。
  assert.equal(points[1].y, CHART_PAD_Y + (CHART_HEIGHT - CHART_PAD_Y * 2) / 2);
});

test("只有一个数据点时居中，不除以 0", () => {
  const points = linePoints([3], 5);
  assert.equal(points.length, 1);
  assert.equal(points[0].x, CHART_WIDTH / 2);
});

test("空序列不产出路径，正常序列按点连线", () => {
  assert.equal(linePath([]), "");
  const path = linePath([
    { x: 0, y: 10, value: 1 },
    { x: 5, y: 20, value: 2 },
  ]);
  assert.equal(path, "M 0 10 L 5 20");
});

test("面积路径从折线末端落回基线再闭合，空序列同样返回空串", () => {
  assert.equal(areaPath([]), "");
  const filled = areaPath(
    [
      { x: 10, y: 30, value: 2 },
      { x: 40, y: 50, value: 1 },
    ],
    100,
  );
  assert.equal(filled, "M 10 30 L 40 50 L 40 100 L 10 100 Z");
});

test("柱状等宽且互不重叠，最高柱贴顶、0 值高度为 0", () => {
  const bars = barRects([0, 5000], 5000);
  assert.equal(bars.length, 2);
  assert.equal(bars[0].width, bars[1].width);
  assert.equal(bars[0].height, 0);
  assert.equal(bars[1].height, CHART_HEIGHT - CHART_PAD_Y * 2);
  assert.equal(bars[1].y, CHART_PAD_Y);
  assert.ok(bars[1].x > bars[0].x + bars[0].width, "相邻柱之间要留缝");
  assert.ok(bars[1].x + bars[1].width <= CHART_WIDTH - CHART_PAD_X, "最后一根不能出界");
});

test("金额缩写：一万以下取整，一万以上按万显示", () => {
  assert.equal(compactAmount(0), "0");
  assert.equal(compactAmount(9999), "9999");
  assert.equal(compactAmount(10000), "1万");
  assert.equal(compactAmount(12000), "1.2万");
  assert.equal(compactAmount(120000), "12万");
  assert.equal(compactAmount(2000000), "200万");
});

test("月份只留数字，空数据判定同时看订单与营收", () => {
  assert.equal(monthLabel("2026-03"), "3月");
  assert.equal(monthLabel("2026-12"), "12月");
  assert.equal(hasData([]), false);
  assert.equal(
    hasData([
      { month: "2026-01", orders: 0, revenue: 0 },
      { month: "2026-02", orders: 0, revenue: 0 },
    ]),
    false,
  );
  assert.equal(hasData([{ month: "2026-01", orders: 1, revenue: 0 }]), true);
  assert.equal(hasData([{ month: "2026-01", orders: 0, revenue: 1200 }]), true);
});
