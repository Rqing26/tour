package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * T7 评价：表结构（T7.1）与提交规则（T7.2）的基线。
 *
 * <p>一条评价挂在"一笔已完成订单"上，所以 {@code booking_id} 既是外键也是唯一键——重复提交在库里就写不进去，
 * 服务层再给一次可读的 409。{@code route_id} / {@code customer_id} 是下单时的事实，写入时从订单取值，
 * 既不接受外部传入（C12），也让均分聚合不必每次回连 {@code bookings}。
 */
class ReviewTest extends AbstractApiTest {
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
    return comment == null ? Map.of("rating", rating) : Map.of("rating", rating, "comment", comment);
  }

  private int reviewsOf(long bookingId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM reviews WHERE booking_id=?", Integer.class, bookingId);
  }

  @Test
  void customerReviewsOwnCompletedOrder() throws Exception {
    var customer = login("customer");
    long bookingId = bookingOf("customer", "COMPLETED");
    long routeId =
        jdbc.queryForObject("SELECT route_id FROM bookings WHERE id=?", Long.class, bookingId);
    long customerId =
        jdbc.queryForObject("SELECT customer_id FROM bookings WHERE id=?", Long.class, bookingId);

    var body = ok(customer, "POST", "/api/orders/" + bookingId + "/review", review(5, "行程安排很顺"));

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
    ok(customer, "POST", "/api/orders/" + bookingId + "/review", review(4, "还不错"));

    var again = request(customer, "POST", "/api/orders/" + bookingId + "/review", review(1, "改个一星"));
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
      var r = request(customer, "POST", "/api/orders/" + bookingId + "/review", review(5, "先给好评"));
      assertEquals(403, r.getResponse().getStatus(), status + " → " + r.getResponse().getContentAsString());
      assertEquals(0, reviewsOf(bookingId), status + " 订单不得留下评价");
    }
  }

  @Test
  void otherCustomersOrderCannotBeReviewed() throws Exception {
    var customer = login("customer");
    long othersBooking = bookingOf("demo_guest", "COMPLETED");

    var r = request(customer, "POST", "/api/orders/" + othersBooking + "/review", review(5, "路过"));
    assertEquals(403, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertEquals(0, reviewsOf(othersBooking), "评价权只属于下单客户本人");
  }

  @Test
  void staffCannotReview() throws Exception {
    long bookingId = bookingOf("customer", "COMPLETED");
    for (String username : new String[] {"employee", "admin"}) {
      var r = request(login(username), "POST", "/api/orders/" + bookingId + "/review", review(5, "代评"));
      assertEquals(403, r.getResponse().getStatus(), username + " → " + r.getResponse().getContentAsString());
    }
    assertEquals(0, reviewsOf(bookingId));
  }

  @Test
  void anonymousCannotReview() throws Exception {
    var guest = anonymous();
    long bookingId = bookingOf("customer", "COMPLETED");
    var r = request(guest, "POST", "/api/orders/" + bookingId + "/review", review(5, "游客"));
    assertEquals(401, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertEquals(0, reviewsOf(bookingId));
  }

  @Test
  void ratingOutsideRangeIsRejectedByValidation() throws Exception {
    var customer = login("customer");
    long bookingId = bookingOf("customer", "COMPLETED");
    for (int rating : new int[] {0, 6}) {
      var r = request(customer, "POST", "/api/orders/" + bookingId + "/review", review(rating, "越界"));
      assertEquals(400, r.getResponse().getStatus(), rating + " → " + r.getResponse().getContentAsString());
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

    var body = ok(customer, "POST", "/api/orders/" + bookingId + "/review", review(3, null));

    assertEquals(3, body.get("rating").asInt());
    assertEquals("", body.get("comment").asText(), "不填文字评价时读作空串，前端不必区分 NULL");
  }
}
