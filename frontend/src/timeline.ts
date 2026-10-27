// 显式带 .ts 后缀：node --experimental-strip-types 直接跑测试时不做后缀补全。
import { dateLabel, roleLabels, statusLabels } from "./assets.ts";
import type { OrderEvent, OrderStatus } from "./types.ts";

/** 时间线一个节点：把后端留痕翻译成界面文案，组件只负责画。 */
export interface TimelineNode {
  id: number;
  status: OrderStatus;
  title: string;
  transition: string;
  actor: string;
  actorRole: string;
  note: string;
  time: string;
}

/** 留痕的"来源→目标"组合有限且稳定（见订单状态机），逐条给出动作名称。 */
const actions: Record<string, string> = {
  "none|PENDING": "客户提交预订",
  "PENDING|ASSIGNED": "顾问接单",
  "ASSIGNED|PENDING": "退回待接池",
  "ASSIGNED|COMPLETED": "旅程完成",
  "PENDING|CANCELLED": "客户取消预订",
  "ASSIGNED|CANCELLED": "客户取消预订",
};

/** 来源为空表示下单建档，此时还没有上一状态。 */
export const transitionTitle = (from: OrderStatus | null, to: OrderStatus) =>
  actions[`${from ?? "none"}|${to}`] ?? "订单状态变更";

export const transitionPath = (from: OrderStatus | null, to: OrderStatus) =>
  `${from ? statusLabels[from] : "新建订单"} → ${statusLabels[to]}`;

export const eventTime = (value: string) => {
  if (!value) return "—";
  const clock = value.slice(11, 16);
  return clock ? `${dateLabel(value.slice(0, 10))} ${clock}` : dateLabel(value);
};

/**
 * 后端已按发生顺序返回，这里再排一次：同一时刻的多条按自增 id 兜底，
 * 保证刷新前后节点顺序不会跳。
 */
export function timelineNodes(events: OrderEvent[] = []): TimelineNode[] {
  return [...events]
    .sort((a, b) =>
      a.createdAt === b.createdAt
        ? a.id - b.id
        : a.createdAt.localeCompare(b.createdAt),
    )
    .map((event) => ({
      id: event.id,
      status: event.toStatus,
      title: transitionTitle(event.fromStatus, event.toStatus),
      transition: transitionPath(event.fromStatus, event.toStatus),
      actor: event.actorName || "系统",
      actorRole: roleLabels[event.actorRole] ?? event.actorRole,
      note: event.note ?? "",
      time: eventTime(event.createdAt),
    }));
}
