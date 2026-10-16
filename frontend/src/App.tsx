import { useEffect, useState } from "react";
import {
  CalendarDays,
  ChevronRight,
  CircleUserRound,
  Compass,
  LayoutDashboard,
  LogOut,
  Menu,
  Mountain,
  PanelLeftClose,
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
import Avatar from "./components/Avatar";
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
      notify("登录已过期，请重新登录。", "error");
    };
    const passwordChanged = () => {
      setUser(null);
      setView("dashboard");
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
        <Spinner text="准备你的旅行工作台" />
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
          }}
        />
        {toastElement}
      </>
    );
  const nav = [
    {
      id: "dashboard" as View,
      label: user.role === "CUSTOMER" ? "我的工作台" : "总览工作台",
      icon: LayoutDashboard,
    },
    {
      id: "routes" as View,
      label: user.role === "ADMIN" ? "线路管理" : "探索线路",
      icon: Compass,
    },
    {
      id: "orders" as View,
      label:
        user.role === "CUSTOMER"
          ? "我的预订"
          : user.role === "EMPLOYEE"
            ? "订单服务"
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
          <Brand light />
          <button
            className="sidebar-close icon-button"
            aria-label="收起菜单"
            onClick={() => setMenuOpen(false)}
          >
            <PanelLeftClose size={21} />
          </button>
        </div>
        <div className="workspace-label">
          <span className="status-dot" />
          {roleLabels[user.role]}工作空间
        </div>
        <div className="nav-group-label">工作空间</div>
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
              {view === item.id && <ChevronRight size={16} />}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-note">
            <Mountain size={28} strokeWidth={1.2} />
            <p>去看山，去看海。</p>
            <span>让每一次出发，都有迹可循。</span>
          </div>
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
            <Avatar user={user} />
            <div>
              <strong>{user.displayName}</strong>
              <small>{roleLabels[user.role]}</small>
            </div>
            <span className="user-online" />
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
            <span>山海行</span>
            <ChevronRight size={14} />
            <strong>{activeTitle}</strong>
          </div>
          <div className="topbar-right">
            <span className="today-date">
              <CalendarDays size={15} />
              {new Intl.DateTimeFormat("zh-CN", {
                month: "long",
                day: "numeric",
                weekday: "long",
              }).format(new Date())}
            </span>
            <button
              className="topbar-search"
              title="探索旅行线路"
              aria-label="探索旅行线路"
              onClick={() => navigate("routes")}
            >
              <Search size={19} />
            </button>
            <button
              className="topbar-avatar"
              onClick={() => navigate("account")}
              aria-label="打开个人账户"
            >
              <Avatar user={user} className="avatar-small" />
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
          <span>SHANHAI TRAVEL</span>
          <span>
            认真对待，每一程。{" "}
            <a
              href={`${import.meta.env.BASE_URL}image-credits.html`}
              target="_blank"
              rel="noreferrer"
            >
              图片来源
            </a>
          </span>
        </footer>
      </div>
      {toastElement}
    </div>
  );
}
