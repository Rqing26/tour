-- 订单分页下推：把"归属过滤 + 创建时间排序"做成复合索引，让 OFFSET 深翻页也能走索引扫描。
-- 只新增索引，不改 V1 已发布的表结构（C1、C2）；索引名与列序由 OrderPaginationTest 钉住。
--
-- 三个查询形状（对应三种角色的可见性）：
--   CUSTOMER  WHERE customer_id=?                    ORDER BY created_at DESC,id DESC
--   ADMIN     WHERE status=?                         ORDER BY created_at DESC,id DESC
--   EMPLOYEE  WHERE status='PENDING' OR employee_id=? ORDER BY created_at DESC,id DESC
-- 每列顺序都是"过滤列 → created_at → id"：created_at 负责范围与排序，id 兜底让同一秒的订单翻页不重不漏。
CREATE INDEX idx_booking_customer_time ON bookings(customer_id, created_at, id);
CREATE INDEX idx_booking_status_time ON bookings(status, created_at, id);
CREATE INDEX idx_booking_employee_time ON bookings(employee_id, created_at, id);
-- 说明：V1 的 idx_booking_customer(bookings.customer_id) 与 idx_booking_employee_status(
-- bookings.employee_id, status) 保留不动——前者已是新索引的前缀，后者覆盖"按员工数各状态"的统计口径，
-- 删除属于收缩性变更，不在本次新增范围内。
