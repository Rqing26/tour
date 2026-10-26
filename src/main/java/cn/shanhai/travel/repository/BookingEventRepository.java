package cn.shanhai.travel.repository;

import cn.shanhai.travel.dto.Api;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** 订单事件读写。事件只追加，不更新也不删除，保证审计链不可篡改。 */
@Repository
public class BookingEventRepository {
  private final JdbcTemplate jdbc;

  public BookingEventRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String SELECT =
      "SELECT e.id,e.booking_id,e.from_status,e.to_status,e.actor_id,u.display_name actor_name,"
          + "e.actor_role,e.note,e.created_at FROM booking_events e"
          + " JOIN app_users u ON u.id=e.actor_id";

  private static final RowMapper<Api.OrderEvent> MAPPER =
      (r, n) ->
          new Api.OrderEvent(
              r.getLong("id"),
              r.getLong("booking_id"),
              r.getString("from_status"),
              r.getString("to_status"),
              r.getLong("actor_id"),
              r.getString("actor_name"),
              r.getString("actor_role"),
              r.getString("note"),
              r.getTimestamp("created_at").toLocalDateTime());

  /** 追加一条事件。事务边界由调用方（Service）决定，仓储不自行开启事务。 */
  public void append(
      long bookingId,
      String fromStatus,
      String toStatus,
      long actorId,
      String actorRole,
      String note) {
    jdbc.update(
        "INSERT INTO booking_events(booking_id,from_status,to_status,actor_id,actor_role,note)"
            + " VALUES(?,?,?,?,?,?)",
        bookingId,
        fromStatus,
        toStatus,
        actorId,
        actorRole,
        note == null ? "" : note);
  }

  /** 按发生顺序返回某订单的事件。同一时刻的多条用自增 id 兜底，排序稳定可复现。 */
  public List<Api.OrderEvent> byBooking(long bookingId) {
    return jdbc.query(
        SELECT + " WHERE e.booking_id=? ORDER BY e.created_at,e.id", MAPPER, bookingId);
  }
}
