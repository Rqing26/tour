package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/**
 * 契约与探活入口：接口文档必须能独立于登录态产出，健康检查必须能在容器编排中作为就绪探针使用。
 *
 * <p>两者都属于运维/联调入口，因此断言的是"内容对得上"，而不只是"没报错"。上下文与演示数据装置见
 * {@link AbstractApiTest}。
 */
class ApiDocsTest extends AbstractApiTest {
  @Test
  void openApiDocumentDescribesExistingEndpoints() throws Exception {
    var body =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openapi").exists())
            .andExpect(jsonPath("$.info.title").value("山海行旅行社管理系统 API"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode doc = json.readTree(body);
    assertTrue(doc.get("openapi").asText().startsWith("3."), "应为 OpenAPI 3 文档");
    assertNotNull(doc.at("/paths/~1api~1orders"), "订单接口应出现在文档里");
    assertNotNull(doc.at("/paths/~1api~1routes~1{id}"), "线路详情接口应出现在文档里");
    assertNotNull(doc.at("/components/schemas/Api.Route"), "响应模型应被登记为 schema");
  }

  @Test
  void healthEndpointReportsUpWithoutAuthentication() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void swaggerUiIsServedWithoutAuthentication() throws Exception {
    var response = mvc.perform(get("/swagger-ui/index.html")).andReturn().getResponse();
    assertEquals(200, response.getStatus());
    assertTrue(
        response.getContentAsString().contains("Swagger UI"), "应返回 Swagger UI 页面而非登录页");
  }
}
