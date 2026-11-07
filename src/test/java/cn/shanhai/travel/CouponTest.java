package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * T6 优惠码：表结构（T6.1）与管理端 CRUD（T6.2）的基线。
 *
 * <p>优惠码有两种数据：配置在 {@code coupons} 上（折扣率、门槛、次数、有效期），订单侧只留**快照**。
 * 因此表结构用例必须同时钉住"配置表齐全"和"历史订单不改也能读"——只检查新表存在，
 * 漏掉 {@code discount_amount} 的默认值，旧订单查询会在下一步直接炸掉。
 */
class CouponTest extends AbstractApiTest {
  private Map<String, Object> column(String table, String name) {
    var rows =
        jdbc.queryForList(
            "SELECT column_name, is_nullable, column_default FROM information_schema.columns"
                + " WHERE LOWER(table_name)=? AND LOWER(column_name)=?",
            table,
            name);
    assertEquals(1, rows.size(), table + " 表缺少列 " + name);
    return rows.getFirst();
  }

  @Test
  void couponTableKeepsDiscountUsageAndValidityColumns() {
    assertNotNull(column("coupons", "code"), "优惠码文本是核销时的唯一入口");
    assertEquals(
        "NO",
        column("coupons", "discount_rate").get("is_nullable"),
        "折扣率必填，否则算不出减免金额");
    assertEquals(
        "NO", column("coupons", "max_uses").get("is_nullable"), "次数上限必填，核销要靠它做条件更新");
    assertEquals(
        "NO", column("coupons", "used_count").get("is_nullable"), "已用次数从 0 起算，不能为空");
    assertEquals("NO", column("coupons", "starts_on").get("is_nullable"), "有效期起点必填");
    assertEquals("NO", column("coupons", "ends_on").get("is_nullable"), "有效期终点必填");
    // 说明允许不填，但落库统一用空串而不是 NULL，与 bookings.remark 同一约定，前端不必区分两种"空"。
    var description = column("coupons", "description");
    assertEquals("NO", description.get("is_nullable"));
    assertNotNull(description.get("column_default"));
  }

  @Test
  void existingBookingsReadZeroDiscountSnapshot() {
    var couponCode = column("bookings", "coupon_code");
    assertEquals("YES", couponCode.get("is_nullable"), "未使用优惠码的订单这一列为空");

    var discount = column("bookings", "discount_amount");
    assertEquals("NO", discount.get("is_nullable"), "历史订单一律当作零减免，不能是空值");
    assertNotNull(discount.get("column_default"), "新增列必须有默认值，旧数据才读得出来（C3）");

    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE discount_amount IS NULL OR discount_amount <> 0",
            Integer.class),
        "演示订单没有用过优惠码，减免金额应全部为 0");
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE coupon_code IS NOT NULL", Integer.class),
        "历史订单不应凭空带上优惠码");
  }
}
