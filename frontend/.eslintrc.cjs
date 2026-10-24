/**
 * ESLint 采用 eslintrc 格式（ESLint 8.57），因为仓库约定配置文件名为 .eslintrc.cjs。
 * 只启用能拦住真实问题的规则：类型安全相关的 @typescript-eslint 推荐集 + React Hooks 依赖检查。
 */
module.exports = {
  root: true,
  env: { browser: true, es2022: true },
  parser: "@typescript-eslint/parser",
  parserOptions: {
    ecmaVersion: "latest",
    sourceType: "module",
    ecmaFeatures: { jsx: true },
  },
  plugins: ["@typescript-eslint", "react-hooks"],
  extends: [
    "eslint:recommended",
    "plugin:@typescript-eslint/recommended",
    "plugin:react-hooks/recommended",
  ],
  ignorePatterns: ["dist/", "node_modules/", "*.cjs", "tests/"],
  rules: {
    // 未使用变量视为错误，允许下划线前缀显式表达“故意忽略”。
    "@typescript-eslint/no-unused-vars": [
      "error",
      { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
    ],
    // 浏览器端日志不该留在生产代码里，允许 warn/error 作为兜底提示。
    "no-console": ["error", { allow: ["warn", "error"] }],
    eqeqeq: ["error", "always"],
  },
};
