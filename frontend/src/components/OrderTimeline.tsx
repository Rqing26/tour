import { CheckCircle2, Clock3, RotateCcw, Ticket, XCircle } from "lucide-react";
import { Spinner } from "./UI";
import { timelineNodes } from "../timeline.ts";
import type { OrderEvent, OrderStatus } from "../types";
import { useResource } from "../useResource";
import "./timeline.css";

const icons: Record<OrderStatus, typeof Ticket> = {
  PENDING: Ticket,
  ASSIGNED: Clock3,
  COMPLETED: CheckCircle2,
  CANCELLED: XCircle,
};

/** 订单变更记录：后端只存留痕，动作名称与时间格式在这里翻译。 */
export default function OrderTimeline({ orderId }: { orderId: number }) {
  const { data, loading, error, refresh } = useResource<OrderEvent[]>(
    `/orders/${orderId}/events`,
  );
  const nodes = timelineNodes(data ?? []);
  return (
    <section className="timeline" aria-label="订单变更记录">
      <header className="timeline-head">
        <div>
          <span className="eyebrow">AUDIT TRAIL</span>
          <h4>订单变更记录</h4>
        </div>
        <button
          type="button"
          className="text-button"
          onClick={refresh}
          disabled={loading}
        >
          <RotateCcw size={14} />
          刷新记录
        </button>
      </header>
      {loading ? (
        <Spinner text="正在读取变更记录" />
      ) : error ? (
        <p className="timeline-empty" role="alert">
          {error}
        </p>
      ) : !nodes.length ? (
        <p className="timeline-empty">这笔订单还没有变更记录。</p>
      ) : (
        <ol className="timeline-list">
          {nodes.map((node) => {
            const Icon = icons[node.status];
            return (
              <li
                key={node.id}
                className={`timeline-item ${node.status.toLowerCase()}`}
              >
                <span className="timeline-dot">
                  <Icon size={14} />
                </span>
                <div className="timeline-body">
                  <div className="timeline-line">
                    <strong>{node.title}</strong>
                    <time>{node.time}</time>
                  </div>
                  <p className="timeline-meta">
                    <span>{node.actor}</span>
                    <span className="timeline-role">{node.actorRole}</span>
                    <span className="timeline-path">{node.transition}</span>
                  </p>
                  {node.note && <p className="timeline-note">{node.note}</p>}
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}
