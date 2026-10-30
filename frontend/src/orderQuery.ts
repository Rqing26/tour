/**
 * 订单列表的服务端分页参数。
 *
 * <p>列表的分页窗口由数据库切，前端只负责把页码、页大小和过滤条件拼成查询串；
 * 顺手把"第几条到第几条"这类纯计算放这里，组件里只剩渲染。
 */
export const ORDER_PAGE_SIZE = 10;

/** 状态页签的取值：空串表示"全部"。计数与列表共用同一套路径构造，口径不会走偏。 */
export const ORDER_STATUS_TABS = [
  "",
  "PENDING",
  "ASSIGNED",
  "COMPLETED",
  "CANCELLED",
] as const;

export interface OrderFilter {
  q?: string;
  status?: string;
}

/**
 * 拼 `GET /api/orders` 的查询串。
 *
 * <p>只要带上 page 与 size，后端就返回 `{items,total,page,size}` 信封（见 docs/API.md），
 * 所以调用方拿到的一定是分页结果，不会误把数组当信封用。
 */
export function pagedOrdersPath(
  filter: OrderFilter,
  page: number,
  size: number,
): string {
  const params = new URLSearchParams();
  const keyword = filter.q?.trim();
  if (keyword) params.set("q", keyword);
  if (filter.status) params.set("status", filter.status);
  params.set("page", String(Math.max(page, 0)));
  params.set("size", String(size));
  return `/orders?${params.toString()}`;
}

/** 总页数：空结果也留一页，否则分页器会显示"第 1 / 0 页"。 */
export const pageCount = (total: number, size: number) =>
  Math.max(1, Math.ceil(Math.max(total, 0) / Math.max(size, 1)));

/** 页码从 0 开始。取消订单后总数变小，越界的页码要拉回最后一页，而不是停在空列表上。 */
export const clampPage = (page: number, total: number, size: number) =>
  Math.min(Math.max(page, 0), pageCount(total, size) - 1);

/** 页脚文案：让人一眼看出当前看的是哪一段，而不只是一个总数。 */
export function rangeLabel(total: number, page: number, size: number): string {
  if (total <= 0) return "共 0 笔订单";
  const from = page * size + 1;
  const to = Math.min(total, (page + 1) * size);
  return `第 ${from}–${to} 笔 / 共 ${total} 笔`;
}
