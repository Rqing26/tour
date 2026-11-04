package cn.shanhai.travel.repository;

import cn.shanhai.travel.dto.Api;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 报表聚合：状态汇总、月度订单量与营收、热门线路、员工接单排行，全部下推给数据库。
 *
 * <p>此前这些数字是把整张 {@code bookings} 捞进内存、再用 stream 数出来的：订单量一涨，全部行都要搬进 JVM，
 * 而真正需要的只是几行折叠后的结果。这里改用 {@code GROUP BY} 与条件聚合（{@code SUM(CASE WHEN …)}），
 * 一条 SQL 得出一个维度，行数不再随订单量增长。
 *
 * <p>条件聚合里的 {@code CASE WHEN} 与 {@code YEAR()} / {@code MONTH()} 都是 H2 {@code MODE=MySQL} 与 MySQL 8
 * 共有的写法，没有用 {@code DATE_FORMAT}（H2 不认）。
 */
@Repository
public class ReportRepository {
  private final JdbcTemplate jdbc;

  public ReportRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * 统计范围：{@code customerId} 与 {@code employeeId} 互斥，都为空表示不限范围（管理员）。
   *
   * <p>{@code pendingPool} 只影响员工：工作台沿用订单列表的可见范围（待接池 + 自己名下），
   * 报表只统计已经归属到本人名下的订单——待接池还没有负责人，不能算作谁的业绩。
   */
  public record Scope(Long customerId, Long employeeId, boolean pendingPool) {
    public static final Scope ALL = new Scope(null, null, false);

    public static Scope customer(long customerId) {
      return new Scope(customerId, null, false);
    }

    /** 经手范围：只算已归属到该员工名下的订单。 */
    public static Scope ownedBy(long employeeId) {
      return new Scope(null, employeeId, false);
    }

    /** 可见范围：与订单列表一致，待接池也算在内。 */
    public static Scope visibleTo(long employeeId) {
      return new Scope(null, employeeId, true);
    }
  }

  /**
   * 状态计数与金额汇总。
   *
   * <p>{@code amount} 是未取消订单的合计（取消即释放名额，不该计入营收），{@code completedAmount} 只算已完成。
   */
  public record Totals(
      long total,
      long pending,
      long assigned,
      long completed,
      long cancelled,
      BigDecimal amount,
      BigDecimal completedAmount) {}

  /** 由范围拼出的 WHERE 片段及其参数：值一律走占位符绑定。 */
  private static String scopeSql(Scope s, List<Object> args) {
    if (s.customerId() != null) {
      args.add(s.customerId());
      return "b.customer_id=?";
    }
    if (s.employeeId() != null) {
      args.add(s.employeeId());
      return s.pendingPool() ? "(b.status='PENDING' OR b.employee_id=?)" : "b.employee_id=?";
    }
    return "1=1";
  }

  /** 一次查询同时得出总数、四个状态计数与两个金额：原来要 6 条 SQL、且其中 5 条是在内存里数的。 */
  public Totals totals(Scope scope) {
    var args = new ArrayList<Object>();
    String sql =
        "SELECT COUNT(*) AS total_count,"
            + " COALESCE(SUM(CASE WHEN b.status='PENDING' THEN 1 ELSE 0 END),0) AS pending_count,"
            + " COALESCE(SUM(CASE WHEN b.status='ASSIGNED' THEN 1 ELSE 0 END),0)"
            + " AS assigned_count,"
            + " COALESCE(SUM(CASE WHEN b.status='COMPLETED' THEN 1 ELSE 0 END),0)"
            + " AS completed_count,"
            + " COALESCE(SUM(CASE WHEN b.status='CANCELLED' THEN 1 ELSE 0 END),0)"
            + " AS cancelled_count,"
            + " COALESCE(SUM(CASE WHEN b.status<>'CANCELLED' THEN b.total_amount ELSE 0 END),0)"
            + " AS amount,"
            + " COALESCE(SUM(CASE WHEN b.status='COMPLETED' THEN b.total_amount ELSE 0 END),0)"
            + " AS completed_amount"
            + " FROM bookings b WHERE "
            + scopeSql(scope, args);
    return jdbc.queryForObject(
        sql,
        (r, n) ->
            new Totals(
                r.getLong("total_count"),
                r.getLong("pending_count"),
                r.getLong("assigned_count"),
                r.getLong("completed_count"),
                r.getLong("cancelled_count"),
                r.getBigDecimal("amount"),
                r.getBigDecimal("completed_amount")),
        args.toArray());
  }

