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
| POST | `/routes/{id}/image` | 上传线路封面，`multipart/form-data`，部件名 `file`，返回更新后的 `Route` | ADMIN |

上传规则（T4.3 起）：单张 ≤ 5MB（超出 413），只认 PNG / JPEG / WebP 且**以文件头为准**，扩展名与实际内容不符、内容不是图片都返回 400；存盘名由服务端生成（UUID + 归一后缀），原始文件名不入库也不落盘；线路不存在返回 404。响应里的 `imagePath` 是存盘文件名，为空表示从未上传，前端回退到 `imageKey`（C7）。

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
| POST | `/orders/{id}/review` | 提交评价，body `ReviewInput`，返回 `Review` | 下单客户本人 |

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

## 评价

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/orders/{id}/review` | 对已完成订单提交评价，body `ReviewInput` | 下单客户本人 |

```json
{ "rating": 5, "comment": "行程安排很顺，向导很耐心" }
```

- `rating` 必填，闭区间 1–5，越界返回 400；`comment` 可空、最长 500 字，不填时响应里是空串而不是 `null`。
- 只有**已完成（COMPLETED）且属于本人**的订单可以评价：他人订单、未完成或已取消订单一律 403（先判归属再判状态，不向外透露别人订单的状态）。
- 一笔订单只能评一次，重复提交返回 409，先提交的那条保持不变。判断由 `reviews.booking_id` 的唯一键兜底，并发提交也只会有一条落地。
- 请求体不接受 `routeId` / `customerId` / `createdAt`：线路与客户从订单取，时间由服务端记录（C12）。
- 错误码：`400` 评分越界或文字超长、`403` 非本人/非已完成/非客户角色、`404` 订单不存在、`409` 重复评价。

## 优惠码

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/coupons?q=&active=` | 优惠码列表（数组），在用的排前面、同组按到期先后 | ADMIN |
| GET | `/coupons/{id}` | 优惠码详情 | ADMIN |
| POST | `/coupons` | 新建优惠码，body `CouponInput` | ADMIN |
| PUT | `/coupons/{id}` | 修改配置，**不改动**已核销次数 | ADMIN |
| DELETE | `/coupons/{id}` | 删除，已核销过的码返回 409（应改为停用） | ADMIN |

- `discountRate` 是折扣率：`0.80` 表示八折，应收 = 订单原价 × 0.80。取值必须落在 0 与 1 之间（不含端点），否则 400。
- 码统一大写存储，前端输小写也能命中；大小写视为同一个码，重复录入返回 409。
- `startsOn` 晚于 `endsOn` 返回 400；`maxUses` 改到小于 `usedCount` 返回 409。
- 删除只允许从未被核销过的码：订单里存的是折扣快照，删掉码会让历史折扣失去来源。

下单时把码填进 `BookingInput.couponCode`（可空，`POST /orders`）：

```json
{ "routeId": 3, "travelerCount": 2, "contactName": "张明", "contactPhone": "13800000000",
  "remark": "", "couponCode": "AUTUMN8" }
```

- 码不区分大小写，快照里存大写形态；不填或留空按原价下单，行为与 T6.6 之前完全一致。
- 校验不通过直接拒绝下单、不落订单：码不存在/已停用/未开始/已过期/未满门槛 → 400（`message` 说明是哪一条），次数用尽 → 409。
- 通过后订单 `totalAmount` 写折后应收、`discountAmount` 写减免额，并占用一次名额；名额与订单在同一个事务里，订单写失败会一起回滚。

## 报表

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/dashboard` | 工作台汇总（统计口径见 `架构说明.md`） | 登录 |
| GET | `/reports/summary?months=6` | 报表汇总（响应 `ReportSummary`） | 登录（按角色收窄） |

`months` 缺省 6，闭区间 1–24，超出按边界生效；`monthly` **总是**回这么多行，没有订单的月份补 0。

统计范围由会话角色决定，没有"看别人"的参数：ADMIN 全量、CUSTOMER 只看自己的订单、EMPLOYEE 只看**已归属自己名下**的订单（待接池没有负责人，不算业绩——与工作台的可见口径不同）。`employeeRank` 只在 ADMIN 时非空，其余角色返回空数组，页面据此隐藏该区块。

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
             capacity, availableSeats, active, imageKey, highlights, imagePath}
Order       {id, orderNo, customerId, customerName, customerPhone, employeeId,
             employeeName, routeId, routeName, destination, startDate, durationDays,
             travelerCount, unitPrice, totalAmount, status, createdAt,
             contactName, contactPhone, remark, couponCode, discountAmount}
BookingInput {routeId, travelerCount, contactName, contactPhone, remark, couponCode?}
OrderEvent  {id, bookingId, fromStatus, toStatus, actorId, actorName, actorRole, note, createdAt}
Coupon      {id, code, description, discountRate, minAmount, maxUses, usedCount,
             startsOn, endsOn, active, createdAt}
CouponInput {code, description, discountRate, minAmount, maxUses, startsOn, endsOn, active}
Review      {id, bookingId, routeId, customerId, rating, comment, createdAt}
ReviewInput {rating, comment?}
Page<T>     {items: T[], total, page, size}
ReportSummary {scope, totalOrders, pendingOrders, assignedOrders, completedOrders,
               cancelledOrders, totalAmount, completedAmount,
               monthly: [{month, orders, revenue}],
               popularRoutes: [{routeId, name, destination, orderCount, travelerCount}],
               employeeRank: [{employeeId, employeeName, orderCount, amount}]}
```

- `employeeId` / `employeeName` / `fromStatus` 可为 `null`：分别表示尚未接单、下单建档。
- `imagePath` 可为 `null`：从未上传过封面的线路（含全部演示数据）保持空值，前端回退 `imageKey`。
- `couponCode` 可为 `null`：没用优惠码的订单（含全部历史订单）读作空值，`discountAmount` 同时为 `0.00`。
- `Review.comment` 不填时是空串；每笔订单最多一条评价，`bookingId` 在响应里可当唯一标识用。
- `totalAmount` 是**折后应收**（未用优惠码时等于"单价 × 人数"），`unitPrice` 始终是线路原价。
- `Coupon.usedCount` 只由核销累加，管理端编辑不会重置；`Coupon.active` 表示"是否可用"，与"是否过期"分开判断。
- `ReportSummary.scope` 是这份数字的口径（`ADMIN` / `CUSTOMER` / `EMPLOYEE`），页面据此写标题，不能把员工个人数字当成全团队。
- 密码散列不出现在任何响应里。
- 新增响应字段必须可空，前端不得假定其存在（C4）。
