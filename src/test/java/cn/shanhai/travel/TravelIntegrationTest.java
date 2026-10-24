package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.shanhai.travel.config.DemoData;
import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.repository.*;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"demo", "test"})
class TravelIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired DemoData demo;
  @Autowired RouteRepository routes;
  @Autowired UserRepository users;
  @Autowired OrderRepository orders;

  record Client(MockHttpSession session, String csrf) {}

  @BeforeEach
  void reset() {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name='chat_messages'", Integer.class) > 0) {
      jdbc.update("DELETE FROM chat_messages");
      jdbc.update("DELETE FROM chat_conversations");
    }
    jdbc.update("DELETE FROM bookings");
    jdbc.update("DELETE FROM travel_routes");
    jdbc.update("DELETE FROM app_users");
    demo.run(new DefaultApplicationArguments());
  }

  private JsonNode data(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  private Client anonymous() throws Exception {
    var r = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    return new Client((MockHttpSession) r.getRequest().getSession(), data(r).get("token").asText());
  }

  private Client login(String username) throws Exception {
    var c = anonymous();
    var result =
        request(
            c, "POST", "/api/auth/login", Map.of("username", username, "password", "Travel@2026"));
    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    var next =
        mvc.perform(get("/api/auth/csrf").session(c.session()))
            .andExpect(status().isOk())
            .andReturn();
    return new Client(c.session(), data(next).get("token").asText());
  }

  private MvcResult request(Client c, String method, String path, Object body) throws Exception {
    return mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                    org.springframework.http.HttpMethod.valueOf(method), path)
                .session(c.session())
                .header("X-CSRF-TOKEN", c.csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andReturn();
  }

  private JsonNode ok(Client c, String method, String path, Object body) throws Exception {
    var r = request(c, method, path, body);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return data(r);
  }

  private long route(int capacity) {
    return routes.create(
        new Api.RouteInput(
            "测试线路",
            "测试目的地",
            "用于验证真实业务约束",
            LocalDate.now().plusDays(40),
            3,
            new BigDecimal("1200.00"),
            capacity,
            true,
            "guilin",
            List.of("测试景点")));
  }

  private Map<String, Object> booking(long id, int count) {
    return Map.of(
        "routeId",
        id,
        "travelerCount",
        count,
        "contactName",
        "测试联系人",
        "contactPhone",
        "13800000000",
        "remark",
        "集成测试");
  }

  private long createOrder(Client c, long route, int count) throws Exception {
    return ok(c, "POST", "/api/orders", booking(route, count)).get("id").asLong();
  }

  private long newUser(Client admin, String name, String role) throws Exception {
    return ok(
            admin,
            "POST",
            "/api/users",
            Map.of(
                "username",
                name,
                "displayName",
                name,
                "phone",
                "13800000000",
                "password",
                "Travel@2026",
                "role",
                role))
        .get("id")
        .asLong();
  }

  private Api.RouteInput edit(long id, String price, int capacity, LocalDate departure) {
    var r = routes.find(id).orElseThrow();
    return new Api.RouteInput(
        r.name(),
        r.destination(),
        r.description(),
        departure,
        r.durationDays(),
        new BigDecimal(price),
        capacity,
        r.active(),
        r.imageKey(),
        r.highlights());
  }

  @Test
  void loginRequiresCsrfAndRotatesTokenAndLogoutEndsSession() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"Travel@2026\"}"))
        .andExpect(status().isForbidden());
    var c = anonymous();
    assertEquals(
        401,
        request(c, "POST", "/api/auth/login", Map.of("username", "admin", "password", "wrong"))
            .getResponse()
            .getStatus());
    var before = c.csrf();
    ok(c, "POST", "/api/auth/login", Map.of("username", "admin", "password", "Travel@2026"));
    var token =
        data(mvc.perform(get("/api/auth/csrf").session(c.session())).andReturn())
            .get("token")
            .asText();
    assertNotEquals(before, token);
    assertEquals(
        403,
        request(c, "PUT", "/api/account", Map.of("displayName", "测试", "phone", "13800000000"))
            .getResponse()
            .getStatus());
    mvc.perform(get("/api/auth/me").session(c.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.role").value("ADMIN"));
    ok(new Client(c.session(), token), "POST", "/api/auth/logout", Map.of());
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void registrationDoesNotEscalateRoleOrAutoLogin() throws Exception {
    var c = anonymous();
    var d =
        ok(
            c,
            "POST",
            "/api/auth/register",
            Map.of(
                "username",
                "new_customer",
                "displayName",
                "新旅客",
                "password",
                "Travel@2026",
                "phone",
                "13800000000",
                "role",
                "ADMIN"));
    assertEquals("CUSTOMER", d.get("role").asText());
    mvc.perform(get("/api/auth/me").session(c.session())).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/auth/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.demoMode").value(true));
    assertEquals(
        409,
        request(
                c,
                "POST",
                "/api/auth/register",
                Map.of(
                    "username",
                    "NEW_CUSTOMER",
                    "displayName",
                    "新旅客",
                    "password",
                    "Travel@2026",
                    "phone",
                    "13800000000"))
            .getResponse()
            .getStatus());
  }

  @Test
  void permissionsValidationAndOrderOwnershipAreEnforced() throws Exception {
    var admin = login("admin");
    var customer = login("customer");
    long rid = route(10);
    long id = createOrder(customer, rid, 2);
    mvc.perform(get("/api/users").session(customer.session())).andExpect(status().isForbidden());
    assertEquals(
        403, request(customer, "DELETE", "/api/routes/" + rid, Map.of()).getResponse().getStatus());
    assertEquals(
        400, request(customer, "POST", "/api/orders", booking(rid, 0)).getResponse().getStatus());
    assertEquals(
        400,
        request(
                admin,
                "POST",
                "/api/users",
                Map.of(
                    "username",
                    "badadmin",
                    "displayName",
                    "Bad",
                    "password",
                    "Travel@2026",
                    "phone",
                    "13800000000",
                    "role",
                    "ADMIN"))
            .getResponse()
            .getStatus());
    newUser(admin, "other_customer", "CUSTOMER");
    var other = login("other_customer");
    assertEquals(
        403,
        request(other, "POST", "/api/orders/" + id + "/cancel", Map.of())
            .getResponse()
            .getStatus());
    var own = data(mvc.perform(get("/api/orders").session(other.session())).andReturn());
    assertEquals(0, own.size());
    assertEquals(
        403,
        request(customer, "POST", "/api/orders/" + id + "/claim", Map.of())
            .getResponse()
            .getStatus());
  }

  @Test
  void completeEmployeeWorkflowRejectsInvalidTransitions() throws Exception {
    var admin = login("admin");
    var customer = login("customer");
    var employee = login("employee");
    newUser(admin, "second_employee", "EMPLOYEE");
    var second = login("second_employee");
    long id = createOrder(customer, route(10), 2);
    assertEquals(
        "ASSIGNED",
        ok(employee, "POST", "/api/orders/" + id + "/claim", Map.of()).get("status").asText());
    assertEquals(
        409,
        request(second, "POST", "/api/orders/" + id + "/claim", Map.of())
            .getResponse()
            .getStatus());
    assertEquals(
        403,
        request(second, "POST", "/api/orders/" + id + "/complete", Map.of())
            .getResponse()
            .getStatus());
    assertEquals(
        "PENDING",
        ok(employee, "POST", "/api/orders/" + id + "/release", Map.of()).get("status").asText());
    ok(second, "POST", "/api/orders/" + id + "/claim", Map.of());
    assertEquals(
        "COMPLETED",
        ok(second, "POST", "/api/orders/" + id + "/complete", Map.of()).get("status").asText());
    assertEquals(
        409,
        request(customer, "POST", "/api/orders/" + id + "/cancel", Map.of())
            .getResponse()
            .getStatus());
    assertEquals(
        409,
        request(second, "POST", "/api/orders/" + id + "/release", Map.of())
            .getResponse()
            .getStatus());
  }

  @Test
  void priceSnapshotCapacityDepartureAndCancellationRemainConsistent() throws Exception {
    var admin = login("admin");
    var customer = login("customer");
    long rid = route(2);
    long id = createOrder(customer, rid, 2);
    var date = routes.find(rid).orElseThrow().startDate();
    assertEquals(0, routes.find(rid).orElseThrow().availableSeats());
    assertEquals(
        409, request(customer, "POST", "/api/orders", booking(rid, 1)).getResponse().getStatus());
    ok(admin, "PUT", "/api/routes/" + rid, edit(rid, "2300.00", 2, date));
    assertEquals(
        0, new BigDecimal("2400.00").compareTo(orders.find(id).orElseThrow().totalAmount()));
    assertEquals(
        409,
        request(admin, "PUT", "/api/routes/" + rid, edit(rid, "2300.00", 1, date))
            .getResponse()
            .getStatus());
    assertEquals(
        409,
        request(admin, "PUT", "/api/routes/" + rid, edit(rid, "2300.00", 2, date.plusDays(1)))
            .getResponse()
            .getStatus());
    assertEquals(
        409, request(admin, "DELETE", "/api/routes/" + rid, Map.of()).getResponse().getStatus());
    ok(customer, "POST", "/api/orders/" + id + "/cancel", Map.of());
    assertEquals(2, routes.find(rid).orElseThrow().availableSeats());
    assertEquals(
        409,
        request(customer, "POST", "/api/orders/" + id + "/cancel", Map.of())
            .getResponse()
            .getStatus());
  }

  @Test
  void disabledAccountsLoseExistingSessionsAndAssignedEmployeesCannotBeDisabled() throws Exception {
    var admin = login("admin");
    var customer = login("customer");
    long uid = users.byUsername("customer").orElseThrow().id();
    ok(
        admin,
        "PUT",
        "/api/users/" + uid,
        Map.of("displayName", "测试用户", "phone", "13800000000", "enabled", false));
    mvc.perform(get("/api/auth/me").session(customer.session()))
        .andExpect(status().isUnauthorized());
    long employeeId = users.byUsername("employee").orElseThrow().id();
    assertEquals(
        409,
        request(
                admin,
                "PUT",
                "/api/users/" + employeeId,
                Map.of("displayName", "测试员工", "phone", "13800000000", "enabled", false))
            .getResponse()
            .getStatus());
    assertEquals(
        409, request(admin, "DELETE", "/api/users/" + uid, Map.of()).getResponse().getStatus());
  }

  @Test
  void lastSeatCannotBeOversoldByConcurrentCustomers() throws Exception {
    var admin = login("admin");
    newUser(admin, "concurrent_customer", "CUSTOMER");
    var first = login("customer");
    var second = login("concurrent_customer");
    long rid = route(1);
    var statuses =
        concurrent(
            () -> request(first, "POST", "/api/orders", booking(rid, 1)).getResponse().getStatus(),
            () ->
                request(second, "POST", "/api/orders", booking(rid, 1)).getResponse().getStatus());
    assertEquals(List.of(200, 409), statuses);
    assertEquals(0, routes.find(rid).orElseThrow().availableSeats());
  }

  @Test
  void oneOrderCannotBeClaimedByTwoEmployees() throws Exception {
    var admin = login("admin");
    newUser(admin, "concurrent_employee", "EMPLOYEE");
    var first = login("employee");
    var second = login("concurrent_employee");
    long id = createOrder(login("customer"), route(5), 1);
    assertEquals(
        List.of(200, 409),
        concurrent(
            () ->
                request(first, "POST", "/api/orders/" + id + "/claim", Map.of())
                    .getResponse()
                    .getStatus(),
            () ->
                request(second, "POST", "/api/orders/" + id + "/claim", Map.of())
                    .getResponse()
                    .getStatus()));
  }

  private List<Integer> concurrent(Callable<Integer> one, Callable<Integer> two) throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      var ready = new CountDownLatch(2);
      var go = new CountDownLatch(1);
      var f1 =
          pool.submit(
              () -> {
                ready.countDown();
                go.await();
                return one.call();
              });
      var f2 =
          pool.submit(
              () -> {
                ready.countDown();
                go.await();
                return two.call();
              });
      assertTrue(ready.await(3, TimeUnit.SECONDS));
      go.countDown();
      var list =
          new ArrayList<>(List.of(f1.get(10, TimeUnit.SECONDS), f2.get(10, TimeUnit.SECONDS)));
      Collections.sort(list);
      return list;
    }
  }

  @Test
  void consultationKeepsHistoryDeduplicatesRetriesAndTracksReadMessages() throws Exception {
    var customer = login("customer");
    var employee = login("employee");
    long employeeId = users.byUsername("employee").orElseThrow().id();
    var contacts = data(mvc.perform(get("/api/chat/contacts").session(customer.session())).andExpect(status().isOk()).andReturn());
    assertTrue(contacts.size() > 0);
    assertFalse(contacts.get(0).has("phone"));
    long thread = ok(customer, "POST", "/api/chat/conversations", Map.of("peerId", employeeId)).get("id").asLong();
    assertEquals(thread, ok(customer, "POST", "/api/chat/conversations", Map.of("peerId", employeeId)).get("id").asLong());
    var input = Map.of("content", "  想咨询云南行程\n带小朋友方便吗？  ", "clientId", UUID.randomUUID().toString());
    var message = ok(customer, "POST", "/api/chat/conversations/" + thread + "/messages", input);
    assertEquals("想咨询云南行程\n带小朋友方便吗？", message.get("content").asText());
    assertEquals(message.get("id"), ok(customer, "POST", "/api/chat/conversations/" + thread + "/messages", input).get("id"));
    var list = data(mvc.perform(get("/api/chat/conversations").session(employee.session())).andExpect(status().isOk()).andReturn());
    assertEquals(1, list.get(0).get("unreadCount").asInt());
    var history = data(mvc.perform(get("/api/chat/conversations/" + thread + "/messages").session(employee.session())).andExpect(status().isOk()).andReturn());
    assertEquals(1, history.size());
    assertEquals(message.get("id"), history.get(0).get("id"));
    ok(employee, "POST", "/api/chat/conversations/" + thread + "/read", Map.of("messageId", message.get("id").asLong()));
    list = data(mvc.perform(get("/api/chat/conversations").session(employee.session())).andReturn());
    assertEquals(0, list.get(0).get("unreadCount").asInt());
    ok(employee, "POST", "/api/chat/conversations/" + thread + "/messages", Map.of("content", "可以，我来帮你安排。", "clientId", UUID.randomUUID().toString()));
    list = data(mvc.perform(get("/api/chat/conversations").session(customer.session())).andReturn());
    assertEquals(1, list.get(0).get("unreadCount").asInt());
  }

  @Test
  void chatRejectsOutsidersInvalidPairsAndInvalidMessages() throws Exception {
    var admin = login("admin");
    var customer = login("customer");
    newUser(admin, "chat_outsider", "CUSTOMER");
    var outsider = login("chat_outsider");
    long employeeId = users.byUsername("employee").orElseThrow().id();
    long customerId = users.byUsername("customer").orElseThrow().id();
    long thread = ok(customer, "POST", "/api/chat/conversations", Map.of("peerId", employeeId)).get("id").asLong();
    mvc.perform(get("/api/chat/conversations")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/chat/conversations").session(admin.session())).andExpect(status().isForbidden());
    mvc.perform(get("/api/chat/conversations/" + thread + "/messages").session(outsider.session())).andExpect(status().isForbidden());
    assertEquals(403, request(outsider, "POST", "/api/chat/conversations/" + thread + "/messages", Map.of("content", "窥探", "clientId", UUID.randomUUID().toString())).getResponse().getStatus());
    assertEquals(403, request(outsider, "POST", "/api/chat/conversations/" + thread + "/read", Map.of("messageId", 1)).getResponse().getStatus());
    assertEquals(403, request(outsider, "POST", "/api/chat/conversations", Map.of("peerId", customerId)).getResponse().getStatus());
    for (String content : List.of("   ", "x".repeat(2001))) {
      assertEquals(400, request(customer, "POST", "/api/chat/conversations/" + thread + "/messages", Map.of("content", content, "clientId", UUID.randomUUID().toString())).getResponse().getStatus());
    }
    mvc.perform(post("/api/chat/conversations/" + thread + "/messages").session(customer.session()).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
    assertEquals(400, request(customer, "POST", "/api/chat/conversations/" + thread + "/read", Map.of("messageId", Long.MAX_VALUE)).getResponse().getStatus());
    jdbc.update("UPDATE app_users SET enabled=FALSE WHERE id=?", employeeId);
    assertEquals(409, request(customer, "POST", "/api/chat/conversations/" + thread + "/messages", Map.of("content", "你好", "clientId", UUID.randomUUID().toString())).getResponse().getStatus());
  }

  @Test
  void chatHistoryPagesBackwardsAndReadCursorCannotHideFutureMessages() throws Exception {
    var customer = login("customer");
    var employee = login("employee");
    long employeeId = users.byUsername("employee").orElseThrow().id();
    long thread = ok(customer, "POST", "/api/chat/conversations", Map.of("peerId", employeeId)).get("id").asLong();
    long first = 0;
    for (int i = 0; i < 53; i++) {
      long id = ok(customer, "POST", "/api/chat/conversations/" + thread + "/messages", Map.of("content", "消息 " + i, "clientId", UUID.randomUUID().toString())).get("id").asLong();
      if (i == 0) first = id;
    }
    var latest = data(mvc.perform(get("/api/chat/conversations/" + thread + "/messages").session(employee.session())).andReturn());
    assertEquals(50, latest.size());
    var older = data(mvc.perform(get("/api/chat/conversations/" + thread + "/messages?beforeId=" + latest.get(0).get("id").asLong()).session(employee.session())).andReturn());
    assertEquals(3, older.size());
    assertEquals(first, older.get(0).get("id").asLong());
    long last = latest.get(49).get("id").asLong();
    ok(employee, "POST", "/api/chat/conversations/" + thread + "/read", Map.of("messageId", last));
    ok(employee, "POST", "/api/chat/conversations/" + thread + "/read", Map.of("messageId", first));
    var list = data(mvc.perform(get("/api/chat/conversations").session(employee.session())).andReturn());
    assertEquals(0, list.get(0).get("unreadCount").asInt());
    ok(customer, "POST", "/api/chat/conversations/" + thread + "/messages", Map.of("content", "新消息", "clientId", UUID.randomUUID().toString()));
    list = data(mvc.perform(get("/api/chat/conversations").session(employee.session())).andReturn());
    assertEquals(1, list.get(0).get("unreadCount").asInt());
  }

  @Test
  void dashboardAggregatesActualScopedBookings() throws Exception {
    var customer = login("customer");
    var result =
        data(
            mvc.perform(get("/api/dashboard").session(customer.session()))
                .andExpect(status().isOk())
                .andReturn());
    assertEquals(4, result.get("totalOrders").asInt());
    assertEquals(0, result.get("customerCount").asInt());
    assertEquals(6, result.get("monthlyOrders").size());
    assertEquals(11920, result.get("totalAmount").asInt());
    var admin = login("admin");
    mvc.perform(get("/api/dashboard").session(admin.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalOrders").value(8));
  }

  @Test
  void deletedAndRecreatedUsernameCannotReuseOldSession() throws Exception {
    var admin = login("admin");
    long oldId = newUser(admin, "recreated_user", "CUSTOMER");
    var old = login("recreated_user");
    ok(admin, "DELETE", "/api/users/" + oldId, Map.of());
    long newId = newUser(admin, "recreated_user", "EMPLOYEE");
    assertNotEquals(oldId, newId);
    mvc.perform(get("/api/auth/me").session(old.session())).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/auth/me").session(login("recreated_user").session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("EMPLOYEE"));
  }

  @Test
  void inactiveAndDepartedRoutesCannotBeBooked() throws Exception {
    var customer = login("customer");
    long rid = route(3);
    jdbc.update("UPDATE travel_routes SET active=FALSE WHERE id=?", rid);
    assertEquals(
        409, request(customer, "POST", "/api/orders", booking(rid, 1)).getResponse().getStatus());
    mvc.perform(get("/api/routes/" + rid).session(customer.session()))
        .andExpect(status().isNotFound());
    jdbc.update(
        "UPDATE travel_routes SET active=TRUE,start_date=? WHERE id=?",
        LocalDate.now().minusDays(1),
        rid);
    assertEquals(
        409, request(customer, "POST", "/api/orders", booking(rid, 1)).getResponse().getStatus());
  }

  @Test
  void passwordChangeRequiresCurrentPasswordAndUpdatesLogin() throws Exception {
    var c = login("customer");
    assertEquals(
        400,
        request(
                c,
                "PUT",
                "/api/account/password",
                Map.of("currentPassword", "wrong", "newPassword", "Changed@2026"))
            .getResponse()
            .getStatus());
    ok(
        c,
        "PUT",
        "/api/account/password",
        Map.of("currentPassword", "Travel@2026", "newPassword", "Changed@2026"));
    mvc.perform(get("/api/auth/me").session(c.session())).andExpect(status().isUnauthorized());
    var anon = anonymous();
    assertEquals(
        401,
        request(
                anon,
                "POST",
                "/api/auth/login",
                Map.of("username", "customer", "password", "Travel@2026"))
            .getResponse()
            .getStatus());
    ok(anon, "POST", "/api/auth/login", Map.of("username", "customer", "password", "Changed@2026"));
  }
}
