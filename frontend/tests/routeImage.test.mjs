import assert from "node:assert/strict";
import { test } from "node:test";
import { coverPath, fallbackImageKey, imageKeys } from "../src/routeImage.ts";

// 上传图优先、内置键回退（C7）；baseUrl 显式传入，断言的是拼出来的路径本身。
const base = "/travel/";

test("上传过封面的线路用 imagePath 拼 /uploads 地址", () => {
  assert.equal(
    coverPath({ imageKey: "guilin", imagePath: "9f2c.png" }, base),
    "/travel/uploads/9f2c.png",
  );
});

test("从未上传的线路回退到内置图片键", () => {
  assert.equal(
    coverPath({ imageKey: "yunnan", imagePath: null }, base),
    "/travel/images/yunnan.jpg",
  );
  assert.equal(
    coverPath({ imageKey: "xiamen" }, base),
    "/travel/images/xiamen.jpg",
  );
});

test("imagePath 是空白串时按未上传处理", () => {
  assert.equal(
    coverPath({ imageKey: "chengdu", imagePath: "   " }, base),
    "/travel/images/chengdu.jpg",
  );
});

test("未知图片键回退到桂林，不会拼出 404 地址", () => {
  assert.equal(fallbackImageKey("mars"), "guilin");
  assert.equal(
    coverPath({ imageKey: "mars", imagePath: "" }, base),
    "/travel/images/guilin.jpg",
  );
});

test("六个内置键原样保留", () => {
  assert.deepEqual(imageKeys, [
    "guilin",
    "yunnan",
    "xinjiang",
    "chengdu",
    "xiamen",
    "hangzhou",
  ]);
  for (const key of imageKeys) assert.equal(fallbackImageKey(key), key);
});
