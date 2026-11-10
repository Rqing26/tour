/**
 * 线路均分的展示规则。
 *
 * <p>后端给的是 `avgRating`（可为 null）与 `reviewCount`（0 表示没有评价）。两者必须一起判断：
 * 只均分为空就画 0 星，会让"还没人评"看起来像"差评"。这里全是纯函数，组件只负责铺 DOM，
 * 判定逻辑因此能脱离浏览器单独测。
 */
export interface RatingLike {
  avgRating?: number | null;
  reviewCount?: number;
}

/** 有评价才算有分。条数为 0 或均分缺失都算没有评分。 */
export function hasRating(route: RatingLike): boolean {
  const count = route.reviewCount ?? 0;
  const avg = route.avgRating;
  return count > 0 && typeof avg === "number" && Number.isFinite(avg);
}

/** 卡片上的均分：有评价给一位小数，没有给"暂无评分"。 */
export function ratingText(route: RatingLike): string {
  return hasRating(route) ? (route.avgRating as number).toFixed(1) : "暂无评分";
}

/** 条数文案：没有评价时不显示"0 条评价"，整段留空由样式收起。 */
export function reviewCountText(route: RatingLike): string {
  return hasRating(route) ? `${route.reviewCount} 条评价` : "";
}

/**
 * 第 index 颗星（0 起）该填多少：0、0.5 或 1。
 *
 * <p>半颗星是能看出来的最小刻度；再细分在小尺寸图标上没有意义。
 */
export function starFill(route: RatingLike, index: number): number {
  if (!hasRating(route)) return 0;
  const remaining = (route.avgRating as number) - index;
  if (remaining >= 1) return 1;
  if (remaining <= 0) return 0;
  return Math.round(remaining * 2) / 2;
}

/** 读屏与悬浮提示用的完整说法，避免只剩一串星号。 */
export function ratingLabel(route: RatingLike): string {
  return hasRating(route)
    ? `平均 ${ratingText(route)} 分，${route.reviewCount} 条评价`
    : "暂无评分";
}
