# HTTP 接口基线

> 由 T3.4 首次建立，作为 C4「接口只增不改」的对照基线。改动任何已有的路径、方法、字段名或状态码都算破坏性变更。
> 所有接口前缀 `/travel/api`（前端由 Vite 代理到后端；直接访问 jar 时走同一前缀）。

## 通用约定

- 会话：Spring Security 服务端会话（`JSESSIONID`），登录成功后会话 ID 轮换，**必须重新取 CSRF 令牌**。
- CSRF：所有非 GET 请求必须带 `X-CSRF-TOKEN`，令牌从 `GET /auth/csrf` 获得（响应 `{token}`）。
- 请求体与响应体均为 JSON（`Content-Type: application/json`）。
- 错误统一为 `{"message": "..."}`，状态码语义：

| 状态码 | 含义 |
| --- | --- |
| 400 | 参数校验失败、状态值非法、排序键不在白名单 |
| 401 | 未登录或会话失效 |
| 403 | 已登录但无权执行该操作（角色不符或不是记录归属人） |
| 404 | 记录不存在 |
| 409 | 业务冲突（名额不足、状态已变化、重复操作） |
| 413 | 上传文件超限（T4 起） |

## 认证与账号

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/auth/csrf` | 取 CSRF 令牌 `{token}`，登录后需重取 | 匿名 |
| GET | `/auth/config` | 是否开放注册 `{registrationOpen}` | 匿名 |
| GET | `/auth/me` | 当前登录用户 | 登录 |
| POST | `/auth/login` | 登录，body `{username,password}`，返回 `User` | 匿名 |
| POST | `/auth/register` | 注册客户，body `{username,displayName,password,phone}` | 需开放注册 |
| PUT | `/account` | 修改自己的资料 `{displayName,phone}` | 登录 |
| PUT | `/account/password` | 改密码 `{currentPassword,newPassword}`，改完需重新登录 | 登录 |

## 线路

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/routes?q=&destination=&active=` | 线路列表（数组） | 登录 |
| GET | `/routes/{id}` | 线路详情 | 登录 |
| POST | `/routes` | 新建线路，body `RouteInput` | ADMIN |
| PUT | `/routes/{id}` | 修改线路 | ADMIN |
| DELETE | `/routes/{id}` | 删除线路，有关联订单时 409 | ADMIN |

## 用户

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/users?q=&role=` | 用户列表（数组） | ADMIN |
| POST | `/users` | 新建账号，body `CreateUser` | ADMIN |
| PUT | `/users/{id}` | 改资料与启用状态，body `UpdateUser` | ADMIN |
| DELETE | `/users/{id}` | 删除账号，有关联订单时 409 | ADMIN |

## 订单

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/orders` | 订单列表，见下方「分页契约」 | 登录（按角色收窄） |
| GET | `/orders/{id}/events` | 订单状态留痕，按时间升序（数组） | 可见该订单的角色 |
| POST | `/orders` | 下单，body `BookingInput` | CUSTOMER |
| POST | `/orders/{id}/claim` | 接单 PENDING→ASSIGNED | EMPLOYEE |
| POST | `/orders/{id}/release` | 退回 ASSIGNED→PENDING | 负责员工 |
| POST | `/orders/{id}/complete` | 完成 ASSIGNED→COMPLETED | 负责员工 |
| POST | `/orders/{id}/cancel` | 取消，仅 PENDING/ASSIGNED | 下单客户 |

### 分页契约（T3.4 起）

`GET /orders` 支持 `q`、`status`、`page`、`size`、`sort` 五个查询参数，**响应形状取决于是否带分页参数**：

- **不带 `page` 且不带 `size`**：返回订单数组，与 T3.4 之前完全一致（旧前端与冒烟脚本依赖此形状）。
- **带了 `page` 或 `size`**：返回分页信封

```json
{ "items": [ /* Order */ ], "total": 128, "page": 0, "size": 10 }
```

| 参数 | 默认 | 规则 |
| --- | --- | --- |
| `q` | 空 | 字面匹配订单号、线路名、客户名、联系人、负责人姓名；`%` 与 `_` 不作通配符 |
| `status` | 空 | 只能是 `PENDING/ASSIGNED/COMPLETED/CANCELLED`，其它值 400 |
| `page` | 0 | 从 0 开始；负数按 0 处理 |
| `size` | 10 | 服务端归一到 1–100，**超过 100 按 100 生效**，响应回显真正生效的值 |
| `sort` | `createdAt` 降序 | `字段` 或 `字段,asc` 或 `字段,desc`；字段白名单：`createdAt`、`totalAmount`、`startDate`，其它值 400 |

`total` 是**同一过滤条件**下的全量行数（可见范围 + 状态 + 关键词），不是当前页条数；排序一律以 `id` 倒序兜底，翻页不重不漏。
三种角色的可见范围在 SQL 里收窄：ADMIN 不限；CUSTOMER 仅 `customer_id=自己`；EMPLOYEE 为 `status='PENDING' OR employee_id=自己`。

## 报表

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/dashboard` | 工作台汇总（统计口径见 `架构说明.md`） | 登录 |

## 运维

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/actuator/health` | 健康检查，匿名可读，只返回 `{"status":"UP"}` |
| GET | `/v3/api-docs` | OpenAPI JSON |
| GET | `/swagger-ui/index.html` | 接口文档页面 |

## 数据模型（响应字段）

```text
User        {id, username, displayName, phone, role, enabled}
Route       {id, name, destination, description, startDate, durationDays, price,
             capacity, availableSeats, active, imageKey, highlights}
Order       {id, orderNo, customerId, customerName, customerPhone, employeeId,
             employeeName, routeId, routeName, destination, startDate, durationDays,
             travelerCount, unitPrice, totalAmount, status, createdAt,
             contactName, contactPhone, remark}
OrderEvent  {id, bookingId, fromStatus, toStatus, actorId, actorName, actorRole, note, createdAt}
Page<T>     {items: T[], total, page, size}
```

- `employeeId` / `employeeName` / `fromStatus` 可为 `null`：分别表示尚未接单、下单建档。
- 密码散列不出现在任何响应里。
- 新增响应字段必须可空，前端不得假定其存在（C4）。
