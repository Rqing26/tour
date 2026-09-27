import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import {
  ArrowRight,
  Eye,
  EyeOff,
  LoaderCircle,
  MapPin,
  ShieldCheck,
} from "lucide-react";
import { api, errorMessage, refreshCsrf } from "../api";
import { photo } from "../assets";
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
  const [demoMode, setDemoMode] = useState(false);
  useEffect(() => {
    api<{ demoMode: boolean }>("/auth/config")
      .then((data) => setDemoMode(data.demoMode))
      .catch(() => {});
  }, []);
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
        setNotice("注册成功。现在可以登录，开启你的旅程。");
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
      <section
        className="auth-story"
        style={{ backgroundImage: `url(${photo("guilin")})` }}
      >
        <Brand light />
        <div className="auth-story-copy">
          <p className="eyebrow">GO FURTHER. FEEL MORE.</p>
          <h1>
            心有山海，
            <br />
            一路同行。
          </h1>
          <p>
            把繁杂交给我们，
            <br />
            把时间留给沿途的风景。
          </p>
          <div className="auth-location">
            <MapPin size={16} />
            <span>在山水之间，发现下一段旅程</span>
          </div>
        </div>
        <span className="auth-copyright">
          © {new Date().getFullYear()} 山海行 · SHANHAI TRAVEL
        </span>
      </section>
      <section className="auth-form-section">
        <div className="auth-mobile-brand">
          <Brand />
        </div>
        <div className="auth-form-wrap">
          <p className="eyebrow">YOUR JOURNEY STARTS HERE</p>
          <h2>{register ? "遇见下一段精彩" : "欢迎回来"}</h2>
          <p className="auth-subtitle">
            {register
              ? "创建旅行者账户，让期待变成出发。"
              : "登录山海行，继续你的旅行与工作。"}
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
              <ArrowRight size={18} />
            </button>
          </form>
          <p className="auth-switch">
            {register ? "已经有账户？" : "还没有账户？"}
            <button
              onClick={() => {
                setRegister(!register);
                setError("");
                setNotice("");
              }}
            >
              {register ? "返回登录" : "注册旅行者账户"}
            </button>
          </p>
          {demoMode && !register && (
            <div className="demo-access">
              <div className="demo-access-title">
                <ShieldCheck size={16} />
                <strong>本地演示体验</strong>
                <span>示例数据</span>
              </div>
              <p>选择身份填入账户，点击登录体验不同工作台。</p>
              <div className="demo-roles">
                {[
                  ["admin", "管理员"],
                  ["employee", "旅行顾问"],
                  ["customer", "旅行者"],
                ].map(([value, label]) => (
                  <button
                    key={value}
                    onClick={() => {
                      setUsername(value);
                      setPassword("Travel@2026");
                      setError("");
                    }}
                  >
                    {label}
                    <ArrowRight size={13} />
                  </button>
                ))}
              </div>
              <small>演示密码：Travel@2026</small>
            </div>
          )}
        </div>
        <div className="auth-footer">
          <span className="status-dot" /> 每一段旅程，都值得认真对待{" "}
          <a
            href={`${import.meta.env.BASE_URL}image-credits.html`}
            target="_blank"
            rel="noreferrer"
          >
            图片来源
          </a>
        </div>
      </section>
    </main>
  );
}
