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

/** 统一的失败翻译：连接失败、401/403 的令牌失效、业务错误消息都在这里收敛。 */
async function send(path: string, init: RequestInit): Promise<Response> {
  let response: Response;
  try {
    response = await fetch(`${prefix}${path}`, {
      credentials: "same-origin",
      ...init,
    });
  } catch {
    throw new ApiError("无法连接服务器，请检查服务是否已启动。", 0);
  }
  if (response.ok) return response;
  const data = (await response.json().catch(() => ({}))) as {
    message?: string;
  };
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

async function read<T>(response: Response): Promise<T> {
  return (await response.json().catch(() => ({}))) as T;
}

export async function api<T>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<T> {
  const mutating = method !== "GET";
  if (mutating && !csrfToken) await refreshCsrf();
  const response = await send(path, {
    method,
    headers: mutating
      ? { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken! }
      : {},
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return read<T>(response);
}

/**
 * 上传单个文件（表单部件名固定 `file`）。
 *
 * <p>不手写 `Content-Type`：multipart 的 boundary 由浏览器生成，自己设会把分隔符写死成错的。
 */
export async function upload<T>(path: string, file: File): Promise<T> {
  if (!csrfToken) await refreshCsrf();
  const body = new FormData();
  body.append("file", file);
  const response = await send(path, {
    method: "POST",
    headers: { "X-CSRF-TOKEN": csrfToken! },
    body,
  });
  return read<T>(response);
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
