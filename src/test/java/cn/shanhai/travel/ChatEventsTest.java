package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.shanhai.travel.dto.ChatApi;
import cn.shanhai.travel.repository.ChatRepository;
import cn.shanhai.travel.service.ChatEvents;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ChatEventsTest extends AbstractApiTest {
  @Autowired ChatEvents events;
  @Autowired ChatRepository chats;
  @Autowired ApplicationEventPublisher publisher;
  @Autowired PlatformTransactionManager transactions;

  private int changes(MvcResult stream) throws Exception {
    return stream.getResponse().getContentAsString().split("event:chat", -1).length - 1;
  }

  @Test
  void committedUpdatesReachOnlyParticipantsAndRollbackStaysSilent() throws Exception {
    var customer = login("customer");
    var employee = login("employee");
    var admin = login("admin");
    newUser(admin, "event_outsider", "CUSTOMER");
    var outsider = login("event_outsider");
    var stream =
        mvc.perform(get("/api/chat/events").session(employee.session()))
            .andExpect(MockMvcResultMatchers.request().asyncStarted())
            .andReturn();
    var other = mvc.perform(get("/api/chat/events").session(outsider.session())).andReturn();
    try {
      long peer = users.byUsername("employee").orElseThrow().id();
      long sender = users.byUsername("customer").orElseThrow().id();
      long id =
          ok(customer, "POST", "/api/chat/conversations", Map.of("peerId", peer))
              .get("id")
              .asLong();
      assertEquals(1, changes(stream));
      var tx = new TransactionTemplate(transactions);
      tx.executeWithoutResult(
          s -> {
            chats.send(id, sender, new ChatApi.Send("应回滚", UUID.randomUUID().toString()));
            publisher.publishEvent(new ChatEvents.Changed(id, sender, peer));
            s.setRollbackOnly();
          });
      assertEquals(1, changes(stream));
      assertTrue(chats.history(id, Long.MAX_VALUE).isEmpty());
      var input = Map.of("content", "提交后可见", "clientId", UUID.randomUUID().toString());
      ok(customer, "POST", "/api/chat/conversations/" + id + "/messages", input);
      assertEquals(2, changes(stream));
      ok(customer, "POST", "/api/chat/conversations/" + id + "/messages", input);
      assertEquals(2, changes(stream));
      assertEquals(0, changes(other));
      assertFalse(stream.getResponse().getContentAsString().contains("提交后可见"));
    } finally {
      employee.session().invalidate();
      outsider.session().invalidate();
      events.heartbeat();
    }
  }

  @Test
  void streamRejectsAnonymousAdminsAndClosesWhenAccountIsDisabled() throws Exception {
    mvc.perform(get("/api/chat/events")).andExpect(status().isUnauthorized());
    var admin = login("admin");
    mvc.perform(get("/api/chat/events").session(admin.session())).andExpect(status().isForbidden());
    var customer = login("customer");
    var stream =
        mvc.perform(get("/api/chat/events").session(customer.session()))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(MockMvcResultMatchers.request().asyncStarted())
            .andReturn();
    assertTrue(stream.getResponse().getContentAsString().contains("event:ready"));
    jdbc.update("UPDATE app_users SET enabled=FALSE WHERE username='customer'");
    events.heartbeat();
    assertNull(stream.getAsyncResult(1000));
  }
}
