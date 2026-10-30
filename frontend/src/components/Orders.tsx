import Pagination from "./Pagination";
import OrderTimeline from "./OrderTimeline";
import { useEffect, useState } from "react";
import {
  ArrowRight,
  CalendarDays,
  CheckCircle2,
  Clock3,
  MapPin,
  Phone,
  RefreshCw,
  Ticket,
  UserRound,
} from "lucide-react";
import { api, errorMessage } from "../api";
import { dateLabel, money, statusLabels } from "../assets";
import {
  ORDER_PAGE_SIZE,
  ORDER_STATUS_TABS,
  clampPage,
  pageCount,
  pagedOrdersPath,
  rangeLabel,
} from "../orderQuery";
import type { Notify, Order, OrderStatus, User } from "../types";
import { usePagedResource } from "../useResource";
import {
  Empty,
  ErrorState,
  Modal,
  SearchInput,
  SectionHeading,
  Spinner,
  StatusBadge,
} from "./UI";

type Action = "claim" | "release" | "complete" | "cancel";
const actionLabels = {
  claim: "接下订单",
  release: "退回待接池",
  complete: "完成服务",
  cancel: "取消预订",
};
const actionDescriptions = {
  claim: "接单后，你将成为该订单的服务顾问，请及时联系旅行者确认出行安排。",
  release: "订单会重新进入待接池，其他旅行顾问可以接单。",
  complete: "请确认行程服务已经完成。完成后，订单将进入历史记录。",
  cancel: "取消后将释放本次预订的席位。你可以随时重新选择旅行线路。",
};

