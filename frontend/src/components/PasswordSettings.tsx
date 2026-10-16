import { useState } from "react";
import type { FormEvent } from "react";
import {
  Check,
  Circle,
  Eye,
  EyeOff,
  KeyRound,
  LoaderCircle,
  ShieldCheck,
} from "lucide-react";
import { api, clearCsrf, errorMessage } from "../api";
import { passwordIssue } from "../accountValidation";

function PasswordInput({
  id,
  label,
  value,
  onChange,
  current = false,
  invalid = false,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  current?: boolean;
  invalid?: boolean;
}) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="password-input">
        <input
          id={id}
          type={visible ? "text" : "password"}
          autoComplete={current ? "current-password" : "new-password"}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          required
          minLength={current ? undefined : 8}
          maxLength={current ? undefined : 72}
          aria-invalid={invalid || undefined}
          aria-describedby={current ? undefined : "password-guidance"}
        />
        <button
          type="button"
          aria-label={`${visible ? "隐藏" : "显示"}${label}`}
          aria-pressed={visible}
          onClick={() => setVisible(!visible)}
        >
          {visible ? <EyeOff size={18} /> : <Eye size={18} />}
        </button>
      </div>
    </div>
  );
}

export default function PasswordSettings() {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const tooLong = new TextEncoder().encode(next).length > 72;
  const mismatch = Boolean(confirmation && next !== confirmation);
  const valid = !passwordIssue(current, next, confirmation);
  const requirements = [
    {
      label: "至少 8 位字符",
      met: next.trim().length > 0 && next.length >= 8 && !tooLong,
    },
    {
      label: "与当前密码不同",
      met: Boolean(current && next && next !== current),
    },
    {
      label: "两次输入一致",
      met: Boolean(next && confirmation && next === confirmation),
    },
  ];
  const change = (setter: (value: string) => void) => (value: string) => {
    setter(value);
    setError("");
  };

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const issue = passwordIssue(current, next, confirmation);
    if (issue) {
      setError(issue);
      return;
    }
    setBusy(true);
    setError("");
    try {
      await api("/account/password", "PUT", {
        currentPassword: current,
        newPassword: next,
      });
      clearCsrf();
      setCurrent("");
      setNext("");
      setConfirmation("");
      window.dispatchEvent(new Event("password-updated"));
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section
      className="panel account-form-panel"
      id="account-security"
      aria-labelledby="account-security-title"
    >
      <div className="account-section-heading">
        <span className="account-section-icon">
          <KeyRound size={21} />
        </span>
        <div>
          <h2 id="account-security-title">修改密码</h2>
          <p>建议使用不在其他网站使用的密码。</p>
        </div>
      </div>
      <div className="account-security-note">
        <ShieldCheck size={18} />
        <p>修改成功后会退出当前登录，请使用新密码重新登录。</p>
      </div>
      <form className="form-stack" onSubmit={submit}>
        <fieldset className="account-fields" disabled={busy}>
          <PasswordInput
            id="account-current-password"
            label="当前密码"
            value={current}
            onChange={change(setCurrent)}
            current
          />
          <div className="form-grid">
            <PasswordInput
              id="account-new-password"
              label="新密码"
              value={next}
              onChange={change(setNext)}
              invalid={tooLong}
            />
            <PasswordInput
              id="account-confirm-password"
              label="确认新密码"
              value={confirmation}
              onChange={change(setConfirmation)}
              invalid={mismatch}
            />
          </div>
        </fieldset>
        <div id="password-guidance">
          <ul className="password-requirements" aria-label="新密码要求">
            {requirements.map(({ label, met }) => (
              <li key={label} data-met={met}>
                {met ? <Check size={15} /> : <Circle size={13} />}
                <span>
                  {label}
                  <span className="sr-only">
                    {met ? "，已满足" : "，未满足"}
                  </span>
                </span>
              </li>
            ))}
          </ul>
          <p className="password-feedback" aria-live="polite">
            {tooLong
              ? "密码过长，请减少字数后重试。"
              : mismatch
                ? "两次输入还不一致，请检查确认密码。"
                : ""}
          </p>
        </div>
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        <div className="form-actions account-form-actions">
          <span className="account-action-note">更新后需要重新登录。</span>
          <button className="btn btn-primary" disabled={busy || !valid}>
            {busy ? (
              <LoaderCircle className="spin" size={16} />
            ) : (
              <KeyRound size={16} />
            )}
            {busy ? "正在更新…" : "更新密码"}
          </button>
        </div>
      </form>
    </section>
  );
}
