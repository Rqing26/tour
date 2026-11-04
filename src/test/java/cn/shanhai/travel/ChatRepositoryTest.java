package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;

import cn.shanhai.travel.dto.ChatApi;
import cn.shanhai.travel.repository.ChatRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ChatRepositoryTest extends AbstractApiTest {
  @Autowired ChatRepository chats;

  @Test
  void pairAndContactQueriesStayScopedToParticipants() {
    long customer = users.byUsername("customer").orElseThrow().id();
    long employee = users.byUsername("employee").orElseThrow().id();
    long id = chats.start(customer, employee);
    assertEquals(id, chats.start(customer, employee));
    var pair = chats.find(id, true).orElseThrow();
    assertTrue(pair.contains(customer));
    assertEquals(employee, pair.peer(customer));
    assertEquals(customer, pair.peer(employee));
    assertTrue(chats.hasConversations(customer));
    assertEquals(id, chats.list(employee).getFirst().id());
    assertEquals(0, chats.list(customer).getFirst().unreadCount());
    assertEquals(employee, chats.contacts().getFirst().id());
    assertTrue(chats.list(Long.MAX_VALUE).isEmpty());
    assertTrue(chats.find(Long.MAX_VALUE, false).isEmpty());
    assertFalse(chats.hasConversations(Long.MAX_VALUE));
  }

  @Test
  void historyRetryAndReadCursorsKeepStableMessageIds() {
    long customer = users.byUsername("customer").orElseThrow().id();
    long employee = users.byUsername("employee").orElseThrow().id();
    long id = chats.start(customer, employee);
    for (int n = 0; n < 52; n++) {
      chats.send(id, customer, new ChatApi.Send(" 消息 " + n + " ", UUID.randomUUID().toString()));
    }
    var page = chats.history(id, Long.MAX_VALUE);
    var last = page.getLast();
    assertEquals(50, page.size());
    assertEquals("消息 51", last.content());
    assertEquals(2, chats.history(id, page.getFirst().id()).size());
    assertEquals(last, chats.retry(id, customer, last.clientId()).orElseThrow());
    assertTrue(chats.retry(id, employee, last.clientId()).isEmpty());
    assertTrue(chats.hasMessage(id, last.id()));
    assertFalse(chats.hasMessage(Long.MAX_VALUE, last.id()));
    assertEquals(52, chats.list(employee).getFirst().unreadCount());
    var pair = chats.find(id, false).orElseThrow();
    assertTrue(chats.read(pair, employee, last.id()));
    assertFalse(chats.read(pair, employee, page.getFirst().id()));
    assertEquals(0, chats.list(employee).getFirst().unreadCount());
    assertEquals(last.id(), chats.list(customer).getFirst().peerReadId());
    assertEquals(last.content(), chats.list(customer).getFirst().lastMessage());
  }
}
