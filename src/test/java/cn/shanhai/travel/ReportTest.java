package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.model.*;
import cn.shanhai.travel.repository.ReportRepository.Scope;
import cn.shanhai.travel.service.ReportService;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T5 报表聚合用例：先钉住"数字从哪来"，再钉住"谁的数字"。
 *
 * <p>期望值尽量用原生 SQL 计数做对照（{@code COUNT(*)} / {@code SUM(total_amount)}），不拿被测仓储的实现自证；
 * 口径类用例则用"同一批数据在两种范围下的差"来表达——只断绝对值的话，把范围改错也能通过。
 */
class ReportTest extends AbstractApiTest {
  @Autowired ReportService reports;

  /** 清空订单后从零造数：报表用例的断言依赖精确行数，不能被演示数据干扰。 */
  private void clearOrders() {
    jdbc.update("DELETE FROM booking_events");
    jdbc.update("DELETE FROM bookings");
  }

  private long accountId(String username) {
    return users.byUsername(username).orElseThrow().id();
  }

  private Account accountOf(String username) {
    return users.byUsername(username).orElseThrow();
  }

  /** 造一笔指定创建月份与状态的订单：创建时间靠 UPDATE 回填，区间聚合才有可断言的边界。 */
  private long order(
      long customerId, Long employeeId, long routeId, int count, String status, YearMonth at) {
    var route = routes.find(routeId).orElseThrow();
    long id =
        orders.create(
            customerId,
            route,
            new Api.BookingInput(routeId, count, "报表联系人", "13800000000", "报表用例", null),
            null);
    orders.transition(id, status, employeeId);
    jdbc.update(
        "UPDATE bookings SET created_at=? WHERE id=?", at.atDay(15).atTime(10, 0), id);
    return id;
  }

  private static void assertAmount(String expected, BigDecimal actual) {
    assertAmount(expected, actual, "金额不符");
  }

  private static void assertAmount(String expected, BigDecimal actual, String message) {
    assertEquals(
        0, new BigDecimal(expected).compareTo(actual), message + "：应为 " + expected + "，实得 " + actual);
  }

  /** 一次聚合要同时给出总数、四个状态与两个金额；金额口径是"未取消"，取消订单只计数不计钱。 */
  @Test
  void totalsCountStatusesAndExcludeCancelledFromRevenue() {
    clearOrders();
    long customer = accountId("customer");
    long employee = accountId("employee");
    long route = route(100);

    order(customer, null, route, 2, "PENDING", YearMonth.now());
    order(customer, employee, route, 1, "ASSIGNED", YearMonth.now());
    order(customer, employee, route, 2, "COMPLETED", YearMonth.now());
    order(customer, null, route, 1, "CANCELLED", YearMonth.now());

    var totals = reports.totals(Scope.ALL);
    assertEquals(4, totals.total());
    assertEquals(1, totals.pending());
    assertEquals(1, totals.assigned());
    assertEquals(1, totals.completed());
    assertEquals(1, totals.cancelled());
    assertAmount("6000.00", totals.amount());
    assertAmount("2400.00", totals.completedAmount());

    // 原生 SQL 做 oracle：聚合结果与直接求和必须一致。
    assertAmount(
        "6000.00",
        jdbc.queryForObject(
            "SELECT COALESCE(SUM(total_amount),0) FROM bookings WHERE status<>'CANCELLED'",
            BigDecimal.class));
  }

