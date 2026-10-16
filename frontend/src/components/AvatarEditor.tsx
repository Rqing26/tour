import { useEffect, useRef, useState } from "react";
import { Camera, Check, LoaderCircle } from "lucide-react";
import { avatarKey, prepareAvatar, saveAvatar } from "../avatar";
import { errorMessage } from "../api";
import type { Notify, User } from "../types";
import Avatar, { useAvatar } from "./Avatar";

export default function AvatarEditor({
  user,
  notify,
}: {
  user: User;
  notify: Notify;
}) {
  const input = useRef<HTMLInputElement>(null);
  const request = useRef(0);
  const stored = useAvatar(user);
  const [preview, setPreview] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(
    () => () => {
      request.current += 1;
    },
    [],
  );

  async function choose(file: File) {
    const id = ++request.current;
    setBusy(true);
    setError("");
    setPreview(null);
    try {
      const image = await prepareAvatar(file);
      if (id === request.current) setPreview(image);
    } catch (error) {
      if (id === request.current) setError(errorMessage(error));
    } finally {
      if (id === request.current) setBusy(false);
    }
  }

  function save(value: string | null) {
    try {
      saveAvatar(avatarKey(user), value);
      setPreview(null);
      setError("");
      notify(value ? "头像已保存在当前浏览器。" : "已恢复默认头像。");
    } catch (error) {
      setError(errorMessage(error));
    }
  }

  return (
    <div className="avatar-editor">
      <div className="avatar-editor-row">
        {preview ? (
          <span className="avatar avatar-editor-image">
            <img src={preview} alt="新头像预览" />
          </span>
        ) : (
          <Avatar user={user} className="avatar-editor-image" />
        )}
        <div className="avatar-editor-content">
          <strong>个人头像</strong>
          <p id="avatar-help">
            JPG、PNG 或 WebP，最大 5 MB。头像仅保存在当前浏览器。
          </p>
          <input
            ref={input}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            aria-label="选择头像图片"
            aria-describedby="avatar-help"
            hidden
            onChange={(event) => {
              const file = event.target.files?.[0];
              event.target.value = "";
              if (file) void choose(file);
            }}
          />
          <div className="avatar-editor-actions">
            <button
              type="button"
              className="btn btn-secondary"
              disabled={busy}
              onClick={() => input.current?.click()}
            >
              {busy ? (
                <LoaderCircle className="spin" size={16} />
              ) : (
                <Camera size={16} />
              )}
              {busy ? "正在处理…" : stored ? "更换头像" : "上传头像"}
            </button>
            {stored && !preview && (
              <button
                type="button"
                className="text-button"
                disabled={busy}
                onClick={() => save(null)}
              >
                移除头像
              </button>
            )}
          </div>
        </div>
      </div>
      {preview && (
        <div className="avatar-preview-actions">
          <p role="status">已按图片中心裁切为方形，请确认预览效果。</p>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => setPreview(null)}
          >
            取消
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => save(preview)}
          >
            <Check size={16} />
            保存头像
          </button>
        </div>
      )}
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