export default function Orders({
  user,
  notify,
}: {
  user: User;
  notify: Notify;
}) {
  const [search, setSearch] = useState("");
  // 搜索改成服务端查询后，敲一个键就发一次请求会打满接口：停顿一会儿再把关键词交给服务端。
  const [appliedSearch, setAppliedSearch] = useState("");
  const [status, setStatus] = useState<OrderStatus | "">("");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Order | null>(null);
  const { items, total, loading, error, refresh, version } =
    usePagedResource<Order>(
      pagedOrdersPath({ q: appliedSearch, status }, page, ORDER_PAGE_SIZE),
    );
  const [confirm, setConfirm] = useState<{
    order: Order;
    action: Action;
  } | null>(null);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState("");
  const [counts, setCounts] = useState<Record<string, number>>({});
  const isCustomer = user.role === "CUSTOMER";
  const isEmployee = user.role === "EMPLOYEE";
  const pages = pageCount(total, ORDER_PAGE_SIZE);
  useEffect(() => {
    const timer = setTimeout(() => {
      setAppliedSearch(search);
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);
  // 页码越界（删到最后一条、取消订单后总数变小）时拉回最后一页，别停在空列表上。
  useEffect(() => {
    if (page > pages - 1) setPage(clampPage(page, total, ORDER_PAGE_SIZE));
  }, [page, pages, total]);
  // 分页后前端不再持有全量订单，页签数字只能各问一次总数：size=1 只取 total，不取详情。
  useEffect(() => {
    let active = true;
    Promise.all(
      ORDER_STATUS_TABS.map((value) =>
        api<{ total: number }>(
          pagedOrdersPath({ q: appliedSearch, status: value }, 0, 1),
        ).then((result) => [value, result.total] as const),
      ),
    )
      .then((entries) => {
        if (active) setCounts(Object.fromEntries(entries));
      })
      .catch(() => {
        // 页签数字只是辅助信息：取不到就留空，列表本身另有错误提示。
        if (active) setCounts({});
      });
    return () => {
      active = false;
    };
  }, [appliedSearch, version]);
  const tabs: { value: OrderStatus | ""; label: string }[] = [
    { value: "", label: "全部订单" },
    { value: "PENDING", label: isEmployee ? "待接订单池" : "待接单" },
    { value: "ASSIGNED", label: isEmployee ? "我服务的" : "服务中" },
    { value: "COMPLETED", label: "已完成" },
    ...(!isEmployee ? [{ value: "CANCELLED" as const, label: "已取消" }] : []),
  ];
  const actions = (order: Order): Action[] =>
    isCustomer && ["PENDING", "ASSIGNED"].includes(order.status)
      ? ["cancel"]
      : isEmployee && order.status === "PENDING"
        ? ["claim"]
        : isEmployee &&
            order.status === "ASSIGNED" &&
            order.employeeId === user.id
          ? ["complete", "release"]
          : [];
  const ask = (order: Order, action: Action) => {
    setSelected(null);
    setConfirm({ order, action });
    setActionError("");
  };
  async function perform() {
    if (!confirm) return;
    setBusy(true);
    setActionError("");
    try {
      await api(`/orders/${confirm.order.id}/${confirm.action}`, "POST", {});
      notify(`${actionLabels[confirm.action]}成功。`);
      setConfirm(null);
      refresh();
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <SectionHeading
        eyebrow={isCustomer ? "MY JOURNEYS" : "ORDER CENTER"}
        title={
          isCustomer
            ? "每一段旅程，都在这里"
            : isEmployee
              ? "用心服务，每一段旅程"
              : "让每笔订单，井然有序"
        }
        description={
          isCustomer
            ? "查看预订进展、出发安排，与旅行顾问保持连接。"
            : isEmployee
              ? "从接单到旅程完成，在这里跟进你的服务。"
              : "掌握所有订单的进展，关注每一位旅行者的出行。"
        }
        action={
          <button
            className="btn btn-secondary"
            onClick={refresh}
            disabled={loading}
          >
            <RefreshCw size={16} className={loading ? "spin" : ""} />
            刷新订单
          </button>
        }
      />
      <section className="panel orders-panel">
        <div className="orders-toolbar">
          <div className="tabs" role="tablist" aria-label="订单状态">
            {tabs.map((tab) => (
              <button
                key={tab.value}
                role="tab"
                aria-selected={status === tab.value}
                className={status === tab.value ? "active" : ""}
                onClick={() => {
                  setStatus(tab.value);
                  setPage(0);
                }}
              >
                {tab.label}
                <span>{counts[tab.value] ?? 0}</span>
              </button>
            ))}
          </div>
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder={
              isCustomer ? "搜索订单号、线路…" : "搜索订单号、线路、旅行者…"
            }
            label="搜索订单"
          />
        </div>
        {loading ? (
          <Spinner />
        ) : error ? (
          <ErrorState message={error} retry={refresh} />
        ) : !items.length ? (
          <Empty
            title={
              status === "PENDING" && isEmployee
                ? "当前没有待接订单"
                : "没有找到订单"
            }
            description={
              appliedSearch || status
                ? "调整筛选条件，看看其他订单。"
                : isCustomer
                  ? "预订一条心仪的线路，开启你的下一段旅程。"
                  : "新订单将在这里显示。"
            }
          />
        ) : (
          <>
            <div className="table-scroll">
              <table className="data-table orders-table">
                <thead>
                  <tr>
                    <th>订单 / 旅行线路</th>
                    <th>{isCustomer ? "出行安排" : "旅行者"}</th>
                    <th>订单金额</th>
                    <th>服务顾问</th>
                    <th>状态</th>
                    <th className="align-right">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((order) => (
                    <tr key={order.id}>
                      <td>
                        <div className="order-line">
                          <span className="order-icon">
                            <Ticket size={19} />
                          </span>
                          <div>
                            <button
                              className="table-name-button"
                              onClick={() => setSelected(order)}
                            >
                              {order.routeName}
                            </button>
                            <small>{order.orderNo}</small>
                          </div>
                        </div>
                      </td>
                      <td>
                        {isCustomer
                          ? dateLabel(order.startDate)
                          : order.customerName}
                        <small>
                          {isCustomer
                            ? `${order.durationDays} 天 · ${order.travelerCount} 人出行`
                            : `${order.contactPhone} · ${order.travelerCount} 人`}
                        </small>
                      </td>
                      <td className="numeric">
                        <strong>¥ {money(order.totalAmount)}</strong>
                        <small>¥ {money(order.unitPrice)} / 人</small>
                      </td>
                      <td>
                        {order.employeeName || (
                          <span className="muted">等待接单</span>
                        )}
                      </td>
                      <td>
                        <StatusBadge status={order.status} />
                      </td>
                      <td>
                        <div className="table-actions">
                          <button
                            className="text-button"
                            onClick={() => setSelected(order)}
                          >
                            详情
                          </button>
                          {actions(order)
                            .slice(0, 1)
                            .map((action) => (
                              <button
                                className={`mini-button ${action === "cancel" ? "mini-danger" : ""}`}
                                key={action}
                                onClick={() => ask(order, action)}
                              >
                                {actionLabels[action]}
                              </button>
                            ))}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="table-footer">
              <span>{rangeLabel(total, page, ORDER_PAGE_SIZE)}</span>
              <span>订单价格以预订时的价格为准</span>
              <Pagination
                page={page + 1}
                pages={pages}
                onChange={(next) => setPage(next - 1)}
              />
            </div>
          </>
        )}
      </section>
      {selected && (
        <Modal
          title="订单详情"
          subtitle={selected.orderNo}
          onClose={() => setSelected(null)}
          wide
        >
          <div className="order-detail-title">
            <div>
              <span className="eyebrow">YOUR JOURNEY</span>
              <h3>{selected.routeName}</h3>
              <p>
                <MapPin size={14} />
                {selected.destination}
              </p>
            </div>
            <StatusBadge status={selected.status} />
          </div>
          <div className="order-progress">
            {[
              { status: "PENDING", label: "预订成功", icon: Ticket },
              { status: "ASSIGNED", label: "顾问服务", icon: Clock3 },
              { status: "COMPLETED", label: "旅程完成", icon: CheckCircle2 },
            ].map((step, index) => (
              <div
                className={`${selected.status !== "CANCELLED" && ["PENDING", "ASSIGNED", "COMPLETED"].indexOf(selected.status) >= index ? "done" : ""}`}
                key={step.status}
              >
                <step.icon size={20} />
                <span>{step.label}</span>
                {index < 2 && <ArrowRight size={16} />}
              </div>
            ))}
          </div>
          {selected.status === "CANCELLED" && (
            <p className="form-note">这笔预订已取消，席位已释放。</p>
          )}
          <OrderTimeline orderId={selected.id} />
          <div className="detail-grid">
            <div>
              <span>
                <CalendarDays size={15} />
                出发安排
              </span>
              <strong>
                {dateLabel(selected.startDate)} · {selected.durationDays} 天
              </strong>
            </div>
            <div>
              <span>
                <UserRound size={15} />
                出行人数
              </span>
              <strong>{selected.travelerCount} 人</strong>
            </div>
            <div>
              <span>
                <UserRound size={15} />
                联系人
              </span>
              <strong>{selected.contactName}</strong>
            </div>
            <div>
              <span>
                <Phone size={15} />
                联系电话
              </span>
              <strong>{selected.contactPhone}</strong>
            </div>
            <div>
              <span>服务顾问</span>
              <strong>{selected.employeeName || "等待顾问接单"}</strong>
            </div>
            <div>
              <span>下单时间</span>
              <strong>
                {selected.createdAt?.replace("T", " ").slice(0, 16)}
              </strong>
            </div>
          </div>
          {selected.remark && (
            <div className="order-remark">
              <span>行程备注</span>
              <p>{selected.remark}</p>
            </div>
          )}
          <div className="booking-total">
            <div>
              <span>订单总额</span>
              <small>
                ¥ {money(selected.unitPrice)} / 人 × {selected.travelerCount} 人
              </small>
            </div>
            <strong>¥ {money(selected.totalAmount)}</strong>
          </div>
          <div className="form-actions">
            <button
              className="btn btn-secondary"
              onClick={() => setSelected(null)}
            >
              关闭
            </button>
            {actions(selected).map((action) => (
              <button
                key={action}
                className={`btn ${action === "release" || action === "cancel" ? "btn-secondary" : "btn-primary"}`}
                onClick={() => ask(selected, action)}
              >
                {actionLabels[action]}
              </button>
            ))}
          </div>
        </Modal>
      )}
      {confirm && (
        <Modal
          title={`${actionLabels[confirm.action]}？`}
          subtitle={`${confirm.order.orderNo} · ${confirm.order.routeName}`}
          onClose={() => {
            if (!busy) setConfirm(null);
          }}
        >
          <p className="dialog-description">
            {actionDescriptions[confirm.action]}
          </p>
          <div className="confirmation-summary">
            <span>当前状态</span>
            <strong>{statusLabels[confirm.order.status]}</strong>
            <span>出行人数</span>
            <strong>{confirm.order.travelerCount} 人</strong>
          </div>
          {actionError && (
            <p className="form-error" role="alert">
              {actionError}
            </p>
          )}
          <div className="form-actions">
            <button
              className="btn btn-secondary"
              disabled={busy}
              onClick={() => setConfirm(null)}
            >
              返回
            </button>
            <button
              className={`btn ${confirm.action === "cancel" ? "btn-danger" : "btn-primary"}`}
              disabled={busy}
              onClick={perform}
            >
              {busy ? "正在处理…" : `确认${actionLabels[confirm.action]}`}
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
