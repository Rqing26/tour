package cn.shanhai.travel.service;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.model.Role;
import cn.shanhai.travel.repository.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.*;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
  private final OrderService orders;
  private final UserService users;
  private final UserRepository accounts;
  private final RouteRepository routes;

  public DashboardService(
      OrderService orders, UserService users, UserRepository accounts, RouteRepository routes) {
    this.orders = orders;
    this.users = users;
    this.accounts = accounts;
    this.routes = routes;
  }

  public Api.Dashboard get() {
    var a = users.current();
    var all = orders.list(null, null);
    var valid = all.stream().filter(o -> !o.status().equals("CANCELLED")).toList();
    var popular =
        valid.stream().collect(Collectors.groupingBy(Api.Order::routeId)).values().stream()
            .map(
                os -> {
                  var r = os.getFirst();
                  return new Api.PopularRoute(
                      r.routeId(),
                      r.routeName(),
                      r.destination(),
                      os.size(),
                      os.stream().mapToInt(Api.Order::travelerCount).sum());
                })
            .sorted(
                Comparator.comparingLong(Api.PopularRoute::orderCount)
                    .reversed()
                    .thenComparingLong(Api.PopularRoute::routeId))
            .limit(5)
            .toList();
    var months =
        IntStream.rangeClosed(0, 5)
            .mapToObj(i -> YearMonth.now().minusMonths(5 - i))
            .map(
                m ->
                    new Api.MonthCount(
                        m.toString(),
                        all.stream().filter(o -> YearMonth.from(o.createdAt()).equals(m)).count()))
            .toList();
    return new Api.Dashboard(
        all.size(),
        count(all, "PENDING"),
        count(all, "ASSIGNED"),
        count(all, "COMPLETED"),
        count(all, "CANCELLED"),
        valid.stream().map(Api.Order::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
        all.stream()
            .filter(o -> o.status().equals("COMPLETED"))
            .map(Api.Order::totalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add),
        routes.list().stream().filter(Api.Route::active).count(),
        a.role() == Role.ADMIN ? accounts.count(Role.CUSTOMER) : 0,
        a.role() == Role.ADMIN ? accounts.count(Role.EMPLOYEE) : 0,
        all.stream().limit(5).toList(),
        popular,
        months);
  }

  private long count(List<Api.Order> all, String status) {
    return all.stream().filter(o -> o.status().equals(status)).count();
  }
}
