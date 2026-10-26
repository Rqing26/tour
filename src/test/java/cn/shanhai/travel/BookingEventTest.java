package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 订单事件（T2 订单审计）用例：表结构、仓储读写、状态变更留痕。
 *
 * <p>公共装置见 {@link AbstractApiTest}；每个用例的数据由基类重置，互不依赖执行顺序。
 */
class BookingEventTest extends AbstractApiTest {

  /** 表结构决定"能回溯到什么程度"：首次建档没有来源状态，必须允许为空。 */
  @Test
  void eventTableAcceptsNullSourceStatusAndFillsDefaults() {
    long actor = users.byUsername("employee").orElseThrow().id();
    long booking = jdbc.queryForObject("SELECT MIN(id) FROM bookings", Long.class);
    jdbc.update(
        "INSERT INTO booking_events(booking_id,from_status,to_status,actor_id,actor_role)"
            + " VALUES(?,?,?,?,?)",
        booking,
        null,
        "PENDING",
        actor,
        "EMPLOYEE");

    var row =
        jdbc.queryForObject(
            "SELECT from_status,to_status,note,created_at FROM booking_events WHERE booking_id=?",
            (r, n) ->
                new Object[] {
                  r.getString("from_status"),
                  r.getString("to_status"),
                  r.getString("note"),
                  r.getTimestamp("created_at")
                },
            booking);
    assertNull(row[0]);
    assertEquals("PENDING", row[1]);
    assertEquals("", row[2]);
    assertNotNull(row[3]);
  }

  /** 事件是订单与操作人的从属记录，指向不存在的主记录会让时间线失去可解释性。 */
  @Test
  void eventRowsMustReferenceExistingBookingAndActor() {
    long actor = users.byUsername("employee").orElseThrow().id();
    long booking = jdbc.queryForObject("SELECT MIN(id) FROM bookings", Long.class);
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "INSERT INTO booking_events(booking_id,to_status,actor_id,actor_role)"
                    + " VALUES(?,?,?,?)",
                999999L,
                "PENDING",
                actor,
                "EMPLOYEE"));
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "INSERT INTO booking_events(booking_id,to_status,actor_id,actor_role)"
                    + " VALUES(?,?,?,?)",
                booking,
                "PENDING",
                999999L,
                "EMPLOYEE"));
  }
}
