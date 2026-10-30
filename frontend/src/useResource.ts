import { useCallback, useEffect, useState } from "react";
import { api, errorMessage } from "./api";

export function useResource<T>(path: string) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [version, setVersion] = useState(0);
  const refresh = useCallback(() => setVersion((value) => value + 1), []);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    api<T>(path)
      .then((value) => {
        if (active) setData(value);
      })
      .catch((error) => {
        if (active) setError(errorMessage(error));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [path, version]);
  return { data, loading, error, refresh, version };
}

/** 后端分页信封，与 docs/API.md 的 `Page<T>` 一致：total 是过滤后的全量行数。 */
export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

/**
 * 服务端分页资源：路径里已经带好 page/size，这里只把信封拆成组件好用的形状。
 *
 * <p>翻页只换路径，不动本地数组；组件不再持有全量数据，也就不会"越翻越慢"。
 */
export function usePagedResource<T>(path: string) {
  const result = useResource<PageResult<T>>(path);
  return {
    ...result,
    items: result.data?.items ?? [],
    total: result.data?.total ?? 0,
  };
}
