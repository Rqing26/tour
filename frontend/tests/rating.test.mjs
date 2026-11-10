import assert from "node:assert/strict";
import { test } from "node:test";
import { hasRating, ratingLabel, ratingText, reviewCountText, starFill } from "../src/rating.ts";

// 均分来自 GET /api/routes 的两个新增字段（C4）：avgRating 可为空，reviewCount 为 0 表示还没人评。
const rated = { avgRating: 4.3, reviewCount: 12 };
const untouched = { avgRating: null, reviewCount: 0 };

test("没有任何评价的线路显示“暂无评分”而不是 0 星", () => {
  assert.equal(hasRating(untouched), false);
  assert.equal(ratingText(untouched), "暂无评分");
  assert.equal(reviewCountText(untouched), "");
  assert.deepEqual([0, 1, 2, 3, 4].map((i) => starFill(untouched, i)), [0, 0, 0, 0, 0]);
});

test("字段缺失的旧响应也按“暂无评分”处理", () => {
  // 后端尚未升级、或旧缓存里没有这两个字段时，不能算出 NaN 颗星。
  assert.equal(hasRating({}), false);
  assert.equal(ratingText({}), "暂无评分");
});

test("有评价时给一位小数的均分与条数", () => {
  assert.equal(hasRating(rated), true);
  assert.equal(ratingText(rated), "4.3");
  assert.equal(reviewCountText(rated), "12 条评价");
  assert.equal(ratingText({ avgRating: 5, reviewCount: 1 }), "5.0");
});

test("星级按半颗星向上填：4.3 分是四颗满星加半颗", () => {
  assert.deepEqual(
    [0, 1, 2, 3, 4].map((i) => starFill(rated, i)),
    [1, 1, 1, 1, 0.5],
  );
  assert.deepEqual(
    [0, 1, 2, 3, 4].map((i) => starFill({ avgRating: 4, reviewCount: 3 }, i)),
    [1, 1, 1, 1, 0],
  );
  assert.deepEqual(
    [0, 1, 2, 3, 4].map((i) => starFill({ avgRating: 5, reviewCount: 3 }, i)),
    [1, 1, 1, 1, 1],
  );
});

test("均分与条数对不上时以条数为准", () => {
  // 只断条数为 0 的情况：均分有值但没人评过属于脏数据，显示成 0 星会误导。
  assert.equal(hasRating({ avgRating: 4.6, reviewCount: 0 }), false);
  assert.equal(ratingText({ avgRating: 4.6, reviewCount: 0 }), "暂无评分");
});

test("无障碍标签把均分与条数都读出来", () => {
  assert.equal(ratingLabel(rated), "平均 4.3 分，12 条评价");
  assert.equal(ratingLabel(untouched), "暂无评分");
});
