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
}
