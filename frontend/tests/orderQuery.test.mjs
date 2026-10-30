import assert from "node:assert/strict";
import { test } from "node:test";
import {
  ORDER_PAGE_SIZE,
  clampPage,
  pageCount,
  pagedOrdersPath,
  rangeLabel,
} from "../src/orderQuery.ts";

test("带分页参数才是信封请求，页码与页大小必须显式写进查询串", () => {
  const path = pagedOrdersPath({}, 0, ORDER_PAGE_SIZE);
  const params = new URLSearchParams(path.split("?")[1]);
  assert.equal(path.startsWith("/orders?"), true);
  assert.equal(params.get("page"), "0");
  assert.equal(params.get("size"), "10");
  // 不发空条件，避免后端把空串当成一次真实的过滤。
  assert.equal(params.has("q"), false);
  assert.equal(params.has("status"), false);
});

test("关键词与状态一起下推，中文和空格都要编码", () => {
  const params = new URLSearchParams(
    pagedOrdersPath({ q: "  林小满  ", status: "PENDING" }, 2, 5).split("?")[1],
  );
  assert.equal(params.get("q"), "林小满");
  assert.equal(params.get("status"), "PENDING");
  assert.equal(params.get("page"), "2");
  assert.equal(params.get("size"), "5");
});

test("负页码按 0 处理，不让请求带着非法偏移量出去", () => {
  const params = new URLSearchParams(pagedOrdersPath({}, -3, 10).split("?")[1]);
  assert.equal(params.get("page"), "0");
});

test("总页数向上取整，空结果也算一页", () => {
  assert.equal(pageCount(0, 10), 1);
  assert.equal(pageCount(10, 10), 1);
  assert.equal(pageCount(11, 10), 2);
  assert.equal(pageCount(23, 10), 3);
});

test("总数变小后页码拉回最后一页", () => {
  assert.equal(clampPage(5, 23, 10), 2);
  assert.equal(clampPage(-1, 23, 10), 0);
  assert.equal(clampPage(0, 0, 10), 0);
});

test("页脚显示当前区间，且不超过总数", () => {
  assert.equal(rangeLabel(23, 0, 10), "第 1–10 笔 / 共 23 笔");
  assert.equal(rangeLabel(23, 2, 10), "第 21–23 笔 / 共 23 笔");
  assert.equal(rangeLabel(0, 0, 10), "共 0 笔订单");
});
