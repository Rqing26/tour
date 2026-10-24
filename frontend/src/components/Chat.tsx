import { useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  Check,
  CheckCheck,
  MessageCircle,
  Send,
  Wifi,
  WifiOff,
} from "lucide-react";
import { api, errorMessage } from "../api";
import type { ChatMessage, Conversation, User } from "../types";
import type { ChatState } from "../useChat";
import { Empty, ErrorState, SectionHeading, Spinner } from "./UI";
import "./chat.css";

const time = (date: string) =>
  new Intl.DateTimeFormat("zh-CN", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(date));

export default function Chat({ user, chat }: { user: User; chat: ChatState }) {
  const [selected, setSelected] = useState<number | null>(null);
  const [mobileList, setMobileList] = useState(true);
  const [mobile, setMobile] = useState(
    () => window.matchMedia("(max-width: 760px)").matches,
  );
  useEffect(() => {
    const media = window.matchMedia("(max-width: 760px)");
    const changed = () => setMobile(media.matches);
    media.addEventListener("change", changed);
    return () => media.removeEventListener("change", changed);
  }, []);
  const [peerId, setPeerId] = useState("");
  const [starting, setStarting] = useState(false);
  const [startError, setStartError] = useState("");
  const active =
    selected === null
      ? chat.conversations[0]
      : chat.conversations.find((c) => c.id === selected);
  async function start() {
    if (!peerId || starting) return;
    setStarting(true);
    setStartError("");
    try {
      const thread = await api<Conversation>("/chat/conversations", "POST", {
        peerId: Number(peerId),
      });
      await chat.refresh();
      setSelected(thread.id);
      setMobileList(false);
    } catch (error) {
      setStartError(errorMessage(error));
    } finally {
      setStarting(false);
    }
  }
  return (
    <>
      <SectionHeading
        eyebrow="TRAVEL CONCIERGE"
        title="在线咨询"
        description={
          user.role === "CUSTOMER"
            ? "行程怎么选、出发前要准备什么，都可以和顾问聊聊。"
            : "及时回应每一份期待，陪旅行者安心出发。"
        }
        action={
          <span className={`chat-connection ${chat.connection}`} role="status">
            {chat.connection === "connected" ? (
              <Wifi size={15} />
            ) : (
              <WifiOff size={15} />
            )}
            {chat.connection === "connected"
              ? "实时连接已接通"
              : chat.connection === "connecting"
                ? "正在连接…"
                : "连接中断，正在重连…"}
          </span>
        }
      />
      {chat.error && (
        <ErrorState message={chat.error} retry={() => void chat.refresh()} />
      )}
      <section
        className={`chat-workspace ${mobileList ? "chat-show-list" : "chat-show-thread"}`}
        aria-label="在线咨询工作区"
      >
        <aside className="chat-sidebar" aria-label="咨询会话">
          <div className="chat-list-heading">
            <h2>我的会话</h2>
            <span>{chat.conversations.length}</span>
          </div>
          {user.role === "CUSTOMER" && (
            <div className="chat-start">
              <label htmlFor="chat-advisor">发起一次咨询</label>
              <select
                id="chat-advisor"
                value={peerId}
                onChange={(e) => setPeerId(e.target.value)}
              >
                <option value="">
                  {chat.contacts.length ? "选择旅行顾问" : "暂无可咨询的顾问"}
                </option>
                {chat.contacts.map((contact) => (
                  <option key={contact.id} value={contact.id}>
                    {contact.displayName}
                  </option>
                ))}
              </select>
              <button
                className="btn btn-secondary"
                disabled={!peerId || starting}
                onClick={() => void start()}
              >
                <MessageCircle size={16} />
                {starting ? "正在开启…" : "开始咨询"}
              </button>
              {startError && (
                <p className="chat-error" role="alert">
                  {startError}
                </p>
              )}
            </div>
          )}
          <div className="chat-threads">
            {chat.loading ? (
              <Spinner text="加载会话" />
            ) : !chat.conversations.length ? (
              <p className="chat-list-empty">
                {user.role === "CUSTOMER"
                  ? "选择一位顾问，聊聊你的旅行计划。"
                  : "旅行者发起咨询后，会话会出现在这里。"}
              </p>
            ) : (
              chat.conversations.map((thread) => (
                <button
                  key={thread.id}
                  className={`chat-thread ${active?.id === thread.id ? "selected" : ""}`}
                  aria-current={active?.id === thread.id ? "true" : undefined}
                  onClick={() => {
                    setSelected(thread.id);
                    setMobileList(false);
                  }}
                >
                  <span className="chat-person" aria-hidden="true">
                    {thread.peer.displayName.slice(0, 1)}
                  </span>
                  <span className="chat-thread-copy">
                    <strong>{thread.peer.displayName}</strong>
                    <small>
                      {thread.lastMessage ?? "咨询已开启，打个招呼吧"}
                    </small>
                  </span>
                  <span className="chat-thread-meta">
                    <time>{time(thread.updatedAt).split(" ").pop()}</time>
                    {thread.unreadCount > 0 && (
                      <b
                        className="chat-unread"
                        aria-label={`${thread.unreadCount} 条未读`}
                      >
                        {thread.unreadCount > 99 ? "99+" : thread.unreadCount}
                      </b>
                    )}
                  </span>
                </button>
              ))
            )}
          </div>
          <p className="chat-sidebar-note">消息会保留，稍后回来也能接着聊。</p>
        </aside>
        {active && (!mobile || !mobileList) ? (
          <ConversationPane
            key={active.id}
            user={user}
            conversation={active}
            chat={chat}
            onBack={() => setMobileList(true)}
          />
        ) : (
          <div className="chat-welcome">
            <span className="chat-welcome-icon">
              <MessageCircle size={36} strokeWidth={1.3} />
            </span>
            <h2>每一程，都有人回应</h2>
            <p>
              {user.role === "CUSTOMER"
                ? "选择旅行顾问，开启你的专属咨询。"
                : "在左侧选择会话，开始为旅行者解答。"}
            </p>
            <span className="chat-welcome-caption">山海很远，沟通很近。</span>
          </div>
        )}
      </section>
    </>
  );
}

