import { useEffect, useState } from "react";
import {
  CircleUserRound,
  Compass,
  LayoutDashboard,
  LogOut,
  Menu,
  Search,
  Ticket,
  UsersRound,
  X,
} from "lucide-react";
import { api, ApiError, errorMessage, refreshCsrf } from "./api";
import { roleLabels } from "./assets";
import type { User, View } from "./types";
import Auth from "./components/Auth";
import Dashboard from "./components/Dashboard";
import Routes from "./components/Routes";
import Orders from "./components/Orders";
import Users from "./components/Users";
import Account from "./components/Account";
import { Brand, Spinner } from "./components/UI";

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [initializing, setInitializing] = useState(true);
  const [initError, setInitError] = useState("");
  const [view, setView] = useState<View>("dashboard");
  const [menuOpen, setMenuOpen] = useState(false);
  const [toast, setToast] = useState<{
    message: string;
    kind: "success" | "error";
  } | null>(null);
  const [logoutBusy, setLogoutBusy] = useState(false);
  const notify = (message: string, kind: "success" | "error" = "success") =>
    setToast({ message, kind });
  const navigate = (next: View) => {
    setView(next);
    setMenuOpen(false);
    window.scrollTo({ top: 0 });
  };
  async function initialize() {
    setInitializing(true);
    setInitError("");
    try {
      setUser(await api<User>("/auth/me"));
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401))
        setInitError(errorMessage(error));
    } finally {
      setInitializing(false);
    }
  }
  useEffect(() => {
    void initialize();
    const expired = () => {
      setUser(null);
      setMenuOpen(false);
      notify("登录已过期，请重新登录。", "error");
    };
    const passwordChanged = () => {
      setUser(null);
      setView("dashboard");
      setMenuOpen(false);
      notify("密码已更新，请重新登录。");
    };
    window.addEventListener("session-expired", expired);
    window.addEventListener("password-updated", passwordChanged);
    return () => {
      window.removeEventListener("session-expired", expired);
      window.removeEventListener("password-updated", passwordChanged);
    };
  }, []);
  useEffect(() => {
    if (!toast) return;
    const id = window.setTimeout(() => setToast(null), 5000);
    return () => clearTimeout(id);
  }, [toast]);
  useEffect(() => {
    const titles = {
      dashboard: "工作台",
      routes: "旅行线路",
      orders: "订单管理",
      users: "用户管理",
      account: "个人账户",
    };
    document.title = `${user ? titles[view] : "登录"} · 山海行`;
  }, [view, user]);
  async function logout() {
    setLogoutBusy(true);
    try {
      await api("/auth/logout", "POST", {});
      await refreshCsrf();
      setUser(null);
      setView("dashboard");
      setMenuOpen(false);
    } catch (error) {
      notify(errorMessage(error), "error");
    } finally {
      setLogoutBusy(false);
    }
  }
  const toastElement = toast && (
    <div
      className={`toast toast-${toast.kind}`}
      role={toast.kind === "error" ? "alert" : "status"}
    >
      <span>{toast.message}</span>
      <button aria-label="关闭提示" onClick={() => setToast(null)}>
        <X size={17} />
      </button>
    </div>
  );
  if (initializing)
    return (
      <div className="app-initializing">
        <Brand />
        <Spinner text="正在加载" />
      </div>
    );
  if (initError)
    return (
      <div className="app-initializing">
        <Brand />
        <p role="alert">{initError}</p>
        <button className="btn btn-primary" onClick={initialize}>
          重新连接
        </button>
      </div>
    );
  if (!user)
    return (
      <>
        <Auth
          onLogin={(next) => {
            setUser(next);
            setView("dashboard");
            setMenuOpen(false);
          }}
        />
        {toastElement}
      </>
    );
  const nav = [
    {
      id: "dashboard" as View,
      label: "工作台",
      icon: LayoutDashboard,
    },
    {
      id: "routes" as View,
      label: user.role === "ADMIN" ? "线路管理" : "旅行线路",
      icon: Compass,
    },
    {
      id: "orders" as View,
      label:
        user.role === "CUSTOMER"
          ? "我的订单"
          : user.role === "EMPLOYEE"
            ? "订单处理"
            : "全部订单",
      icon: Ticket,
    },
    ...(user.role === "ADMIN"
      ? [{ id: "users" as View, label: "用户管理", icon: UsersRound }]
      : []),
  ];
  const activeTitle =
    view === "account"
      ? "个人账户"
      : nav.find((item) => item.id === view)?.label;
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        跳到主要内容
      </a>
      {menuOpen && (
        <button
          className="sidebar-backdrop"
          aria-label="关闭菜单"
          onClick={() => setMenuOpen(false)}
        />
      )}
      <aside className={`sidebar ${menuOpen ? "sidebar-open" : ""}`}>
        <div className="sidebar-top">
          <Brand />
          <button
            className="sidebar-close icon-button"
            aria-label="关闭导航"
            onClick={() => setMenuOpen(false)}
          >
            <X size={21} />
          </button>
        </div>
        <nav aria-label="主导航">
          {nav.map((item) => (
            <button
              key={item.id}
              className={`nav-item ${view === item.id ? "active" : ""}`}
              onClick={() => navigate(item.id)}
              aria-current={view === item.id ? "page" : undefined}
            >
              <item.icon size={20} strokeWidth={1.7} />
              <span>{item.label}</span>
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <button
            className={`nav-item ${view === "account" ? "active" : ""}`}
            onClick={() => navigate("account")}
          >
            <CircleUserRound size={20} />
            <span>个人账户</span>
          </button>
          <button
            className="nav-item logout-button"
            onClick={logout}
            disabled={logoutBusy}
          >
            <LogOut size={19} />
            <span>{logoutBusy ? "正在退出…" : "退出登录"}</span>
          </button>
          <div className="sidebar-user">
            <span className="avatar">
              {user.displayName?.slice(0, 1) || "山"}
            </span>
            <div>
              <strong>{user.displayName}</strong>
              <small>{roleLabels[user.role]}</small>
            </div>
          </div>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            <button
              className="menu-button icon-button"
              aria-label="打开导航"
              onClick={() => setMenuOpen(true)}
            >
              <Menu size={22} />
            </button>
            <strong>{activeTitle}</strong>
          </div>
          <div className="topbar-right">
            <span className="topbar-role">{roleLabels[user.role]}</span>
            <button
              className="topbar-search"
              title="查看旅行线路"
              aria-label="查看旅行线路"
              onClick={() => navigate("routes")}
            >
              <Search size={19} />
              <span>查看线路</span>
            </button>
            <button
              className="avatar avatar-small"
              onClick={() => navigate("account")}
              aria-label="打开个人账户"
            >
              {user.displayName?.slice(0, 1) || "山"}
            </button>
          </div>
        </header>
        <main id="main-content" className="page-content" key={view}>
          {view === "dashboard" && (
            <Dashboard user={user} navigate={navigate} />
          )}{" "}
          {view === "routes" && (
            <Routes user={user} notify={notify} navigate={navigate} />
          )}{" "}
          {view === "orders" && <Orders user={user} notify={notify} />}{" "}
          {view === "users" && user.role === "ADMIN" && (
            <Users user={user} notify={notify} />
          )}{" "}
          {view === "account" && (
            <Account user={user} setUser={setUser} notify={notify} />
          )}
        </main>
        <footer className="app-footer">
          <span>山海行 · 旅行社管理系统</span>
          <a
            href={`${import.meta.env.BASE_URL}image-credits.html`}
            target="_blank"
            rel="noreferrer"
          >
            图片来源
          </a>
        </footer>
      </div>
      {toastElement}
    </div>
  );
}
