package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;

/** 业务流程用例：公共装置（上下文、造数、登录、并发）见 {@link AbstractApiTest}。 */
class TravelIntegrationTest extends AbstractApiTest {
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