function ConversationPane({
  user,
  conversation,
  chat,
  onBack,
}: {
  user: User;
  conversation: Conversation;
  chat: ChatState;
  onBack: () => void;
}) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [historyError, setHistoryError] = useState("");
  const [hasOlder, setHasOlder] = useState(false);
  const [olderBusy, setOlderBusy] = useState(false);
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState("");
  const [atBottom, setAtBottom] = useState(true);
  const scroller = useRef<HTMLDivElement>(null);
  const pinned = useRef(true);
  const readId = useRef(0);
  const retry = useRef<{ content: string; clientId: string } | null>(null);
  const mounted = useRef(true);
  const sendingRef = useRef(false);
  const historyId = useRef(0);
  const historyInitialized = useRef(false);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      const latest = await api<ChatMessage[]>(
        `/chat/conversations/${conversation.id}/messages`,
      );
      let batch = latest;
      const collected = [...latest];
      // Fill every missed page before acknowledging messages after a reconnection.
      while (
        historyId.current > 0 &&
        batch.length === 50 &&
        batch[0].id > historyId.current
      ) {
        batch = await api<ChatMessage[]>(
          `/chat/conversations/${conversation.id}/messages?beforeId=${batch[0].id}`,
        );
        collected.push(...batch);
        if (cancelled) return;
      }
      if (cancelled) return;
      if (!historyInitialized.current) {
        setHasOlder(latest.length === 50);
        historyInitialized.current = true;
      }
      historyId.current = latest.at(-1)?.id ?? historyId.current;
      setMessages((previous) => {
        const merged = new Map(previous.map((m) => [m.id, m]));
        collected.forEach((m) => merged.set(m.id, m));
        return [...merged.values()].sort((a, b) => a.id - b.id);
      });
      setError("");
    };
    void load()
      .catch((error) => {
        if (!cancelled) setError(errorMessage(error));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [conversation.id, chat.revision]);

  useEffect(() => {
    const element = scroller.current;
    if (element && pinned.current) element.scrollTop = element.scrollHeight;
  }, [messages]);

  const lastId = messages.at(-1)?.id ?? 0;
  useEffect(() => {
    if (
      loading ||
      !lastId ||
      !atBottom ||
      document.visibilityState !== "visible" ||
      historyId.current < lastId ||
      !!error ||
      conversation.unreadCount === 0 ||
      readId.current >= lastId
    )
      return;
    const previous = readId.current;
    readId.current = lastId;
    void api(`/chat/conversations/${conversation.id}/read`, "POST", {
      messageId: lastId,
    })
      .then(() => chat.refresh())
      .catch(() => {
        readId.current = previous;
      });
  }, [
    lastId,
    loading,
    atBottom,
    conversation.id,
    conversation.unreadCount,
    chat.revision,
    chat.refresh,
    error,
  ]);

  async function older() {
    if (!messages.length || olderBusy) return;
    setOlderBusy(true);
    setHistoryError("");
    const element = scroller.current;
    const height = element?.scrollHeight ?? 0;
    const top = element?.scrollTop ?? 0;
    try {
      const batch = await api<ChatMessage[]>(
        `/chat/conversations/${conversation.id}/messages?beforeId=${messages[0].id}`,
      );
      if (!mounted.current) return;
      pinned.current = false;
      setAtBottom(false);
      setHasOlder(batch.length === 50);
      setMessages((current) => {
        const merged = new Map([...batch, ...current].map((m) => [m.id, m]));
        return [...merged.values()].sort((a, b) => a.id - b.id);
      });
      requestAnimationFrame(() => {
        if (element) element.scrollTop = top + element.scrollHeight - height;
      });
    } catch (error) {
      if (mounted.current) setHistoryError(errorMessage(error));
    } finally {
      if (mounted.current) setOlderBusy(false);
    }
  }

  async function send() {
    const content = draft.trim();
    if (!content || sendingRef.current || !conversation.peer.enabled) return;
    sendingRef.current = true;
    setSending(true);
    setSendError("");
    const input =
      retry.current?.content === content
        ? retry.current
        : { content, clientId: crypto.randomUUID() };
    retry.current = input;
    try {
      const message = await api<ChatMessage>(
        `/chat/conversations/${conversation.id}/messages`,
        "POST",
        input,
      );
      if (!mounted.current) return;
      pinned.current = true;
      setAtBottom(true);
      setMessages((current) =>
        [...current.filter((m) => m.id !== message.id), message].sort(
          (a, b) => a.id - b.id,
        ),
      );
      setDraft("");
      retry.current = null;
      void chat.refresh();
    } catch (error) {
      if (mounted.current) setSendError(errorMessage(error));
    } finally {
      sendingRef.current = false;
      if (mounted.current) setSending(false);
    }
  }
  return (
    <div className="chat-conversation">
      <header className="chat-conversation-heading">
        <button
          className="icon-button chat-back"
          onClick={onBack}
          aria-label="返回会话列表"
        >
          <ArrowLeft size={20} />
        </button>
        <span className="chat-person" aria-hidden="true">
          {conversation.peer.displayName.slice(0, 1)}
        </span>
        <div>
          <h2>{conversation.peer.displayName}</h2>
          <p>
            {conversation.peer.role === "EMPLOYEE"
              ? "你的旅行顾问"
              : "旅行者咨询"}
            {!conversation.peer.enabled && " · 账号已停用"}
          </p>
        </div>
        <MessageCircle
          className="chat-heading-icon"
          size={22}
          strokeWidth={1.4}
        />
      </header>
      <div
        className="chat-messages"
        ref={scroller}
        role="log"
        aria-label="聊天消息"
        aria-live="polite"
        aria-relevant="additions"
        tabIndex={0}
        onScroll={() => {
          const el = scroller.current;
          if (el) {
            pinned.current =
              el.scrollHeight - el.scrollTop - el.clientHeight < 50;
            setAtBottom(pinned.current);
          }
        }}
      >
        {loading ? (
          <Spinner text="加载聊天记录" />
        ) : error ? (
          <ErrorState message={error} retry={() => void chat.refresh()} />
        ) : (
          <>
            {hasOlder && (
              <button
                className="chat-load-older"
                disabled={olderBusy}
                onClick={() => void older()}
              >
                {olderBusy ? "正在加载…" : "查看更早消息"}
              </button>
            )}
            {historyError && (
              <p className="chat-error" role="alert">
                {historyError}
              </p>
            )}
            {!messages.length && (
              <Empty
                title="从一句你好开始"
                description="把你的想法留在这里，对方回来后也能看到。"
              />
            )}
            {messages.map((message) => {
              const mine = message.senderId === user.id;
              const read = conversation.peerReadId >= message.id;
              return (
                <article
                  key={message.id}
                  className={`chat-message ${mine ? "mine" : "theirs"}`}
                >
                  <p>{message.content}</p>
                  <div className="chat-message-meta">
                    <time dateTime={message.createdAt}>
                      {time(message.createdAt)}
                    </time>
                    {mine && (
                      <span>
                        {read ? <CheckCheck size={13} /> : <Check size={13} />}
                        {read ? "已读" : "已发送"}
                      </span>
                    )}
                  </div>
                </article>
              );
            })}
          </>
        )}
      </div>
      {!atBottom && (
        <button
          className="chat-latest"
          onClick={() => {
            pinned.current = true;
            setAtBottom(true);
            if (scroller.current)
              scroller.current.scrollTop = scroller.current.scrollHeight;
          }}
        >
          回到最新消息 ↓
        </button>
      )}
      <form
        className="chat-composer"
        onSubmit={(e) => {
          e.preventDefault();
          void send();
        }}
      >
        {!conversation.peer.enabled && (
          <p className="chat-error" role="status">
            对方账号已停用，可以查看历史消息。
          </p>
        )}
        {sendError && (
          <p className="chat-error" role="alert">
            {sendError} 内容已保留，可再次发送。
          </p>
        )}
        <label className="sr-only" htmlFor="chat-draft">
          输入聊天消息
        </label>
        <textarea
          id="chat-draft"
          placeholder="说说你的旅行计划…"
          value={draft}
          maxLength={2000}
          rows={3}
          disabled={sending || !conversation.peer.enabled}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (
              e.key === "Enter" &&
              !e.shiftKey &&
              !e.nativeEvent.isComposing &&
              e.keyCode !== 229
            ) {
              e.preventDefault();
              void send();
            }
          }}
        />
        <div className="chat-composer-footer">
          <span>
            Enter 发送 · Shift + Enter 换行 <small>{draft.length}/2000</small>
          </span>
          <button
            className="btn btn-primary"
            type="submit"
            disabled={!draft.trim() || sending || !conversation.peer.enabled}
          >
            <Send size={16} />
            {sending ? "发送中…" : "发送"}
          </button>
        </div>
      </form>
    </div>
  );
}
