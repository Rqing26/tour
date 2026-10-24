# AGENTS.md · AI 执行手册

> 本文件是 AI agent 在本仓库独立推进工作的唯一依据。
> 阅读顺序：全文通读一遍 → 执行 D0 → 按 §6 的每日循环逐日推进 → 每天更新 `docs/进度看板.md`。
> **本文件是规格，不是状态。** 不要修改本文件的任务内容；进度只写进 `docs/进度看板.md`。

---

## 1. 目标（可衡量）

| 维度 | 目标值 | 验收方式 |
| --- | --- | --- |
| 周期 | 24 个工作日（D1–D24） | `docs/进度看板.md` 勾选 |
| 提交数 | ≥ 78 条，全部为 Conventional Commits | `git rev-list --count HEAD` |
| 净增代码 | 4,500–5,500 行（不含 lock / 构建产物） | `git diff --shortstat <起点>..HEAD` |
| 功能 | 6 个可演示功能（P0×4 + P1×2） | 按 §5 验收条件逐项演示 |
| 后端测试 | 测试类 ≥ 10 个，用例 ≥ 45 个，全绿 | `mvn -B -ntp verify` 输出 |
| 前端测试 | 测试文件 ≥ 7 个，全绿 | `npm test` 输出 |
| 冒烟 | `scripts/smoke_test.py` 全 PASS | 脚本退出码 0 |
| 兼容性 | 无破坏性变更（§3 全部满足） | 最终回归清单 |

**完成定义**：以上 8 项全部达成，且 D24 收尾清单全部勾选。

---

## 2. AI 执行规则（必须遵守）

1. **一次只推进一个任务。** 同一时刻最多 2 个活跃分支，其余全部合并或删除。
2. **每个任务必须闭环**：代码 + 测试 + 文档 + 提交 + 看板勾选，缺一项不算完成。
3. **自己跑测试，不许让别人跑。** 每个任务收尾必须由 AI 在本机执行 §4 的全部命令。
4. **不许为凑数制造提交。** 空提交、改空格、重复代码一律禁止。行数不达标就如实记录，不要注水。
5. **测试失败必须修根因。** 禁止 `@Disabled`、`-DskipTests`、`|| true`、`catch (Exception e) {}`、把断言改成 `assertTrue(true)`。
6. **同一任务连续 2 次修复失败**，停止推进，把失败命令、完整报错、已尝试的方案写入汇报，等待人工介入。
7. **不许改 `V1__` 迁移脚本、不许改本文件、不许 force-push 已合并分支。**

---

## 3. 兼容性硬约束（违反即失败）

| # | 约束 | 判定方式 |
| --- | --- | --- |
| C1 | **禁止修改** `src/main/resources/db/migration/V1__create_travel_schema.sql`，只允许新增 `V2__`、`V3__`… | `git diff` 中该文件必须无改动 |
| C2 | 表结构变更只能**新增列 / 新增表 / 新增索引**。删列、改类型、改约束必须走新迁移且显式说明 | 审查迁移脚本 |
| C3 | 新增列必须是 `NULL` 或有 `DEFAULT`，保证旧数据可读 | 迁移脚本语法检查 |
| C4 | HTTP 接口**只增不改**：已有路径、方法、字段名、状态码不得变更；新增响应字段必须可空 | 对照现有 `dto/Api.java`；`docs/API.md` 目前不存在，由 T3.4 首次创建后作为基线 |
| C5 | 订单状态只能是 `PENDING/ASSIGNED/COMPLETED/CANCELLED`，不得增删 | `ck_booking_status` 未改动 |
| C6 | 三个演示账号 `admin` / `employee` / `customer`，密码 `Travel@2026` 必须始终可登录 | 冒烟脚本第一段 |
| C7 | `travel_routes.image_key` 保留且 `NOT NULL`；图片上传新增 `image_path`（可空），前端优先读 `image_path`，为空回退 `image_key` | `assets.ts` 的 `photo()` 逻辑 |
| C8 | 前端资源必须由 `frontend/` 构建并复制到 `src/main/resources/static/`，访问路径保持 `/travel/` | `npm run build` 后 jar 可启动 |
| C9 | 不升级技术栈：Java 21、Spring Boot 3.5.16、React 19、Node 22、Vite 8、TypeScript 5.7 | `pom.xml`、`package.json` 版本号未变 |
| C10 | 持久层保持 **JdbcTemplate 手写 SQL**，不引入 JPA / MyBatis / 任何 ORM | `pom.xml` 依赖清单 |
| C11 | 前端不引入 UI 框架、图表库、状态管理库（图表手写 SVG） | `package.json` 依赖清单 |
| C12 | 安全基线不得降级：CSRF 开启、`BCryptPasswordEncoder(12)`、用户文本不拼 HTML、SQL 全参数绑定 | 审查 `SecurityConfig.java` 与新增代码 |
| C13 | SQL 必须同时兼容 H2 `MODE=MySQL` 与 MySQL 8；不确定时先用 H2 跑通再查 MySQL 语法 | `mvn verify`（H2）+ 手工核对 |
| C14 | 构建产物不进 Git：`target/`、`frontend/dist/`、`src/main/resources/static/*`、`data/`、`.runtime/` | `git status` 干净 |

