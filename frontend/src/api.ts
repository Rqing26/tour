let csrfToken: string | null = null;
export const clearCsrf = () => {
  csrfToken = null;
};
const prefix = "/travel/api";

export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

export async function refreshCsrf() {
  const response = await fetch(`${prefix}/auth/csrf`, {
    credentials: "same-origin",
  });
  if (!response.ok)
    throw new ApiError("暂时无法连接服务，请稍后重试。", response.status);
  const data = (await response.json()) as { token: string };
  csrfToken = data.token;
}

export async function api<T>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<T> {
  const mutating = method !== "GET";
  if (mutating && !csrfToken) await refreshCsrf();
  let response: Response;
  try {
    response = await fetch(`${prefix}${path}`, {
      method,
      credentials: "same-origin",
      headers: mutating
        ? { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken! }
        : {},
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError("无法连接服务器，请检查服务是否已启动。", 0);
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    if (response.status === 401) {
      csrfToken = null;
      if (!path.startsWith("/auth/"))
        window.dispatchEvent(new Event("session-expired"));
    }
    if (response.status === 403) csrfToken = null;
    throw new ApiError(
      data.message || "操作未完成，请稍后重试。",
      response.status,
    );
  }
  return data as T;
}

export function query(values: Record<string, string>) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value) params.set(key, value);
  });
  return params.toString() ? `?${params}` : "";
}

export const errorMessage = (error: unknown) =>
  error instanceof Error ? error.message : "操作未完成，请稍后重试。";
