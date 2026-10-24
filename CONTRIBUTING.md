# 协作规范

## 分支模型

```
main                      受保护，只接受 PR，CI 必须绿
├─ feat/<issue>-<简述>     新功能
├─ fix/<issue>-<简述>      缺陷修复
├─ test/<模块>             测试补充
└─ docs/<主题>             文档
```

分支从最新 `main` 切出，合并后删除。命名示例：`feat/42-booking-audit-log`。

## 提交信息

采用 Conventional Commits，由 commitlint 校验：

```
<type>(<scope>): <subject>

<body 说明为什么这么改>

<footer 关联 issue>
```

| type | 用途 |
| --- | --- |
| feat | 新功能 |
| fix | 缺陷修复 |
| refactor | 重构，行为不变 |
| test | 测试 |
| docs | 文档 |
| style | 格式化、样式（不影响逻辑） |
| perf | 性能优化 |
| ci / build / chore | 工程化 |

scope 用模块名：`auth`、`order`、`route`、`user`、`account`、`ui`、`db`、`deps`。

```bash
git commit -m "fix(order): 取消订单前先锁定线路，避免与预订竞争名额"
git commit -m "test(order): 覆盖最后一名额并发超卖场景"
```

**一次提交只做一件事。** 如果提交信息里出现"并且""同时"，说明该拆。

## 开发流程

1. 建 Issue，写清验收标准，指认负责人。
2. 切分支，按"先测试后实现"推进：红灯提交 → 绿灯提交 → 重构提交。
3. 推送后开 PR，填完模板里的"怎么验证的"。
4. 至少 1 名队友 review。评审意见要具体，不要只写 LGTM。
5. 作者回应每条意见，修改后追加提交（不要 force-push 覆盖评审历史）。
6. CI 绿 + 已 approve → 合并，删除分支。

## 代码要求

- 后端：Google Java Format（2 空格缩进），Service 层不直接拼 SQL，事务边界在 Service。
- 前端：Prettier + ESLint，组件不直接 `fetch`，统一走 `src/api.ts`。
- 所有涉及名额、金额、状态的改动，必须同时提交测试。
- 数据库结构变更必须新增 Flyway 迁移文件，禁止修改已发布的 `V1__` 脚本。

## 本地检查（提交前必跑）

```bash
# 后端
mvn -B verify

# 前端
cd frontend && npm test && npm run build && npm run format:check
```

## 文档同步

| 改了什么 | 同步更新 |
| --- | --- |
| 接口签名 | `docs/API.md` |
| 表结构 | `docs/数据库设计.md` + 迁移脚本 |
| 架构决策 | `docs/ADR/NNNN-标题.md` |
| 每周进展 | `docs/开发日志.md` |
