package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * 客户端请求本身用错协议时的状态码：方法不对该给 405，格式不支持该给 415。
 *
 * <p>这两类异常由 Spring MVC 在进入控制器之前抛出，此前没有专门的翻译规则，全部落进 {@code Exception} 兜底变成 500，
 * 于是"你用了错误的方法"被说成"服务端出错"，调用方会误以为该重试。用例只断言状态码与可读文案，不锁死具体措辞。
 */
class ApiErrorStatusTest extends AbstractApiTest {

  private String messageOf(int status, String body) {
    assertNotNull(body, "错误响应必须有消息体");
    var node = assertDoesNotThrow(() -> json.readTree(body));
    assertTrue(node.hasNonNull("message"), "错误响应必须是 {message} 形状：" + body);
    return node.get("message").asText();
  }

  /** 接单只接受 POST；用 GET 打同一个路径时，路径存在但方法不对，应是 405 而不是 500。 */
  @Test
  void wrongMethodOnExistingPathReturns405() throws Exception {
    var employee = login("employee");

    var r =
        mvc.perform(get("/api/orders/1/claim").session(employee.session()))
            .andReturn();

    assertEquals(405, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    var body = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertFalse(messageOf(405, body).isBlank(), "405 也要给出可读原因");
  }

  /** 登录接口只接受 GET；POST 到 /auth/csrf 同样是方法不对。 */
  @Test
  void wrongMethodOnCsrfReturns405() throws Exception {
    var customer = login("customer");

    var r =
        mvc.perform(
                post("/api/auth/csrf")
                    .session(customer.session())
                    .header("X-CSRF-TOKEN", customer.csrf()))
            .andReturn();

    assertEquals(405, r.getResponse().getStatus(), r.getResponse().getContentAsString());
  }

  /** 下单只吃 JSON；别的 Content-Type 属于格式不支持，应是 415。 */
  @Test
  void unsupportedContentTypeReturns415() throws Exception {
    var customer = login("customer");

    var r =
        mvc.perform(
                post("/api/orders")
                    .session(customer.session())
                    .header("X-CSRF-TOKEN", customer.csrf())
                    .contentType("text/plain")
                    .content("routeId=1"))
            .andReturn();

    assertEquals(415, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    var body = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertTrue(messageOf(415, body).contains("json"), "文案应提示该用什么格式：" + body);
  }

  /** 同一条路径上 JSON 解析失败仍归 400，别被新加的 415 规则抢走。 */
  @Test
  void malformedJsonStays400() throws Exception {
    var customer = login("customer");

    var r =
        mvc.perform(
                post("/api/orders")
                    .session(customer.session())
                    .header("X-CSRF-TOKEN", customer.csrf())
                    .contentType("application/json")
                    .content("{not json"))
            .andReturn();

    assertEquals(400, r.getResponse().getStatus(), r.getResponse().getContentAsString());
  }
}
