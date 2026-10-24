package cn.shanhai.travel.repository;

import cn.shanhai.travel.dto.ChatApi;
import cn.shanhai.travel.model.Role;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ChatRepository {
  private final JdbcTemplate jdbc;
  public ChatRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public record Conversation(long id, long customerId, long employeeId) {
    public boolean contains(long userId) { return customerId == userId || employeeId == userId; }
    public long peer(long userId) { return customerId == userId ? employeeId : customerId; }
  }
  private static final RowMapper<Conversation> THREAD = (r, n) ->
      new Conversation(r.getLong("id"), r.getLong("customer_id"), r.getLong("employee_id"));
  private static final RowMapper<ChatApi.Message> MESSAGE = (r, n) ->
      new ChatApi.Message(r.getLong("id"), r.getLong("conversation_id"), r.getLong("sender_id"),
          r.getString("content"), r.getString("client_id"), r.getTimestamp("created_at").toLocalDateTime());

  public Optional<Conversation> find(long id, boolean lock) {
    return jdbc.query("SELECT * FROM chat_conversations WHERE id=?" + (lock ? " FOR UPDATE" : ""), THREAD, id).stream().findFirst();
  }
  public long start(long customerId, long employeeId) {
    // The caller locks the customer row, serializing concurrent starts for the same pair.
    var existing = jdbc.query("SELECT * FROM chat_conversations WHERE customer_id=? AND employee_id=?", THREAD, customerId, employeeId);
    if (!existing.isEmpty()) return existing.getFirst().id();
    var key = new GeneratedKeyHolder();
    jdbc.update(c -> {
      var p = c.prepareStatement("INSERT INTO chat_conversations(customer_id,employee_id) VALUES(?,?)", new String[]{"id"});
      p.setLong(1, customerId); p.setLong(2, employeeId); return p;
    }, key);
    return Objects.requireNonNull(key.getKey()).longValue();
  }
  public List<ChatApi.Contact> contacts() {
    return jdbc.query("SELECT id,display_name,role,enabled FROM app_users WHERE role='EMPLOYEE' AND enabled=TRUE ORDER BY display_name,id",
        (r,n) -> new ChatApi.Contact(r.getLong("id"), r.getString("display_name"), Role.valueOf(r.getString("role")), r.getBoolean("enabled")));
  }
  public List<ChatApi.Conversation> list(long actor) {
    return jdbc.query("""
        SELECT c.id, u.id AS peer_id, u.display_name, u.role, u.enabled,
          m.content AS last_message, COALESCE(m.created_at,c.created_at) AS updated_at,
          CASE WHEN c.customer_id=? THEN c.employee_read_id ELSE c.customer_read_id END AS peer_read_id,
          (SELECT COUNT(*) FROM chat_messages x WHERE x.conversation_id=c.id AND x.sender_id<>?
            AND x.id > CASE WHEN c.customer_id=? THEN c.customer_read_id ELSE c.employee_read_id END) AS unread_count
        FROM chat_conversations c
        JOIN app_users u ON u.id=CASE WHEN c.customer_id=? THEN c.employee_id ELSE c.customer_id END
        LEFT JOIN chat_messages m ON m.id=(SELECT MAX(x.id) FROM chat_messages x WHERE x.conversation_id=c.id)
        WHERE c.customer_id=? OR c.employee_id=?
        ORDER BY COALESCE(m.created_at,c.created_at) DESC, c.id DESC
        """, (r,n) -> new ChatApi.Conversation(r.getLong("id"),
            new ChatApi.Contact(r.getLong("peer_id"), r.getString("display_name"), Role.valueOf(r.getString("role")), r.getBoolean("enabled")),
            r.getString("last_message"), r.getTimestamp("updated_at").toLocalDateTime(), r.getLong("unread_count"), r.getLong("peer_read_id")),
        actor, actor, actor, actor, actor, actor);
  }
  public List<ChatApi.Message> history(long conversation, long before) {
    var result = new ArrayList<>(jdbc.query("SELECT * FROM chat_messages WHERE conversation_id=? AND id<? ORDER BY id DESC LIMIT 50", MESSAGE, conversation, before));
    Collections.reverse(result);
    return result;
  }
  public Optional<ChatApi.Message> retry(long conversation, long sender, String clientId) {
    return jdbc.query("SELECT * FROM chat_messages WHERE conversation_id=? AND sender_id=? AND client_id=?", MESSAGE, conversation, sender, clientId).stream().findFirst();
  }
  public ChatApi.Message send(long conversation, long sender, ChatApi.Send input) {
    var key = new GeneratedKeyHolder();
    jdbc.update(c -> {
      var p = c.prepareStatement("INSERT INTO chat_messages(conversation_id,sender_id,client_id,content) VALUES(?,?,?,?)", new String[]{"id"});
      p.setLong(1, conversation); p.setLong(2, sender); p.setString(3, input.clientId()); p.setString(4, input.content().strip()); return p;
    }, key);
    return jdbc.queryForObject("SELECT * FROM chat_messages WHERE id=?", MESSAGE, Objects.requireNonNull(key.getKey()).longValue());
  }
  public boolean hasMessage(long conversation, long message) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE conversation_id=? AND id=?", Integer.class, conversation, message) > 0;
  }
  public boolean read(Conversation conversation, long actor, long message) {
    String column = conversation.customerId() == actor ? "customer_read_id" : "employee_read_id";
    return jdbc.update("UPDATE chat_conversations SET " + column + "=? WHERE id=? AND " + column + "<?", message, conversation.id(), message) > 0;
  }
  public boolean hasConversations(long user) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM chat_conversations WHERE customer_id=? OR employee_id=?", Integer.class, user, user) > 0;
  }
}