**新增依赖的唯一豁免**：测试库（如 Vitest、Mockito 已在内）、`springdoc-openapi`、Testcontainers。其余一律需在 PR 描述里写清"为什么现有依赖做不到"。

---

## 4. 质量标准与验收条件

### 4.1 每个任务的完成定义（DoD，7 条全满足才算完成）

- [ ] 代码符合仓库既有风格（后端 Google Java Format 2 空格、前端 Prettier）
- [ ] 新增 public 方法 / 新增接口 / 新增组件**都有对应测试**
- [ ] 涉及事务、并发、金额、权限的逻辑**必须有专门用例**（参考 `lastSeatCannotBeOversoldByConcurrentCustomers`）
- [ ] `mvn -B -ntp verify` 全绿
- [ ] `cd frontend && npm test && npm run build && npm run format:check` 全绿
- [ ] 涉及接口变更时同步更新 `docs/API.md`；涉及表结构时同步更新 `docs/架构说明.md`
- [ ] `docs/进度看板.md` 对应任务已勾选，且写明实际行数与提交数量

### 4.2 测试要求的硬指标

| 项目 | 要求 |
| --- | --- |
| 后端测试类 | 从 1 个拆到 ≥ 10 个（按 Controller / Service / Repository 分） |
| 后端用例 | ≥ 45 个，其中并发类 ≥ 3 个、事务回滚类 ≥ 2 个、权限越权类 ≥ 5 个 |
| 前端测试文件 | ≥ 7 个（现有 `account.test.mjs` + 新增 6 个） |
| 断言质量 | 断言响应状态码 + 关键字段值，不只断言"不报错" |
| 测试隔离 | 每个用例独立数据，不依赖执行顺序；用内存库 `traveltest` |
| 执行证据 | 汇报时必须粘贴 `Tests run: NN, Failures: 0, Errors: 0, Skipped: 0` 原行 |

**跳过即失败**：`Skipped` 不为 0、使用 `@Disabled`、`mvn ... -DskipTests`，均判定该任务未完成。

### 4.3 提交规范

```
<type>(<scope>): <不超过 72 字符的中文说明>

<为什么这么改，一到三行>
```

- `type`：`feat|fix|refactor|test|docs|style|perf|ci|build|chore`
- `scope`：`db|event|order|route|user|account|image|report|coupon|review|ui|deps`
- 一次提交只做一件事；单提交改动 ≤ 300 行，超过必须拆
- 每条提交必须能独立通过编译

### 4.4 分支与合并

```
main                          受保护，只接受 PR
├─ feat/T2-booking-events     功能分支，命名带任务 ID
├─ test/split-integration
└─ docs/api-reference
```

PR 描述必须包含：关联任务 ID、改了什么、**怎么验证的（命令 + 结果）**、影响面、是否触碰 §3 约束。

