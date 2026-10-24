## 这个 PR 做了什么

<!-- 一到三句话，说清解决的问题，不要复制 issue 标题 -->

关联 Issue：Closes #

## 改动类型

- [ ] feat 新功能
- [ ] fix 缺陷修复
- [ ] refactor 重构（无行为变化）
- [ ] test 测试补充
- [ ] docs 文档
- [ ] ci / chore 工程化

## 怎么验证的

<!-- 必填。写明具体命令和手工验证步骤，评审人照着能复现 -->

**自动化：**

```bash
# 例：mvn -Dtest=OrderServiceTest test
```

**手工验证：**

1.
2.

## 影响范围

- [ ] 涉及数据库结构变更（需附 Flyway 迁移文件）
- [ ] 涉及接口签名变更（需更新 `docs/API.md`）
- [ ] 涉及权限或安全逻辑

## 自查

- [ ] 单个 PR 改动不超过 400 行（超过已说明拆分计划）
- [ ] 本地 `mvn verify` 与 `npm test` 均通过
- [ ] 没有提交编译产物、`data/`、`.env`
- [ ] 提交信息符合 Conventional Commits
