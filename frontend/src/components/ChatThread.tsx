import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { createChatRefresh } from "../chatRefresh";
import { prepareChatFocus } from "../chatFocus";
import { api, errorMessage } from "../api";
import { mergeMessages, reconcileMessages } from "../chat";
import type { ChatMessage, Conversation, User } from "../types";
import type { ChatState } from "../useChat";

export default function ChatThread({
  user,
  thread,
  chat,
  onBack,
}: {
  user: User;
  thread: Conversation;
  chat: ChatState;
  onBack: () => void;
}) {
  const { revision, refresh } = chat;
  const drafts = chat.drafts!;
  const {
    content: draft,
    sending,
    error: sendError,
  } = useSyncExternalStore(
    (listener) => drafts.subscribe(thread.id, listener),
    () => drafts.get(thread.id),
    () => drafts.get(thread.id),
  );
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [hasOlder, setHasOlder] = useState(false);
  const [olderBusy, setOlderBusy] = useState(false);
  const [atBottom, setAtBottom] = useState(true);
  const scroller = useRef<HTMLDivElement>(null);
  const composer = useRef<HTMLTextAreaElement>(null);
  const focusReturn = useRef<ReturnType<typeof prepareChatFocus>>(undefined);
  const pinned = useRef(true);
  const lastLoaded = useRef(0);
  const historyRefresh = useRef<() => Promise<void>>(async () => {});
  const readId = useRef(0);
  const mounted = useRef(true);
  const path = `/chat/conversations/${thread.id}`;
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      focusReturn.current?.cancel();
    };
  }, []);
  useEffect(() => {
    let cancelled = false;
    const updates = createChatRefresh(
      async () => {
        try {
          const result = await reconcileMessages((beforeId) => {
            if (cancelled) throw new Error("会话已关闭");
            return api<ChatMessage[]>(
              `${path}/messages${beforeId ? `?beforeId=${beforeId}` : ""}`,
            );
          }, lastLoaded.current);
          if (cancelled) return;
          if (!lastLoaded.current) setHasOlder(result.hasOlder);
          lastLoaded.current = result.messages.at(-1)?.id ?? lastLoaded.current;
          setMessages((current) => mergeMessages(current, result.messages));
          setError("");
        } finally {
          if (!cancelled) setLoading(false);
        }
      },
      (error) => {
        if (!cancelled) setError(errorMessage(error));
      },
    );
    historyRefresh.current = updates.refresh;
    return () => {
      cancelled = true;
      updates.dispose();
    };
  }, [path]);
  useEffect(() => {
    void historyRefresh.current();
  }, [path, revision]);
  useEffect(() => {
    if (pinned.current && scroller.current)
      scroller.current.scrollTop = scroller.current.scrollHeight;
  }, [messages]);
  const lastId = messages.at(-1)?.id ?? 0;
  useEffect(() => {
    if (
      loading ||
      error ||
      !atBottom ||
      !lastId ||
      !thread.unreadCount ||
      document.visibilityState !== "visible" ||
      lastLoaded.current < lastId ||
      readId.current >= lastId
    )
      return;
    const previous = readId.current;
    readId.current = lastId;
    void api(`${path}/read`, "POST", { messageId: lastId })
      .then(() => refresh())
      .catch(() => {
        readId.current = previous;
      });
  }, [
    path,
    lastId,
    loading,
    error,
    atBottom,
    thread.unreadCount,
    revision,
    refresh,
  ]);
  async function older() {
    if (olderBusy || !messages.length) return;
    setOlderBusy(true);
    const element = scroller.current;
    const height = element?.scrollHeight ?? 0;
    const top = element?.scrollTop ?? 0;
    try {
      const batch = await api<ChatMessage[]>(
        `${path}/messages?beforeId=${messages[0].id}`,
      );
      if (!mounted.current) return;
      pinned.current = false;
      setAtBottom(false);
      setHasOlder(batch.length === 50);
      setMessages((current) => mergeMessages(batch, current));
      setError("");
      requestAnimationFrame(() => {
        if (element) element.scrollTop = top + element.scrollHeight - height;
      });
    } catch (e) {
      if (mounted.current) setError(errorMessage(e));
    } finally {
      if (mounted.current) setOlderBusy(false);
    }
  }
  async function send() {
    const pending = drafts.get(thread.id);
    if (!thread.peer.enabled || pending.sending || !pending.content.trim())
      return;
    focusReturn.current?.cancel();
    const focus = prepareChatFocus(composer.current);
    focusReturn.current = focus;
    const message = await drafts.send(thread.id, (input) =>
      api<ChatMessage>(`${path}/messages`, "POST", input),
    );
    focus?.finish();
    if (!message) return;
    if (mounted.current) {
      pinned.current = true;
      setAtBottom(true);
      setMessages((current) => mergeMessages(current, [message]));
    }
    void refresh();
  }

  return (
    <section
      className="chat-conversation"
      aria-label={`与${thread.peer.displayName}的咨询`}
    >
      <header>
        <button className="btn btn-secondary chat-back" onClick={onBack}>
          返回会话
        </button>
        <h2>{thread.peer.displayName}</h2>
        <span>
          {thread.peer.enabled ? "专属旅行咨询" : "账号已停用，仅可查看历史"}
        </span>
      </header>
      <div
        className="chat-messages"
        role="log"
        aria-label="聊天消息"
        aria-live="polite"
        tabIndex={0}
        ref={scroller}
        onScroll={(e) => {
          const el = e.currentTarget;
          pinned.current =
            el.scrollHeight - el.scrollTop - el.clientHeight < 50;
          setAtBottom(pinned.current);
        }}
      >
        {loading && <p role="status">正在加载聊天记录…</p>}
        {hasOlder && (
          <button
            className="btn btn-secondary"
            disabled={olderBusy}
            onClick={() => void older()}
          >
            查看更早消息
          </button>
        )}
        {error && (
          <p role="alert">
            {error} <button onClick={() => void refresh()}>重新加载</button>
          </p>
        )}
        {!loading && !messages.length && !error && (
          <p>从一句你好开始，消息会为你保留。</p>
        )}
        {messages.map((message) => (
          <article
            key={message.id}
            className={`chat-message ${message.senderId === user.id ? "mine" : "theirs"}`}
          >
            <p>{message.content}</p>
            <small>
              <time dateTime={message.createdAt}>
                {new Date(message.createdAt).toLocaleString("zh-CN")}
              </time>
              {message.senderId === user.id &&
                (thread.peerReadId >= message.id ? " · 已读" : " · 已发送")}
            </small>
          </article>
        ))}
      </div>
      {!atBottom && (
        <button
          className="btn btn-secondary"
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
        {sendError && <p role="alert">{sendError} 内容已保留，可再次发送。</p>}
        <label htmlFor="chat-draft">输入聊天消息</label>
        <textarea
          id="chat-draft"
          ref={composer}
          value={draft}
          maxLength={2000}
          rows={3}
          disabled={sending || !thread.peer.enabled}
          placeholder="说说你的旅行计划…"
          onChange={(e) => drafts.edit(thread.id, e.target.value)}
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
        <footer>
          <small>Enter 发送 · Shift + Enter 换行 · {draft.length}/2000</small>
          <button
            className="btn btn-primary"
            disabled={!draft.trim() || sending || !thread.peer.enabled}
          >
            {sending ? "发送中…" : "发送"}
          </button>
        </footer>
      </form>
    </section>
  );
}
