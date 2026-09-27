package cn.shanhai.travel.model;

import cn.shanhai.travel.dto.Api;

public record Account(
    long id,
    String username,
    String displayName,
    String phone,
    Role role,
    boolean enabled,
    String passwordHash) {
  public Api.User view() {
    return new Api.User(id, username, displayName, phone, role, enabled);
  }
}
