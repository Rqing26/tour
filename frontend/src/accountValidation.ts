export function passwordIssue(
  current: string,
  next: string,
  confirmation: string,
): string {
  if (!current.trim()) return "请输入当前密码。";
  if (next.trim().length === 0 || next.length < 8)
    return "新密码至少需要 8 位字符。";
  if (next.length > 72 || new TextEncoder().encode(next).length > 72)
    return "密码过长，请减少字数后重试。";
  if (next === current) return "新密码与当前密码相同，请使用新的密码。";
  if (next !== confirmation) return "两次输入的新密码不一致，请重新确认。";
  return "";
}
