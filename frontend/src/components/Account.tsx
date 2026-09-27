import { useState } from "react";
import type { FormEvent } from "react";
import {
  Check,
  CircleUserRound,
  KeyRound,
  LoaderCircle,
  ShieldCheck,
} from "lucide-react";
import { api, clearCsrf, errorMessage } from "../api";
import { roleLabels } from "../assets";
import type { Notify, User } from "../types";
import { Field, SectionHeading } from "./UI";

interface AccountProps {
  user: User;
  setUser: (user: User) => void;
  notify: Notify;
}

export default function Account({ user, setUser, notify }: AccountProps) {
  const [displayName, setDisplayName] = useState(user.displayName);
  const [phone, setPhone] = useState(user.phone);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [profileBusy, setProfileBusy] = useState(false);
  const [passwordBusy, setPasswordBusy] = useState(false);
  const [profileError, setProfileError] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const dirty =
    displayName.trim() !== user.displayName || phone.trim() !== user.phone;

  async function saveProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setProfileError("");
    if (!displayName.trim()) {
      setProfileError("请填写姓名或称呼。");
      return;
    }
    setProfileBusy(true);
    try {
      const updated = await api<User>("/account", "PUT", {
        displayName: displayName.trim(),
        phone: phone.trim(),
      });
      setUser(updated);
      setDisplayName(updated.displayName);
      setPhone(updated.phone);
      notify("个人资料已保存。");
    } catch (error) {
      setProfileError(errorMessage(error));
    } finally {
      setProfileBusy(false);
    }
  }

  async function changePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPasswordError("");
    if (newPassword !== confirmation) {
      setPasswordError("两次输入的新密码不一致，请重新确认。");
      return;
    }
    if (newPassword === currentPassword) {
      setPasswordError("新密码与当前密码相同，请使用新的密码。");
      return;
    }
    if (new TextEncoder().encode(newPassword).length > 72) {
      setPasswordError("密码过长，请适当缩短；包含中文时最多约 24 个字。");
      return;
    }
    setPasswordBusy(true);
    try {
      await api("/account/password", "PUT", { currentPassword, newPassword });
      clearCsrf();
      setCurrentPassword("");
      setNewPassword("");
      setConfirmation("");
      window.dispatchEvent(new Event("password-updated"));
    } catch (error) {
      setPasswordError(errorMessage(error));
    } finally {
      setPasswordBusy(false);
    }
  }

  return (
    <>
      <SectionHeading
        eyebrow="YOUR ACCOUNT"
        title="个人账户"
        description="让每一程都能联系上你，也照顾好账户的安全。"
      />
      <div className="account-layout">
        <aside className="panel account-summary">
          <span className="avatar">{user.displayName.slice(0, 1) || "山"}</span>
          <h2>{user.displayName}</h2>
          <p>@{user.username}</p>
          <span className="badge">
            <ShieldCheck size={14} />
            {roleLabels[user.role]}
          </span>
          <div className="form-note">
            <p>用户名用于登录，姓名和手机号码用于行程沟通。</p>
            <p>更新资料后，后续预订会使用新的联系方式。</p>
          </div>
        </aside>
        <div className="account-forms">
          <section className="panel account-form-panel">
            <div className="panel-heading">
              <h2>
                <CircleUserRound size={20} /> 基本资料
              </h2>
            </div>
            <form className="form-stack" onSubmit={saveProfile}>
              <Field label="登录用户名" hint="用户名为账户的固定登录标识。">
                <input value={user.username} readOnly autoComplete="username" />
              </Field>
              <div className="form-grid">
                <Field label="姓名 / 称呼">
                  <input
                    required
                    maxLength={60}
                    autoComplete="name"
                    value={displayName}
                    onChange={(event) => setDisplayName(event.target.value)}
                    placeholder="请输入姓名或称呼"
                  />
                </Field>
                <Field label="联系电话">
                  <input
                    type="tel"
                    autoComplete="tel"
                    required
                    minLength={5}
                    maxLength={25}
                    value={phone}
                    onChange={(event) => setPhone(event.target.value)}
                    placeholder="请输入常用联系电话"
                  />
                </Field>
              </div>
              {profileError && (
                <p className="form-error" role="alert">
                  {profileError}
                </p>
              )}
              <div className="form-actions">
                <button
                  className="btn btn-primary"
                  disabled={profileBusy || !dirty}
                >
                  {profileBusy ? (
                    <LoaderCircle className="spin" size={16} />
                  ) : (
                    <Check size={16} />
                  )}
                  {profileBusy ? "正在保存…" : "保存资料"}
                </button>
              </div>
            </form>
          </section>
          <section className="panel account-form-panel">
            <div className="panel-heading">
              <h2>
                <KeyRound size={20} /> 修改密码
              </h2>
            </div>
            <p className="form-note">
              使用至少 8 位的新密码。修改成功后需要重新登录。
            </p>
            <form className="form-stack" onSubmit={changePassword}>
              <Field label="当前密码">
                <input
                  type="password"
                  autoComplete="current-password"
                  required
                  value={currentPassword}
                  onChange={(event) => setCurrentPassword(event.target.value)}
                  placeholder="请输入当前密码"
                />
              </Field>
              <div className="form-grid">
                <Field label="新密码">
                  <input
                    type="password"
                    autoComplete="new-password"
                    required
                    minLength={8}
                    maxLength={72}
                    value={newPassword}
                    onChange={(event) => setNewPassword(event.target.value)}
                    placeholder="至少 8 位"
                  />
                </Field>
                <Field label="确认新密码">
                  <input
                    type="password"
                    autoComplete="new-password"
                    required
                    minLength={8}
                    maxLength={72}
                    value={confirmation}
                    onChange={(event) => setConfirmation(event.target.value)}
                    placeholder="再次输入新密码"
                  />
                </Field>
              </div>
              {passwordError && (
                <p className="form-error" role="alert">
                  {passwordError}
                </p>
              )}
              <div className="form-actions">
                <button className="btn btn-secondary" disabled={passwordBusy}>
                  {passwordBusy ? (
                    <LoaderCircle className="spin" size={16} />
                  ) : (
                    <KeyRound size={16} />
                  )}
                  {passwordBusy ? "正在更新…" : "更新密码"}
                </button>
              </div>
            </form>
          </section>
        </div>
      </div>
    </>
  );
}
