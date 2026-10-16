import { useState, useSyncExternalStore } from "react";
import { AVATAR_CHANGED, avatarKey, readAvatar } from "../avatar";
import type { User } from "../types";

function subscribe(callback: () => void) {
  window.addEventListener(AVATAR_CHANGED, callback);
  window.addEventListener("storage", callback);
  return () => {
    window.removeEventListener(AVATAR_CHANGED, callback);
    window.removeEventListener("storage", callback);
  };
}

export function useAvatar(user: User) {
  const key = avatarKey(user);
  return useSyncExternalStore(
    subscribe,
    () => readAvatar(key),
    () => null,
  );
}

export default function Avatar({
  user,
  className = "",
}: {
  user: User;
  className?: string;
}) {
  const source = useAvatar(user);
  const [failed, setFailed] = useState<string | null>(null);
  return (
    <span
      className={`avatar ${className}`}
      role="img"
      aria-label={`${user.displayName}的头像`}
    >
      {source && source !== failed ? (
        <img src={source} alt="" onError={() => setFailed(source)} />
      ) : (
        <span aria-hidden="true">{user.displayName.slice(0, 1) || "山"}</span>
      )}
    </span>
  );
}
