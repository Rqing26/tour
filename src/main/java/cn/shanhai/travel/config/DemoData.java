package cn.shanhai.travel.config;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.model.Role;
import cn.shanhai.travel.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Isolated demo-only seed. Never runs against the mysql profile. */
@Component
@Profile("demo & !mysql")
public class DemoData implements ApplicationRunner {
  private final UserRepository users;
  private final RouteRepository routes;
  private final OrderRepository orders;
  private final PasswordEncoder passwords;
  private final JdbcTemplate jdbc;

  public DemoData(
      UserRepository users,
      RouteRepository routes,
      OrderRepository orders,
      PasswordEncoder passwords,
      JdbcTemplate jdbc) {
    this.users = users;
    this.routes = routes;
    this.orders = orders;
    this.passwords = passwords;
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!users.list(null, null).isEmpty()) return;
    String hash = passwords.encode("Travel@2026");
    users.create("admin", "运营管理员", "" + hash, "13800000001", Role.ADMIN);
    long employee = users.create("employee", "旅行顾问 · 小夏", hash, "13800000002", Role.EMPLOYEE);
    long customer = users.create("customer", "旅行者 · 小林", hash, "13800000003", Role.CUSTOMER);
    long guest = users.create("demo_guest", "旅行者 · 小周", hash, "13800000004", Role.CUSTOMER);
    var ids = new ArrayList<Long>();
    ids.add(
        route(
            "桂林山水 · 漓江慢游",
            "广西 · 桂林",
            "沿漓江而下，在阳朔的青山与田野之间，留一段轻盈的假期。含当地接送、精选住宿与向导服务。",
            14,
            4,
            "1680",
            24,
            "guilin",
            List.of("漓江游船", "阳朔骑行", "龙脊梯田")));
    ids.add(
        route(
            "云南寻光 · 大理丽江",
            "云南 · 大理",
            "从洱海日出到雪山脚下，走进滇西的风、古城与日常。含当地交通、精选住宿与向导服务。",
            21,
            6,
            "3290",
            20,
            "yunnan",
            List.of("洱海环线", "丽江古城", "玉龙雪山")));
    ids.add(
        route(
            "新疆旷野 · 北疆环线",
            "新疆 · 伊犁",
            "穿过草原、公路与雪山，遇见开阔的北疆。小团出行，含当地交通、住宿与向导服务。",
            35,
            8,
            "5680",
            16,
            "xinjiang",
            List.of("赛里木湖", "那拉提草原", "独库公路")));
    ids.add(
        route(
            "成都烟火 · 熊猫之约",
            "四川 · 成都",
            "慢下来，逛街巷、看熊猫，在一碗盖碗茶里认识成都。含精选住宿、当地交通与向导服务。",
            10,
            3,
            "1280",
            28,
            "chengdu",
            List.of("熊猫基地", "宽窄巷子", "都江堰")));
    ids.add(
        route(
            "厦门海风 · 岛屿来信",
            "福建 · 厦门",
            "把步调交给海风，在鼓浪屿和环岛路之间寻找南方的松弛感。含精选住宿与当地向导服务。",
            18,
            4,
            "1980",
            24,
            "xiamen",
            List.of("鼓浪屿漫步", "环岛骑行", "沙坡尾")));
    ids.add(
        route(
            "杭州听雨 · 江南小住",
            "浙江 · 杭州",
            "西湖、茶山与水乡，一趟不赶路的江南短途。含精选住宿、当地交通与向导服务。",
            28,
            3,
            "1480",
            22,
            "hangzhou",
            List.of("西湖漫步", "龙井问茶", "南浔古镇")));
    seedOrder(customer, employee, ids.get(0), 2, "ASSIGNED", 0);
    seedOrder(customer, null, ids.get(1), 2, "PENDING", 0);
    seedOrder(guest, null, ids.get(2), 3, "PENDING", 0);
    seedOrder(guest, employee, ids.get(3), 2, "COMPLETED", 1);
    seedOrder(customer, employee, ids.get(4), 1, "COMPLETED", 2);
    seedOrder(guest, null, ids.get(5), 2, "PENDING", 1);
    seedOrder(customer, null, ids.get(3), 1, "CANCELLED", 3);
    seedOrder(guest, employee, ids.get(1), 2, "COMPLETED", 4);
  }

  private long route(
      String name,
      String destination,
      String description,
      int days,
      int duration,
      String price,
      int capacity,
      String image,
      List<String> highlights) {
    return routes.create(
        new Api.RouteInput(
            name,
            destination,
            description,
            LocalDate.now().plusDays(days),
            duration,
            new BigDecimal(price),
            capacity,
            true,
            image,
            highlights));
  }

  private void seedOrder(
      long customer, Long employee, long routeId, int count, String status, int monthsAgo) {
    var r = routes.find(routeId).orElseThrow();
    var u = users.byId(customer).orElseThrow();
    long id =
        orders.create(
            customer,
            r,
            new Api.BookingInput(routeId, count, u.displayName(), u.phone(), "演示订单，用于体验业务流程"));
    orders.transition(id, status, employee);
    jdbc.update(
        "UPDATE bookings SET created_at=? WHERE id=?",
        LocalDateTime.now().minusMonths(monthsAgo).minusDays(2),
        id);
  }
}
