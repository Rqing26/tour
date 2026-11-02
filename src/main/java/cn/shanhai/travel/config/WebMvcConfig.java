package cn.shanhai.travel.config;

import cn.shanhai.travel.service.ImageStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 上传图片的静态访问入口。
 *
 * <p>映射到 {@code travel.upload.dir} 指向的目录，**不指向** {@code src/main/resources/static}：
 * 后者是构建产物（C8、C14），重新打包会被清空，上传的图片放进去等于每次构建都丢图。
 * 根目录与上传服务共用同一个解析结果（{@link ImageStorageService#root()}），两处不会各拼一次相对路径。
 *
 * <p>Spring 的资源处理器自身会拒绝 {@code ..} 与目录外的路径，越界请求按 404 处理。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
  private final ImageStorageService images;

  public WebMvcConfig(ImageStorageService images) {
    this.images = images;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/uploads/**").addResourceLocations(location());
  }

  /** {@code Path.toUri()} 只有在目录已存在时才带结尾斜杠，这里统一补齐，否则解析会退到上一级目录。 */
  private String location() {
    var uri = images.root().toUri().toString();
    return uri.endsWith("/") ? uri : uri + "/";
  }
}
