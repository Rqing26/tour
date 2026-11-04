package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ChatMigrationTest {
  @Test
  void upgradesExistingImageSchemaWithoutRewritingItsHistory() {
    var source =
        new DriverManagerDataSource(
            "jdbc:h2:mem:chatupgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa",
            "");
    Flyway.configure().dataSource(source).target("4").load().migrate();
    var jdbc = new JdbcTemplate(source);
    jdbc.update(
        "INSERT INTO app_users(username,password_hash,display_name,phone,role)"
            + " VALUES('legacy','hash','旧账号','13800000000','CUSTOMER')");
    var checksum =
        jdbc.queryForObject(
            "SELECT checksum FROM flyway_schema_history WHERE version='4'", Integer.class);

    Flyway.configure().dataSource(source).load().migrate();

    assertEquals(
        checksum,
        jdbc.queryForObject(
            "SELECT checksum FROM flyway_schema_history WHERE version='4'", Integer.class));
    assertEquals(
        "旧账号",
        jdbc.queryForObject(
            "SELECT display_name FROM app_users WHERE username='legacy'", String.class));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages", Integer.class));
    assertEquals(
        "4.1",
        jdbc.queryForObject(
            "SELECT version FROM flyway_schema_history WHERE script='V4_1__create_chat.sql'",
            String.class));
  }
}
