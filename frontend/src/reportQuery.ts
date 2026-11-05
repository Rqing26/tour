/**
 * 报表页的路径、口径文案与入口权限。
 *
 * <p>这三件事都只在页面上用一次，但都是"说错了会误导"的地方：窗口发错、把员工数字写成全站、
 * 给客户开一个看不懂的入口。抽成纯函数后可以脱离 DOM 单独测。
 */
export const REPORT_WINDOWS = [6, 12, 24] as const;

/** 月份窗口归一：与服务端同一区间（1–24），前端先压一次，不把非法参数发出去。 */
const normalizeMonths = (months: number) =>
  Math.min(Math.max(Math.round(months), 1), 24);

export const reportsPath = (months: number) =>
  `/reports/summary?months=${normalizeMonths(months)}`;

/** 报表标题：口径不同，标题必须不同，否则员工会把自己的业绩当成全站营收。 */
export function scopeTitle(scope: string): string {
  if (scope === "ADMIN") return "全站经营报表";
  if (scope === "EMPLOYEE") return "我的服务报表";
  if (scope === "CUSTOMER") return "我的消费报表";
  return "经营报表";
}

export function scopeNote(scope: string): string {
  if (scope === "ADMIN") return "全站订单口径，含团队接单排行。";
  if (scope === "EMPLOYEE")
    return "只统计已归属你名下的订单，待接池不计入业绩。";
  if (scope === "CUSTOMER") return "只统计你自己的订单。";
  return "统计范围由当前账号角色决定。";
}

/** 报表入口只对管理员与员工开放；客户看的是自己的预订列表，不需要经营视角。 */
export const canViewReports = (role: string) =>
  role === "ADMIN" || role === "EMPLOYEE";

export const windowLabel = (months: number) => `近 ${months} 个月`;

/** 月度营收合计：窗口内缺月已由服务端补 0，这里只做累加。 */
export const revenueTotal = (monthly: { revenue: number }[]) =>
  monthly.reduce((sum, month) => sum + month.revenue, 0);
