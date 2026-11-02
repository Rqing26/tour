package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ChatSchemaTest extends AbstractApiTest {
  @Test
  void migrationPreservesOrdersAndStartsReadCursorsAtZero() {
    long customer = users.byUsername("customer").orElseThrow().id();
    long employee = users.byUsername("employee").orElseThrow().id();
    jdbc.update(
        "INSERT INTO chat_conversations(customer_id,employee_id) VALUES(?,?)", customer, employee);
    var row = jdbc.queryForMap("SELECT * FROM chat_conversations");
    assertEquals(0L, ((Number) row.get("customer_read_id")).longValue());
    assertEquals(0L, ((Number) row.get("employee_read_id")).longValue());
    assertNotNull(row.get("created_at"));
    assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM bookings", Long.class) > 0);
    assertEquals(
        4,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success=TRUE AND version IN"
                + " ('1','2','3','4')",
            Integer.class));
  }

  @Test
  void conversationsAndMessageRetriesHaveDatabaseUniqueness() {
    long customer = users.byUsername("customer").orElseThrow().id();
    long employee = users.byUsername("employee").orElseThrow().id();
    jdbc.update(
        "INSERT INTO chat_conversations(customer_id,employee_id) VALUES(?,?)", customer, employee);
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "INSERT INTO chat_conversations(customer_id,employee_id) VALUES(?,?)",
                customer,
                employee));
    long conversation = jdbc.queryForObject("SELECT id FROM chat_conversations", Long.class);
    String insert =
        "INSERT INTO chat_messages(conversation_id,sender_id,client_id,content) VALUES(?,?,?,?)";
    jdbc.update(insert, conversation, customer, "retry-key", "原消息");
    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbc.update(insert, conversation, customer, "retry-key", "重复消息"));
    jdbc.update(insert, conversation, employee, "retry-key", "另一位发送者");
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages", Integer.class));
    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbc.update(insert, Long.MAX_VALUE, customer, "missing-conversation", "无效会话"));
    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbc.update(insert, conversation, Long.MAX_VALUE, "missing-sender", "无效发送者"));
  }
}
