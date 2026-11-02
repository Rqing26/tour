import { coverPath, fallbackImageKey } from "./routeImage.ts";
import type { Route } from "./types.ts";

export { imageKeys, imageLabels } from "./routeImage.ts";

/** 内置封面的固定地址，用于登录页、工作台这类没有线路对象的位置。 */
export const photo = (key: string) =>
  `${import.meta.env.BASE_URL}images/${fallbackImageKey(key)}.jpg`;

/** 线路封面：上传图优先、内置键回退（C7）。 */
export const cover = (route: Route) =>
  coverPath(route, import.meta.env.BASE_URL);
export const money = (amount: number) =>
  new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 2 }).format(amount);
export const dateLabel = (date: string) =>
  date ? date.slice(0, 10).replaceAll("-", ".") : "—";
export const roleLabels = {
  ADMIN: "管理员",
  EMPLOYEE: "旅行顾问",
  CUSTOMER: "旅行者",
};
export const statusLabels = {
  PENDING: "待接单",
  ASSIGNED: "服务中",
  COMPLETED: "已完成",
  CANCELLED: "已取消",
};
