import type { MonthlyReport } from "../types";
import {
  CHART_HEIGHT,
  CHART_PAD_X,
  CHART_PAD_Y,
  CHART_WIDTH,
  areaPath,
  axisTicks,
  barRects,
  compactAmount,
  hasData,
  linePath,
  linePoints,
  monthLabel,
  niceMax,
} from "../chart";
import "./chart.css";

const PLOT_HEIGHT = CHART_HEIGHT - CHART_PAD_Y * 2;

/**
 * 报表图表：订单走折线、营收走柱状，全部手写 SVG（C11 不引图表库）。
 *
 * <p>坐标计算在 `chart.ts`，这里只把算好的点与矩形铺进 SVG；空窗口直接给空态，
 * 全 0 的折线贴在基线上，看起来像故障而不是"没有数据"。
 */
export default function RevenueChart({
  monthly,
}: {
  monthly: MonthlyReport[];
}) {
  if (!hasData(monthly))
    return (
      <section className="panel report-chart-empty" role="status">
        <h2>趋势图表</h2>
        <p>
          这个窗口内还没有订单。第一笔预订进来后，这里会出现订单与营收趋势。
        </p>
      </section>
    );
  const orderMax = niceMax(monthly.map((month) => month.orders));
  const revenueMax = niceMax(monthly.map((month) => month.revenue));
  const points = linePoints(
    monthly.map((month) => month.orders),
    orderMax,
  );
  const bars = barRects(
    monthly.map((month) => month.revenue),
    revenueMax,
  );
  // 24 个月的柱顶标签会挤成一片，只在 12 个月内标注数值。
  const showValues = monthly.length <= 12;
  const months = (
    <div className="chart-months">
      {monthly.map((month) => (
        <span key={month.month}>{monthLabel(month.month)}</span>
      ))}
    </div>
  );
  return (
    <div className="report-charts">
      <figure className="panel report-chart">
        <figcaption className="panel-heading">
          <div>
            <h2>订单趋势</h2>
            <p>近 {monthly.length} 个月的订单量</p>
          </div>
          <span className="legend">
            <i />
            订单数
          </span>
        </figcaption>
        <div className="report-chart-frame">
          <div className="chart-y-labels" aria-hidden="true">
            {axisTicks(orderMax).map((tick, index) => (
              <span key={index}>{tick}</span>
            ))}
          </div>
          <div className="chart-plot">
            <svg
              viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
              role="img"
              aria-label={monthly
                .map((month) => `${month.month}：${month.orders} 笔订单`)
                .join("；")}
            >
              <defs>
                <linearGradient
                  id="report-orders-fill"
                  x1="0"
                  y1="0"
                  x2="0"
                  y2="1"
                >
                  <stop
                    offset="0%"
                    stopColor="var(--primary)"
                    stopOpacity=".18"
                  />
                  <stop
                    offset="100%"
                    stopColor="var(--primary)"
                    stopOpacity=".02"
                  />
                </linearGradient>
              </defs>
              {axisTicks(orderMax).map((_, index) => (
                <line
                  key={index}
                  x1={CHART_PAD_X}
                  y1={CHART_PAD_Y + (index * PLOT_HEIGHT) / 2}
                  x2={CHART_WIDTH - CHART_PAD_X}
                  y2={CHART_PAD_Y + (index * PLOT_HEIGHT) / 2}
                  stroke="var(--line)"
                  strokeDasharray="4 6"
                />
              ))}
              <path d={areaPath(points)} fill="url(#report-orders-fill)" />
              <path
                d={linePath(points)}
                fill="none"
                stroke="var(--primary)"
                strokeWidth="2.6"
                strokeLinejoin="round"
                strokeLinecap="round"
              />
              {points.map((point, index) => (
                <g key={monthly[index].month}>
                  <title>
                    {monthly[index].month}：{point.value} 笔
                  </title>
                  <circle
                    cx={point.x}
                    cy={point.y}
                    r="4.5"
                    fill="#fff"
                    stroke="var(--primary)"
                    strokeWidth="2.4"
                  />
                  {showValues && (
                    <text
                      x={point.x}
                      y={Math.max(12, point.y - 12)}
                      textAnchor="middle"
                      fill="var(--muted)"
                      fontSize="11"
                    >
                      {point.value}
                    </text>
                  )}
                </g>
              ))}
            </svg>
            {months}
          </div>
        </div>
      </figure>
      <figure className="panel report-chart">
        <figcaption className="panel-heading">
          <div>
            <h2>营收趋势</h2>
            <p>不含已取消订单</p>
          </div>
          <span className="legend legend-bar">
            <i />
            营收（元）
          </span>
        </figcaption>
        <div className="report-chart-frame">
          <div className="chart-y-labels" aria-hidden="true">
            {axisTicks(revenueMax).map((tick, index) => (
              <span key={index}>{compactAmount(tick)}</span>
            ))}
          </div>
          <div className="chart-plot">
            <svg
              viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
              role="img"
              aria-label={monthly
                .map(
                  (month) =>
                    `${month.month}：营收 ${compactAmount(month.revenue)} 元`,
                )
                .join("；")}
            >
              {axisTicks(revenueMax).map((_, index) => (
                <line
                  key={index}
                  x1={CHART_PAD_X}
                  y1={CHART_PAD_Y + (index * PLOT_HEIGHT) / 2}
                  x2={CHART_WIDTH - CHART_PAD_X}
                  y2={CHART_PAD_Y + (index * PLOT_HEIGHT) / 2}
                  stroke="var(--line)"
                  strokeDasharray="4 6"
                />
              ))}
              {bars.map((bar, index) => (
                <g key={monthly[index].month}>
                  <title>
                    {monthly[index].month}：¥ {compactAmount(bar.value)}
                  </title>
                  <rect
                    className="report-bar"
                    x={bar.x}
                    y={bar.y}
                    width={bar.width}
                    height={bar.height}
                    rx="5"
                  />
                  {showValues && bar.value > 0 && (
                    <text
                      x={bar.x + bar.width / 2}
                      y={Math.max(12, bar.y - 8)}
                      textAnchor="middle"
                      fill="var(--muted)"
                      fontSize="11"
                    >
                      {compactAmount(bar.value)}
                    </text>
                  )}
                </g>
              ))}
            </svg>
            {months}
          </div>
        </div>
      </figure>
    </div>
  );
}
