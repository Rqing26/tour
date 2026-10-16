import Pagination, { usePagination } from "./Pagination";
import { useState } from "react";
import type { FormEvent } from "react";
import {
  Pencil,
  Plus,
  ShieldCheck,
  Trash2,
  UserRound,
  UsersRound,
} from "lucide-react";
import { api, errorMessage } from "../api";
import { roleLabels } from "../assets";
import type { Notify, Role, User } from "../types";
import { useResource } from "../useResource";
import {
  Empty,
  ErrorState,
  Field,
  Modal,
  SearchInput,
  SectionHeading,
  Spinner,
} from "./UI";

export default function Users({
  user,
  notify,
}: {
  user: User;
  notify: Notify;
}) {
  const { data, loading, error, refresh } = useResource<User[]>("/users");
  const [search, setSearch] = useState("");
  const [role, setRole] = useState("");
  const [editing, setEditing] = useState<User | "new" | null>(null);
  const [deleting, setDeleting] = useState<User | null>(null);
  const [busy, setBusy] = useState(false);
  const [deleteError, setDeleteError] = useState("");
  const filtered =
    data?.filter(
      (item) =>
        (!role || item.role === role) &&
        (!search ||
          `${item.username} ${item.displayName} ${item.phone}`
            .toLowerCase()
            .includes(search.toLowerCase())),
    ) || [];
  const pagination = usePagination(filtered, 10, `${search}|${role}`);
  async function remove() {
    if (!deleting) return;
    setBusy(true);
    setDeleteError("");
    try {
      await api(`/users/${deleting.id}`, "DELETE");
      setDeleting(null);
      refresh();
      notify("用户已删除。");
    } catch (error) {
      setDeleteError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <SectionHeading
        title="用户管理"
        description="管理账户角色、联系方式和启用状态。"
        action={
          <button className="btn btn-primary" onClick={() => setEditing("new")}>
            <Plus size={17} />
            新增用户
          </button>
        }
      />
      <div className="people-stats">
        {(
          [
            { role: "CUSTOMER", label: "旅行者", icon: UsersRound },
            { role: "EMPLOYEE", label: "旅行顾问", icon: UserRound },
            { role: "ADMIN", label: "管理员", icon: ShieldCheck },
          ] as const
        ).map((item) => (
          <button
            className={role === item.role ? "selected" : ""}
            key={item.role}
            onClick={() => setRole(role === item.role ? "" : item.role)}
          >
            <span className="people-stat-icon">
              <item.icon size={21} />
            </span>
            <span>
              {item.label}
              <strong>
                {data?.filter((person) => person.role === item.role).length ||
                  0}
                <small>位</small>
              </strong>
            </span>
          </button>
        ))}
      </div>
      <section className="panel">
        <div className="users-toolbar">
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder="搜索姓名、用户名或手机号…"
            label="搜索用户"
          />
          <select
            aria-label="按用户角色筛选"
            value={role}
            onChange={(e) => setRole(e.target.value)}
          >
            <option value="">全部角色</option>
            <option value="CUSTOMER">旅行者</option>
            <option value="EMPLOYEE">旅行顾问</option>
            <option value="ADMIN">管理员</option>
          </select>
        </div>
        {loading ? (
          <Spinner />
        ) : error ? (
          <ErrorState message={error} retry={refresh} />
        ) : !filtered.length ? (
          <Empty
            title="没有找到用户"
            description="试试其他姓名，或调整角色筛选。"
          />
        ) : (
          <>
            <div className="table-scroll">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>用户</th>
                    <th>账户角色</th>
                    <th>联系手机</th>
                    <th>账户状态</th>
                    <th className="align-right">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {pagination.visibleItems.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <div className="person-cell">
                          <span
                            className={`person-avatar person-${item.role.toLowerCase()}`}
                          >
                            {item.displayName.slice(0, 1)}
                          </span>
                          <div>
                            <strong>
                              {item.displayName}
                              {item.id === user.id && (
                                <span className="self-tag">我</span>
                              )}
                            </strong>
                            <small>@{item.username}</small>
                          </div>
                        </div>
                      </td>
                      <td>
                        <span
                          className={`role-badge role-${item.role.toLowerCase()}`}
                        >
                          {roleLabels[item.role]}
                        </span>
                      </td>
                      <td>{item.phone || "—"}</td>
                      <td>
                        <span
                          className={`badge ${item.enabled ? "status-completed" : "status-cancelled"}`}
                        >
                          <i />
                          {item.enabled ? "正常" : "已停用"}
                        </span>
                      </td>
                      <td>
                        <div className="table-actions">
                          <button
                            className="icon-button"
                            aria-label={`编辑${item.displayName}`}
                            title="编辑用户"
                            onClick={() => setEditing(item)}
                          >
                            <Pencil size={17} />
                          </button>
                          {item.role !== "ADMIN" && (
                            <button
                              className="icon-button danger"
                              aria-label={`删除${item.displayName}`}
                              title="删除用户"
                              onClick={() => {
                                setDeleting(item);
                                setDeleteError("");
                              }}
                            >
                              <Trash2 size={17} />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="table-footer">
              <span>共 {filtered.length} 位用户</span>
              <span>停用账户将限制其登录访问</span>
              <Pagination
                page={pagination.page}
                pages={pagination.pages}
                onChange={pagination.setPage}
              />
            </div>
          </>
        )}
      </section>
      {editing && (
        <UserForm
          existing={editing === "new" ? undefined : editing}
          isSelf={editing !== "new" && editing.id === user.id}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            refresh();
            notify(editing === "new" ? "新用户已创建。" : "用户信息已更新。");
          }}
        />
      )}
      {deleting && (
        <Modal
          title="删除这个用户？"
          subtitle={`${deleting.displayName} · @${deleting.username}`}
          onClose={() => {
            if (!busy) setDeleting(null);
          }}
        >
          <p className="dialog-description">
            删除后无法恢复。有关联订单的账户不能删除；你可以通过编辑账户将其停用。
          </p>
          {deleteError && (
            <p className="form-error" role="alert">
              {deleteError}
            </p>
          )}
          <div className="form-actions">
            <button
              className="btn btn-secondary"
              disabled={busy}
              onClick={() => setDeleting(null)}
            >
              保留用户
            </button>
            <button className="btn btn-danger" disabled={busy} onClick={remove}>
              {busy ? "正在删除…" : "确认删除"}
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}

function UserForm({
  existing,
  isSelf,
  onClose,
  onSaved,
}: {
  existing?: User;
  isSelf: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [username, setUsername] = useState(existing?.username || "");
  const [displayName, setDisplayName] = useState(existing?.displayName || "");
  const [phone, setPhone] = useState(existing?.phone || "");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<Role>(existing?.role || "EMPLOYEE");
  const [enabled, setEnabled] = useState(existing?.enabled ?? true);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function save(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api(
        existing ? `/users/${existing.id}` : "/users",
        existing ? "PUT" : "POST",
        existing
          ? { displayName, phone, enabled }
          : { username, displayName, phone, password, role },
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
      title={existing ? "编辑用户信息" : "新增用户"}
      subtitle={
        existing ? "更新联系方式与账户状态。" : "为旅行顾问或旅行者创建账户。"
      }
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <form className="form-stack" onSubmit={save}>
        <Field
          label="用户名"
          hint={existing ? "用户名创建后不可修改。" : undefined}
        >
          <input
            required
            disabled={!!existing}
            minLength={3}
            maxLength={40}
            pattern="[A-Za-z0-9_]+"
            title="请使用 3 至 40 位字母、数字或下划线"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
        </Field>
        <div className="form-grid">
          <Field label="姓名 / 称呼">
            <input
              required
              maxLength={50}
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
            />
          </Field>
          <Field label="手机号码">
            <input
              required
              minLength={5}
              maxLength={25}
              pattern="[+0-9 \(\)\-]{5,25}"
              title="请填写 5 至 25 位联系电话，可包含数字、空格、加号、括号和连字符"
              inputMode="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
            />
          </Field>
        </div>
        {!existing && (
          <>
            <Field label="账户角色">
              <select
                value={role}
                onChange={(e) => setRole(e.target.value as Role)}
              >
                <option value="EMPLOYEE">旅行顾问</option>
                <option value="CUSTOMER">旅行者</option>
              </select>
            </Field>
            <Field label="初始密码" hint="至少 8 位，请包含字母和数字。">
              <input
                required
                type="password"
                minLength={8}
                maxLength={72}
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </Field>
          </>
        )}
        {existing && (
          <label className="checkbox-field">
            <input
              type="checkbox"
              checked={enabled}
              disabled={isSelf || existing.role === "ADMIN"}
              onChange={(e) => setEnabled(e.target.checked)}
            />
            <span>
              <strong>启用此账户</strong>
              <small>
                {existing.role === "ADMIN"
                  ? "管理员账户不能停用。"
                  : "取消勾选后，用户将无法继续访问系统。"}
              </small>
            </span>
          </label>
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
            {busy ? "正在保存…" : existing ? "保存修改" : "创建用户"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
