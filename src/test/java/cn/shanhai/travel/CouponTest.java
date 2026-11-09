package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.repository.CouponRepository;
import cn.shanhai.travel.repository.OrderRepository;
import cn.shanhai.travel.service.ApiException;
import cn.shanhai.travel.service.CouponService;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * T6 优惠码：表结构（T6.1）与管理端 CRUD（T6.2）的基线。
 *
 * <p>优惠码有两种数据：配置在 {@code coupons} 上（折扣率、门槛、次数、有效期），订单侧只留**快照**。
 * 因此表结构用例必须同时钉住"配置表齐全"和"历史订单不改也能读"——只检查新表存在，
 * 漏掉 {@code discount_amount} 的默认值，旧订单查询会在下一步直接炸掉。
 */
class CouponTest extends AbstractApiTest {
  @Autowired private CouponService couponService;
  @Autowired private CouponRepository couponRepo;

  /** 用 spy 包住真实仓储：平时照常写库，只在验证回滚时让写入失败。 */
  @MockitoSpyBean private OrderRepository spyOrders;

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

  // ---- T6.3 折扣计算（纯函数，不碰库） ----

  private static Api.Coupon couponOf(String rate, int maxUses, int usedCount) {
    var today = LocalDate.now();
    return new Api.Coupon(
        1L,
        "AUTUMN8",
        "",
        new BigDecimal(rate),
        new BigDecimal("0.00"),
        maxUses,
        usedCount,
        today.minusDays(1),
        today.plusDays(30),
        true,
        today.atStartOfDay());
  }

  @Test
  void hundredYuanOrderPaysEightyWithEightyPercentCoupon() {
    var quote = CouponService.compute(couponOf("0.80", 5, 0), new BigDecimal("100.00"));

    assertEquals(0, new BigDecimal("100.00").compareTo(quote.originalAmount()));
    assertEquals(
        new BigDecimal("20.00"), quote.discountAmount(), "八折码在原价 100 元上减免 20 元");
    assertEquals(new BigDecimal("80.00"), quote.payableAmount(), "应收是原价的八成");
    assertEquals("AUTUMN8", quote.code());
  }

  @Test
  void discountRoundsHalfUpToTheCent() {
    // 12.34 × 0.85 = 10.4890，第三位是 9，必须进位到 10.49；截断会得到 10.48，差一分钱。
    // 用 double 算的话这个乘积是 10.489000000000001，四舍五入的边界就不可靠了。
    var quote = CouponService.compute(couponOf("0.85", 5, 0), new BigDecimal("12.34"));

    assertEquals(new BigDecimal("10.49"), quote.payableAmount());
    assertEquals(new BigDecimal("1.85"), quote.discountAmount());
  }

  @Test
  void roundingUpToTheOriginalPriceLeavesNothingToDiscount() {
    // 0.01 × 0.99 = 0.0099 进位到 0.01，正好等于原价：减免是 0，不能因为"折扣率小于 1"就硬凑出一分钱。
    var quote = CouponService.compute(couponOf("0.99", 5, 0), new BigDecimal("0.01"));

    assertEquals(new BigDecimal("0.01"), quote.payableAmount());
    assertEquals(new BigDecimal("0.00"), quote.discountAmount());
  }

  @Test
  void rateAboveOneNeverRaisesThePrice() {
    // ck_coupon_rate 挡住 rate ≥ 1，这条只可能出现在人工改库或约束放宽之后：
    // 应收夹回原价，减免是 0——用码不能变成涨价，也不能给出负减免。
    var quote = CouponService.compute(couponOf("1.20", 5, 0), new BigDecimal("100.00"));

    assertEquals(new BigDecimal("100.00"), quote.payableAmount(), "折后应收不得超过原价");
    assertEquals(new BigDecimal("0.00"), quote.discountAmount(), "这种情况下没有减免可给");
  }

  // ---- T6.4 核销校验 + T6.5 条件自增 ----

  private void insertCoupon(
      String code, String rate, String minAmount, int maxUses, LocalDate from, LocalDate to) {
    insertCoupon(code, rate, minAmount, maxUses, from, to, true);
  }

