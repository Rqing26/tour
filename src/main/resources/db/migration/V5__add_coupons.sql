-- 优惠码（T6）：折扣率、门槛、次数与有效期都配置在 coupons 上，订单侧只留快照。
-- 只新增表与可空/带默认值的列（C2、C3）：历史订单读出来是 NULL 与 0，不需要回填。
CREATE TABLE coupons (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE,
  description VARCHAR(200) NOT NULL DEFAULT '',
  -- 折扣率形如 0.80（八折）：应收 = 原价 * discount_rate，减免 = 原价 - 应收。
  discount_rate DECIMAL(4,2) NOT NULL,
  -- 门槛按订单原价判断，未达门槛不参与折扣。
  min_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  -- 次数上限与已用次数分开记：核销靠 max_uses/used_count 的条件更新防超发（T6.5）。
  max_uses INT NOT NULL DEFAULT 1,
  used_count INT NOT NULL DEFAULT 0,
  starts_on DATE NOT NULL,
  ends_on DATE NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_coupon_rate CHECK (discount_rate > 0 AND discount_rate < 1),
  CONSTRAINT ck_coupon_amount CHECK (min_amount >= 0),
  CONSTRAINT ck_coupon_uses CHECK (max_uses > 0 AND used_count >= 0 AND used_count <= max_uses),
  CONSTRAINT ck_coupon_window CHECK (ends_on >= starts_on)
);

-- 订单侧快照：coupon_code 记下单时用的码，码后来被改写或删除都不影响历史；
-- discount_amount 记下单当时算出的减免额，默认 0 让旧订单直接可读（C3），也保证总价口径不变。
ALTER TABLE bookings ADD COLUMN coupon_code VARCHAR(32);
ALTER TABLE bookings ADD COLUMN discount_amount DECIMAL(14,2) NOT NULL DEFAULT 0;
