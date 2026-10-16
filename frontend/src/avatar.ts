import type { User } from "./types";

export const AVATAR_CHANGED = "shanhai-avatar-changed";
const MAX_FILE_SIZE = 5 * 1024 * 1024;
const MAX_STORED_LENGTH = 200_000;

export function avatarKey(user: Pick<User, "id" | "username">) {
  return `shanhai:avatar:v1:${user.id}:${encodeURIComponent(user.username)}`;
}

function isStoredAvatar(value: string): boolean {
  return (
    value.length <= MAX_STORED_LENGTH &&
    /^data:image\/jpeg;base64,[A-Za-z0-9+/]+={0,2}$/.test(value)
  );
}

export function readAvatar(key: string): string | null {
  try {
    const value = localStorage.getItem(key);
    return value && isStoredAvatar(value) ? value : null;
  } catch {
    return null;
  }
}

export function saveAvatar(key: string, value: string | null) {
  if (value !== null && !isStoredAvatar(value))
    throw new Error("头像格式不正确，请重新选择图片。");
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    throw new Error("无法保存头像，请检查浏览器是否允许本站保存数据。");
  }
  if (typeof window !== "undefined")
    window.dispatchEvent(new Event(AVATAR_CHANGED));
}

export function validateAvatarFile(file: Pick<File, "type" | "size">) {
  if (!["image/jpeg", "image/png", "image/webp"].includes(file.type))
    throw new Error("请选择 JPG、PNG 或 WebP 图片。");
  if (!file.size) throw new Error("图片文件为空，请重新选择。");
  if (file.size > MAX_FILE_SIZE)
    throw new Error("图片不能超过 5 MB，请选择更小的图片。");
}

export async function prepareAvatar(file: File): Promise<string> {
  validateAvatarFile(file);
  let image: ImageBitmap;
  try {
    image = await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    throw new Error("无法读取这张图片，请换一张图片重试。");
  }
  try {
    if (
      !image.width ||
      !image.height ||
      image.width * image.height > 40_000_000
    )
      throw new Error("图片尺寸过大，请缩小后重新选择。");
    const canvas = document.createElement("canvas");
    canvas.width = canvas.height = 256;
    const context = canvas.getContext("2d");
    if (!context) throw new Error("浏览器无法处理图片，请刷新后重试。");
    const side = Math.min(image.width, image.height);
    context.fillStyle = "#f5f7fb";
    context.fillRect(0, 0, 256, 256);
    context.drawImage(
      image,
      (image.width - side) / 2,
      (image.height - side) / 2,
      side,
      side,
      0,
      0,
      256,
      256,
    );
    return canvas.toDataURL("image/jpeg", 0.9);
  } finally {
    image.close();
  }
}
