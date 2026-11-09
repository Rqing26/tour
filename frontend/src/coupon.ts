/**
 * 优惠码的输入与展示规则。
 *
 * 放在这里而不是直接写进组件，是因为 `node --test` 跑不了 TSX：把"填了什么码、请求带不带这个键、
 * 减免怎么写成一句话"抽成纯函数，才能脱离浏览器验证。
 */
export interface BookingDraft {
  routeId: number;
  travelerCount: number;
  contactName: string;
  contactPhone: string;
  remark: string;
}

/** 输入归一：去空格并转大写；空串表示不使用优惠码。 */
export function normalizeCouponCode(raw: string): string | undefined {
  const code = raw.trim().toUpperCase();
  return code ? code : undefined;
}

/**
 * 下单请求体：填了码才加 `couponCode` 键。
 *
 * 空串不发出去——后端把空白当"没填"处理，但少传一个键能让请求体本身就说清意图，
 * 也让旧调用方的形状保持不变。
 */
export function bookingPayload(
  draft: BookingDraft,
  raw: string,
): BookingDraft & { couponCode?: string } {
  const code = normalizeCouponCode(raw);
  return code ? { ...draft, couponCode: code } : draft;
}

/** 订单减免文案：没有减免时返回空串，页面据此不渲染这一行。 */
export function savingsLabel(
  code: string | null | undefined,
  discount: number | null | undefined,
): string {
  if (!discount || discount <= 0) return "";
  const source = code ? `优惠码 ${code}` : "优惠";
  return `${source} 已减 ¥${discount.toFixed(2)}`;
}
