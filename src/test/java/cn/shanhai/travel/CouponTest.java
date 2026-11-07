package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
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

  // ---- T6.2 管理端 CRUD ----

  /** 一份合格的录入体：门槛 100 元、一个月有效，只把要验证的字段换成变量。 */
  private Map<String, Object> input(String code, String rate, int maxUses, boolean active) {
    return Map.of(
        "code", code,
        "description", "秋季促销",
        "discountRate", rate,
        "minAmount", "100.00",
        "maxUses", maxUses,
        "startsOn", LocalDate.now().minusDays(1).toString(),
        "endsOn", LocalDate.now().plusDays(30).toString(),
        "active", active);
  }

  private JsonNode create(Client admin, String code, String rate, int maxUses) throws Exception {
    return ok(admin, "POST", "/api/coupons", input(code, rate, maxUses, true));
  }

  private int status(Client c, String method, String path, Object body) throws Exception {
    return request(c, method, path, body).getResponse().getStatus();
  }

  private int getStatus(Client c, String path) throws Exception {
    return mvc.perform(get(path).session(c.session())).andReturn().getResponse().getStatus();
  }

  @Test
  void adminCreatesCouponWithUppercasedCode() throws Exception {
    var admin = login("admin");
    var created = create(admin, "autumn8", "0.80", 5);

    assertEquals("AUTUMN8", created.get("code").asText(), "码统一大写存储，前端输小写也能命中");
    assertEquals(0, created.get("usedCount").asInt(), "新码从 0 次起算");
    assertEquals(5, created.get("maxUses").asInt());
    assertEquals(
        0,
        new BigDecimal("0.80").compareTo(created.get("discountRate").decimalValue()),
        "折扣率按两位小数原样回显，0.8 与 0.80 不应出现两种形态");
    assertEquals("秋季促销", created.get("description").asText());
  }

  @Test
  void duplicateCodeIsRejectedRegardlessOfCase() throws Exception {
    var admin = login("admin");
    create(admin, "AUTUMN8", "0.80", 5);

    assertEquals(
        409,
        status(admin, "POST", "/api/coupons", input("autumn8", "0.75", 3, true)),
        "大小写不同仍是同一个码，重复录入应当 409");
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM coupons", Integer.class));
  }

  @Test
  void couponManagementIsAdminOnly() throws Exception {
    var admin = login("admin");
    create(admin, "AUTUMN8", "0.80", 5);

    var customer = login("customer");
    assertEquals(403, getStatus(customer, "/api/coupons"), "客户没有优惠码管理入口");
    assertEquals(
        403,
        status(customer, "POST", "/api/coupons", input("CUST10", "0.90", 1, true)),
        "客户不能自行建码");

    var employee = login("employee");
    assertEquals(
        403,
        status(employee, "DELETE", "/api/coupons/1", Map.of()),
        "员工同样只有接单权限");

    assertEquals(401, mvc.perform(get("/api/coupons")).andReturn().getResponse().getStatus());
    assertEquals(
        1,
        jdbc.queryForObject("SELECT COUNT(*) FROM coupons", Integer.class),
        "被拒的请求不能落库");
  }

  @Test
  void listFiltersByActiveFlagAndKeyword() throws Exception {
    var admin = login("admin");
    create(admin, "SUMMER8", "0.80", 5);
    ok(admin, "POST", "/api/coupons", input("WINTER9", "0.90", 5, false));

    assertEquals(2, data(mvc.perform(get("/api/coupons").session(admin.session())).andReturn()).size());

    var activeOnly =
        data(mvc.perform(get("/api/coupons?active=true").session(admin.session())).andReturn());
    assertEquals(1, activeOnly.size());
    assertEquals("SUMMER8", activeOnly.get(0).get("code").asText());

    var byKeyword =
        data(mvc.perform(get("/api/coupons?q=winter").session(admin.session())).andReturn());
    assertEquals(1, byKeyword.size(), "关键词不区分大小写");
    assertEquals("WINTER9", byKeyword.get(0).get("code").asText());
  }

  @Test
  void editingRulesKeepsUsedCountAndRefusesShrinkingBelowIt() throws Exception {
    var admin = login("admin");
    long id = create(admin, "AUTUMN8", "0.80", 5).get("id").asLong();
    // 已核销 3 次：核销本身属 T6.5，这里只把计数摆好，验证编辑不会把它抹掉。
    jdbc.update("UPDATE coupons SET used_count=3 WHERE id=?", id);

    var shrink = request(admin, "PUT", "/api/coupons/" + id, input("AUTUMN8", "0.75", 2, true));
    assertEquals(409, shrink.getResponse().getStatus(), "上限 2 小于已用 3，直接改会让计数自相矛盾");
    assertTrue(
        data(shrink).get("message").asText().contains("已使用次数"),
        "拒绝原因要说清是已用次数，而不是笼统的「记录重复」");

    var updated = ok(admin, "PUT", "/api/coupons/" + id, input("AUTUMN8", "0.75", 10, true));
    assertEquals(10, updated.get("maxUses").asInt());
    assertEquals(3, updated.get("usedCount").asInt(), "编辑配置不能重置已核销次数");
    assertEquals(0, new BigDecimal("0.75").compareTo(updated.get("discountRate").decimalValue()));
  }

  @Test
  void unusedCouponCanBeDeletedButUsedOneOnlyDeactivated() throws Exception {
    var admin = login("admin");
    long unused = create(admin, "UNUSED8", "0.80", 5).get("id").asLong();
    long used = create(admin, "USED9", "0.90", 5).get("id").asLong();
    jdbc.update("UPDATE coupons SET used_count=1 WHERE id=?", used);

    ok(admin, "DELETE", "/api/coupons/" + unused, Map.of());
    assertEquals(404, getStatus(admin, "/api/coupons/" + unused), "删掉之后详情应当 404");

    assertEquals(
        409,
        status(admin, "DELETE", "/api/coupons/" + used, Map.of()),
        "用过的码要保留，否则订单快照里的折扣对不上来源");
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM coupons WHERE id=?", Integer.class, used));
  }

  @Test
  void rejectsInvertedWindowAndOutOfRangeRate() throws Exception {
    var admin = login("admin");
    var inverted =
        Map.of(
            "code", "BADWIN",
            "description", "",
            "discountRate", "0.80",
            "minAmount", "0.00",
            "maxUses", 5,
            "startsOn", LocalDate.now().plusDays(10).toString(),
            "endsOn", LocalDate.now().toString(),
            "active", true);
    assertEquals(400, status(admin, "POST", "/api/coupons", inverted));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM coupons", Integer.class));

    assertEquals(400, status(admin, "POST", "/api/coupons", input("TOOHIGH", "1.50", 5, true)));
    assertEquals(
        400,
        status(admin, "POST", "/api/coupons", input("TOOLOW", "0.00", 5, true)),
        "零折等于白送，不允许；折扣率必须落在 0 与 1 之间（不含端点）");
  }

  @Test
  void renamingCodeOntoAnotherCouponIsRejected() throws Exception {
    var admin = login("admin");
    long first = create(admin, "FIRST8", "0.80", 5).get("id").asLong();
    create(admin, "SECOND9", "0.90", 5);

    assertEquals(
        409,
        status(admin, "PUT", "/api/coupons/" + first, input("SECOND9", "0.80", 5, true)));

    var renamed = ok(admin, "PUT", "/api/coupons/" + first, input("THIRD7", "0.80", 5, true));
    assertEquals("THIRD7", renamed.get("code").asText(), "改成没被占用的码应当成功");
  }
}
