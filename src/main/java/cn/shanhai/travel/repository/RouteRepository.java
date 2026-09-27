package cn.shanhai.travel.repository;

import cn.shanhai.travel.dto.Api;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class RouteRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public RouteRepository(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  private RowMapper<Api.Route> mapper() {
    return (r, n) ->
        new Api.Route(
            r.getLong("id"),
            r.getString("name"),
            r.getString("destination"),
            r.getString("description"),
            r.getDate("start_date").toLocalDate(),
            r.getInt("duration_days"),
            r.getBigDecimal("price"),
            r.getInt("capacity"),
            r.getInt("capacity") - sold(r.getLong("id")),
            r.getBoolean("active"),
            r.getString("image_key"),
            readHighlights(r.getString("highlights")));
  }

  private List<String> readHighlights(String raw) {
    try {
      return json.readValue(raw, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Invalid stored route highlights", e);
    }
  }

  private String highlights(List<String> values) {
    try {
      return json.writeValueAsString(values);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  public List<Api.Route> list() {
    return jdbc.query("SELECT * FROM travel_routes ORDER BY start_date,id", mapper());
  }

  public Optional<Api.Route> find(long id) {
    return jdbc.query("SELECT * FROM travel_routes WHERE id=?", mapper(), id).stream().findFirst();
  }

  public Optional<Api.Route> lock(long id) {
    return jdbc.query("SELECT * FROM travel_routes WHERE id=? FOR UPDATE", mapper(), id).stream()
        .findFirst();
  }

  public int sold(long id) {
    return jdbc.queryForObject(
        "SELECT COALESCE(SUM(traveler_count),0) FROM bookings WHERE route_id=? AND"
            + " status<>'CANCELLED'",
        Integer.class,
        id);
  }

  public int bookings(long id) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE route_id=?", Integer.class, id);
  }

  public long create(Api.RouteInput d) {
    var key = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var p =
              c.prepareStatement(
                  "INSERT INTO"
                      + " travel_routes(name,destination,description,start_date,duration_days,price,capacity,active,image_key,highlights)"
                      + " VALUES(?,?,?,?,?,?,?,?,?,?)",
                  new String[] {"id"});
          p.setString(1, d.name().trim());
          p.setString(2, d.destination().trim());
          p.setString(3, d.description().trim());
          p.setDate(4, java.sql.Date.valueOf(d.startDate()));
          p.setInt(5, d.durationDays());
          p.setBigDecimal(6, d.price());
          p.setInt(7, d.capacity());
          p.setBoolean(8, d.active());
          p.setString(9, d.imageKey());
          p.setString(10, highlights(d.highlights()));
          return p;
        },
        key);
    return Objects.requireNonNull(key.getKey()).longValue();
  }

  public void update(long id, Api.RouteInput d) {
    jdbc.update(
        "UPDATE travel_routes SET"
            + " name=?,destination=?,description=?,start_date=?,duration_days=?,price=?,capacity=?,active=?,image_key=?,highlights=?"
            + " WHERE id=?",
        d.name().trim(),
        d.destination().trim(),
        d.description().trim(),
        d.startDate(),
        d.durationDays(),
        d.price(),
        d.capacity(),
        d.active(),
        d.imageKey(),
        highlights(d.highlights()),
        id);
  }

  public void delete(long id) {
    jdbc.update("DELETE FROM travel_routes WHERE id=?", id);
  }
}
