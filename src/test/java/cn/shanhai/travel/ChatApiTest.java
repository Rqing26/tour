package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatApiTest extends AbstractApiTest {
  private static final String ROOT = "/api/chat/conversations";

  private Map<String, String> message(String content) {
    return Map.of("content", content, "clientId", UUID.randomUUID().toString());
  }

  @Test
  void concurrentStartsAndRetriesProduceOnlyOneConversationAndMessage() throws Exception {
    var customer = login("customer");
    var pair = Map.of("peerId", users.byUsername("employee").orElseThrow().id());
    assertEquals(
        java.util.List.of(200, 200),
        concurrent(
            () -> request(customer, "POST", ROOT, pair).getResponse().getStatus(),
            () -> request(customer, "POST", ROOT, pair).getResponse().getStatus()));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM chat_conversations", Integer.class));
    long id = ok(customer, "GET", ROOT, Map.of()).get(0).get("id").asLong();
    String path = ROOT + "/" + id + "/messages";
    var input = message("同一条消息");
    assertEquals(
        java.util.List.of(200, 200),
        concurrent(
            () -> request(customer, "POST", path, input).getResponse().getStatus(),
            () -> request(customer, "POST", path, input).getResponse().getStatus()));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages", Integer.class));
  }

  @Test
  void participantsExchangeMessagesRetryAndAcknowledgeReads() throws Exception {
    var customer = login("customer");
    var employee = login("employee");
    long peer = users.byUsername("employee").orElseThrow().id();
    var contacts = ok(customer, "GET", "/api/chat/contacts", Map.of());
    assertFalse(contacts.get(0).has("phone"));
    var input = Map.of("peerId", peer);
    long id = ok(customer, "POST", ROOT, input).get("id").asLong();
    assertEquals(id, ok(customer, "POST", ROOT, input).get("id").asLong());
    String path = ROOT + "/" + id;
    var draft = message("  想咨询云南行程\n带小朋友方便吗？  ");
    var sent = ok(customer, "POST", path + "/messages", draft);
    assertEquals("想咨询云南行程\n带小朋友方便吗？", sent.get("content").asText());
    assertEquals(sent, ok(customer, "POST", path + "/messages", draft));
    assertEquals(sent, ok(employee, "GET", path + "/messages", Map.of()).get(0));
    assertEquals(1, ok(employee, "GET", ROOT, Map.of()).get(0).get("unreadCount").asInt());
    ok(employee, "POST", path + "/read", Map.of("messageId", sent.get("id").asLong()));
    assertEquals(0, ok(employee, "GET", ROOT, Map.of()).get(0).get("unreadCount").asInt());
    ok(employee, "POST", path + "/messages", message("可以，我来安排。"));
    assertEquals(1, ok(customer, "GET", ROOT, Map.of()).get(0).get("unreadCount").asInt());
  }

  @Test
  void outsidersInvalidMessagesAndDisabledPeersAreRejected() throws Exception {
    var admin = login("admin");
    var customer = login("customer");
    newUser(admin, "chat_outsider", "CUSTOMER");
    var outsider = login("chat_outsider");
    long peer = users.byUsername("employee").orElseThrow().id();
    long id = ok(customer, "POST", ROOT, Map.of("peerId", peer)).get("id").asLong();
    String path = ROOT + "/" + id;
    assertEquals(401, request(anonymous(), "GET", ROOT, Map.of()).getResponse().getStatus());
    assertEquals(403, request(admin, "GET", ROOT, Map.of()).getResponse().getStatus());
    assertEquals(
        403, request(outsider, "GET", path + "/messages", Map.of()).getResponse().getStatus());
    assertEquals(
        403,
        request(outsider, "POST", path + "/messages", message("越权")).getResponse().getStatus());
    assertEquals(
        400, request(customer, "POST", path + "/messages", message(" ")).getResponse().getStatus());
    assertEquals(
        400,
        request(customer, "POST", path + "/messages", message("x".repeat(2001)))
            .getResponse()
            .getStatus());
    assertEquals(
        400,
        request(customer, "POST", path + "/read", Map.of("messageId", Long.MAX_VALUE))
            .getResponse()
            .getStatus());
    long ownId = ok(outsider, "POST", ROOT, Map.of("peerId", peer)).get("id").asLong();
    assertNotEquals(id, ownId);
    long userId = users.byUsername("chat_outsider").orElseThrow().id();
    assertEquals(
        409, request(admin, "DELETE", "/api/users/" + userId, Map.of()).getResponse().getStatus());
    jdbc.update("UPDATE app_users SET enabled=FALSE WHERE id=?", peer);
    assertEquals(
        409,
        request(customer, "POST", path + "/messages", message("你好")).getResponse().getStatus());
    assertTrue(ok(customer, "GET", "/api/chat/contacts", Map.of()).isEmpty());
  }
}
