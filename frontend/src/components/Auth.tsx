import { useState } from "react";
import type { FormEvent } from "react";
import { Eye, EyeOff, LoaderCircle } from "lucide-react";
import { api, errorMessage, refreshCsrf } from "../api";
import type { User } from "../types";
import { Brand, Field } from "./UI";

export default function Auth({ onLogin }: { onLogin: (user: User) => void }) {
  const [register, setRegister] = useState(false);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [phone, setPhone] = useState("");
  const [visible, setVisible] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");
    try {
      if (register) {
        await api<User>("/auth/register", "POST", {
          username,
          password,
          displayName,
          phone,
        });
        setRegister(false);
        setNotice("注册成功，请登录。");
      } else {
        const user = await api<User>("/auth/login", "POST", {
          username,
          password,
        });
        await refreshCsrf();
        onLogin(user);
      }
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="auth-layout">
      <header className="auth-topbar">
        <Brand />
      </header>
      <section className="auth-form-section">
        <div className="auth-form-wrap">
          <h1>{register ? "创建旅行者账户" : "登录"}</h1>
          <p className="auth-subtitle">
            {register
              ? "填写基本信息以查看和预订线路。"
              : "使用山海行账户继续。"}
          </p>
          <form className="form-stack" onSubmit={submit}>
            <Field label="用户名">
              <input
                autoFocus
                autoComplete="username"
                required
                minLength={3}
                maxLength={40}
                pattern="[A-Za-z0-9_]+"
                title="请使用 3 至 40 位字母、数字或下划线"
                placeholder="请输入用户名"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </Field>
            {register && (
              <>
                <Field label="姓名 / 称呼">
                  <input
                    required
                    maxLength={50}
                    placeholder="我们该如何称呼你"
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                  />
                </Field>
                <Field label="手机号码">
                  <input
                    autoComplete="tel"
                    inputMode="tel"
                    required
                    minLength={5}
                    maxLength={25}
                    pattern="[+0-9 \(\)\-]{5,25}"
                    title="请填写 5 至 25 位联系电话，可包含数字、空格、加号、括号和连字符"
                    placeholder="用于行程联系"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                  />
                </Field>
              </>
            )}
            <Field
              label="密码"
              hint={register ? "至少 8 位，请包含字母和数字。" : undefined}
            >
              <span className="password-input">
                <input
                  autoComplete={register ? "new-password" : "current-password"}
                  type={visible ? "text" : "password"}
                  required
                  minLength={register ? 8 : 1}
                  maxLength={72}
                  placeholder={register ? "设置安全密码" : "请输入密码"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
                <button
                  type="button"
                  aria-label={visible ? "隐藏密码" : "显示密码"}
                  onClick={() => setVisible(!visible)}
                >
                  {visible ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </span>
            </Field>
            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}
            {notice && (
              <p className="form-success" role="status">
                {notice}
              </p>
            )}
            <button className="btn btn-primary auth-submit" disabled={busy}>
              {busy ? <LoaderCircle className="spin" size={18} /> : null}
              {register ? "创建账户" : "登录"}
            </button>
          </form>
          <p className="auth-switch">
            {register ? "已有账户？" : "没有账户？"}
            <button
              onClick={() => {
                setRegister(!register);
                setError("");
                setNotice("");
              }}
            >
              {register ? "返回登录" : "注册"}
            </button>
          </p>
        </div>
      </section>
      <footer className="auth-footer">
        © {new Date().getFullYear()} 山海行
      </footer>
    </main>
  );
}