---

## 5. D0 环境引导（必须先做，约 30 分钟）

**现状**：Java 21 ✅、Node 22 ✅、**Maven 缺失**、无 `mvnw`。

```bash
# D0.1 确认工具链
java -version      # 需 21.x
node -v            # 需 22.x
mvn -v             # 若无输出，执行 D0.2

# D0.2 安装 Maven（装到 .runtime/，已被 .gitignore 忽略）
mkdir -p .runtime
curl -L -o .runtime/maven.zip https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip
unzip -q .runtime/maven.zip -d .runtime
export PATH="$PWD/.runtime/apache-maven-3.9.9/bin:$PATH"
mvn -v             # 应输出 Apache Maven 3.9.9 + Java 21

# D0.3 生成 wrapper，让后续构建不再依赖本机安装
mvn -N wrapper:wrapper -Dmaven=3.9.9
./mvnw -v

# D0.4 跑基线，确认现状全绿（预期 12 个用例）
./mvnw -B -ntp verify

# D0.5 前端基线
cd frontend && npm ci && npm test && npm run build && npm run format:check && cd ..
```

**D0 完成标准**：`./mvnw -B -ntp verify` 输出 `Tests run: 12, Failures: 0, Errors: 0`；前端四条命令全绿。

**若 D0 基线本来就红**：停止，先提交一个 `fix(build): 修复基线测试失败`，再进入 D1。

---

## 6. 每日执行循环（SOP）

```
1. 打开 docs/进度看板.md，找到今天编号最小的未完成任务
2. 读该任务的「改动文件」清单，先读这些文件的现有实现
3. 切分支：git switch -c feat/T<编号>-<简述>
4. 写测试（此时应红）→ 提交 test(...)
5. 写实现（最小可用）→ 提交 feat(...)
6. 补边界与重构 → 提交 refactor(...) / fix(...)
7. 跑 §4.1 的 7 条 DoD 检查
8. 更新 docs/进度看板.md（勾选 + 填实际行数）
9. 提交 docs(...)，推分支，开 PR
10. 无阻塞 → 回到第 1 步；有阻塞 → 按 §2 第 6 条上报
```

**每天收工前必须满足**：`git status` 干净、`./mvnw verify` 绿、看板已更新、当天提交数 ≥ 1。

---

## 7. 24 天任务表

行数为**净增行数**估算（实现 : 测试 ≈ 6 : 4）。提交数为该任务的最低条数。

### 第 1 周 · 基建（D1–D3，16 条提交，约 700 行）

| 日 | 任务 ID | 内容 | 改动文件 | 行数 | 提交 |
| --- | --- | --- | --- | --- | --- |
| D1 | T1.1 | CI 后端 job（`mvn verify` + 缓存 + 上传 surefire 报告） | `.github/workflows/ci.yml` | 60 | 2 |
| D1 | T1.2 | CI 前端 job + ESLint 配置接入 | `ci.yml`、`.eslintrc.cjs`、`package.json` | 130 | 2 |
| D2 | T1.3 | commitlint + husky 钩子 | `.commitlintrc.json`、`.husky/*`、`package.json` | 60 | 2 |
| D2 | T1.4 | Maven wrapper 入库（`mvnw`、`.mvn/wrapper/*`） | 新增 | 40 | 1 |
| D3 | T1.5 | Dockerfile + docker-compose（H2 / MySQL 双环境） | `Dockerfile`、`docker-compose.yml`、`.dockerignore` | 130 | 3 |
| D3 | T1.6 | springdoc OpenAPI + Actuator 健康检查 | `pom.xml`、`OpenApiConfig.java`、`application.yml` | 90 | 2 |
| D3 | T1.7 | 抽取 `AbstractApiTest` 基类，迁移 12 个用例的公共装置 | `src/test/java/**/AbstractApiTest.java`、`TravelIntegrationTest.java` | 190 | 2 |

**周验收**：PR 上 CI 出现双绿徽章；`docker compose up` 能起 H2 环境；`/travel/v3/api-docs` 返回 200；`Tests run: 12` 不变。

