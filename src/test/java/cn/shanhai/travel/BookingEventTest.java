package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;

import cn.shanhai.travel.repository.BookingEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 订单事件（T2 订单审计）用例：表结构、仓储读写、状态变更留痕。
 *
 * <p>公共装置见 {@link AbstractApiTest}；每个用例的数据由基类重置，互不依赖执行顺序。
 */
class BookingEventTest extends AbstractApiTest {
  @Autowired BookingEventRepository events;

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

  /** 时间线要显示"谁做的"，所以读事件时一并取出操作人姓名，缺失 note 归一成空串。 */
  @Test
  void appendResolvesActorNameAndDefaultsBlankNote() {
    long actor = users.byUsername("employee").orElseThrow().id();
    String actorName = users.byId(actor).orElseThrow().displayName();
    long booking = jdbc.queryForObject("SELECT MIN(id) FROM bookings", Long.class);

    events.append(booking, null, "PENDING", actor, "EMPLOYEE", null);

    var list = events.byBooking(booking);
    assertEquals(1, list.size());
    var event = list.get(0);
    assertEquals(booking, event.bookingId());
    assertNull(event.fromStatus());
    assertEquals("PENDING", event.toStatus());
    assertEquals(actor, event.actorId());
    assertEquals(actorName, event.actorName());
    assertEquals("EMPLOYEE", event.actorRole());
    assertEquals("", event.note());
    assertNotNull(event.createdAt());
  }

  /** 每次状态变更都追加一行，读取时必须按发生顺序、且只返回本订单的记录。 */
  @Test
  void byBookingReturnsItsOwnEventsInOccurrenceOrder() {
    long actor = users.byUsername("employee").orElseThrow().id();
    var ids = jdbc.queryForList("SELECT id FROM bookings ORDER BY id", Long.class);
    long first = ids.get(0);
    long second = ids.get(1);

    events.append(first, null, "PENDING", actor, "EMPLOYEE", "");
    events.append(second, null, "PENDING", actor, "EMPLOYEE", "");
    events.append(first, "PENDING", "ASSIGNED", actor, "EMPLOYEE", "接单");

    var list = events.byBooking(first);
    assertEquals(2, list.size());
    assertNull(list.get(0).fromStatus());
    assertEquals("PENDING", list.get(0).toStatus());
    assertEquals("PENDING", list.get(1).fromStatus());
    assertEquals("ASSIGNED", list.get(1).toStatus());
    assertEquals("接单", list.get(1).note());
    assertEquals(1, events.byBooking(second).size());
  }
}