  private void insertCoupon(
      String code,
      String rate,
      String minAmount,
      int maxUses,
      LocalDate from,
      LocalDate to,
      boolean active) {
    jdbc.update(
        "INSERT INTO coupons(code,description,discount_rate,min_amount,max_uses,used_count,starts_on,ends_on,active)"
            + " VALUES(?,?,?,?,?,0,?,?,?)",
        code,
        "",
        new BigDecimal(rate),
        new BigDecimal(minAmount),
        maxUses,
        java.sql.Date.valueOf(from),
        java.sql.Date.valueOf(to),
        active);
  }

  /** 不存在的码读作 0 次已用：它本来就没占用名额，这样同一句断言能覆盖"码根本没建成"的情形。 */
  private int usedCount(String code) {
    var counts =
        jdbc.queryForList("SELECT used_count FROM coupons WHERE code=?", Integer.class, code);
    return counts.isEmpty() ? 0 : counts.getFirst();
  }

  /** 被拒的核销既要给对状态码与可读原因，也不能占用名额——只断异常类型会让"先加次数再校验"照样通过。 */
  private void assertRejected(String code, String amount, HttpStatus status, String keyword) {
    var e =
        assertThrows(
            ApiException.class, () -> couponService.redeem(code, new BigDecimal(amount)));
    assertEquals(status, e.status());
    assertTrue(
        e.getMessage().contains(keyword), "拒绝原因要能直接告诉用户，实得：" + e.getMessage());
    assertEquals(0, usedCount(code), "被拒的核销不能占用名额");
  }

  @Test
  void redeemNormalizesCodeAndCountsOneUse() {
    insertCoupon(
        "AUTUMN8", "0.80", "0.00", 5, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30));

    var quote = couponService.redeem(" autumn8 ", new BigDecimal("100.00"));

