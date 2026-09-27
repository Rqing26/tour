package cn.shanhai.travel.service;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.model.*;
import cn.shanhai.travel.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
  private final UserRepository users;
  private final PasswordEncoder passwords;

  public UserService(UserRepository users, PasswordEncoder passwords) {
    this.users = users;
    this.passwords = passwords;
  }

  public Account current() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof AccountPrincipal principal))
      throw ApiException.forbidden();
    var user = users.byUsername(auth.getName()).orElseThrow(ApiException::forbidden);
    if (!user.enabled()
        || user.id() != principal.id()
        || !user.passwordHash().equals(principal.password())) throw ApiException.forbidden();
    return user;
  }

  public Account lockCurrent() {
    var a = users.lock(current().id()).orElseThrow(ApiException::forbidden);
    if (!a.enabled()) throw ApiException.forbidden();
    return a;
  }

  private void validPassword(String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw ApiException.invalid("密码的 UTF-8 长度不能超过 72 字节");
  }

  @Transactional
  public Api.User register(Api.Register d) {
    return create(d.username(), d.displayName(), d.password(), d.phone(), Role.CUSTOMER);
  }

  @Transactional
  public Api.User create(Api.CreateUser d) {
    if (d.role() == Role.ADMIN) throw ApiException.invalid("不能通过此接口创建管理员");
    return create(d.username(), d.displayName(), d.password(), d.phone(), d.role());
  }

  private Api.User create(String username, String name, String password, String phone, Role role) {
    validPassword(password);
    if (users.byUsername(username).isPresent()) throw ApiException.conflict("此用户名已被使用");
    return users
        .byId(users.create(username, name, passwords.encode(password), phone, role))
        .orElseThrow()
        .view();
  }

  public List<Api.User> list(String q, Role role) {
    return users.list(q, role);
  }

  @Transactional
  public Api.User profile(Api.Profile d) {
    var a = lockCurrent();
    users.update(a.id(), d.displayName(), d.phone(), true);
    return users.byId(a.id()).orElseThrow().view();
  }

  @Transactional
  public void password(Api.Password d) {
    var a = lockCurrent();
    validPassword(d.newPassword());
    if (!passwords.matches(d.currentPassword(), a.passwordHash()))
      throw ApiException.invalid("当前密码不正确");
    users.password(a.id(), passwords.encode(d.newPassword()));
  }

  @Transactional
  public Api.User update(long id, Api.UpdateUser d) {
    var actor = current();
    var account = users.lock(id).orElseThrow(() -> ApiException.missing("用户不存在"));
    if (!d.enabled()) {
      if (actor.id() == id || account.role() == Role.ADMIN)
        throw ApiException.conflict("不能停用当前账号或管理员账号");
      if (users.hasAssigned(id)) throw ApiException.conflict("员工仍有待完成订单，请先完成或释放订单");
    }
    users.update(id, d.displayName(), d.phone(), d.enabled());
    return users.byId(id).orElseThrow().view();
  }

  @Transactional
  public void delete(long id) {
    var actor = current();
    var account = users.lock(id).orElseThrow(() -> ApiException.missing("用户不存在"));
    if (actor.id() == id || account.role() == Role.ADMIN)
      throw ApiException.conflict("不能删除当前账号或管理员账号");
    if (users.hasBookings(id)) throw ApiException.conflict("用户已关联订单，请改为停用账号以保留记录");
    users.delete(id);
  }
}
