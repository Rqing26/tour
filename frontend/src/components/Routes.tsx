import Pagination, { usePagination } from "./Pagination";
import { useMemo, useState } from "react";
import {
  ArrowRight,
  CalendarDays,
  Clock3,
  MapPin,
  Pencil,
  Plus,
  SlidersHorizontal,
  Trash2,
  UsersRound,
} from "lucide-react";
import { api, errorMessage } from "../api";
import { dateLabel, money, photo } from "../assets";
import type { Notify, Route, User, View } from "../types";
import { useResource } from "../useResource";
import {
  Empty,
  ErrorState,
  Modal,
  SearchInput,
  SectionHeading,
  Spinner,
} from "./UI";
import RouteForm from "./RouteForm";
import BookingDialog from "./BookingDialog";

export default function Routes({
  user,
  notify,
  navigate,
}: {
  user: User;
  notify: Notify;
  navigate: (view: View) => void;
}) {
  const { data, loading, error, refresh } = useResource<Route[]>("/routes");
  const [search, setSearch] = useState("");
  const [destination, setDestination] = useState("");
  const [active, setActive] = useState("");
  const [editing, setEditing] = useState<Route | "new" | null>(null);
  const [selected, setSelected] = useState<Route | null>(null);
  const [deleting, setDeleting] = useState<Route | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteError, setDeleteError] = useState("");
  const isAdmin = user.role === "ADMIN";
  const destinations = useMemo(
    () => [...new Set(data?.map((route) => route.destination) || [])],
    [data],
  );
  const filtered =
    data?.filter(
      (route) =>
        (!search ||
          `${route.name} ${route.destination} ${route.description}`
            .toLowerCase()
            .includes(search.toLowerCase())) &&
        (!destination || route.destination === destination) &&
        (!active || String(route.active) === active),
    ) || [];
  const pagination = usePagination(
    filtered,
    6,
    `${search}|${destination}|${active}`,
  );
  async function remove() {
    if (!deleting) return;
    setDeleteBusy(true);
    setDeleteError("");
    try {
      await api(`/routes/${deleting.id}`, "DELETE");
      setDeleting(null);
      refresh();
      notify("线路已删除。");
    } catch (error) {
      setDeleteError(errorMessage(error));
    } finally {
      setDeleteBusy(false);
    }
  }
  return (
    <>
      <SectionHeading
        eyebrow={isAdmin ? "ROUTE MANAGEMENT" : "EXPLORE & DISCOVER"}
        title={isAdmin ? "每一条线路，都通向期待" : "下一站，去哪里？"}
        description={
          isAdmin
            ? "维护旅行线路、出发安排与席位，让每一程都有好风景。"
            : "从城市的烟火，到山野的辽阔，找到属于你的那段旅程。"
        }
        action={
          isAdmin ? (
            <button
              className="btn btn-primary"
              onClick={() => setEditing("new")}
            >
              <Plus size={17} />
              新增线路
            </button>
          ) : (
            <span className="quiet-label">
              <CompassMark />
              为热爱出发
            </span>
          )
        }
      />
      <section className="route-filter-bar">
        <SearchInput
          value={search}
          onChange={setSearch}
          placeholder="搜索线路名称、目的地…"
          label="搜索线路"
        />
        <div className="filter-selects">
          <label className="select-with-icon">
            <MapPin size={16} />
            <select
              aria-label="按目的地筛选"
              value={destination}
              onChange={(event) => setDestination(event.target.value)}
            >
              <option value="">全部目的地</option>
              {destinations.map((item) => (
                <option key={item}>{item}</option>
              ))}
            </select>
          </label>
          {isAdmin && (
            <label className="select-with-icon">
              <SlidersHorizontal size={16} />
              <select
                aria-label="按上架状态筛选"
                value={active}
                onChange={(event) => setActive(event.target.value)}
              >
                <option value="">全部状态</option>
                <option value="true">在售线路</option>
                <option value="false">已下架</option>
              </select>
            </label>
          )}
        </div>
      </section>
      <div className="results-heading">
        <span>
          {loading ? (
            "正在发现好风景…"
          ) : (
            <>
              共 <strong>{filtered.length}</strong> 条旅行线路
            </>
          )}
        </span>
        <span>山海之间，总有心之所向</span>
      </div>
      {loading ? (
        <Spinner />
      ) : error ? (
        <ErrorState message={error} retry={refresh} />
      ) : !filtered.length ? (
        <Empty
          title="暂时没有匹配的线路"
          description="试试其他目的地，或调整搜索关键词。"
          action={
            <button
              className="btn btn-secondary"
              onClick={() => {
                setSearch("");
                setDestination("");
                setActive("");
              }}
            >
              重置筛选
            </button>
          }
        />
      ) : (
        <div className="routes-grid">
          {pagination.visibleItems.map((route) => (
            <article className="route-card" key={route.id}>
              <button
                className="route-image-button"
                onClick={() => setSelected(route)}
                aria-label={`查看${route.name}`}
              >
                <img
                  src={photo(route.imageKey)}
                  alt={`${route.destination}旅行风景`}
                  loading="lazy"
                />
                <span className="route-location">
                  <MapPin size={13} />
                  {route.destination}
                </span>
                <span
                  className={`route-availability ${!route.active ? "route-inactive" : ""}`}
                >
                  {!route.active
                    ? "已下架"
                    : route.startDate <= new Date().toLocaleDateString("en-CA")
                      ? "已截止"
                      : route.availableSeats <= 0
                        ? "已满员"
                        : `余 ${route.availableSeats} 席`}
                </span>
              </button>
              <div className="route-card-body">
                <div className="route-highlights">
                  {route.highlights.slice(0, 2).map((item) => (
                    <span key={item}>{item}</span>
                  ))}
                </div>
                <button
                  className="route-title-button"
                  onClick={() => setSelected(route)}
                >
                  <h2>{route.name}</h2>
                </button>
                <p className="route-description">{route.description}</p>
                <div className="route-meta">
                  <span>
                    <CalendarDays size={14} />
                    {dateLabel(route.startDate)}
                  </span>
                  <span>
                    <Clock3 size={14} />
                    {route.durationDays} 天
                  </span>
                </div>
                <div className="route-card-footer">
                  <div className="route-price">
                    <span>¥</span>
                    <strong>{money(route.price)}</strong>
                    <small>/ 人</small>
                  </div>
                  {isAdmin ? (
                    <div className="row-actions">
                      <button
                        className="icon-button"
                        onClick={() => setEditing(route)}
                        aria-label={`编辑${route.name}`}
                        title="编辑线路"
                      >
                        <Pencil size={17} />
                      </button>
                      <button
                        className="icon-button danger"
                        onClick={() => {
                          setDeleting(route);
                          setDeleteError("");
                        }}
                        aria-label={`删除${route.name}`}
                        title="删除线路"
                      >
                        <Trash2 size={17} />
                      </button>
                    </div>
                  ) : (
                    <button
                      className="route-book-button"
                      onClick={() => setSelected(route)}
                    >
                      {user.role === "CUSTOMER" ? "查看行程" : "线路详情"}
                      <ArrowRight size={16} />
                    </button>
                  )}
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
      <div className="route-pagination">
        <Pagination
          page={pagination.page}
          pages={pagination.pages}
          onChange={pagination.setPage}
        />
      </div>
      {editing && (
        <RouteForm
          route={editing === "new" ? undefined : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            refresh();
            notify(editing === "new" ? "新线路已创建。" : "线路信息已更新。");
          }}
        />
      )}
      {selected && (
        <BookingDialog
          route={selected}
          user={user}
          onClose={() => setSelected(null)}
          onBooked={() => {
            setSelected(null);
            notify("预订成功，旅行顾问将为你跟进行程。");
            navigate("orders");
          }}
          onEdit={
            isAdmin
              ? () => {
                  setEditing(selected);
                  setSelected(null);
                }
              : undefined
          }
        />
      )}
      {deleting && (
        <Modal
          title="删除这条线路？"
          subtitle={deleting.name}
          onClose={() => {
            if (!deleteBusy) setDeleting(null);
          }}
        >
          <p className="dialog-description">
            删除后无法恢复。如果已有订单关联，请编辑线路并将其下架，以保留订单记录。
          </p>
          {deleteError && (
            <p className="form-error" role="alert">
              {deleteError}
            </p>
          )}
          <div className="form-actions">
            <button
              className="btn btn-secondary"
              disabled={deleteBusy}
              onClick={() => setDeleting(null)}
            >
              保留线路
            </button>
            <button
              className="btn btn-danger"
              disabled={deleteBusy}
              onClick={remove}
            >
              {deleteBusy ? "正在删除…" : "确认删除"}
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
function CompassMark() {
  return <UsersRound size={16} />;
}
