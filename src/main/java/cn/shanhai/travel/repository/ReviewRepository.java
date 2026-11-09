package cn.shanhai.travel.repository;

import cn.shanhai.travel.dto.Api;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 评价读写。
 *
 * <p>写入只用一条 INSERT：{@code route_id} / {@code customer_id} 从订单里直接取，
 * 客户端提交不了这两个值；重复评价由 {@code uk_review_booking} 拒绝，仓储不做"先查再插"。
 */
@Repository
public class ReviewRepository {
  private final JdbcTemplate jdbc;

  public ReviewRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final RowMapper<Api.Review> MAPPER =
      (r, n) ->
          new Api.Review(
              r.getLong("id"),
              r.getLong("booking_id"),
              r.getLong("route_id"),
              r.getLong("customer_id"),
              r.getInt("rating"),
              r.getString("comment"),
              r.getTimestamp("created_at").toLocalDateTime());

  public Optional<Api.Review> find(long id) {
    return jdbc.query("SELECT * FROM reviews WHERE id=?", MAPPER, id).stream().findFirst();
  }

  public Optional<Api.Review> findByBooking(long bookingId) {
    return jdbc.query("SELECT * FROM reviews WHERE booking_id=?", MAPPER, bookingId).stream()
        .findFirst();
  }

  /**
   * 落一条评价：线路与客户取自订单本身，不来自请求体。
   *
   * <p>重复提交在这里抛唯一键冲突，由服务层翻成 409——判断交给数据库，就没有"先查再插"的窗口。
   */
  public long create(long bookingId, long routeId, long customerId, Api.ReviewInput d) {
    var key = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var p =
              c.prepareStatement(
                  "INSERT INTO reviews(booking_id,route_id,customer_id,rating,comment)"
                      + " VALUES(?,?,?,?,?)",
                  new String[] {"id"});
          p.setLong(1, bookingId);
          p.setLong(2, routeId);
          p.setLong(3, customerId);
          p.setInt(4, d.rating());
          p.setString(5, d.comment() == null ? "" : d.comment().trim());
          return p;
        },
        key);
    return Objects.requireNonNull(key.getKey()).longValue();
  }
}