---

### 第 1 周末–第 2 周初 · T2 订单审计（D4–D6，9 条提交，约 600 行）

**业务目标**：订单状态每次变更都留痕，可回溯"谁在何时把订单从什么状态改成了什么状态"。

**表变更** `V2__add_booking_events.sql`：

```sql
CREATE TABLE booking_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  booking_id BIGINT NOT NULL,
  from_status VARCHAR(20),
  to_status VARCHAR(20) NOT NULL,
  actor_id BIGINT NOT NULL,
  actor_role VARCHAR(20) NOT NULL,
  note VARCHAR(200) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_event_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
  CONSTRAINT fk_event_actor FOREIGN KEY (actor_id) REFERENCES app_users(id)
);
CREATE INDEX idx_event_booking_time ON booking_events(booking_id, created_at);
```

**子任务**：

| ID | 内容 | 文件 | 行数 |
| --- | --- | --- | --- |
| T2.1 | 建表迁移 | `db/migration/V2__*.sql` | 40 |
| T2.2 | 事件仓储：`append()` / `byBooking()` | `repository/BookingEventRepository.java` | 110 |
| T2.3 | `transition()` 内埋点，**与状态变更同事务** | `service/OrderService.java` | 70 |
| T2.4 | 查询接口 `GET /api/orders/{id}/events` | `controller/OrderController.java`、`dto/Api.java` | 70 |
| T2.5 | 前端时间线组件 | `components/OrderTimeline.tsx`、`Orders.tsx`、`timeline.css` | 190 |
| T2.6 | 测试：四种迁移各写一条 + 同事务回滚 + 越权查看 | `OrderEventTest.java` | 160 |

**验收条件**：
- [ ] customer 下单 → employee 接单 → employee 完成，时间线出现 3 条记录，含 `fromStatus`、`toStatus`、操作人姓名、时间
- [ ] 取消订单后时间线追加第 4 条，`toStatus=CANCELLED`
- [ ] 故意让第 4 步失败（临时抛异常），订单状态**未变**且**无事件写入**（同事务验证），验证后删除临时代码
- [ ] customer A 无法查看 customer B 的订单事件（返回 403）

**关键约束**：事件写入必须复用 `transition()` 已有的 `@Transactional` 边界，**不得新开事务**，否则状态与事件不一致。

---

### 第 2 周 · T3 分页与索引下推（D7–D9，8 条提交，约 600 行）

**业务目标**：消灭 `OrderService.list()` 的全表内存过滤（现位于 `service/OrderService.java:29`），分页下推到 SQL。

**子任务**：

| ID | 内容 | 文件 | 行数 |
| --- | --- | --- | --- |
| T3.1 | 补索引迁移 `V3__add_booking_query_indexes.sql` | 新增 | 30 |
| T3.2 | 仓储 `page(条件, sort, offset, limit)` + `count(条件)` | `repository/OrderRepository.java` | 160 |
| T3.3 | 三角色可见性改写为 SQL `WHERE` 片段（ADMIN 无条件 / CUSTOMER `customer_id=?` / EMPLOYEE `status='PENDING' OR employee_id=?`） | `OrderService.java`、`OrderRepository.java` | 90 |
| T3.4 | 接口接收 `page/size/sort`，`size` 服务端兜底上限 100 | `OrderController.java`、`dto/Api.Page.java` | 80 |
| T3.5 | 前端列表改服务端分页，复用现有 `components/Pagination.tsx` | `Orders.tsx`、`useResource.ts` | 90 |
| T3.6 | 测试：三角色边界、越权、空结果、页大小超限 | `OrderPaginationTest.java` | 150 |

**验收条件**：
- [ ] `GET /api/orders?page=0&size=5` 返回 `{items:[…], total, page, size}`
- [ ] `size=1000` 实际生效 100（响应里 `size=100`）
- [ ] 造 5000 条订单数据后首页加载 < 1 秒（脚本造数，造完删除）
- [ ] 越权：customer 分页结果只含自己的订单，`total` 也只统计自己的
- [ ] 冒烟脚本 `smoke_test.py` 全 PASS（分页改动后必须重跑）