  /**
   * 按月聚合订单量与营收，窗口为 {@code [from 月初, to 月末]}。
   *
   * <p>只返回有订单的月份，缺月由服务层补 0：SQL 里造不出没有数据的行，补零是展示层的事。
   */
  public List<Api.MonthlyReport> monthly(Scope scope, YearMonth from, YearMonth to) {
    var args = new ArrayList<Object>();
    String sql =
        "SELECT YEAR(b.created_at) AS year_no,MONTH(b.created_at) AS month_no,"
            + " COUNT(*) AS order_count,"
            + " COALESCE(SUM(CASE WHEN b.status<>'CANCELLED' THEN b.total_amount ELSE 0 END),0)"
            + " AS revenue"
            + " FROM bookings b WHERE "
            + scopeSql(scope, args)
            + " AND b.created_at>=? AND b.created_at<?"
            + " GROUP BY YEAR(b.created_at),MONTH(b.created_at)"
            + " ORDER BY YEAR(b.created_at),MONTH(b.created_at)";
    args.add(from.atDay(1).atStartOfDay());
    args.add(to.plusMonths(1).atDay(1).atStartOfDay());
    return jdbc.query(
        sql,
        (r, n) ->
            new Api.MonthlyReport(
                String.format("%04d-%02d", r.getInt("year_no"), r.getInt("month_no")),
                r.getLong("order_count"),
                r.getBigDecimal("revenue")),
        args.toArray());
  }

  /** 热门线路 Top N：已取消订单不计入，订单量相同时按线路 id 升序，保证结果稳定。 */
  public List<Api.PopularRoute> popularRoutes(Scope scope, int limit) {
    var args = new ArrayList<Object>();
    String sql =
        "SELECT b.route_id,r.name,r.destination,COUNT(*) AS order_count,"
            + " COALESCE(SUM(b.traveler_count),0) AS traveler_count"
            + " FROM bookings b JOIN travel_routes r ON r.id=b.route_id"
            + " WHERE "
            + scopeSql(scope, args)
            + " AND b.status<>'CANCELLED'"
            + " GROUP BY b.route_id,r.name,r.destination"
            + " ORDER BY COUNT(*) DESC,b.route_id ASC LIMIT ?";
    args.add(Math.max(limit, 0));
    return jdbc.query(
        sql,
        (r, n) ->
            new Api.PopularRoute(
                r.getLong("route_id"),
                r.getString("name"),
                r.getString("destination"),
                r.getLong("order_count"),
                r.getInt("traveler_count")),
        args.toArray());
  }

  /**
   * 员工接单排行：全团队口径，只统计未取消的订单。
   *
   * <p>这是唯一不接受 {@link Scope} 的查询——排行比较的是同事之间的量，按个人范围过滤后只剩自己一行，没有意义。
   */
  public List<Api.EmployeeRank> employeeRank(int limit) {
    String sql =
        "SELECT b.employee_id,u.display_name,COUNT(*) AS order_count,"
            + " COALESCE(SUM(b.total_amount),0) AS amount"
            + " FROM bookings b JOIN app_users u ON u.id=b.employee_id"
            + " WHERE b.employee_id IS NOT NULL AND b.status<>'CANCELLED'"
            + " GROUP BY b.employee_id,u.display_name"
            + " ORDER BY COUNT(*) DESC,COALESCE(SUM(b.total_amount),0) DESC,b.employee_id ASC"
            + " LIMIT ?";
    return jdbc.query(
        sql,
        (r, n) ->
            new Api.EmployeeRank(
                r.getLong("employee_id"),
                r.getString("display_name"),
                r.getLong("order_count"),
                r.getBigDecimal("amount")),
        Math.max(limit, 0));
  }

  /** 在售线路数：只取计数。线路列表每行还要算一次已售名额，用它做统计会变成一次 N+1。 */
  public long activeRoutes() {
    Long count = jdbc.queryForObject("SELECT COUNT(*) FROM travel_routes WHERE active=TRUE", Long.class);
    return count == null ? 0 : count;
  }
}
