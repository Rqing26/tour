import assert from "node:assert/strict";
import { beforeEach, test } from "node:test";
import {
  avatarKey,
  readAvatar,
  saveAvatar,
  validateAvatarFile,
} from "../src/avatar.ts";
import { passwordIssue } from "../src/accountValidation.ts";

let entries;
beforeEach(() => {
  entries = new Map();
  globalThis.localStorage = {
    getItem: (key) => entries.get(key) ?? null,
    setItem: (key, value) => entries.set(key, value),
    removeItem: (key) => entries.delete(key),
  };
});

test("avatar storage separates users and recreated accounts", () => {
  const first = avatarKey({ id: 1, username: "customer" });
  assert.notEqual(first, avatarKey({ id: 2, username: "customer" }));
  assert.notEqual(first, avatarKey({ id: 1, username: "admin" }));
  saveAvatar(first, "data:image/jpeg;base64,/9j/AA==");
  assert.ok(readAvatar(first));
  assert.equal(readAvatar(avatarKey({ id: 2, username: "customer" })), null);
  saveAvatar(first, null);
  assert.equal(readAvatar(first), null);
});

test("untrusted or malformed stored image values fall back to initials", () => {
  for (const value of [
    "https://example.com/avatar.png",
    "data:image/svg+xml,<svg/>",
    "data:image/jpeg;base64,!",
  ]) {
    entries.set("avatar", value);
    assert.equal(readAvatar("avatar"), null);
    assert.throws(() => saveAvatar("avatar", value));
  }
});

test("storage errors do not claim success or discard a previous avatar", () => {
  entries.set("avatar", "data:image/jpeg;base64,/9j/AA==");
  globalThis.localStorage.setItem = () => {
    throw new Error("QuotaExceededError");
  };
  assert.throws(
    () => saveAvatar("avatar", "data:image/jpeg;base64,/9j/BB=="),
    /保存/,
  );
  assert.equal(readAvatar("avatar"), "data:image/jpeg;base64,/9j/AA==");
  globalThis.localStorage.getItem = () => {
    throw new Error("SecurityError");
  };
  assert.equal(readAvatar("avatar"), null);
});

test("avatar picker accepts supported images and rejects oversized or empty files", () => {
  for (const type of ["image/jpeg", "image/png", "image/webp"]) {
    assert.doesNotThrow(() =>
      validateAvatarFile({ type, size: 5 * 1024 * 1024 }),
    );
  }
  for (const type of ["image/svg+xml", "image/gif", "text/plain", ""]) {
    assert.throws(() => validateAvatarFile({ type, size: 100 }));
  }
  assert.throws(() => validateAvatarFile({ type: "image/png", size: 0 }));
  assert.throws(() =>
    validateAvatarFile({ type: "image/png", size: 5 * 1024 * 1024 + 1 }),
  );
});

test("password checks follow existing API limits without adding composition requirements", () => {
  assert.equal(passwordIssue("old-password", "12345678", "12345678"), "");
  assert.match(passwordIssue("old-password", "1234567", "1234567"), /8/);
  assert.match(
    passwordIssue("old-password", "old-password", "old-password"),
    /相同/,
  );
  assert.match(
    passwordIssue("old-password", "new-password", "different"),
    /不一致/,
  );
  assert.equal(
    passwordIssue("old-password", "旅".repeat(24), "旅".repeat(24)),
    "",
  );
  assert.match(
    passwordIssue("old-password", "旅".repeat(25), "旅".repeat(25)),
    /过长/,
  );
  assert.match(passwordIssue("", "new-password", "new-password"), /当前密码/);
});
