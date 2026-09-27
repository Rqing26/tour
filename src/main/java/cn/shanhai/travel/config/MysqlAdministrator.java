package cn.shanhai.travel.config;

import cn.shanhai.travel.model.Role;
import cn.shanhai.travel.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("mysql")
public class MysqlAdministrator implements ApplicationRunner {
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final Environment env;

  public MysqlAdministrator(UserRepository users, PasswordEncoder encoder, Environment env) {
    this.users = users;
    this.encoder = encoder;
    this.env = env;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.count(Role.ADMIN) > 0) return;
    String username = env.getProperty("TRAVEL_ADMIN_USERNAME");
    String password = env.getProperty("TRAVEL_ADMIN_PASSWORD");
    if (username == null
        || !username.matches("[a-zA-Z0-9_]{3,40}")
        || password == null
        || password.length() < 12
        || password.getBytes(StandardCharsets.UTF_8).length > 72
        || !password.matches("(?s).*[a-z].*")
        || !password.matches("(?s).*[A-Z].*")
        || !password.matches("(?s).*\\d.*")
        || !password.matches("(?s).*[^a-zA-Z0-9].*")) {
      throw new IllegalStateException(
          "首次 MySQL 启动需设置 TRAVEL_ADMIN_USERNAME 与 TRAVEL_ADMIN_PASSWORD（至少 12 位，包含大小写字母、数字及符号，至多 72"
              + " 个 UTF-8 字节）");
    }
    users.create(username, "管理员", encoder.encode(password), "00000000000", Role.ADMIN);
  }
}
