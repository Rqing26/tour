/**
 * 报表图表的几何计算。
 *
 * <p>项目不引图表库（C11），折线与柱状都由 SVG 手写，因此"数值 → 坐标"这一步必须能从组件里拿出来单独验证：
 * 组件里只剩渲染，几何错误在 `node --test` 里就能拦住。
 */

export const CHART_WIDTH = 660;
export const CHART_HEIGHT = 220;
export const CHART_PAD_X = 36;
export const CHART_PAD_Y = 26;

export interface ChartPoint {
  x: number;
  y: number;
  value: number;
}

export interface BarRect {
  x: number;
  y: number;
  width: number;
  height: number;
  value: number;
}

/** Y 轴上限：至少 1（它同时是除数），峰值向上取到 1/2/5/10 这类整刻度，标签才好读。 */
export function niceMax(values: number[]): number {
  const peak = Math.max(0, ...values);
  if (peak <= 0) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(peak));
  const scaled = peak / magnitude;
  const factor = scaled <= 1 ? 1 : scaled <= 2 ? 2 : scaled <= 5 ? 5 : 10;
  return magnitude * factor;
}

/** 纵轴三档刻度：上限、中值、0；中值取整，避免出现 2.5 笔订单。 */
export function axisTicks(max: number): number[] {
  return [max, Math.round(max / 2), 0];
}

/**
 * 折线各点坐标：横向等距铺满绘图区，纵向按占比反推（SVG 的 y 轴朝下）。
 *
 * <p>只有一个月时居中放一个点，否则 `length - 1` 会变成 0 除数。
 */
export function linePoints(
  values: number[],
  max: number,
  width = CHART_WIDTH,
  height = CHART_HEIGHT,
): ChartPoint[] {
  const plotWidth = width - CHART_PAD_X * 2;
  const plotHeight = height - CHART_PAD_Y * 2;
  const safeMax = Math.max(max, 1);
  return values.map((value, index) => ({
    x:
      values.length === 1
        ? width / 2
        : CHART_PAD_X + (index * plotWidth) / (values.length - 1),
    y:
      height -
      CHART_PAD_Y -
      (Math.min(Math.max(value, 0), safeMax) / safeMax) * plotHeight,
    value,
  }));
}

export const linePath = (points: ChartPoint[]): string =>
  points.length
    ? points
        .map((point, index) => `${index ? "L" : "M"} ${point.x} ${point.y}`)
        .join(" ")
    : "";

/** 折线下方的填充：从末点落到基线，回到首点再闭合。 */
export function areaPath(
  points: ChartPoint[],
  baseline = CHART_HEIGHT - CHART_PAD_Y,
): string {
  if (!points.length) return "";
  const first = points[0];
  const last = points[points.length - 1];
  return `${linePath(points)} L ${last.x} ${baseline} L ${first.x} ${baseline} Z`;
}

/** 柱状：每根柱子占一个等宽的槽，柱宽取槽宽的 52%，剩下的留作间隔。 */
export function barRects(
  values: number[],
  max: number,
  width = CHART_WIDTH,
  height = CHART_HEIGHT,
): BarRect[] {
  const plotWidth = width - CHART_PAD_X * 2;
  const plotHeight = height - CHART_PAD_Y * 2;
  const baseline = height - CHART_PAD_Y;
  const slot = plotWidth / Math.max(values.length, 1);
  const barWidth = slot * 0.52;
  const safeMax = Math.max(max, 1);
  return values.map((value, index) => {
    const barHeight =
      (Math.min(Math.max(value, 0), safeMax) / safeMax) * plotHeight;
    return {
      x: CHART_PAD_X + slot * index + (slot - barWidth) / 2,
      y: baseline - barHeight,
      width: barWidth,
      height: barHeight,
      value,
    };
  });
}

/** 金额缩写：一万以下按元取整，一万以上换成"万"，轴标签与柱顶才放得下。 */
export function compactAmount(value: number): string {
  const amount = Math.max(value, 0);
  if (amount >= 10000) {
    const wan = amount / 10000;
    const text = wan >= 100 ? wan.toFixed(0) : wan.toFixed(1);
    return `${text.replace(/\.0$/, "")}万`;
  }
  return String(Math.round(amount));
}

export const monthLabel = (month: string): string =>
  `${Number(month.slice(5))}月`;

/** 订单与营收都为 0 的窗口不必画图，直接给空态：全 0 折线看起来像故障。 */
export const hasData = (
  monthly: { orders: number; revenue: number }[],
): boolean => monthly.some((month) => month.orders > 0 || month.revenue > 0);
