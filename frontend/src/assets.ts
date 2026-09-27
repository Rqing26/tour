export const imageKeys = [
  "guilin",
  "yunnan",
  "xinjiang",
  "chengdu",
  "xiamen",
  "hangzhou",
];
export const photo = (key: string) =>
  `${import.meta.env.BASE_URL}images/${imageKeys.includes(key) ? key : "guilin"}.jpg`;
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
