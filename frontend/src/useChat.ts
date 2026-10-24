import { useCallback, useEffect, useRef, useState } from "react";
import { api, errorMessage } from "./api";
import type { ChatContact, Conversation, User } from "./types";

export function useChat(user: User | null) {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [contacts, setContacts] = useState<ChatContact[]>([]);
  const [connection, setConnection] = useState<
    "connecting" | "connected" | "reconnecting"
  >("connecting");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const refreshRef = useRef<() => Promise<void>>(async () => {});
  const refresh = useCallback(() => refreshRef.current(), []);

  useEffect(() => {
    setConversations([]);
    setContacts([]);
    setError("");
    setLoading(true);
    setConnection("connecting");
    if (!user || user.role === "ADMIN") {
      refreshRef.current = async () => {};
      return;
    }
    let disposed = false;
    let running = false;
    let queued = false;
    const load = async () => {
      if (running) {
        queued = true;
        return;
      }
      running = true;
      do {
        queued = false;
        try {
          const [threads, people] = await Promise.all([
            api<Conversation[]>("/chat/conversations"),
            api<ChatContact[]>("/chat/contacts"),
          ]);
          if (!disposed) {
            setConversations(threads);
            setContacts(people);
            setError("");
            setRevision((n) => n + 1);
          }
        } catch (error) {
          if (!disposed) setError(errorMessage(error));
        } finally {
          if (!disposed) setLoading(false);
        }
      } while (queued && !disposed);
      running = false;
    };
    refreshRef.current = load;
    void load();
    let events: EventSource | null = null;
    const ready = () => {
      if (disposed) return;
      setConnection("connected");
      void load(); // Reconcile history after every reconnection, including missed events.
    };
    const connect = () => {
      events?.close();
      events = new EventSource("/travel/api/chat/events");
      events.addEventListener("ready", ready);
      events.addEventListener("chat", () => void load());
      events.onerror = () => {
        if (!disposed) {
          setConnection("reconnecting");
          void load();
        }
      };
    };
    const offline = () => {
      events?.close();
      setConnection("reconnecting");
    };
    const online = () => {
      if (!disposed) {
        connect();
        void load();
      }
    };
    connect();
    window.addEventListener("offline", offline);
    window.addEventListener("online", online);
    const visible = () => {
      if (document.visibilityState === "visible") void load();
    };
    window.addEventListener("focus", visible);
    document.addEventListener("visibilitychange", visible);
    const fallback = window.setInterval(visible, 30000);
    return () => {
      disposed = true;
      events?.close();
      window.removeEventListener("offline", offline);
      window.removeEventListener("online", online);
      clearInterval(fallback);
      window.removeEventListener("focus", visible);
      document.removeEventListener("visibilitychange", visible);
      refreshRef.current = async () => {};
    };
  }, [user?.id, user?.role]);

  return {
    conversations,
    contacts,
    connection,
    error,
    loading,
    revision,
    refresh,
    unread: conversations.reduce((sum, thread) => sum + thread.unreadCount, 0),
  };
}
export type ChatState = ReturnType<typeof useChat>;
