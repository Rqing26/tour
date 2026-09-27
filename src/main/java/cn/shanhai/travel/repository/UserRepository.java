package cn.shanhai.travel.repository;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.model.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
  private final JdbcTemplate jdbc;

  public UserRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final RowMapper<Account> MAPPER =
      (r, n) ->
          new Account(
              r.getLong("id"),
              r.getString("username"),
              r.getString("display_name"),
              r.getString("phone"),
              Role.valueOf(r.getString("role")),
              r.getBoolean("enabled"),
              r.getString("password_hash"));

  public Optional<Account> byUsername(String username) {
    return jdbc
        .query(
            "SELECT * FROM app_users WHERE username=?", MAPPER, username.toLowerCase(Locale.ROOT))
        .stream()
        .findFirst();
  }

  public Optional<Account> byId(long id) {
    return jdbc.query("SELECT * FROM app_users WHERE id=?", MAPPER, id).stream().findFirst();
  }

  public Optional<Account> lock(long id) {
    return jdbc.query("SELECT * FROM app_users WHERE id=? FOR UPDATE", MAPPER, id).stream()
        .findFirst();
  }

  public List<Api.User> list(String q, Role role) {
    return jdbc.query("SELECT * FROM app_users ORDER BY id DESC", MAPPER).stream()
        .filter(a -> role == null || a.role() == role)
        .filter(
            a ->
                q == null
                    || (a.username() + " " + a.displayName() + " " + a.phone())
                        .toLowerCase(Locale.ROOT)
                        .contains(q.toLowerCase(Locale.ROOT)))
        .map(Account::view)
        .toList();
  }

  public long create(
      String username, String displayName, String passwordHash, String phone, Role role) {
    var key = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var p =
              c.prepareStatement(
                  "INSERT INTO app_users(username,display_name,password_hash,phone,role)"
                      + " VALUES(?,?,?,?,?)",
                  new String[] {"id"});
          p.setString(1, username.toLowerCase(Locale.ROOT));
          p.setString(2, displayName.trim());
          p.setString(3, passwordHash);
          p.setString(4, phone.trim());
          p.setString(5, role.name());
          return p;
        },
        key);
    return Objects.requireNonNull(key.getKey()).longValue();
  }

  public void update(long id, String name, String phone, boolean enabled) {
    jdbc.update(
        "UPDATE app_users SET display_name=?,phone=?,enabled=? WHERE id=?",
        name.trim(),
        phone.trim(),
        enabled,
        id);
  }

  public void password(long id, String hash) {
    jdbc.update("UPDATE app_users SET password_hash=? WHERE id=?", hash, id);
  }

  public long count(Role role) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM app_users WHERE role=? AND enabled=TRUE", Long.class, role.name());
  }

  public boolean hasAssigned(long id) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE employee_id=? AND status='ASSIGNED'",
            Integer.class,
            id)
        > 0;
  }

  public boolean hasBookings(long id) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE employee_id=? OR customer_id=?",
            Integer.class,
            id,
            id)
        > 0;
  }

  public void delete(long id) {
    jdbc.update("DELETE FROM app_users WHERE id=?", id);
  }
}
