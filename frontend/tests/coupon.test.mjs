import assert from "node:assert/strict";
import test from "node:test";
import {
  bookingPayload,
  normalizeCouponCode,
  savingsLabel,
} from "../src/coupon.ts";

const draft = {
  routeId: 7,
  travelerCount: 2,
  contactName: "张明",
  contactPhone: "13800000000",
  remark: "靠窗",
};

test("码去空格并转大写，与后端快照同一形态", () => {
  assert.equal(normalizeCouponCode("  autumn8 "), "AUTUMN8");
  assert.equal(normalizeCouponCode("Save-20"), "SAVE-20");
});

test("空输入表示不使用优惠码", () => {
  assert.equal(normalizeCouponCode(""), undefined);
  assert.equal(normalizeCouponCode("   "), undefined);
});

test("不填码时请求体不带 couponCode 键", () => {
  const payload = bookingPayload(draft, "  ");
  assert.deepEqual(payload, draft);
  assert.equal("couponCode" in payload, false);
});

test("填了码时保留原有字段并追加归一后的码", () => {
  const payload = bookingPayload(draft, " autumn8 ");
  assert.equal(payload.couponCode, "AUTUMN8");
  assert.equal(payload.routeId, 7);
  assert.equal(payload.travelerCount, 2);
  assert.equal(payload.contactName, "张明");
});

test("有减免时说明用哪张码减了多少", () => {
  assert.equal(savingsLabel("AUTUMN8", 20), "优惠码 AUTUMN8 已减 ¥20.00");
  assert.equal(savingsLabel("AUTUMN8", 1.5), "优惠码 AUTUMN8 已减 ¥1.50");
});

test("零减免或没有减免金额时不显示这一行", () => {
  assert.equal(savingsLabel(null, 0), "");
  assert.equal(savingsLabel("AUTUMN8", 0), "");
  assert.equal(savingsLabel(null, null), "");
  assert.equal(savingsLabel(null, undefined), "");
});
