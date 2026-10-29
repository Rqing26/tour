package cn.shanhai.travel.repository;

import cn.shanhai.travel.dto.Api;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {
  private final JdbcTemplate jdbc;

  public OrderRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * 列表查询的公共 FROM：关键字要同时匹配置线与客户名，所以三个关联表必须一直在。
   *
   * <p>COUNT 与分页共用这一段，保证"总数"与"当前页"出自同一套过滤条件。
   */
  private static final String FROM =
      " FROM bookings b JOIN app_users c ON c.id=b.customer_id LEFT JOIN app_users e ON"
          + " e.id=b.employee_id JOIN travel_routes r ON r.id=b.route_id";

  private static final String SELECT =
      "SELECT b.*,c.display_name customer_name,c.phone customer_phone,e.display_name"
          + " employee_name,r.name route_name,r.destination,r.start_date,r.duration_days"
          + FROM;
  private static final RowMapper<Api.Order> MAPPER =
      (r, n) ->
          new Api.Order(
              r.getLong("id"),
              "SH" + String.format("%06d", r.getLong("id")),
              r.getLong("customer_id"),
              r.getString("customer_name"),
              r.getString("customer_phone"),
              r.getObject("employee_id", Long.class),
              r.getString("employee_name"),
              r.getLong("route_id"),
              r.getString("route_name"),
              r.getString("destination"),
              r.getDate("start_date").toLocalDate(),
              r.getInt("duration_days"),
              r.getInt("traveler_count"),
              r.getBigDecimal("unit_price"),
              r.getBigDecimal("total_amount"),
              r.getString("status"),
              r.getTimestamp("created_at").toLocalDateTime(),
              r.getString("contact_name"),
              r.getString("contact_phone"),
              r.getString("remark"));

  /** 匹配范围与列表原有的内存过滤一致：订单号、线路名、客户名、联系人和负责人姓名。 */
  private static final String KEYWORD =
      "(LOWER(CONCAT('SH',LPAD(b.id,6,'0'))) LIKE ? OR LOWER(r.name) LIKE ?"
          + " OR LOWER(c.display_name) LIKE ? OR LOWER(b.contact_name) LIKE ?"
          + " OR LOWER(COALESCE(e.display_name,'')) LIKE ?)";

  /**
   * 订单列表的查询条件：可见性、状态与关键词都在 SQL 里收窄，Service 不再把全表捞回内存过滤。
   *
   * <p>两个归属字段互斥：填 {@code customerId} 只看该客户的订单；填 {@code employeeId} 看"待接池 + 自己接的"；
   * 都为空表示不限制范围（管理员）。
   */
  public record Filter(Long customerId, Long employeeId, String status, String keyword) {
    public static final Filter ALL = new Filter(null, null, null, null);
  }

  /** 排序白名单：外部只能选键，列名由这里映射，任何用户输入都不会被拼进 SQL。 */
  public record Sort(String key, boolean descending) {
    public static final Sort DEFAULT = new Sort("createdAt", true);
    private static final Map<String, String> COLUMNS =
        Map.of(
            "createdAt", "b.created_at",
            "totalAmount", "b.total_amount",
            "startDate", "r.start_date");

    public static boolean supports(String key) {
      return COLUMNS.containsKey(key);
    }

    String column() {
      return COLUMNS.getOrDefault(key, COLUMNS.get("createdAt"));
    }
  }

  /** 由白名单拼出的 WHERE 片段及其参数，值一律走占位符绑定。 */
  private record Where(String sql, List<Object> args) {}

  private static Where where(Filter f) {
    var parts = new ArrayList<String>();
    var args = new ArrayList<Object>();
    if (f.customerId() != null) {
      parts.add("b.customer_id=?");
      args.add(f.customerId());
    } else if (f.employeeId() != null) {
      // 待接单是公共池，员工既要看到它也要看到自己名下的订单，两者无法用单列条件表达。
      parts.add("(b.status='PENDING' OR b.employee_id=?)");
      args.add(f.employeeId());
    }
    if (f.status() != null && !f.status().isBlank()) {
      parts.add("b.status=?");
      args.add(f.status());
    }
    if (f.keyword() != null && !f.keyword().isBlank()) {
      parts.add(KEYWORD);
      // 关键词按字面匹配：% 与 _ 是被搜索的普通字符，不当作通配符。
      String pattern = "%" + escapeLike(f.keyword().trim().toLowerCase(Locale.ROOT)) + "%";
      for (int i = 0; i < 5; i++) args.add(pattern);
    }
    return new Where(parts.isEmpty() ? "" : " WHERE " + String.join(" AND ", parts), args);
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  /** 排序永远以 id 兜底：同一秒创建的订单不会因为翻页而重复或漏掉。 */
  private static String orderBy(Sort sort) {
    return " ORDER BY " + sort.column() + (sort.descending() ? " DESC" : " ASC") + ",b.id DESC";
  }

  /** 取一页订单。limit 与 offset 由调用方兜底，仓储只负责把窗口下推给数据库。 */
  public List<Api.Order> page(Filter filter, Sort sort, int offset, int limit) {    var w = where(filter);
    var args = new ArrayList<>(w.args());
    args.add(Math.max(limit, 0));
    args.add(Math.max(offset, 0));
    return jdbc.query(
        SELECT + w.sql() + orderBy(sort) + " LIMIT ? OFFSET ?", MAPPER, args.toArray());
  }

  /** 与 {@link #page} 同一套过滤条件的总数，用于前端算总页数。 */
  public long count(Filter filter) {
    var w = where(filter);
    Long total =
        jdbc.queryForObject("SELECT COUNT(*)" + FROM + w.sql(), Long.class, w.args().toArray());
    return total == null ? 0 : total;
  }

  public Optional<Api.Order> find(long id) {
    return jdbc.query(SELECT + " WHERE b.id=?", MAPPER, id).stream().findFirst();
  }

  public Optional<Api.Order> lock(long id) {
    // Lock the booking row itself; avoid locking every joined route and user.
    var ids = jdbc.queryForList("SELECT id FROM bookings WHERE id=? FOR UPDATE", Long.class, id);
    return ids.isEmpty() ? Optional.empty() : find(id);
  }

  public long create(long customerId, Api.Route route, Api.BookingInput d) {
    var key = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var p =
              c.prepareStatement(
                  "INSERT INTO"
                      + " bookings(customer_id,route_id,traveler_count,unit_price,total_amount,status,contact_name,contact_phone,remark)"
                      + " VALUES(?,?,?,?,?,'PENDING',?,?,?)",
                  new String[] {"id"});
          p.setLong(1, customerId);
          p.setLong(2, route.id());
          p.setInt(3, d.travelerCount());
          p.setBigDecimal(4, route.price());
          p.setBigDecimal(5, route.price().multiply(BigDecimal.valueOf(d.travelerCount())));
          p.setString(6, d.contactName().trim());
          p.setString(7, d.contactPhone().trim());
          p.setString(8, d.remark() == null ? "" : d.remark().trim());
          return p;
        },
        key);
    return Objects.requireNonNull(key.getKey()).longValue();
  }

  public boolean compareAndTransition(Api.Order old, String status, Long employee) {
    String sql = "UPDATE bookings SET status=?,employee_id=? WHERE id=? AND status=? AND ";
    int changed =
        old.employeeId() == null
            ? jdbc.update(sql + "employee_id IS NULL", status, employee, old.id(), old.status())
            : jdbc.update(
                sql + "employee_id=?", status, employee, old.id(), old.status(), old.employeeId());
    return changed == 1;
  }

  public void transition(long id, String status, Long employee) {
    jdbc.update("UPDATE bookings SET status=?,employee_id=? WHERE id=?", status, employee, id);
  }
}
