import { useState } from "react";
import type { FormEvent } from "react";
import { api, errorMessage } from "../api";
import { imageKeys, photo } from "../assets";
import type { Route, RouteInput } from "../types";
import { Field, Modal } from "./UI";

export default function RouteForm({
  route,
  onClose,
  onSaved,
}: {
  route?: Route;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<RouteInput>(
    route
      ? { ...route }
      : {
          name: "",
          destination: "",
          description: "",
          startDate: "",
          durationDays: 3,
          price: 1280,
          capacity: 20,
          active: true,
          imageKey: "guilin",
          highlights: [],
        },
  );
  const [highlights, setHighlights] = useState(
    route?.highlights.join("，") || "",
  );
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const update = <K extends keyof RouteInput>(key: K, value: RouteInput[K]) =>
    setForm((current) => ({ ...current, [key]: value }));
  async function save(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const tags = highlights
      .split(/[,，]/)
      .map((item) => item.trim())
      .filter(Boolean);
    if (tags.length > 6 || tags.some((item) => item.length > 60)) {
      setError("最多添加 6 个特色标签，每个标签不超过 60 个字。");
      setBusy(false);
      return;
    }
    const input = { ...form, highlights: tags };
    try {
      await api(
        route ? `/routes/${route.id}` : "/routes",
        route ? "PUT" : "POST",
        input,
      );
      onSaved();
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title={route ? "编辑线路" : "新增线路"}
      subtitle="填写线路信息和上架设置。"
      wide
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <form onSubmit={save} className="form-stack">
        <Field label="线路名称">
          <input
            required
            maxLength={100}
            value={form.name}
            onChange={(e) => update("name", e.target.value)}
            placeholder="例如：桂林阳朔 · 山水慢游"
          />
        </Field>
        <div className="form-grid">
          <Field label="目的地">
            <input
              required
              maxLength={50}
              value={form.destination}
              onChange={(e) => update("destination", e.target.value)}
              placeholder="例如：桂林"
            />
          </Field>
          <Field label="出发日期">
            <input
              type="date"
              required
              value={form.startDate}
              onChange={(e) => update("startDate", e.target.value)}
            />
          </Field>
        </div>
        <div className="form-grid form-grid-three">
          <Field label="行程天数">
            <input
              type="number"
              min="1"
              max="90"
              required
              value={form.durationDays}
              onChange={(e) => update("durationDays", Number(e.target.value))}
            />
          </Field>
          <Field label="每人价格（元）">
            <input
              type="number"
              min="0.01"
              max="9999999"
              step="0.01"
              required
              value={form.price}
              onChange={(e) => update("price", Number(e.target.value))}
            />
          </Field>
          <Field label="总席位">
            <input
              type="number"
              min="1"
              max="10000"
              required
              value={form.capacity}
              onChange={(e) => update("capacity", Number(e.target.value))}
            />
          </Field>
        </div>
        <Field label="行程介绍">
          <textarea
            required
            rows={3}
            maxLength={2000}
            placeholder="介绍行程安排、特色体验与费用包含内容。"
            value={form.description}
            onChange={(e) => update("description", e.target.value)}
          />
        </Field>
        <Field label="特色标签" hint="使用逗号分隔，例如：山水风光，小团慢游。">
          <input
            maxLength={250}
            value={highlights}
            onChange={(e) => setHighlights(e.target.value)}
            placeholder="山水风光，小团慢游"
          />
        </Field>
        <fieldset className="image-picker">
          <legend>线路封面</legend>
          <div>
            {imageKeys.map((key, index) => (
              <label
                className={form.imageKey === key ? "selected" : ""}
                key={key}
              >
                <input
                  type="radio"
                  name="cover"
                  value={key}
                  checked={form.imageKey === key}
                  onChange={() => update("imageKey", key)}
                />
                <img
                  src={photo(key)}
                  alt={["桂林", "云南", "新疆", "成都", "厦门", "杭州"][index]}
                />
                <span>
                  {["桂林", "云南", "新疆", "成都", "厦门", "杭州"][index]}
                </span>
              </label>
            ))}
          </div>
        </fieldset>
        <label className="checkbox-field">
          <input
            type="checkbox"
            checked={form.active}
            onChange={(e) => update("active", e.target.checked)}
          />
          <span>
            <strong>上架此线路</strong>
            <small>上架后，旅行者可查看并预订。</small>
          </span>
        </label>
        {route && route.availableSeats < route.capacity && (
          <p className="form-note">
            这条线路已有预订。出发安排受订单保护；总席位不能小于已预订人数。
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
            取消
          </button>
          <button className="btn btn-primary" disabled={busy}>
            {busy ? "正在保存…" : route ? "保存修改" : "创建线路"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
