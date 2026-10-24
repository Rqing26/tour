package cn.shanhai.travel.service;

import cn.shanhai.travel.dto.ChatApi;
import cn.shanhai.travel.model.*;
import cn.shanhai.travel.repository.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
  private final ChatRepository chats;
  private final UserService users;
  private final UserRepository accounts;
  private final ApplicationEventPublisher events;
  public ChatService(ChatRepository chats, UserService users, UserRepository accounts, ApplicationEventPublisher events) {
    this.chats = chats; this.users = users; this.accounts = accounts; this.events = events;
  }
  public Account actor() {
    var actor = users.current();
    if (actor.role() == Role.ADMIN) throw ApiException.forbidden();
    return actor;
  }
  public List<ChatApi.Contact> contacts() {
    return actor().role() == Role.CUSTOMER ? chats.contacts() : List.of();
  }
  public List<ChatApi.Conversation> list() { return chats.list(actor().id()); }
  private ChatRepository.Conversation access(long id, Account actor, boolean lock) {
    var conversation = chats.find(id, lock).orElseThrow(() -> ApiException.missing("会话不存在"));
    if (!conversation.contains(actor.id())) throw ApiException.forbidden();
    return conversation;
  }
  @Transactional
  public ChatApi.Conversation start(ChatApi.Start input) {
    var actor = actor();
    if (actor.role() != Role.CUSTOMER) throw ApiException.forbidden();
    users.lockCurrent();
    var peer = accounts.byId(input.peerId()).orElseThrow(() -> ApiException.missing("顾问不存在"));
    if (peer.role() != Role.EMPLOYEE) throw ApiException.forbidden();
    if (!peer.enabled()) throw ApiException.conflict("该顾问账号已停用");
    long id = chats.start(actor.id(), peer.id());
    events.publishEvent(new ChatEvents.Changed(id, actor.id(), peer.id()));
    return chats.list(actor.id()).stream().filter(c -> c.id() == id).findFirst().orElseThrow();
  }
  public List<ChatApi.Message> history(long id, long before) {
    access(id, actor(), false);
    if (before <= 0) throw ApiException.invalid("历史消息游标无效");
    return chats.history(id, before);
  }
  @Transactional
  public ChatApi.Message send(long id, ChatApi.Send input) {
    var actor = actor();
    var conversation = access(id, actor, true);
    var existing = chats.retry(id, actor.id(), input.clientId());
    if (existing.isPresent()) return existing.get();
    var peer = accounts.byId(conversation.peer(actor.id())).orElseThrow(() -> ApiException.missing("对方账号不存在"));
    if (!peer.enabled()) throw ApiException.conflict("对方账号已停用，暂时无法发送消息");
    if (input.content().isBlank() || input.content().strip().isEmpty()) throw ApiException.invalid("消息不能为空");
    var message = chats.send(id, actor.id(), input);
    events.publishEvent(new ChatEvents.Changed(id, conversation.customerId(), conversation.employeeId()));
    return message;
  }
  @Transactional
  public void read(long id, ChatApi.Read input) {
    var actor = actor();
    var conversation = access(id, actor, true);
    if (!chats.hasMessage(id, input.messageId())) throw ApiException.invalid("消息不属于此会话");
    if (chats.read(conversation, actor.id(), input.messageId()))
      events.publishEvent(new ChatEvents.Changed(id, conversation.customerId(), conversation.employeeId()));
  }
}