    assertEquals("AUTUMN8", quote.code(), "码不区分大小写，回显统一大写");
    assertEquals(new BigDecimal("20.00"), quote.discountAmount());
    assertEquals(new BigDecimal("80.00"), quote.payableAmount());
    assertEquals(1, usedCount("AUTUMN8"), "核销一次就把计数加一");
  }

  @Test
  void expiredAndNotYetStartedCouponsAreRejectedWithDistinctReasons() {
    var today = LocalDate.now();
    insertCoupon("OLD9", "0.80", "0.00", 5, today.minusDays(10), today.minusDays(1));
    insertCoupon("SOON9", "0.80", "0.00", 5, today.plusDays(1), today.plusDays(10));

    assertRejected("OLD9", "100.00", HttpStatus.BAD_REQUEST, "已过期");
    assertRejected("SOON9", "100.00", HttpStatus.BAD_REQUEST, "尚未开始");
  }

  @Test
  void inactiveUnknownAndBelowThresholdCouponsAreRejected() {
    var today = LocalDate.now();
    insertCoupon("OFF9", "0.80", "0.00", 5, today.minusDays(1), today.plusDays(30), false);
    insertCoupon("BIG8", "0.80", "200.00", 5, today.minusDays(1), today.plusDays(30));

    assertRejected("OFF9", "500.00", HttpStatus.BAD_REQUEST, "已停用");
    assertRejected("BIG8", "100.00", HttpStatus.BAD_REQUEST, "未满 200.00");
    assertRejected("NOSUCH", "100.00", HttpStatus.BAD_REQUEST, "不存在");
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM coupons WHERE code='NOSUCH'", Integer.class));
  }

  @Test
  void exhaustedCouponKeepsCounterAtItsLimit() {
    var today = LocalDate.now();
    insertCoupon("ONCE9", "0.90", "0.00", 1, today.minusDays(1), today.plusDays(30));

    couponService.redeem("ONCE9", new BigDecimal("100.00"));
    assertEquals(1, usedCount("ONCE9"));

    var e =
        assertThrows(
            ApiException.class, () -> couponService.redeem("ONCE9", new BigDecimal("100.00")));
    assertEquals(HttpStatus.CONFLICT, e.status(), "次数用尽是状态冲突，不是参数错误");
    assertTrue(e.getMessage().contains("已达上限"));
    assertEquals(1, usedCount("ONCE9"), "拒绝之后计数不能被多刷一次");
  }

  @Test
  void conditionalConsumeStopsExactlyAtMaxUses() {
    var today = LocalDate.now();
    insertCoupon("LIMIT2", "0.90", "0.00", 2, today.minusDays(1), today.plusDays(30));
    long id = jdbc.queryForObject("SELECT id FROM coupons WHERE code='LIMIT2'", Long.class);

    assertTrue(couponRepo.consume(id));
    assertTrue(couponRepo.consume(id));
    assertFalse(couponRepo.consume(id), "第三次条件更新影响 0 行，这才是防超发的判据");
    assertEquals(2, usedCount("LIMIT2"), "计数停在上限，不会被刷成 3");
  }

  // ---- T6.6 下单接入 + 订单快照 ----

  /** 按指定单价造一条线路：金额用例要整百、要小数，靠固定单价算不出来。 */
  private long pricedRoute(String price, int capacity) {
    return routes.create(
        new Api.RouteInput(
            "折扣线路",
            "测试目的地",
            "用于验证优惠码金额",
            LocalDate.now().plusDays(40),
            3,
            new BigDecimal(price),
            capacity,
            true,
            "guilin",
            List.of("测试景点")));
  }

  private Map<String, Object> bookingWithCoupon(long routeId, int count, String code) {
    return Map.of(
        "routeId",
        routeId,
        "travelerCount",
        count,
        "contactName",
        "折扣联系人",
        "contactPhone",
        "13800000000",
        "remark",
        "优惠码用例",
        "couponCode",
        code);
  }

  private int orderCount() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM bookings", Integer.class);
  }

  private BigDecimal stored(String column, long orderId) {
    return jdbc.queryForObject(
        "SELECT " + column + " FROM bookings WHERE id=?", BigDecimal.class, orderId);
  }

  @Test
  void orderWithCouponStoresDiscountedSnapshot() throws Exception {
    var admin = login("admin");
    create(admin, "AUTUMN8", "0.80", 5);
    long route = pricedRoute("100.00", 10);

    var created =
        ok(login("customer"), "POST", "/api/orders", bookingWithCoupon(route, 1, "autumn8"));

    assertEquals("AUTUMN8", created.get("couponCode").asText(), "码按大写快照，与配置表同一形态");
    assertEquals(
        0,
        new BigDecimal("20.00").compareTo(created.get("discountAmount").decimalValue()),
        "八折码在 100 元订单上减免 20 元");
    assertEquals(
        0,
        new BigDecimal("80.00").compareTo(created.get("totalAmount").decimalValue()),
        "订单总价是折后应收，不是原价");
    assertEquals(
        0,
        new BigDecimal("100.00").compareTo(created.get("unitPrice").decimalValue()),
        "单价仍是线路原价，折后金额只体现在总额与减免两列");
    assertEquals(1, usedCount("AUTUMN8"), "下单成功即占用一次名额");
  }

  @Test
  void orderWithoutCouponKeepsFullPriceAndEmptySnapshot() throws Exception {
    long route = pricedRoute("100.00", 10);

    var created = ok(login("customer"), "POST", "/api/orders", booking(route, 2));

    assertTrue(created.get("couponCode").isNull(), "不填优惠码时快照列为空");
    assertEquals(
        0,
        BigDecimal.ZERO.compareTo(created.get("discountAmount").decimalValue()),
        "零减免而不是缺字段，前端不必判断两种「空」");
    assertEquals(
        0,
        new BigDecimal("200.00").compareTo(created.get("totalAmount").decimalValue()),
        "不用优惠码时总价口径与改动前一致（C4）");
  }

  @Test
  void expiredCouponRejectsOrderWithoutWritingAnything() throws Exception {
    var today = LocalDate.now();
    insertCoupon("OLD9", "0.80", "0.00", 5, today.minusDays(10), today.minusDays(1));
    long route = pricedRoute("100.00", 10);
    var customer = login("customer");
    int before = orderCount();

    var rejected = request(customer, "POST", "/api/orders", bookingWithCoupon(route, 1, "OLD9"));

    assertEquals(400, rejected.getResponse().getStatus());
    assertTrue(
        data(rejected).get("message").asText().contains("已过期"),
        "拒绝原因要能直接给用户看，实得：" + data(rejected).get("message").asText());
    assertEquals(before, orderCount(), "优惠码被拒时不能留下半张订单");
    assertEquals(0, usedCount("OLD9"), "被拒的下单不能占用名额");
  }

  @Test
  void exhaustedCouponRejectsOrderWithConflict() throws Exception {
    var today = LocalDate.now();
    insertCoupon("ONCE9", "0.90", "0.00", 1, today.minusDays(1), today.plusDays(30));
    jdbc.update("UPDATE coupons SET used_count=1 WHERE code='ONCE9'");
    long route = pricedRoute("100.00", 10);
    var customer = login("customer");
    int before = orderCount();

    assertEquals(
        409,
        request(customer, "POST", "/api/orders", bookingWithCoupon(route, 1, "ONCE9"))
            .getResponse()
            .getStatus(),
        "次数用尽是状态冲突，不是参数错误");
    assertEquals(before, orderCount());
    assertEquals(1, usedCount("ONCE9"));
  }

  @Test
  void changingCouponConfigKeepsHistoricalDiscount() throws Exception {
    var admin = login("admin");
    long couponId = create(admin, "AUTUMN8", "0.80", 5).get("id").asLong();
    long route = pricedRoute("100.00", 10);
    long orderId =
        ok(login("customer"), "POST", "/api/orders", bookingWithCoupon(route, 1, "AUTUMN8"))
            .get("id")
            .asLong();

    ok(admin, "PUT", "/api/coupons/" + couponId, input("AUTUMN8", "0.50", 5, true));

    assertEquals(
        0,
        new BigDecimal("20.00").compareTo(stored("discount_amount", orderId)),
        "改配置不能回头改写历史订单：减免是下单当时的快照");
    assertEquals(
        0,
        new BigDecimal("80.00").compareTo(stored("total_amount", orderId)),
        "历史总价同样保持不变");
    assertEquals("AUTUMN8", jdbc.queryForObject(
        "SELECT coupon_code FROM bookings WHERE id=?", String.class, orderId));
  }

  // ---- T6.8 并发超发、金额精度、门槛不足与事务回滚 ----

  /** 一个"同时起跑"的批量请求：每个线程拿到自己的序号，方便取各自的会话与请求体。 */
  @FunctionalInterface
  private interface Attempt {
    int run(int index) throws Exception;
  }

  private List<Integer> attempts(int count, Attempt attempt) throws Exception {
    try (var pool = Executors.newFixedThreadPool(count)) {
      var ready = new CountDownLatch(count);
      var go = new CountDownLatch(1);
      var futures = new ArrayList<Future<Integer>>();
      for (int i = 0; i < count; i++) {
        int index = i;
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  return attempt.run(index);
                }));
      }
      assertTrue(ready.await(5, TimeUnit.SECONDS), "线程未能在 5 秒内全部就绪");
      go.countDown();
      var results = new ArrayList<Integer>();
      for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
      return results;
    }
  }

  private static long countOf(List<Integer> statuses, int wanted) {
    return statuses.stream().filter(s -> s == wanted).count();
  }

  @Test
  void tenConcurrentRedemptionsYieldExactlyFiveSuccesses() throws Exception {
    var today = LocalDate.now();
    insertCoupon("RUSH9", "0.90", "0.00", 5, today.minusDays(1), today.plusDays(30));

    var statuses =
        attempts(
            10,
            i -> {
              try {
                couponService.redeem("RUSH9", new BigDecimal("100.00"));
                return 200;
              } catch (ApiException e) {
                return e.status().value();
              }
            });

    assertEquals(5, countOf(statuses, 200), "max_uses=5 的码在 10 个并发核销下只能成功 5 次");
    assertEquals(5, countOf(statuses, 409), "其余请求按状态冲突拒绝，不能是 500");
    assertEquals(5, usedCount("RUSH9"), "计数停在上限");
  }

  @Test
  void tenConcurrentOrdersCannotOverspendCoupon() throws Exception {
    var today = LocalDate.now();
    insertCoupon("RUSH8", "0.80", "0.00", 5, today.minusDays(1), today.plusDays(30));
    long route = pricedRoute("100.00", 50);
    // 每个下单请求都要自己的会话与 CSRF 令牌：同一个会话跨线程用会被安全链判成串号。
    var clients = new ArrayList<Client>();
    for (int i = 0; i < 10; i++) clients.add(login("customer"));
    int before = orderCount();

    var statuses =
        attempts(
            10,
            i ->
                request(clients.get(i), "POST", "/api/orders", bookingWithCoupon(route, 1, "RUSH8"))
                    .getResponse()
                    .getStatus());

    assertEquals(5, countOf(statuses, 200), "10 个并发下单只能有 5 笔用上这张码");
    assertEquals(5, countOf(statuses, 409), "抢不到名额的请求返回 409，不能让数据库报 500");
    assertEquals(
        before + 5, orderCount(), "被拒的请求不能留下订单，也不能把整张表写乱");
    assertEquals(
        5,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE coupon_code='RUSH8'", Integer.class),
        "订单数与占用的名额必须一一对应");
    assertEquals(5, usedCount("RUSH8"));
  }

  @Test
  void orderWriteFailureRollsBackCouponUsage() throws Exception {
    var admin = login("admin");
    create(admin, "AUTUMN8", "0.80", 5);
    long route = pricedRoute("100.00", 10);
    var customer = login("customer");
    int before = orderCount();
    doThrow(new IllegalStateException("模拟订单写入失败"))
        .when(spyOrders)
        .create(anyLong(), any(), any(), any());
    try {
      var failed = request(customer, "POST", "/api/orders", bookingWithCoupon(route, 1, "AUTUMN8"));
      assertEquals(500, failed.getResponse().getStatus(), "写入失败要如实报错，不能假装成功");
    } finally {
      reset(spyOrders);
    }

    assertEquals(0, usedCount("AUTUMN8"), "订单没写成，占掉的名额必须跟着回滚");
    assertEquals(before, orderCount());
  }

  @Test
  void orderStoresDiscountedAmountsRoundedToTheCent() throws Exception {
    var today = LocalDate.now();
    // 12.34 × 0.85 = 10.489：落库必须进位到 10.49，截断会得到 10.48。
    insertCoupon("PREC85", "0.85", "0.00", 5, today.minusDays(1), today.plusDays(30));
    long route = pricedRoute("12.34", 10);

    var created =
        ok(login("customer"), "POST", "/api/orders", bookingWithCoupon(route, 1, "PREC85"));
    long orderId = created.get("id").asLong();

    assertEquals(
        0,
        new BigDecimal("10.49").compareTo(created.get("totalAmount").decimalValue()),
        "响应里的总价必须与算出来的应收一致");
    assertEquals(
        0,
        new BigDecimal("1.85").compareTo(created.get("discountAmount").decimalValue()),
        "减免 = 原价 − 应收，同样进位到分");
    assertEquals(
        0, new BigDecimal("10.49").compareTo(stored("total_amount", orderId)), "落库金额不能差一分");
    assertEquals(0, new BigDecimal("1.85").compareTo(stored("discount_amount", orderId)));
  }

  @Test
  void belowThresholdCouponRejectsOrderWithReadableReason() throws Exception {
    var today = LocalDate.now();
    insertCoupon("BIG8", "0.80", "200.00", 5, today.minusDays(1), today.plusDays(30));
    long route = pricedRoute("100.00", 10);
    var customer = login("customer");
    int before = orderCount();

    var rejected = request(customer, "POST", "/api/orders", bookingWithCoupon(route, 1, "BIG8"));

    assertEquals(400, rejected.getResponse().getStatus());
    assertTrue(
        data(rejected).get("message").asText().contains("未满 200.00"),
        "提示要带上门槛金额，用户才知道差多少，实得：" + data(rejected).get("message").asText());
    assertEquals(before, orderCount());
    assertEquals(0, usedCount("BIG8"));
  }
}
