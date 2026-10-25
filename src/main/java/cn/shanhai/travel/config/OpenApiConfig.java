package cn.shanhai.travel.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 只声明文档元信息，接口本身由注解与 Spring MVC 映射自动推导，避免文档与实现脱节。
 *
 * <p>文档路径固定为 {@code /v3/api-docs}，经 context-path 后即 {@code /travel/v3/api-docs}。
 */
@Configuration
public class OpenApiConfig {
  @Bean
  OpenAPI travelOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("山海行旅行社管理系统 API")
                .version("2.0.0")
                .description(
                    "会话型 HTTP 接口：除登录、注册、CSRF 令牌与配置查询外，均需登录态；"
                        + "修改请求还需携带 X-CSRF-TOKEN。错误统一返回 {message} 与对应状态码。"));
  }
}
