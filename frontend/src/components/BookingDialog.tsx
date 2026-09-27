import { useState } from "react";
import type { FormEvent } from "react";
import { CalendarDays, Check, Clock3, MapPin, UsersRound } from "lucide-react";
import { api, errorMessage } from "../api";
import { dateLabel, money, photo } from "../assets";
import type { Route, User } from "../types";
import { Field, Modal } from "./UI";

export default function BookingDialog({
  route,
  user,
  onClose,
  onBooked,
  onEdit,
}: {
  route: Route;
  user: User;
  onClose: () => void;
  onBooked: () => void;
  onEdit?: () => void;
}) {
  const [travelerCount, setTravelerCount] = useState(1);
  const [contactName, setContactName] = useState(user.displayName);
  const [contactPhone, setContactPhone] = useState(user.phone);
  const [remark, setRemark] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const localToday = new Date().toLocaleDateString("en-CA");
  const unavailable =
    !route.active || route.availableSeats <= 0 || route.startDate <= localToday;
  async function book(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api("/orders", "POST", {
        routeId: route.id,
        travelerCount,
        contactName,
        contactPhone,
        remark,
      });
      onBooked();
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title={route.name}
      subtitle={`${route.destination} · ${route.durationDays} 天旅程`}
      wide
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <div className="booking-cover">
        <img src={photo(route.imageKey)} alt={`${route.destination}旅行风景`} />
        <span>
          <MapPin size={14} />
          {route.destination}
        </span>
      </div>
      <div className="booking-facts">
        <span>
          <CalendarDays size={16} />
          {dateLabel(route.startDate)} 出发
        </span>
        <span>
          <Clock3 size={16} />
          {route.durationDays} 天
        </span>
        <span>
          <UsersRound size={16} />余 {route.availableSeats} 席
        </span>
      </div>
      <p className="booking-description">{route.description}</p>
      <div className="booking-highlights">
        {route.highlights.map((highlight) => (
          <span key={highlight}>
            <Check size={13} />
            {highlight}
          </span>
        ))}
      </div>
      {user.role === "CUSTOMER" ? (
        <form className="form-stack booking-form" onSubmit={book}>
          <h3>确认你的出行安排</h3>
          <div className="form-grid">
            <Field label="联系人">
              <input
                required
                maxLength={50}
                value={contactName}
                onChange={(e) => setContactName(e.target.value)}
              />
            </Field>
            <Field label="联系电话">
              <input
                required
                inputMode="tel"
                minLength={5}
                maxLength={25}
                pattern="[+0-9 \(\)\-]{5,25}"
                title="请填写 5 至 25 位联系电话，可包含数字、空格、加号、括号和连字符"
                value={contactPhone}
                onChange={(e) => setContactPhone(e.target.value)}
              />
            </Field>
          </div>
          <div className="form-grid">
            <Field label="出行人数">
              <input
                required
                type="number"
                min="1"
                max={Math.min(route.availableSeats || 1, 20)}
                value={travelerCount}
                onChange={(e) => setTravelerCount(Number(e.target.value))}
              />
            </Field>
            <Field label="行程备注（选填）">
              <input
                maxLength={500}
                placeholder="其他出行需求"
                value={remark}
                onChange={(e) => setRemark(e.target.value)}
              />
            </Field>
          </div>
          <div className="booking-total">
            <div>
              <span>订单总额</span>
              <small>
                ¥ {money(route.price)} / 人 × {travelerCount || 0} 人
              </small>
            </div>
            <strong>¥ {money(route.price * travelerCount)}</strong>
          </div>
          <p className="form-note">
            提交后将由旅行顾问跟进。订单完成前，可以在「我的预订」中取消。
          </p>
          {unavailable && (
            <p className="form-error">
              这条线路目前无法预订，请选择其他出发安排。
            </p>
          )}
          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}
          <div className="form-actions">
            <button
              type="button"
              className="btn btn-secondary"
              disabled={busy}
              onClick={onClose}
            >
              再看看
            </button>
            <button className="btn btn-primary" disabled={busy || unavailable}>
              {busy ? "正在提交…" : "确认预订"}
            </button>
          </div>
        </form>
      ) : (
        <div className="form-actions">
          <div className="route-price">
            <span>¥</span>
            <strong>{money(route.price)}</strong>
            <small>/ 人</small>
          </div>
          <button className="btn btn-primary" onClick={onEdit || onClose}>
            {onEdit ? "编辑线路" : "了解了"}
          </button>
        </div>
      )}
    </Modal>
  );
}
