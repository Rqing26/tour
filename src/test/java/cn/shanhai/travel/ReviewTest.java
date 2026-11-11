package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import cn.shanhai.travel.repository.ReviewRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * T7 评价：表结构（T7.1）与提交规则（T7.2）的基线。
 *
 * <p>一条评价挂在"一笔已完成订单"上，所以 {@code booking_id} 既是外键也是唯一键——重复提交在库里就写不进去，
 * 服务层再给一次可读的 409。{@code route_id} / {@code customer_id} 是下单时的事实，写入时从订单取值，
 * 既不接受外部传入（C12），也让均分聚合不必每次回连 {@code bookings}。
 */
class ReviewTest extends AbstractApiTest {
  /** 用来数聚合查询被调了几次：均分必须是"一次批量取回"，不能按线路逐条查（N+1）。 */
  @MockitoSpyBean private ReviewRepository spyReviews;

  private Map<String, Object> column(String name) {
    var rows =
        jdbc.queryForList(
            "SELECT column_name, is_nullable, column_default FROM information_schema.columns"
                + " WHERE LOWER(table_name)='reviews' AND LOWER(column_name)=?",
            name);
    assertEquals(1, rows.size(), "reviews 表缺少列 " + name);
    return rows.getFirst();
  }

  /** 演示数据里客户与访客各有已完成订单，取最新的一笔作为评价对象。 */
  private long completedBooking() {
    return jdbc.queryForList(
            "SELECT id FROM bookings WHERE status='COMPLETED' ORDER BY id DESC", Long.class)
        .getFirst();
  }

  /** 只写评价表，不经过接口：T7.1 要单独验证约束，此时仓储与接口都还不存在。 */
  private void insert(long bookingId, int rating, String comment) {
    jdbc.update(
        "INSERT INTO reviews(booking_id,route_id,customer_id,rating,comment)"
            + " SELECT b.id,b.route_id,b.customer_id,?,? FROM bookings b WHERE b.id=?",
        rating,
        comment,
        bookingId);
  }

  @Test
  void reviewTableKeepsBookingRatingAndCommentColumns() {
    for (String required : new String[] {"booking_id", "route_id", "customer_id", "rating"}) {
      assertEquals("NO", column(required).get("is_nullable"), required + " 必填，缺失就无法定位评价对象");
    }
    // 文字评价允许不填，但落库统一空串而不是 NULL，与 bookings.remark、coupons.description 同一约定。
    var comment = column("comment");
    assertEquals("NO", comment.get("is_nullable"));
    assertNotNull(comment.get("column_default"), "文字评价应有默认值，不填时读作空串");
    assertEquals("NO", column("created_at").get("is_nullable"), "评价时间用于排序，不能为空");

    assertEquals(
        0,
        jdbc.queryForObject("SELECT COUNT(*) FROM reviews", Integer.class),
        "演示数据不生成评价，均分只能来自真实业务操作");
  }

  @Test
  void oneReviewPerBooking() {
    long bookingId = completedBooking();
    insert(bookingId, 5, "行程安排很顺");
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reviews", Integer.class));

    assertThrows(
        DataIntegrityViolationException.class,
        () -> insert(bookingId, 1, "再来一条"),
        "同一笔订单只能评价一次，唯一键必须在库层面兜住");
    assertEquals(
        1,
        jdbc.queryForObject("SELECT COUNT(*) FROM reviews", Integer.class),
        "被拒的重复评价不能留下痕迹");
  }

  @Test
  void ratingOutsideOneToFiveIsRejected() {
    long bookingId = completedBooking();
    assertThrows(
        DataIntegrityViolationException.class,
        () -> insert(bookingId, 0, "零分"),
        "评分从 1 起，0 星没有意义");
    assertThrows(
        DataIntegrityViolationException.class,
        () -> insert(bookingId, 6, "六星好评"),
        "评分上限 5，超出范围的分数会让均分失真");
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM reviews", Integer.class));
  }

  // ---- T7.2 提交评价 ----

  private long bookingOf(String username, String status) {
    return jdbc.queryForList(
            "SELECT b.id FROM bookings b JOIN app_users u ON u.id=b.customer_id"
                + " WHERE u.username=? AND b.status=? ORDER BY b.id DESC",
            Long.class,
            username,
            status)
        .getFirst();
  }

  private Map<String, Object> review(int rating, String comment) {
    if (comment == null) return Map.of("rating", rating);
    return Map.of("rating", rating, "comment", comment);
  }

  private int reviewsOf(long bookingId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM reviews WHERE booking_id=?", Integer.class, bookingId);
  }

