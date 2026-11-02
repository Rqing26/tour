import assert from "node:assert/strict";
import { beforeEach, test } from "node:test";
import { ApiError, clearCsrf, upload } from "../src/api.ts";

// fetch 用桩替换：这里验证的是"请求怎么组装的"，不打真实后端。
const reply = (status, body) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => body,
});

let calls = [];

beforeEach(() => {
  calls = [];
  clearCsrf();
});

const file = (name = "封面.png") =>
  new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], name, {
    type: "image/png",
  });

test("上传前自动取 CSRF 令牌，并以 multipart 提交", async () => {
  globalThis.fetch = async (url, init) => {
    calls.push({ url: String(url), init });
    if (String(url).endsWith("/auth/csrf")) return reply(200, { token: "t-1" });
    return reply(200, { imagePath: "9f2c.png", imageKey: "guilin" });
  };

  const saved = await upload("/routes/7/image", file());

  assert.deepEqual(saved, { imagePath: "9f2c.png", imageKey: "guilin" });
  assert.equal(calls.length, 2);
  assert.equal(calls[0].url, "/travel/api/auth/csrf");
  assert.equal(calls[1].url, "/travel/api/routes/7/image");
  assert.equal(calls[1].init.method, "POST");
  assert.equal(calls[1].init.headers["X-CSRF-TOKEN"], "t-1");
  assert.equal(
    calls[1].init.headers["Content-Type"],
    undefined,
    "不能手写 Content-Type，multipart 的 boundary 必须由浏览器生成",
  );
  assert.ok(calls[1].init.body instanceof FormData);
  assert.equal(calls[1].init.body.get("file").name, "封面.png");
});

test("已有令牌时不再重复取 CSRF", async () => {
  globalThis.fetch = async (url) => {
    calls.push({ url: String(url), init: {} });
    if (String(url).endsWith("/auth/csrf")) return reply(200, { token: "t-2" });
    return reply(200, { imagePath: "a.png" });
  };

  await upload("/routes/1/image", file("a.png"));
  await upload("/routes/2/image", file("b.png"));

  assert.equal(
    calls.filter((c) => c.url.endsWith("/auth/csrf")).length,
    1,
    "令牌一次登录内复用，不该每次上传都多打一次",
  );
});

test("413 原样抛出后端的提示，前端能区分太大与格式错", async () => {
  globalThis.fetch = async (url) =>
    String(url).endsWith("/auth/csrf")
      ? reply(200, { token: "t" })
      : reply(413, { message: "图片不能超过 5MB" });

  await assert.rejects(
    () => upload("/routes/1/image", file()),
    (error) =>
      error instanceof ApiError &&
      error.status === 413 &&
      error.message === "图片不能超过 5MB",
  );
});

test("400 也走同一条错误通道", async () => {
  globalThis.fetch = async (url) =>
    String(url).endsWith("/auth/csrf")
      ? reply(200, { token: "t" })
      : reply(400, { message: "文件内容不是图片，仅支持 PNG、JPEG、WebP" });

  await assert.rejects(
    () => upload("/routes/1/image", file("fake.jpg")),
    (error) =>
      error instanceof ApiError &&
      error.status === 400 &&
      error.message.includes("不是图片"),
  );
});
