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

  private static final String SELECT =
      "SELECT b.*,c.display_name customer_name,c.phone customer_phone,e.display_name"
          + " employee_name,r.name route_name,r.destination,r.start_date,r.duration_days FROM"
          + " bookings b JOIN app_users c ON c.id=b.customer_id LEFT JOIN app_users e ON"
          + " e.id=b.employee_id JOIN travel_routes r ON r.id=b.route_id";
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

  public List<Api.Order> all() {
    return jdbc.query(SELECT + " ORDER BY b.created_at DESC,b.id DESC", MAPPER);
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