  private MvcResult submit(Client c, long bookingId, int rating, String comment) throws Exception {
    return request(c, "POST", "/api/orders/" + bookingId + "/review", review(rating, comment));
  }

  /** 成功提交并返回响应体：路径拼装只写一次，用例里只剩"谁、对哪笔、给几分"。 */
  private JsonNode reviewed(Client c, long bookingId, int rating, String comment) throws Exception {
    var r = submit(c, bookingId, rating, comment);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return data(r);
  }

  @Test
  void customerReviewsOwnCompletedOrder() throws Exception {
    var customer = login("customer");
    long bookingId = bookingOf("customer", "COMPLETED");
    long routeId =
        jdbc.queryForObject("SELECT route_id FROM bookings WHERE id=?", Long.class, bookingId);
    long customerId =
        jdbc.queryForObject("SELECT customer_id FROM bookings WHERE id=?", Long.class, bookingId);

    var body = reviewed(customer, bookingId, 5, "行程安排很顺");

    assertEquals(bookingId, body.get("bookingId").asLong());
    assertEquals(routeId, body.get("routeId").asLong(), "线路取自订单，不接受外部传入");
    assertEquals(customerId, body.get("customerId").asLong());
    assertEquals(5, body.get("rating").asInt());
    assertEquals("行程安排很顺", body.get("comment").asText());
    assertFalse(body.get("createdAt").asText().isBlank(), "评价时间由服务端记录");
    assertEquals(1, reviewsOf(bookingId));
  }

  @Test
  void secondReviewOnSameOrderIsRejected() throws Exception {
    var customer = login("customer");
    long bookingId = bookingOf("customer", "COMPLETED");
    reviewed(customer, bookingId, 4, "还不错");

    var again = submit(customer, bookingId, 1, "改个一星");
    assertEquals(409, again.getResponse().getStatus(), again.getResponse().getContentAsString());
    assertEquals(1, reviewsOf(bookingId), "被拒的重复评价不能覆盖已有的那条");
    assertEquals(
        4,
        jdbc.queryForObject(
            "SELECT rating FROM reviews WHERE booking_id=?", Integer.class, bookingId),
        "先提交的那条保持不变");
  }

  @Test
  void unfinishedOrCancelledOrderCannotBeReviewed() throws Exception {
    var customer = login("customer");
    for (String status : new String[] {"PENDING", "ASSIGNED", "CANCELLED"}) {
      long bookingId = bookingOf("customer", status);
      var r = submit(customer, bookingId, 5, "先给好评");
      assertEquals(
          403, r.getResponse().getStatus(), status + " → " + r.getResponse().getContentAsString());
      assertEquals(0, reviewsOf(bookingId), status + " 订单不得留下评价");
    }
  }

