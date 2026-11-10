import { Star } from "lucide-react";
import {
  hasRating,
  ratingLabel,
  ratingText,
  reviewCountText,
  starFill,
} from "../rating";
import type { Route } from "../types";
import "./rating.css";

/**
 * 线路均分星级。
 *
 * <p>五颗星用同一套尺寸叠两层：底层灰星做轮廓，上层金星按百分比裁切，所以 4.3 分能画出半颗。
 * 均分与条数都为空时显示"暂无评分"——这是线路卡片上唯一允许出现的空态文案。
 */
export default function Rating({ route }: { route: Route }) {
  const label = ratingLabel(route);
  const rated = hasRating(route);
  return (
    <div
      className={`route-rating${rated ? "" : " route-rating-empty"}`}
      role="img"
      aria-label={label}
      title={label}
    >
      <span className="route-rating-stars" aria-hidden="true">
        {[0, 1, 2, 3, 4].map((index) => (
          <span className="route-rating-star" key={index}>
            <Star size={13} className="route-rating-star-base" />
            <span
              className="route-rating-star-fill"
              style={{ width: `${starFill(route, index) * 100}%` }}
            >
              <Star size={13} />
            </span>
          </span>
        ))}
      </span>
      <span className="route-rating-text">{ratingText(route)}</span>
      <span className="route-rating-count">{reviewCountText(route)}</span>
    </div>
  );
}
