-- 评价（T7）：一条已完成订单对应一条评价，评分 1-5，均分在查询时按线路聚合（T7.3）。
-- 只新增表，不动既有列（C2）；booking_id 既是外键也是唯一键，重复评价在库层面就写不进去。
CREATE TABLE reviews (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  booking_id BIGINT NOT NULL,
  -- route_id / customer_id 是下单时的事实，写入时从 bookings 取值：
  -- 均分聚合直接按 route_id 分组，不必每次回连订单表；客户维度也不依赖二次查询。
  route_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  -- 评分只收 1-5：0 星与 6 星都会让均分失真，用约束而不是只靠接口校验。
  rating INT NOT NULL,
  -- 文字评价允许留空，落库用空串而不是 NULL，与 bookings.remark 同一约定。
  comment VARCHAR(500) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_review_booking UNIQUE (booking_id),
  CONSTRAINT fk_review_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
  CONSTRAINT fk_review_route FOREIGN KEY (route_id) REFERENCES travel_routes(id),
  CONSTRAINT fk_review_customer FOREIGN KEY (customer_id) REFERENCES app_users(id),
  CONSTRAINT ck_review_rating CHECK (rating BETWEEN 1 AND 5)
);

-- 线路详情与卡片按线路取均分，聚合与最新评价都走这条索引。
CREATE INDEX idx_review_route ON reviews(route_id, created_at);