---

### 第 2 周末 · T4 线路图片上传（D10–D12，8 条提交，约 570 行）

**业务目标**：解除 `image_key` 只有 6 个固定枚举的限制。

**子任务**：

| ID | 内容 | 文件 | 行数 |
| --- | --- | --- | --- |
| T4.1 | 迁移 `V4__add_route_image_path.sql`：新增 `image_path VARCHAR(200)` 可空 | 新增 | 20 |
| T4.2 | 存储服务：扩展名白名单 + **文件头魔数校验** + ≤5MB + UUID 重命名 | `service/ImageStorageService.java` | 150 |
| T4.3 | 上传接口 `POST /api/routes/{id}/image` | `RouteController.java` | 60 |
| T4.4 | 静态映射 `/uploads/**`（**禁止**指向 `src/main/resources/static`） | `config/WebMvcConfig.java` | 40 |
| T4.5 | 前端上传 + 预览，`assets.ts` 的 `photo()` 改为优先 `imagePath` | `RouteForm.tsx`、`assets.ts` | 160 |
| T4.6 | 测试：伪造扩展名（zip 改名 .jpg）、超限、非法路径 | `ImageUploadTest.java` | 140 |

**验收条件**：
- [ ] 上传 PNG 成功，线路卡片显示新图
- [ ] 把 `.zip` 改名 `.jpg` 上传 → 400，且磁盘无残留文件
- [ ] 6MB 文件 → 413
- [ ] 未上传图片的旧线路仍显示原 `image_key` 对应图片（C7 回退验证）

---

### 第 3 周 · T5 报表聚合下推（D13–D15，9 条提交，约 620 行）

**业务目标**：`DashboardService` 目前把全部订单捞进内存用 stream 统计，改为 SQL 聚合。

**子任务**：

| ID | 内容 | 文件 | 行数 |
| --- | --- | --- | --- |
| T5.1 | 聚合仓储：月度订单量、营收、热门线路 Top5、员工接单排行 | `repository/ReportRepository.java` | 180 |
| T5.2 | 服务层按角色限制统计范围 | `service/ReportService.java` | 90 |
| T5.3 | 接口 `GET /api/reports/summary?months=6` | `controller/ReportController.java`、`dto/Api.java` | 70 |
| T5.4 | 前端手写 SVG 图表组件（折线 + 柱状，**不引库**） | `components/RevenueChart.tsx`、`chart.css` | 170 |
| T5.5 | 报表页接入 | `App.tsx`、`Reports.tsx` | 60 |
| T5.6 | 测试：角色范围、空数据、月份边界（跨年） | `ReportTest.java` | 150 |

**验收条件**：
- [ ] ADMIN 看到全量营收，EMPLOYEE 只看到自己接单部分，两者数字不同且可解释
- [ ] 空库时图表显示空态不报错
- [ ] 12 月与 1 月的跨年查询结果正确
- [ ] 移除 `DashboardService` 中的全表加载后，`mvn verify` 仍全绿（原有用例不能被破坏）

---

### 第 3 周末 · T6 优惠码（D16–D18，9 条提交，约 600 行）

**子任务**：

| ID | 内容 | 文件 | 行数 |
| --- | --- | --- | --- |
| T6.1 | 迁移 `V5__add_coupons.sql`：`coupons` 表 + `bookings` 新增 `coupon_code`、`discount_amount`（NOT NULL DEFAULT 0） | 新增 | 50 |
| T6.2 | 优惠码仓储 + 管理端 CRUD | `CouponRepository.java`、`CouponController.java` | 150 |
| T6.3 | 折扣计算（`BigDecimal` + `HALF_UP` + 下限 0） | `service/CouponService.java` | 90 |
| T6.4 | 校验：有效期、门槛、次数、启用状态 | `CouponService.java` | 80 |
| T6.5 | **乐观锁核销**：`UPDATE coupons SET used_count=used_count+1 WHERE id=? AND used_count<max_uses`，判断影响行数 | `CouponRepository.java` | 40 |
| T6.6 | 下单流程接入 + 订单快照 | `OrderService.create()` | 70 |
| T6.7 | 前端优惠码输入与错误提示 | `BookingDialog.tsx` | 90 |
| T6.8 | 测试：并发核销超发、金额精度、过期、门槛不足 | `CouponTest.java` | 170 |

