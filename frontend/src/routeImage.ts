import type { Route } from "./types.ts";

/**
 * 内置封面键。后端 `RouteInput.imageKey` 的正则只认这 6 个值，前端多传会被 400。
 * 上传图片走 `imagePath`，不再往这里加键（C7）。
 */
export const imageKeys = [
  "guilin",
  "yunnan",
  "xinjiang",
  "chengdu",
  "xiamen",
  "hangzhou",
];

export const imageLabels = ["桂林", "云南", "新疆", "成都", "厦门", "杭州"];

/** 未知键回退到桂林，避免拼出一个必然 404 的地址。 */
export const fallbackImageKey = (key: string) =>
  imageKeys.includes(key) ? key : "guilin";

/**
 * 线路封面地址：上传图优先、内置键回退（C7）。
 *
 * <p>`baseUrl` 由调用方传入（浏览器里是 `import.meta.env.BASE_URL`），这样这段判断能脱离构建产物单独测试。
 */
export function coverPath(
  route: Pick<Route, "imageKey" | "imagePath">,
  baseUrl: string,
) {
  const uploaded = (route.imagePath ?? "").trim();
  return uploaded
    ? `${baseUrl}uploads/${uploaded}`
    : `${baseUrl}images/${fallbackImageKey(route.imageKey)}.jpg`;
}
