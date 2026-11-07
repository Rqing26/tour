package cn.shanhai.travel.repository;

import cn.shanhai.travel.dto.Api;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 优惠码配置的读写。
 *
 * <p>核销（{@code used_count} 的条件自增）不在这里：它属于下单流程，只有和订单写入同一个事务才有意义，
 * 见 T6.5。本类只负责管理端的增删改查，以及按码取配置。
 */
@Repository
public class CouponRepository {
  private final JdbcTemplate jdbc;

  public CouponRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final RowMapper<Api.Coupon> MAPPER =
      (r, n) ->
          new Api.Coupon(
              r.getLong("id"),
              r.getString("code"),
              r.getString("description"),
              r.getBigDecimal("discount_rate"),
              r.getBigDecimal("min_amount"),
              r.getInt("max_uses"),
              r.getInt("used_count"),
              r.getDate("starts_on").toLocalDate(),
              r.getDate("ends_on").toLocalDate(),
              r.getBoolean("active"),
              r.getTimestamp("created_at").toLocalDateTime());

  /** 关键词按字面匹配码与说明；`%`、`_` 转义成普通字符，避免搜通配符命中全表。 */
  public List<Api.Coupon> list(String q, Boolean active) {
    var parts = new ArrayList<String>();
    var args = new ArrayList<Object>();
    if (q != null && !q.isBlank()) {
      parts.add("(LOWER(code) LIKE ? OR LOWER(description) LIKE ?)");
      String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
      args.add(pattern);
      args.add(pattern);
    }
    if (active != null) {
      parts.add("active=?");
      args.add(active);
    }
    String sql =
        "SELECT * FROM coupons"
            + (parts.isEmpty() ? "" : " WHERE " + String.join(" AND ", parts))
            // 在用的排前面，同组按到期先后：管理端最关心"哪些还能用、什么时候失效"。
            + " ORDER BY active DESC, ends_on, id DESC";
    return jdbc.query(sql, MAPPER, args.toArray());
  }

  public Optional<Api.Coupon> find(long id) {
    return jdbc.query("SELECT * FROM coupons WHERE id=?", MAPPER, id).stream().findFirst();
  }

  public Optional<Api.Coupon> lock(long id) {
    return jdbc.query("SELECT * FROM coupons WHERE id=? FOR UPDATE", MAPPER, id).stream()
        .findFirst();
  }

  public Optional<Api.Coupon> byCode(String code) {
    return jdbc.query("SELECT * FROM coupons WHERE code=?", MAPPER, code).stream().findFirst();
  }

  public long create(Api.CouponInput d) {
    var key = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var p =
              c.prepareStatement(
                  "INSERT INTO"
                      + " coupons(code,description,discount_rate,min_amount,max_uses,used_count,starts_on,ends_on,active)"
                      + " VALUES(?,?,?,?,?,0,?,?,?)",
                  new String[] {"id"});
          p.setString(1, d.code());
          p.setString(2, d.description());
          p.setBigDecimal(3, d.discountRate());
          p.setBigDecimal(4, d.minAmount());
          p.setInt(5, d.maxUses());
          p.setDate(6, java.sql.Date.valueOf(d.startsOn()));
          p.setDate(7, java.sql.Date.valueOf(d.endsOn()));
          p.setBoolean(8, d.active());
          return p;
        },
        key);
    return Objects.requireNonNull(key.getKey()).longValue();
  }

  /** 只改配置，不碰 {@code used_count}：已核销的次数属于历史，编辑规则不能把它清零。 */
  public void update(long id, Api.CouponInput d) {
    jdbc.update(
        "UPDATE coupons SET"
            + " code=?,description=?,discount_rate=?,min_amount=?,max_uses=?,starts_on=?,ends_on=?,active=?"
            + " WHERE id=?",
        d.code(),
        d.description(),
        d.discountRate(),
        d.minAmount(),
        d.maxUses(),
        d.startsOn(),
        d.endsOn(),
        d.active(),
        id);
  }

  public void delete(long id) {
    jdbc.update("DELETE FROM coupons WHERE id=?", id);
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