  /** 范围收窄：客户的汇总只含自己的订单，管理员含全部，且两者的差额恰是别人的订单。 */
  @Test
  void customerScopeCountsOnlyOwnOrders() {
    clearOrders();
    long customer = accountId("customer");
    long guest = accountId("demo_guest");
    long route = route(100);

    order(customer, null, route, 1, "PENDING", YearMonth.now());
    order(customer, null, route, 1, "COMPLETED", YearMonth.now());
    order(guest, null, route, 1, "PENDING", YearMonth.now());

    var mine = reports.totals(Scope.customer(customer));
    assertEquals(2, mine.total());
    assertAmount("2400.00", mine.amount());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE customer_id=? AND status='COMPLETED'",
            Long.class,
            customer));
    assertEquals(3, reports.totals(Scope.ALL).total(), "管理员应看到全部 3 笔");
  }

  /** 员工的两个范围必须分清：待接池对列表可见，但不算任何人的业绩。 */
  @Test
  void employeeScopeSeparatesOwnedOrdersFromPendingPool() {
    clearOrders();
    long customer = accountId("customer");
    long employee = accountId("employee");
    long other =
        users.create("report_emp", "报表顾问", "x", "13800000009", Role.EMPLOYEE);
    long route = route(100);

    order(customer, employee, route, 1, "ASSIGNED", YearMonth.now());
    order(customer, employee, route, 1, "COMPLETED", YearMonth.now());
    order(customer, null, route, 1, "PENDING", YearMonth.now());
    order(customer, other, route, 1, "ASSIGNED", YearMonth.now());

    assertEquals(2, reports.totals(Scope.ownedBy(employee)).total(), "经手范围只算自己名下的");
    assertEquals(3, reports.totals(Scope.visibleTo(employee)).total(), "可见范围还要算上待接池");
    assertEquals(4, reports.totals(Scope.ALL).total());
  }

  /** 月度序列按窗口补齐：没有订单的月份返回 0 而不是缺行，营收不含已取消。 */
  @Test
  void monthlyFillsGapsAndSkipsCancelledRevenue() {
    clearOrders();
    long customer = accountId("customer");
    long route = route(100);
    var now = YearMonth.now();

    order(customer, null, route, 2, "COMPLETED", now);
    order(customer, null, route, 1, "PENDING", now.minusMonths(3));
    order(customer, null, route, 1, "CANCELLED", now.minusMonths(3));

    var monthly = reports.monthly(Scope.ALL, 6);
    assertEquals(6, monthly.size(), "窗口内每个月都要有一行");
    assertEquals(now.minusMonths(5).toString(), monthly.getFirst().month());
    assertEquals(0, monthly.getFirst().orders(), "五个月前没有订单，应补 0");
    assertAmount("0", monthly.getFirst().revenue());

    var thisMonth = monthly.getLast();
    assertEquals(now.toString(), thisMonth.month());
    assertEquals(1, thisMonth.orders());
    assertAmount("2400.00", thisMonth.revenue());

    var threeMonthsAgo = monthly.get(2);
    assertEquals(now.minusMonths(3).toString(), threeMonthsAgo.month());
    assertEquals(2, threeMonthsAgo.orders(), "取消订单仍要计数");
    assertAmount("1200.00", threeMonthsAgo.revenue(), "但营收只算未取消的");
  }

  /** 热门线路：按未取消订单量倒序，同量按线路 id 升序，超出条数截断。 */
  @Test
  void popularRoutesRankByNonCancelledOrders() {
    clearOrders();
    long customer = accountId("customer");
    long first = route(100);
    long second = route(100);
    long third = route(100);

    for (int i = 0; i < 3; i++) order(customer, null, first, 1, "PENDING", YearMonth.now());
    order(customer, null, first, 2, "CANCELLED", YearMonth.now());
    for (int i = 0; i < 3; i++) order(customer, null, second, 2, "COMPLETED", YearMonth.now());
    order(customer, null, third, 1, "PENDING", YearMonth.now());

    var popular = reports.popular(Scope.ALL, 5);
    assertEquals(3, popular.size());
    assertEquals(first, popular.get(0).routeId(), "同为 3 单时线路 id 小的在前");
    assertEquals(3, popular.get(0).orderCount(), "已取消订单不计入");
    assertEquals(3, popular.get(0).travelerCount());
    assertEquals(second, popular.get(1).routeId());
    assertEquals(6, popular.get(1).travelerCount(), "出行人数按订单累加");
    assertEquals(third, popular.get(2).routeId());
    assertEquals(1, reports.popular(Scope.ALL, 1).size(), "limit 应截断结果");
  }

  /** 员工排行：按未取消订单量排名，取消订单既不计数也不计金额。 */
  @Test
  void employeeRankCountsNonCancelledOrders() {
    clearOrders();
    long customer = accountId("customer");
    long busy = accountId("employee");
    long quiet = users.create("report_emp2", "安静顾问", "x", "13800000008", Role.EMPLOYEE);
    long route = route(100);

    order(customer, busy, route, 1, "COMPLETED", YearMonth.now());
    order(customer, busy, route, 1, "ASSIGNED", YearMonth.now());
    order(customer, busy, route, 1, "CANCELLED", YearMonth.now());
    order(customer, quiet, route, 1, "COMPLETED", YearMonth.now());

    var rank = reports.summary(accountOf("admin"), 6).employeeRank();
    assertEquals(2, rank.size(), "只列出有经手订单的员工");
    assertEquals(busy, rank.getFirst().employeeId());
    assertEquals(2, rank.getFirst().orderCount(), "取消的那单不计入接单量");
    assertAmount("2400.00", rank.getFirst().amount());
    assertEquals(quiet, rank.get(1).employeeId());
    assertEquals(1, rank.get(1).orderCount());
  }

  /** 汇总的形状与窗口：scope 说明口径，员工排行只给管理员，月份数按 1–24 归一。 */
  @Test
  void summaryCarriesScopeAndClampedMonthWindow() {
    var admin = reports.summary(accountOf("admin"), 6);
    assertEquals("ADMIN", admin.scope());
    assertEquals(6, admin.monthly().size());
    assertFalse(admin.employeeRank().isEmpty(), "管理员应看到团队排行");
    assertEquals(
        jdbc.queryForObject("SELECT COUNT(*) FROM bookings", Long.class), admin.totalOrders());

    var customer = reports.summary(accountOf("customer"), 6);
    assertEquals("CUSTOMER", customer.scope());
    assertTrue(customer.employeeRank().isEmpty(), "客户不需要团队排行");
    assertEquals(
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE customer_id=?", Long.class, accountId("customer")),
        customer.totalOrders());

    // 员工报表只算自己名下的，工作台的可见范围（含待接池）不算在内。
    var employee = reports.summary(accountOf("employee"), 6);
    assertEquals("EMPLOYEE", employee.scope());
    assertEquals(
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE employee_id=?",
            Long.class,
            accountId("employee")),
        employee.totalOrders());

    assertEquals(1, reports.summary(accountOf("admin"), 0).monthly().size());
    assertEquals(24, reports.summary(accountOf("admin"), 999).monthly().size());
    assertEquals(6, reports.summary(accountOf("admin"), null).monthly().size());
    assertEquals(24, reports.summary(accountOf("admin"), 25).monthly().size(), "上限压到 24 个月");
  }

  /**
   * 空库不该报错：计数与金额都是 0，月度仍是完整窗口（全 0），排行与热门都空——前端据此显示空态。
   *
   * <p>空表上 {@code SUM} 返回 NULL，条件聚合里的 {@code COALESCE} 一旦漏写，这里会直接抛空指针；所以这条用例盯的
   * 不是"有个 0"，而是聚合在没有任何行时仍然成形。
   */
  @Test
  void emptyDatabaseReportsZerosInsteadOfFailing() throws Exception {
    clearOrders();

    var totals = reports.totals(Scope.ALL);
    assertEquals(0, totals.total());
    assertEquals(0, totals.pending() + totals.assigned() + totals.completed() + totals.cancelled());
    assertAmount("0", totals.amount());
    assertAmount("0", totals.completedAmount());
    assertEquals(
        0,
        jdbc.queryForObject("SELECT COALESCE(SUM(total_amount),0) FROM bookings", BigDecimal.class)
            .signum(),
        "原生 SQL 在空表上也是 0，两侧口径一致");

    var monthly = reports.monthly(Scope.ALL, 6);
    assertEquals(6, monthly.size(), "没有订单也要补齐整个窗口，不能返回空列表");
    assertTrue(
        monthly.stream().allMatch(m -> m.orders() == 0 && m.revenue().signum() == 0),
        "窗口内每一行都应是 0 单 0 元");
    assertTrue(reports.popular(Scope.ALL, 5).isEmpty(), "没有订单就没有热门线路");
    assertTrue(reports.summary(accountOf("admin"), 6).employeeRank().isEmpty(), "也没有排行");

    // 接口层同样不能 500：前端拿到的是一份"全 0 + 空列表"，画图走空态分支。
    var body = summary(login("admin"), "");
    assertEquals(0L, body.get("totalOrders").asLong());
    assertEquals(0, new BigDecimal(body.get("totalAmount").asText()).signum(), "空库营收应为 0");
    assertEquals(6, body.get("monthly").size());
    assertTrue(body.get("employeeRank").isEmpty());
  }

  /**
   * 跨年窗口：去年 12 月必须排在次年 1 月之前，年份标签不能丢，营收各归各月。
   *
   * <p>窗口长度由"去年 12 月到本月有几个月"算出，所以无论今天是几号，这段窗口都恰好跨过一次年；
   * 若 SQL 只按 {@code MONTH()} 排序，{@code 01} 会跑到 {@code 12} 前面，这条用例立刻红。
   */
  @Test
  void monthlyWindowKeepsDecemberBeforeJanuaryAcrossYears() {
    clearOrders();
    long customer = accountId("customer");
    long route = route(100);
    var now = YearMonth.now();
    var december = YearMonth.of(now.getYear() - 1, 12);
    var january = YearMonth.of(now.getYear(), 1);
    int window = (int) ChronoUnit.MONTHS.between(december, now) + 1;

    order(customer, null, route, 1, "COMPLETED", december);
    order(customer, null, route, 1, "CANCELLED", december);
    order(customer, null, route, 2, "PENDING", january);

    var monthly = reports.monthly(Scope.ALL, window);
    assertEquals(window, monthly.size());
    assertEquals(december.toString(), monthly.getFirst().month(), "窗口第一行应是去年 12 月");
    assertEquals(january.toString(), monthly.get(1).month(), "紧接着就是次年 1 月");

    var dec = monthly.get(0);
    assertEquals(2, dec.orders());
    assertAmount("1200.00", dec.revenue(), "12 月的取消订单只计数不计钱");
    var jan = monthly.get(1);
    assertEquals(1, jan.orders());
    assertAmount("2400.00", jan.revenue());

    int decYear = Integer.parseInt(dec.month().substring(0, 4));
    int janYear = Integer.parseInt(jan.month().substring(0, 4));
    assertEquals(decYear + 1, janYear, "两行确实分属相邻两年，而不是同一年内的 12 月与 1 月");
  }

  /**
   * 两个范围的差别不止行数：待接池的金额出现在工作台，不出现在个人业绩里。
   *
   * <p>只断言"行数差 1"的话，把金额也一起算错仍然通得过；这里直接断两个金额及它们的差。
   */
  @Test
  void dashboardScopeCountsPendingPoolAmountButReportScopeDoesNot() {
    clearOrders();
    long customer = accountId("customer");
    long employee = accountId("employee");
    long route = route(100);

    order(customer, employee, route, 2, "COMPLETED", YearMonth.now());
    order(customer, employee, route, 1, "ASSIGNED", YearMonth.now());
    order(customer, null, route, 2, "PENDING", YearMonth.now());

    var owned = reports.totals(reports.ownedScope(accountOf("employee")));
    var visible = reports.totals(reports.visibleScope(accountOf("employee")));

    assertEquals(2, owned.total(), "报表只算自己名下");
    assertAmount("3600.00", owned.amount());
    assertEquals(3, visible.total(), "工作台还要算上待接池");
    assertAmount("6000.00", visible.amount());
    assertAmount(
        "2400.00", visible.amount().subtract(owned.amount()), "差额恰是待接池那一笔订单的金额");
  }

  private JsonNode summary(Client c, String query) throws Exception {
    return data(
        mvc.perform(get("/api/reports/summary" + query).session(c.session()))
            .andExpect(status().isOk())
            .andReturn());
  }

  /** 报表不向匿名访问开口子：没有会话连字段都拿不到，不能只断"状态码不是 200"。 */
  @Test
  void summaryEndpointRequiresLogin() throws Exception {
    var anon = anonymous();
    var r = mvc.perform(get("/api/reports/summary").session(anon.session())).andReturn();
    assertEquals(401, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertFalse(
        r.getResponse().getContentAsString().contains("totalOrders"),
        "未登录的响应不应包含任何报表字段");
  }

  /** 默认窗口 6 个月，最后一行是本月的：窗口从"今天"往回推，而不是从最早订单往前推。 */
  @Test
  void summaryEndpointReturnsSixMonthWindowByDefault() throws Exception {
    var admin = login("admin");
    var body = summary(admin, "");
    assertEquals("ADMIN", body.get("scope").asText());
    assertEquals(6, body.get("monthly").size());
    assertEquals(
        jdbc.queryForObject("SELECT COUNT(*) FROM bookings", Long.class),
        body.get("totalOrders").asLong());
    assertFalse(body.get("employeeRank").isEmpty(), "管理员应看到团队排行");
    assertEquals(YearMonth.now().toString(), body.get("monthly").get(5).get("month").asText());
  }

  /** `months` 由服务端归一：0 抬到 1、99 压到 24，响应回显真正生效的窗口而不是请求值。 */
  @Test
  void summaryEndpointClampsMonthWindow() throws Exception {
    var admin = login("admin");
    assertEquals(12, summary(admin, "?months=12").get("monthly").size());
    assertEquals(1, summary(admin, "?months=0").get("monthly").size());
    assertEquals(24, summary(admin, "?months=99").get("monthly").size());
  }

  /** 口径只看会话角色，不能由请求参数改写：客户拿不到别人的数字，也看不到团队排行。 */
  @Test
  void summaryEndpointScopesBySessionRole() throws Exception {
    var customer = summary(login("customer"), "");
    assertEquals("CUSTOMER", customer.get("scope").asText());
    assertEquals(
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE customer_id=?", Long.class, accountId("customer")),
        customer.get("totalOrders").asLong());
    assertTrue(customer.get("employeeRank").isEmpty(), "客户不需要团队排行");

    var employee = summary(login("employee"), "");
    assertEquals("EMPLOYEE", employee.get("scope").asText());
    assertEquals(
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE employee_id=?",
            Long.class,
            accountId("employee")),
        employee.get("totalOrders").asLong());
    assertTrue(employee.get("employeeRank").isEmpty(), "员工不参与团队排行");
  }
}
