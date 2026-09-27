import { useEffect, useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import "./pagination.css";

export function usePagination<T>(
  items: T[],
  pageSize: number,
  resetKey: string,
) {
  const [page, setPage] = useState(1);
  useEffect(() => setPage(1), [resetKey]);
  const pages = Math.max(1, Math.ceil(items.length / pageSize));
  const currentPage = Math.min(page, pages);
  return {
    page: currentPage,
    pages,
    setPage,
    visibleItems: items.slice(
      (currentPage - 1) * pageSize,
      currentPage * pageSize,
    ),
  };
}

interface PaginationProps {
  page: number;
  pages: number;
  onChange: (page: number) => void;
}

export default function Pagination({ page, pages, onChange }: PaginationProps) {
  if (pages <= 1) return null;
  return (
    <nav className="pagination-controls" aria-label="列表分页">
      <button
        className="icon-button"
        aria-label="上一页"
        disabled={page <= 1}
        onClick={() => onChange(page - 1)}
      >
        <ChevronLeft size={17} />
      </button>
      <span aria-live="polite">
        第 {page} / {pages} 页
      </span>
      <button
        className="icon-button"
        aria-label="下一页"
        disabled={page >= pages}
        onClick={() => onChange(page + 1)}
      >
        <ChevronRight size={17} />
      </button>
    </nav>
  );
}
