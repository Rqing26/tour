import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { api, errorMessage, upload } from "../api";
import { cover, imageKeys, imageLabels, photo } from "../assets";
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
  const [image, setImage] = useState<File | null>(null);
  const [preview, setPreview] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  // 预览用 blob URL，换文件或关闭表单时释放，避免一直占着内存。
  useEffect(
    () => () => {
      if (preview) URL.revokeObjectURL(preview);
    },
    [preview],
  );
  const shownCover = preview || (route ? cover(route) : photo(form.imageKey));
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
      const saved = await api<Route>(
        route ? `/routes/${route.id}` : "/routes",
        route ? "PUT" : "POST",
        input,
      );
      // 上传要线路 id，所以先存线路再传图；图传失败不影响线路本身已保存。
      if (image) await upload(`/routes/${saved.id}/image`, image);
      onSaved();
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title={route ? "编辑旅行线路" : "创建一条新线路"}
      subtitle="把目的地的美好，写进下一段旅程。"
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
          <div className="image-options">
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
                <img src={photo(key)} alt={imageLabels[index]} />
                <span>{imageLabels[index]}</span>
              </label>
            ))}
          </div>
        </fieldset>
        <div className="image-upload">
          <img src={shownCover} alt="当前封面预览" />
          <div>
            <label className="btn btn-secondary">
              选择本地图片
              <input
                type="file"
                accept="image/png,image/jpeg,image/webp"
                onChange={(e) => {
                  const picked = e.target.files?.[0] ?? null;
                  setImage(picked);
                  setPreview(picked ? URL.createObjectURL(picked) : "");
                }}
              />
            </label>
            <small>
              选填。PNG / JPEG / WebP，单张不超过
              5MB；上传后线路卡片优先用这张图，不选则沿用上面的内置封面。
            </small>
            {image && (
              <button
                type="button"
                className="link-button"
                onClick={() => {
                  setImage(null);
                  setPreview("");
                }}
              >
                取消这张图，继续用内置封面
              </button>
            )}
          </div>
        </div>
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