**验收条件**：
- [ ] 100 元订单用 8 折码 → `discount_amount=20`、`total_amount=80`
- [ ] `max_uses=5` 的码在 10 个并发请求下恰好成功 5 次（其余返回 409）
- [ ] 过期码、门槛不足、已停用码分别返回明确错误信息
- [ ] 修改优惠码配置后，历史订单的 `discount_amount` 不变（快照验证）

---

### 第 4 周 · T7 评价系统（D19–D21，8 条提交，约 540 行）

**子任务**：迁移建表（`booking_id UNIQUE`）→ 仓储 → 提交评价（仅 `COMPLETED` 且属于本人）→ 均分聚合（`GROUP BY route_id` 批量）→ 列表接口 → 前端星级组件 → 线路卡片显示评分 → 测试（重复评价、未完成订单评价、越权）。

**验收条件**：
- [ ] 已完成订单可评价，重复提交返回 409
- [ ] 未完成订单/他人订单评价 → 403
- [ ] 线路列表一次查询返回所有均分，**不出现 N+1**（日志里 `SELECT` 次数固定）
- [ ] 无评价线路显示"暂无评分"而非 0 星

---

### 收尾（D22–D24，13 条提交）

| 日 | 任务 | 产出 |
| --- | --- | --- |
| D22 | 全量回归 + 修 bug（**冻结新功能**） | 回归报告写入 `docs/验收记录.md` |
| D22 | 更新 `docs/API.md`、`docs/架构说明.md` 至最新表结构 | 文档 |
| D23 | 写 `docs/演示脚本.md`：每个功能三步演示 + 数据佐证 + 可能提问 | 文档 |
| D23 | 更新 `README.md`：新功能清单、环境要求、`mvnw` 用法 | 文档 |
| D24 | 端到端冒烟 + `docker compose` 全新环境验证 | 报告 |
| D24 | 打 release tag `v3.0.0`，合并全部分支，关闭所有 PR | `git tag` |

---

## 8. 任务追踪

**唯一状态来源**：`docs/进度看板.md`。每天按 §6 第 8 步更新。

看板每行必须包含：任务 ID、状态（`TODO / DOING / DONE / BLOCKED`）、计划日、实际完成日、实际净增行数、提交数、验证命令结果。

**汇报格式**（每次任务完成后给用户）：

```
任务：T2.3 状态变更写入审计事件
分支：feat/T2-booking-events
改动：3 文件，+187 −12（git diff --shortstat）
测试：./mvnw -B -ntp verify → Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
      前端 npm test → 18 passed
      冒烟 → 全 PASS
约束检查：C1 未改 V1 ✅  C4 仅新增接口 ✅  C12 CSRF 未动 ✅
遗留：无
```

---

## 9. 禁止事项汇总

| 禁止 | 原因 |
| --- | --- |
| 空提交 / 改空格 / 复制粘贴充行数 | 可被 `git diff` 直接识别，且违反 §2.4 |
| `@Disabled` / `-DskipTests` / 断言改恒真 | 测试形同虚设，判定任务未完成 |
| 修改已执行的 Flyway 迁移 | 校验和失败，他人无法启动（C1） |
| 删除或改名已有接口字段 | 破坏前端与冒烟脚本（C4） |
| 引入 ORM / UI 框架 / 图表库 | 违反 C10、C11，且不利于答辩讲解 |
| 把 `.env`、`data/`、构建产物提交 | 违反 C14 |
| 功能没跑通就开下一个 | 答辩演示会崩，返工成本翻倍 |
| 连续失败仍反复重试同一方案 | 触发 §2.6 上报 |
