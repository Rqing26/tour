package cn.shanhai.travel.model;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Stable account identity prevents old sessions from inheriting a recreated username. */
public record AccountPrincipal(
    long id, String username, String password, Role role, boolean enabled) implements UserDetails {
  public static AccountPrincipal from(Account account) {
    return new AccountPrincipal(
        account.id(),
        account.username(),
        account.passwordHash(),
        account.role(),
        account.enabled());
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }
}
