import { useEffect, useState } from "react";
import { api, errorMessage } from "../api";
import type { Conversation, User } from "../types";
import type { ChatState } from "../useChat";
import ChatThread from "./ChatThread";
import { SectionHeading } from "./UI";
import "./chat.css";

export default function Chat({ user, chat }: { user: User; chat: ChatState }) {
  const [selected, setSelected] = useState<number | null>(null);
  const [mobile, setMobile] = useState(false);
  const [peerId, setPeerId] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    const media = window.matchMedia("(max-width: 760px)");
    const changed = () => setMobile(media.matches);
    changed();
    media.addEventListener("change", changed);
    return () => media.removeEventListener("change", changed);
  }, []);
  const active =
    chat.conversations.find((c) => c.id === selected) ?? chat.conversations[0];
  async function start() {
    if (!peerId || busy) return;
    setBusy(true);
    setError("");
    try {
      const thread = await api<Conversation>("/chat/conversations", "POST", {
        peerId: Number(peerId),
      });
      await chat.refresh();
      setSelected(thread.id);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <SectionHeading
        eyebrow="TRAVEL CONCIERGE"
        title="在线咨询"
        description="每一程，都有人回应。和旅行顾问聊聊你的出发计划。"
        action={
          <span role="status">
            {chat.connection === "connected"
              ? "实时连接已接通"
              : "正在连接，消息将自动同步…"}
          </span>
        }
      />
      {(error || chat.error) && (
        <p role="alert">
          {error || chat.error}{" "}
          <button onClick={() => void chat.refresh()}>重新加载</button>
        </p>
      )}
      <section
        className={`panel chat-workspace ${selected === null ? "chat-closed" : "chat-open"}`}
        aria-label="在线咨询工作区"
      >
        <aside className="chat-sidebar" aria-label="咨询会话">
          <h2>我的会话</h2>
          {user.role === "CUSTOMER" && (
            <form
              className="field"
              onSubmit={(e) => {
                e.preventDefault();
                void start();
              }}
            >
              <label htmlFor="chat-advisor">发起一次咨询</label>
              <select
                id="chat-advisor"
                value={peerId}
                onChange={(e) => setPeerId(e.target.value)}
              >
                <option value="">选择旅行顾问</option>
                {chat.contacts.map((contact) => (
                  <option key={contact.id} value={contact.id}>
                    {contact.displayName}
                  </option>
                ))}
              </select>
              <button className="btn btn-secondary" disabled={!peerId || busy}>
                {busy ? "正在开启…" : "开始咨询"}
              </button>
            </form>
          )}
          {chat.loading && <p role="status">正在加载会话…</p>}
          {!chat.loading && !chat.conversations.length && (
            <p>消息会保留，稍后回来也能接着聊。</p>
          )}
          {chat.conversations.map((thread) => (
            <button
              key={thread.id}
              className="chat-thread"
              aria-current={active?.id === thread.id ? "true" : undefined}
              onClick={() => setSelected(thread.id)}
            >
              <strong>{thread.peer.displayName}</strong>
              {thread.unreadCount > 0 && (
                <b aria-label={`${thread.unreadCount} 条未读`}>
                  {" "}
                  · {thread.unreadCount}
                </b>
              )}
              <small>{thread.lastMessage ?? "咨询已开启，打个招呼吧"}</small>
            </button>
          ))}
        </aside>
        {active && (!mobile || selected !== null) ? (
          <ChatThread
            key={active.id}
            user={user}
            thread={active}
            chat={chat}
            onBack={() => setSelected(null)}
          />
        ) : (
          <div className="empty-state chat-welcome">
            <h2>山海很远，沟通很近。</h2>
            <p>选择一段会话，开始你的专属咨询。</p>
          </div>
        )}
      </section>
    </>
  );
}
