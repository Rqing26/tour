import {
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  Check,
  Clock3,
  MapPin,
  Ticket,
  UsersRound,
  Wallet,
} from "lucide-react";
import type { DashboardData, User, View } from "../types";
import { useResource } from "../useResource";
import { dateLabel, money, photo } from "../assets";
import {
  Empty,
  ErrorState,
  SectionHeading,
  Spinner,
  StatusBadge,
  TextLink,
} from "./UI";

export default function Dashboard({
  user,
  navigate,
}: {
  user: User;
  navigate: (view: View) => void;
}) {
  const { data, loading, error, refresh } =
    useResource<DashboardData>("/dashboard");
  if (loading) return <Spinner />;
  if (error || !data)
    return <ErrorState message={error || "加载失败"} retry={refresh} />;
  const isCustomer = user.role === "CUSTOMER";
  const isStaff = user.role === "EMPLOYEE";
  const hour = new Date().getHours();
  const greeting = hour < 12 ? "上午好" : hour < 18 ? "下午好" : "晚上好";
  const metrics = [
    {
      label: isCustomer ? "我的预订" : "订单总数",
      value: String(data.totalOrders),
      unit: "笔",
      hint: `${data.completedOrders} 笔已完成`,
      icon: Ticket,
      color: "green",
    },
    {
      label: "待接订单",
      value: String(data.pendingOrders),
      unit: "笔",
      hint: isStaff ? "挑选订单，开始新的服务" : "等待旅行顾问接单",
      icon: Clock3,
      color: "amber",
    },
    {
      label: isCustomer ? "预订总额" : "订单总额",
      value: `¥ ${money(data.totalAmount)}`,
      unit: "",
      hint: "不含已取消订单",
      icon: Wallet,
      color: "blue",
    },
    {
      label: isCustomer ? "正在服务" : isStaff ? "已完成服务" : "在售线路",
      value: String(
        isCustomer
          ? data.assignedOrders
          : isStaff
            ? data.completedOrders
            : data.activeRoutes,
      ),
      unit: isCustomer || isStaff ? "笔" : "条",
      hint: isCustomer
        ? "旅行顾问正在跟进行程"
        : isStaff
          ? "每一次服务，积累一份信任"
          : `${data.customerCount} 位旅行者 · ${data.employeeCount} 位顾问`,
      icon: isStaff ? Check : UsersRound,
      color: "purple",
    },
  ];
  return (
    <>
      <SectionHeading
        eyebrow="OVERVIEW"
        title={`${greeting}，${user.displayName}`}
        description={
          isCustomer
            ? "远方的风景，正在等待你的下一次出发。"
            : "新的一天，从井然有序的旅程开始。"
        }
        action={
          <button
            className="btn btn-primary"
            onClick={() => navigate(isCustomer ? "routes" : "orders")}
          >
            {isCustomer ? "探索下一段旅程" : "查看订单"}
            <ArrowUpRight size={17} />
          </button>
        }
      />
      <section className="metrics-grid" aria-label="业务概况">
        {metrics.map((metric, index) => (
          <article
            className={`metric-card ${index === 0 ? "metric-featured" : ""}`}
            key={metric.label}
          >
            <div className="metric-top">
              <span>{metric.label}</span>
              <span className={`metric-icon metric-icon-${metric.color}`}>
                <metric.icon size={19} />
              </span>
            </div>
            <div className="metric-value">
              {metric.value}
              <small>{metric.unit}</small>
            </div>
            <p>
              {index === 0 ? (
                <span className="metric-hint-check">
                  <Check size={13} />
                </span>
              ) : null}
              {metric.hint}
            </p>
          </article>
        ))}
      </section>
      <div className="dashboard-middle">
        <section className="panel trend-panel">
          <div className="panel-heading">
            <div>
              <h2>{isCustomer ? "我的出行足迹" : "订单趋势"}</h2>
              <p>近 6 个月的订单数量</p>
            </div>
            <span className="legend">
              <i />
              订单数
            </span>
          </div>
          <TrendChart months={data.monthlyOrders} />
          <div className="chart-summary">
            <span>
              已完成订单金额 <strong>¥ {money(data.completedAmount)}</strong>
            </span>
            <span className="muted">按下单时间统计</span>
          </div>
        </section>
        <section
          className="journey-feature"
          style={{ backgroundImage: `url(${photo("yunnan")})` }}
        >
          <div className="journey-feature-top">
            <span className="small-label">A LITTLE INSPIRATION</span>
            <ArrowUpRight size={22} />
          </div>
          <div className="journey-feature-bottom">
            <p>
              <MapPin size={14} /> 向往的远方
            </p>
            <h2>
              下一站，
              <br />
              遇见山海。
            </h2>
            <button onClick={() => navigate("routes")}>
              发现旅行线路
              <ArrowRight size={17} />
            </button>
          </div>
        </section>
      </div>
      <div className="dashboard-bottom">
        <section className="panel recent-panel">
          <div className="panel-heading">
            <div>
              <h2>{isCustomer ? "最近的预订" : "最新订单"}</h2>
              <p>每一段旅程的最新动态</p>
            </div>
            <TextLink onClick={() => navigate("orders")}>查看全部</TextLink>
          </div>
          {data.recentOrders.length ? (
            <div className="table-scroll">
              <table className="data-table compact-table">
                <thead>
                  <tr>
                    <th>旅行线路 / 订单号</th>
                    <th>{isCustomer ? "出发日期" : "旅行者"}</th>
                    <th>订单金额</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  {data.recentOrders.slice(0, 5).map((order) => (
                    <tr key={order.id}>
                      <td>
                        <strong>{order.routeName}</strong>
                        <small>{order.orderNo}</small>
                      </td>
                      <td>
                        {isCustomer
                          ? dateLabel(order.startDate)
                          : order.customerName}
                        <small>{order.travelerCount} 人出行</small>
                      </td>
                      <td className="numeric">¥ {money(order.totalAmount)}</td>
                      <td>
                        <StatusBadge status={order.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <Empty
              title="还没有订单"
              description={
                isCustomer
                  ? "找一条心仪的线路，开始记录你的旅程。"
                  : "新订单会显示在这里。"
              }
              action={
                isCustomer ? (
                  <button
                    className="btn btn-secondary"
                    onClick={() => navigate("routes")}
                  >
                    去探索线路
                  </button>
                ) : undefined
              }
            />
          )}
        </section>
        <section className="panel popular-panel">
          <div className="panel-heading">
            <div>
              <h2>{isCustomer ? "我的心选目的地" : "热门目的地"}</h2>
              <p>按订单数量排序</p>
            </div>
            <ArrowDownRight size={19} />
          </div>
          {data.popularRoutes.length ? (
            <div className="popular-list">
              {data.popularRoutes.slice(0, 4).map((route, index) => (
                <button
                  key={route.routeId}
                  className="popular-item"
                  onClick={() => navigate("routes")}
                >
                  <span className="popular-rank">
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  <span className="popular-content">
                    <strong>{route.name}</strong>
                    <small>
                      <MapPin size={12} />
                      {route.destination} · {route.travelerCount} 人出行
                    </small>
                  </span>
                  <span className="popular-count">
                    {route.orderCount}
                    <small>笔订单</small>
                  </span>
                </button>
              ))}
            </div>
          ) : (
            <Empty
              title="期待第一段旅程"
              description="产生订单后，即可在此查看热门目的地。"
            />
          )}
          <div className="popular-footer">
            <span className="status-dot" />
            根据当前可见订单实时统计
          </div>
        </section>
      </div>
    </>
  );
}

function TrendChart({ months }: { months: DashboardData["monthlyOrders"] }) {
  const width = 650,
    height = 184,
    padX = 25,
    padY = 17;
  const max = Math.max(4, ...months.map((month) => month.count));
  const points = months.map((month, index) => ({
    x: padX + (index * (width - padX * 2)) / Math.max(1, months.length - 1),
    y: height - padY - (month.count / max) * (height - padY * 2),
    ...month,
  }));
  const line = points
    .map((point, index) => `${index ? "L" : "M"} ${point.x} ${point.y}`)
    .join(" ");
  const area = points.length
    ? `${line} L ${points[points.length - 1].x} ${height - padY} L ${padX} ${height - padY} Z`
    : "";
  return (
    <div className="trend-chart">
      <div className="chart-y-labels">
        {[max, Math.round(max / 2), 0].map((value, index) => (
          <span key={index}>{value}</span>
        ))}
      </div>
      <div className="chart-plot">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          role="img"
          aria-label={months
            .map((month) => `${month.month}：${month.count} 笔订单`)
            .join("；")}
        >
          <defs>
            <linearGradient id="chart-fill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#42796c" stopOpacity=".16" />
              <stop offset="100%" stopColor="#42796c" stopOpacity=".015" />
            </linearGradient>
          </defs>
          {[padY, height / 2, height - padY].map((y) => (
            <line
              key={y}
              x1={padX}
              y1={y}
              x2={width - padX}
              y2={y}
              stroke="#e9eeea"
              strokeDasharray="4 6"
            />
          ))}
          <path d={area} fill="url(#chart-fill)" />
          <path
            d={line}
            fill="none"
            stroke="#397969"
            strokeWidth="2.7"
            strokeLinejoin="round"
            strokeLinecap="round"
          />
          {points.map((point) => (
            <g key={point.month}>
              <title>
                {point.month}：{point.count} 笔
              </title>
              <circle
                cx={point.x}
                cy={point.y}
                r="5"
                fill="#fff"
                stroke="#397969"
                strokeWidth="2.5"
              />
              <text
                x={point.x}
                y={Math.max(12, point.y - 13)}
                textAnchor="middle"
                fill="#426254"
                fontSize="12"
              >
                {point.count}
              </text>
            </g>
          ))}
        </svg>
        <div className="chart-months">
          {months.map((month) => (
            <span key={month.month}>{Number(month.month.slice(5))}月</span>
          ))}
        </div>
      </div>
    </div>
  );
}
