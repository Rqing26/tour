-- 订单审计：每次状态变更写一条不可变事件，用于回溯"谁在何时把订单从什么状态改成了什么状态"。
-- 只新增表与索引，不改 V1 已发布的三张业务表（C1、C2）。
-- from_status 可空：下单即为首次建档，没有上一状态；note 有默认值，旧数据与调用方无需感知（C3）。
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
-- 时间线永远按"某订单 + 时间"取，这个复合索引同时覆盖过滤与排序。
CREATE INDEX idx_event_booking_time ON booking_events(booking_id, created_at);
