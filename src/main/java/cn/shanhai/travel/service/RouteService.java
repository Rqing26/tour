package cn.shanhai.travel.service;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.model.Role;
import cn.shanhai.travel.repository.RouteRepository;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RouteService {
  private final RouteRepository routes;
  private final UserService users;

  public RouteService(RouteRepository routes, UserService users) {
    this.routes = routes;
    this.users = users;
  }

  public List<Api.Route> list(String q, String destination, Boolean active) {
    var customer = users.current().role() == Role.CUSTOMER;
    return routes.list().stream()
        .filter(r -> !customer || r.active())
        .filter(r -> active == null || r.active() == active)
        .filter(
            r ->
                destination == null
                    || destination.isBlank()
                    || r.destination().contains(destination))
        .filter(
            r ->
                q == null
                    || (r.name() + " " + r.destination() + " " + r.description())
                        .toLowerCase(Locale.ROOT)
                        .contains(q.toLowerCase(Locale.ROOT)))
        .toList();
  }

  public Api.Route get(long id) {
    var r = routes.find(id).orElseThrow(() -> ApiException.missing("线路不存在"));
    if (users.current().role() == Role.CUSTOMER && !r.active()) throw ApiException.missing("线路已下架");
    return r;
  }

  @Transactional
  public Api.Route create(Api.RouteInput d) {
    if (!d.startDate().isAfter(LocalDate.now())) throw ApiException.invalid("新线路出发日期必须在今天之后");
    return routes.find(routes.create(d)).orElseThrow();
  }

  @Transactional
  public Api.Route update(long id, Api.RouteInput d) {
    var old = routes.lock(id).orElseThrow(() -> ApiException.missing("线路不存在"));
    int sold = old.capacity() - old.availableSeats();
    if (d.capacity() < sold) throw ApiException.conflict("容量不能低于已预订人数（" + sold + " 人）");
    boolean changed =
        !old.startDate().equals(d.startDate())
            || old.durationDays() != d.durationDays()
            || !old.destination().equals(d.destination().trim())
            || !old.name().equals(d.name().trim());
    if (sold > 0 && changed) throw ApiException.conflict("已有有效订单，不能修改线路名称、目的地、出发日期或行程天数；请新建线路");
    if (!old.startDate().equals(d.startDate()) && !d.startDate().isAfter(LocalDate.now()))
      throw ApiException.invalid("新出发日期必须在今天之后");
    routes.update(id, d);
    return routes.find(id).orElseThrow();
  }

  @Transactional
  public void delete(long id) {
    routes.lock(id).orElseThrow(() -> ApiException.missing("线路不存在"));
    if (routes.bookings(id) > 0) throw ApiException.conflict("此线路已关联订单，请改为下架线路以保留记录");
    routes.delete(id);
  }
}
