package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 接口测试公共装置：上下文、演示数据重置、登录态与 CSRF 令牌管理、造数工具。
 *
 * <p>测试库是内存 H2 的 {@code traveltest}，每个用例前按外键顺序清空业务表再灌演示数据，保证用例互不依赖执行顺序。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"demo", "test"})
abstract class AbstractApiTest {
  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper json;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected DemoData demo;
  @Autowired protected RouteRepository routes;
  @Autowired protected UserRepository users;
  @Autowired protected OrderRepository orders;

  /** 一个已建立会话的客户端及其当前 CSRF 令牌，登录后令牌会轮换，所以要成对传递。 */
  protected record Client(MockHttpSession session, String csrf) {}

  @BeforeEach
  void resetDatabase() {
    // 事件表通过外键挂在 bookings 上，必须先清子表，否则重置顺序会被约束挡住。
    jdbc.update("DELETE FROM booking_events");
    jdbc.update("DELETE FROM bookings");
    jdbc.update("DELETE FROM travel_routes");
    jdbc.update("DELETE FROM app_users");
    demo.run(new DefaultApplicationArguments());
  }

  protected JsonNode data(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  protected Client anonymous() throws Exception {
    var r =
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/csrf"))
            .andExpect(status().isOk())
            .andReturn();
    return new Client((MockHttpSession) r.getRequest().getSession(), data(r).get("token").asText());
  }

  protected Client login(String username) throws Exception {
    var c = anonymous();
    var result =
        request(
            c, "POST", "/api/auth/login", Map.of("username", username, "password", "Travel@2026"));
    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    var next =
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/csrf").session(c.session()))
            .andExpect(status().isOk())
            .andReturn();
    return new Client(c.session(), data(next).get("token").asText());
  }

  protected MvcResult request(Client c, String method, String path, Object body) throws Exception {
    return mvc.perform(
            MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
                .session(c.session())
                .header("X-CSRF-TOKEN", c.csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andReturn();
  }

  protected JsonNode ok(Client c, String method, String path, Object body) throws Exception {
    var r = request(c, method, path, body);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return data(r);
  }

  protected long route(int capacity) {
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

  protected Map<String, Object> booking(long id, int count) {
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

  protected long createOrder(Client c, long route, int count) throws Exception {
    return ok(c, "POST", "/api/orders", booking(route, count)).get("id").asLong();
  }

  protected long newUser(Client admin, String name, String role) throws Exception {
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

  protected Api.RouteInput edit(long id, String price, int capacity, LocalDate departure) {
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

  /** 两个请求尽量同时起跑，用来验证数据库条件更新是否真的挡住了并发冲突。 */
  protected List<Integer> concurrent(Callable<Integer> one, Callable<Integer> two) throws Exception {
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
}
