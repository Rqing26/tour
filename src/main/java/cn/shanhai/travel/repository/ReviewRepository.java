package cn.shanhai.travel.repository;

import cn.shanhai.travel.dto.Api;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 评价读写。
 *
 * <p>写入只用一条 INSERT：{@code route_id} / {@code customer_id} 从订单里直接取，
 * 客户端提交不了这两个值；重复评价由 {@code uk_review_booking} 拒绝，仓储不做"先查再插"。
 *
 * <p>均分只提供两条读法：{@link #averages()} 一次取回全部线路（列表用），{@link #average(long)}
 * 取单条（详情与加锁路径用）。刻意不提供"按线路循环调用"的入口，否则列表页会退化成 N+1。
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

  /**
   * 全部线路的均分：一条 {@code GROUP BY route_id} 的聚合查询。
   *
   * <p>线路列表用这个方法，一次就把所有均分拿回来——按线路逐条查会让页面查询次数随线路数线性增长。
   */
  public Map<Long, Api.RouteRating> averages() {
    var ratings = new LinkedHashMap<Long, Api.RouteRating>();
    RowCallbackHandler collect =
        r -> {
          long routeId = r.getLong("route_id");
          double average = round(r.getDouble("avg_rating"));
          ratings.put(routeId, new Api.RouteRating(routeId, average, r.getInt("review_count")));
        };
    jdbc.query(
        "SELECT route_id, AVG(rating) AS avg_rating, COUNT(*) AS review_count FROM reviews"
            + " GROUP BY route_id",
        collect);
    return ratings;
  }

  /** 单条线路的均分：详情路径只处理一行，没有评价时返回空——前端据此显示"暂无评分"。 */
  public Optional<Api.RouteRating> average(long routeId) {
    var rows =
        jdbc.queryForList(
            "SELECT AVG(rating) AS avg_rating, COUNT(*) AS review_count FROM reviews"
                + " WHERE route_id=?",
            routeId);
    // 聚合查询恒返回一行；没有评价时 AVG 为 NULL、COUNT 为 0，所以只能靠条数判断"有没有"。
    int count = ((Number) rows.getFirst().get("review_count")).intValue();
    if (count == 0) return Optional.empty();
    double avg = ((Number) rows.getFirst().get("avg_rating")).doubleValue();
    return Optional.of(new Api.RouteRating(routeId, round(avg), count));
  }

  /** 均分保留一位小数，四舍五入：4.25 → 4.3，截断会系统性偏低。 */
  private static double round(double value) {
    return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
  }
}