  @Test
  void otherCustomersOrderCannotBeReviewed() throws Exception {
    var customer = login("customer");
    long othersBooking = bookingOf("demo_guest", "COMPLETED");

    var r = submit(customer, othersBooking, 5, "路过");
    assertEquals(403, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertEquals(0, reviewsOf(othersBooking), "评价权只属于下单客户本人");
  }

  @Test
  void staffCannotReview() throws Exception {
    long bookingId = bookingOf("customer", "COMPLETED");
    for (String username : new String[] {"employee", "admin"}) {
      var r = submit(login(username), bookingId, 5, "代评");
      assertEquals(
          403, r.getResponse().getStatus(), username + " → " + r.getResponse().getContentAsString());
    }
    assertEquals(0, reviewsOf(bookingId));
  }

  @Test
  void anonymousCannotReview() throws Exception {
    var guest = anonymous();
    long bookingId = bookingOf("customer", "COMPLETED");
    var r = submit(guest, bookingId, 5, "游客");
    assertEquals(401, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertEquals(0, reviewsOf(bookingId));
  }

  @Test
  void ratingOutsideRangeIsRejectedByValidation() throws Exception {
    var customer = login("customer");
    long bookingId = bookingOf("customer", "COMPLETED");
    for (int rating : new int[] {0, 6}) {
      var r = submit(customer, bookingId, rating, "越界");
      assertEquals(
          400, r.getResponse().getStatus(), rating + " → " + r.getResponse().getContentAsString());
    }
    assertEquals(0, reviewsOf(bookingId), "校验失败的请求不得落库");
  }

  @Test
  void unknownOrderIsReportedAsMissing() throws Exception {
    var r = request(login("customer"), "POST", "/api/orders/999999/review", review(5, "幽灵订单"));
    assertEquals(404, r.getResponse().getStatus(), r.getResponse().getContentAsString());
  }

  @Test
  void commentIsOptionalAndStoredAsEmpty() throws Exception {
    var customer = login("customer");
    long bookingId = bookingOf("customer", "COMPLETED");

    var body = reviewed(customer, bookingId, 3, null);

    assertEquals(3, body.get("rating").asInt());
    assertEquals("", body.get("comment").asText(), "不填文字评价时读作空串，前端不必区分 NULL");
  }

  // ---- T7.3 均分聚合 ----

  /** 造一条带 n 条评价的线路：一笔订单只能评一次，所以分数与订单一一对应。 */
  private long ratedRoute(Client customer, int... ratings) throws Exception {
    long routeId = route(10);
    for (int rating : ratings) {
      insert(createOrder(customer, routeId, 1), rating, "");
    }
    return routeId;
  }

  private JsonNode routeIn(JsonNode list, long id) {
    for (JsonNode node : list) {
      if (node.get("id").asLong() == id) return node;
    }
    fail("线路列表里没有 id=" + id + " 的那条");
    return null;
  }

  private JsonNode routeDetail(Client c, long id) throws Exception {
    var r = mvc.perform(get("/api/routes/" + id).session(c.session())).andReturn();
    assertEquals(200, r.getResponse().getStatus());
    return data(r);
  }

  @Test
  void routeListCarriesAverageAndCount() throws Exception {
    var customer = login("customer");
    long rated = ratedRoute(customer, 5, 4, 4);
    long untouched = route(10);

    var list = data(mvc.perform(get("/api/routes").session(customer.session())).andReturn());

    var one = routeIn(list, rated);
    assertEquals(4.3, one.get("avgRating").asDouble(), 1e-9, "5 星、4 星、4 星均分 4.33，保留一位");
    assertEquals(3, one.get("reviewCount").asInt());

    var none = routeIn(list, untouched);
    assertTrue(
        none.get("avgRating").isNull(), "没有评价时给空值而不是 0——0 星和“还没人评”是两件事");
    assertEquals(0, none.get("reviewCount").asInt());
  }

  @Test
  void averageRoundsHalfUpToOneDecimal() throws Exception {
    var customer = login("customer");
    // 4+4+4+5 = 17 / 4 = 4.25：四舍五入到一位是 4.3，截断会得 4.2，两者必须能区分。
    long routeId = ratedRoute(customer, 4, 4, 4, 5);

    var detail = routeDetail(customer, routeId);

    assertEquals(4.3, detail.get("avgRating").asDouble(), 1e-9, "4.25 应进位到 4.3");
    assertEquals(4, detail.get("reviewCount").asInt());
    assertEquals(
        detail.get("avgRating").asDouble(),
        routeIn(data(mvc.perform(get("/api/routes").session(customer.session())).andReturn()), routeId)
            .get("avgRating")
            .asDouble(),
        1e-9,
        "列表与详情必须同口径，否则同一个页面上会出现两个均分");
  }

  @Test
  void averageOnlyCoversReviewsOfThatRoute() throws Exception {
    var customer = login("customer");
    long high = ratedRoute(customer, 5, 5);
    long low = ratedRoute(customer, 1);

    var list = data(mvc.perform(get("/api/routes").session(customer.session())).andReturn());

    assertEquals(5.0, routeIn(list, high).get("avgRating").asDouble(), 1e-9);
    assertEquals(1.0, routeIn(list, low).get("avgRating").asDouble(), 1e-9);
    assertEquals(2, routeIn(list, high).get("reviewCount").asInt());
  }

  @Test
  void routeListReadsRatingsInOneQuery() throws Exception {
    var customer = login("customer");
    ratedRoute(customer, 5);
    for (int i = 0; i < 5; i++) route(10); // 线路从 1 条涨到 6 条，查询次数不能跟着涨

    clearInvocations(spyReviews);
    assertEquals(200, mvc.perform(get("/api/routes").session(customer.session())).andReturn()
        .getResponse()
        .getStatus());

    verify(spyReviews, times(1)).averages();
    verify(spyReviews, never()).average(anyLong());
  }

  // ---- T7.5 评价回归：均分即时性、与订单互不干扰、并发重复提交 ----

  /** 走真实链路造一笔已完成订单：下单 → 员工接单 → 员工完成，同时在审计表留下 3 条事件。 */
  private long completedOrder(Client customer, Client employee, long routeId) throws Exception {
    long id = createOrder(customer, routeId, 1);
    ok(employee, "POST", "/api/orders/" + id + "/claim", Map.of());
    ok(employee, "POST", "/api/orders/" + id + "/complete", Map.of());
    return id;
  }

  private int eventsOf(long bookingId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM booking_events WHERE booking_id=?", Integer.class, bookingId);
  }

  /**
   * 均分随每一条新评价立刻变化，不是"只在启动时算一次"。
   *
   * <p>三次读取分别落在 0 条、1 条、2 条评价上：没有评价时给空值而不是 0，4 星单人时 4.0，再加一条 5 星变
   * 4.5。中间没有任何可失效的缓存，页面才不会出现"评了但分数不动"。
   */
  @Test
  void averageFollowsEachNewReview() throws Exception {
    var customer = login("customer");
    var employee = login("employee");
    long routeId = route(10);
    long first = completedOrder(customer, employee, routeId);

    var empty = routeDetail(customer, routeId);
    assertTrue(empty.get("avgRating").isNull(), "还没有人评时详情也给空值，而不是 0 星");
    assertEquals(0, empty.get("reviewCount").asInt());

    reviewed(customer, first, 4, "第一次");
    var one = routeDetail(customer, routeId);
    assertEquals(4.0, one.get("avgRating").asDouble(), 1e-9);
    assertEquals(1, one.get("reviewCount").asInt());

    long second = completedOrder(customer, employee, routeId);
    reviewed(customer, second, 5, "第二次");
    var two = routeDetail(customer, routeId);
    assertEquals(4.5, two.get("avgRating").asDouble(), 1e-9, "两笔订单各评一次，均分应为 4.5");
    assertEquals(2, two.get("reviewCount").asInt(), "唯一键钉的是订单，同一线路可以有多条评价");
    assertEquals(
        4.5,
        routeIn(data(mvc.perform(get("/api/routes").session(customer.session())).andReturn()), routeId)
            .get("avgRating")
            .asDouble(),
        1e-9,
        "列表要跟着变，否则同一页面上详情与列表会给出两个分数");
  }

  /** 评价是订单的附属动作：写完评价，订单状态与审计时间线都不该有任何变化。 */
  @Test
  void reviewLeavesOrderStateAndTimelineAlone() throws Exception {
    var customer = login("customer");
    var employee = login("employee");
    long bookingId = completedOrder(customer, employee, route(10));
    assertEquals(3, eventsOf(bookingId), "下单、接单、完成各留一条事件");

    reviewed(customer, bookingId, 4, "已完成行程");

    var order = orders.find(bookingId).orElseThrow();
    assertEquals("COMPLETED", order.status(), "评价不改订单状态");
    assertEquals(3, eventsOf(bookingId), "评价不是状态迁移，不得往时间线里塞事件");
    assertEquals(users.byUsername("employee").orElseThrow().id(), order.employeeId());
  }

  /**
   * 编辑线路（改价格与容量）之后均分仍在。
   *
   * <p>均分是在仓储的 {@code find} 里装配的，而 {@code create} / {@code update} / {@code uploadImage}
   * 都走同一个 {@code find}；只在某个 service 入口补一次的实现，编辑完线路就会突然显示"暂无评分"。
   */
  @Test
  void editingRouteKeepsAverage() throws Exception {
    var customer = login("customer");
    var admin = login("admin");
    long routeId = ratedRoute(customer, 5, 4, 4);
    var start = routes.find(routeId).orElseThrow().startDate();

    ok(admin, "PUT", "/api/routes/" + routeId, edit(routeId, "1300.00", 12, start));

    var detail = routeDetail(customer, routeId);
    assertEquals(4.3, detail.get("avgRating").asDouble(), 1e-9, "编辑线路不该把评分清成“暂无评分”");
    assertEquals(3, detail.get("reviewCount").asInt());
    assertEquals(
        0, new BigDecimal("1300.00").compareTo(detail.get("price").decimalValue()), "价格按编辑生效");
  }

  /**
   * 同一笔订单并发提交两次：恰好一条写入，另一个拿 409。
   *
   * <p>闸门是唯一键而不是"先查再插"——后者两次读之间正是两个请求都认为"还没评过"的窗口。
   */
  @Test
  void concurrentDuplicateReviewsKeepOneRow() throws Exception {
    var customer = login("customer");
    long bookingId = bookingOf("customer", "COMPLETED");

    var statuses =
        concurrent(
            () -> submit(customer, bookingId, 5, "并发一").getResponse().getStatus(),
            () -> submit(customer, bookingId, 3, "并发二").getResponse().getStatus());

    assertEquals(List.of(200, 409), statuses, "两个请求不能都成功，也不该都失败");
    assertEquals(1, reviewsOf(bookingId), "并发重复提交在库里只留一条");
  }
}
