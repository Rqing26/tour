import { useState } from "react";
import type { FormEvent } from "react";
import {
  Check,
  CircleUserRound,
  LoaderCircle,
  LockKeyhole,
  ShieldCheck,
  Undo2,
} from "lucide-react";
import { api, errorMessage } from "../api";
import { roleLabels } from "../assets";
import type { Notify, User } from "../types";
import { Field, SectionHeading } from "./UI";
import Avatar from "./Avatar";
import AvatarEditor from "./AvatarEditor";
import PasswordSettings from "./PasswordSettings";
import "./account.css";

interface AccountProps {
  user: User;
  setUser: (user: User) => void;
  notify: Notify;
}

export default function Account({ user, setUser, notify }: AccountProps) {
  const [displayName, setDisplayName] = useState(user.displayName);
  const [phone, setPhone] = useState(user.phone);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const dirty =
    displayName.trim() !== user.displayName || phone.trim() !== user.phone;
  const edited = displayName !== user.displayName || phone !== user.phone;
  function reset() {
    setDisplayName(user.displayName);
    setPhone(user.phone);
    setError("");
    setSaved(false);
  }
  async function saveProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    if (!displayName.trim()) {
      setError("请填写姓名或称呼。");
      return;
    }
    if (!/^[+0-9 ()-]{5,25}$/.test(phone.trim())) {
      setError("请填写有效的联系电话。");
      return;
    }
    setBusy(true);
    setSaved(false);
    try {
      const updated = await api<User>("/account", "PUT", {
        displayName: displayName.trim(),
        phone: phone.trim(),
      });
      setUser(updated);
      setDisplayName(updated.displayName);
      setPhone(updated.phone);
      setSaved(true);
      notify("个人资料已保存。");
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <SectionHeading
        eyebrow="ACCOUNT SETTINGS"
        title="个人账户"
        description="管理你的头像、个人资料和登录密码。"
      />
      <div className="account-layout">
        <aside className="panel account-summary">
          <Avatar user={user} className="account-avatar" />
          <h2>{user.displayName}</h2>
          <p>@{user.username}</p>
          <span className="badge">
            <ShieldCheck size={14} />
            {roleLabels[user.role]}
          </span>
          <dl className="account-summary-details">
            <div>
              <dt>登录用户名</dt>
              <dd>{user.username}</dd>
            </div>
            <div>
              <dt>联系电话</dt>
              <dd>{user.phone || "尚未填写"}</dd>
            </div>
          </dl>
          <p className="account-summary-note">
            姓名和联系电话用于预订与行程联系，请保持信息准确。
          </p>
        </aside>
        <div className="account-forms">
          <section
            className="panel account-form-panel"
            aria-labelledby="account-profile-title"
          >
            <div className="account-section-heading">
              <span className="account-section-icon">
                <CircleUserRound size={21} />
              </span>
              <div>
                <h2 id="account-profile-title">基本资料</h2>
                <p>设置头像，并更新你的称呼与联系方式。</p>
              </div>
            </div>
            <AvatarEditor user={user} notify={notify} />
            <form className="form-stack" onSubmit={saveProfile}>
              <div className="account-username">
                <div>
                  <span>登录用户名</span>
                  <strong>{user.username}</strong>
                </div>
                <span>
                  <LockKeyhole size={14} />
                  不可修改
                </span>
              </div>
              <fieldset className="account-fields" disabled={busy}>
                <div className="form-grid">
                  <Field label="姓名 / 称呼" hint="用于账户展示和行程联系。">
                    <input
                      required
                      maxLength={60}
                      autoComplete="name"
                      value={displayName}
                      onChange={(event) => {
                        setDisplayName(event.target.value);
                        setError("");
                        setSaved(false);
                      }}
                      placeholder="请输入姓名或称呼"
                    />
                  </Field>
                  <Field label="联系电话" hint="支持手机、座机或带区号的号码。">
                    <input
                      type="tel"
                      autoComplete="tel"
                      inputMode="tel"
                      required
                      minLength={5}
                      maxLength={25}
                      pattern="[+0-9 \(\)\-]{5,25}"
                      title="请使用 5 至 25 位数字、空格、加号、括号或连字符"
                      value={phone}
                      onChange={(event) => {
                        setPhone(event.target.value);
                        setError("");
                        setSaved(false);
                      }}
                      placeholder="请输入常用联系电话"
                    />
                  </Field>
                </div>
              </fieldset>
              {error && (
                <p className="form-error" role="alert">
                  {error}
                </p>
              )}
              <div className="form-actions account-form-actions">
                <span className="account-action-note" role="status">
                  {busy ? (
                    "正在保存资料…"
                  ) : dirty ? (
                    "有未保存的修改"
                  ) : saved ? (
                    <>
                      <Check size={15} />
                      资料已保存
                    </>
                  ) : (
                    "资料已是最新"
                  )}
                </span>
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={busy || !edited}
                  onClick={reset}
                >
                  <Undo2 size={16} />
                  撤销修改
                </button>
                <button className="btn btn-primary" disabled={busy || !dirty}>
                  {busy ? (
                    <LoaderCircle className="spin" size={16} />
                  ) : (
                    <Check size={16} />
                  )}
                  {busy ? "正在保存…" : "保存资料"}
                </button>
              </div>
            </form>
          </section>
          <PasswordSettings />
        </div>
      </div>
    </>
  );
}
