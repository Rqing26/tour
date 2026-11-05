import { useState } from "react";
import { Clock3, Ticket, TrendingUp, Wallet } from "lucide-react";
import type { ReportSummary, User } from "../types";
import { useResource } from "../useResource";
import { money } from "../assets";
import { Empty, ErrorState, SectionHeading, Spinner } from "./UI";
import RevenueChart from "./RevenueChart";
import {
  REPORT_WINDOWS,
  reportsPath,
  scopeNote,
  scopeTitle,
  windowLabel,
} from "../reportQuery.ts";

/**
 * 经营报表页。
 *
 * <p>数字全部来自 `GET /api/reports/summary`，口径由会话角色决定，页面只负责换窗口与展示；
 * 标题与说明跟着 `scope` 走，员工看到的是"我的"而不是全站。
 */
export default function Reports({ user }: { user: User }) {
  const [months, setMonths] = useState<number>(6);
  const { data, loading, error, refresh } = useResource<ReportSummary>(
    reportsPath(months),
  );
  const selector = (
    <div className="tabs" role="tablist" aria-label="统计窗口">
      {REPORT_WINDOWS.map((window) => (
        <button
          key={window}
          role="tab"
          aria-selected={months === window}
          className={months === window ? "active" : ""}
          onClick={() => setMonths(window)}
        >
          {window} 个月
        </button>
      ))}
    </div>
  );
  const heading = (
    <SectionHeading
      eyebrow="REPORTS"
      title={scopeTitle(data?.scope ?? user.role)}
      description={`${scopeNote(data?.scope ?? user.role)}当前窗口：${windowLabel(months)}。`}
      action={selector}
    />
  );
  if (loading)
    return (
      <>
        {heading}
        <Spinner text="正在汇总报表" />
      </>
    );
  if (error || !data)
    return (
      <>
        {heading}
        <ErrorState message={error || "加载失败"} retry={refresh} />
      </>
    );
  const inProgress = data.pendingOrders + data.assignedOrders;
  const metrics = [
    {
      label: "订单总量",
      value: String(data.totalOrders),
      unit: "笔",
      hint: `已完成 ${data.completedOrders} 笔 · 已取消 ${data.cancelledOrders} 笔`,
      icon: Ticket,
      color: "green",
    },
    {
      label: "进行中",
      value: String(inProgress),
      unit: "笔",
      hint: `待接 ${data.pendingOrders} 笔 · 服务中 ${data.assignedOrders} 笔`,
      icon: Clock3,
      color: "amber",
    },
    {
      label: "营收合计",
      value: `¥ ${money(data.totalAmount)}`,
      unit: "",
      hint: "不含已取消订单",
      icon: Wallet,
      color: "blue",
    },
    {
      label: "已完成金额",
      value: `¥ ${money(data.completedAmount)}`,
      unit: "",
      hint: "只算已完成订单",
      icon: TrendingUp,
      color: "purple",
    },
  ];
  return (
    <>
      {heading}
      <section className="metrics-grid" aria-label="报表概况">
        {metrics.map((metric) => (
          <article className="metric-card" key={metric.label}>
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
            <p>{metric.hint}</p>
          </article>
        ))}
      </section>
      <RevenueChart monthly={data.monthly} />
      <div className="dashboard-bottom">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <h2>热门线路</h2>
              <p>{windowLabel(months)}按未取消订单量排序</p>
            </div>
          </div>
          {data.popularRoutes.length ? (
            <div className="table-scroll">
              <table className="data-table compact-table">
                <thead>
                  <tr>
                    <th>排名</th>
                    <th>线路</th>
                    <th>订单数</th>
                    <th>出行人数</th>
                  </tr>
                </thead>
                <tbody>
                  {data.popularRoutes.map((route, index) => (
                    <tr key={route.routeId}>
                      <td className="numeric">
                        {String(index + 1).padStart(2, "0")}
                      </td>
                      <td>
                        <strong>{route.name}</strong>
                        <small>{route.destination}</small>
                      </td>
                      <td className="numeric">{route.orderCount} 笔</td>
                      <td className="numeric">{route.travelerCount} 人</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <Empty
              title="还没有可排行的线路"
              description="这个窗口内没有未取消的订单。"
            />
          )}
        </section>
        <section className="panel">
          <div className="panel-heading">
            <div>
              <h2>顾问接单排行</h2>
              <p>全团队口径，仅管理员可见</p>
            </div>
          </div>
          {data.employeeRank.length ? (
            <div className="table-scroll">
              <table className="data-table compact-table">
                <thead>
                  <tr>
                    <th>顾问</th>
                    <th>接单</th>
                    <th>金额</th>
                  </tr>
                </thead>
                <tbody>
                  {data.employeeRank.map((employee) => (
                    <tr key={employee.employeeId}>
                      <td>
                        <strong>{employee.employeeName}</strong>
                        <small>编号 {employee.employeeId}</small>
                      </td>
                      <td className="numeric">{employee.orderCount} 笔</td>
                      <td className="numeric">¥ {money(employee.amount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <Empty
              title="暂无团队排行"
              description={
                data.scope === "ADMIN"
                  ? "员工接单后会出现在这里。"
                  : "排行只对管理员开放，你的数字统计在左上角。"
              }
            />
          )}
        </section>
      </div>
    </>
  );
}
