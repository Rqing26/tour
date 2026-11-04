package cn.shanhai.travel.service;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.model.Account;
import cn.shanhai.travel.model.Role;
import cn.shanhai.travel.repository.ReportRepository;
import cn.shanhai.travel.repository.ReportRepository.Scope;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 报表口径与月度补齐：把"谁能看哪些订单"和"缺月补零"这两件事收在一处，仓储只负责聚合。
 *
 * <p>两个范围是有意分开的：
 *
 * <ul>
 *   <li>{@link #visibleScope} 与订单列表一致，员工的待接池也算在内——工作台要能看见可接的单；
 *   <li>{@link #ownedScope} 只算已归属到本人名下的订单——待接池没有负责人，不能算进任何人的业绩。
 * </ul>
 */
@Service
public class ReportService {
  /** 默认看最近 6 个月，最多 24 个月：更长的窗口在页面上读不出趋势，还会把响应撑大。 */
  static final int DEFAULT_MONTHS = 6;

  static final int MAX_MONTHS = 24;

  /** 热门线路与员工排行各取前 5 名。 */
  static final int TOP = 5;

  private final ReportRepository reports;
  private final UserService users;

  public ReportService(ReportRepository reports, UserService users) {
    this.reports = reports;
    this.users = users;
  }

  /** 当前登录人的报表，口径由角色决定。 */
  public Api.ReportSummary summary(Integer months) {
    return summary(users.current(), months);
  }

  /** 指定账号的报表：工作台与测试都从这里进，不必伪造登录上下文。 */
  public Api.ReportSummary summary(Account account, Integer months) {
    var scope = ownedScope(account);
    var totals = reports.totals(scope);
    return new Api.ReportSummary(
        account.role().name(),
        totals.total(),
        totals.pending(),
        totals.assigned(),
        totals.completed(),
        totals.cancelled(),
        totals.amount(),
        totals.completedAmount(),
        monthly(scope, months),
        reports.popularRoutes(scope, TOP),
        rank(account));
  }

  /** 报表口径：管理员全量，客户按自己的订单，员工按自己经手的订单。 */
  public Scope ownedScope(Account account) {
    return switch (account.role()) {
      case ADMIN -> Scope.ALL;
      case CUSTOMER -> Scope.customer(account.id());
      case EMPLOYEE -> Scope.ownedBy(account.id());
    };
  }

  /** 工作台口径：与订单列表的可见范围一致，员工的待接池也计入。 */
  public Scope visibleScope(Account account) {
    return switch (account.role()) {
      case ADMIN -> Scope.ALL;
      case CUSTOMER -> Scope.customer(account.id());
      case EMPLOYEE -> Scope.visibleTo(account.id());
    };
  }

  public ReportRepository.Totals totals(Scope scope) {
    return reports.totals(scope);
  }

  public List<Api.PopularRoute> popular(Scope scope, int limit) {
    return reports.popularRoutes(scope, limit);
  }

  public long activeRoutes() {
    return reports.activeRoutes();
  }

  /**
   * 月度序列：窗口内每个月都返回一行，没有订单的月记 0。
   *
   * <p>口径外的月份在 SQL 里根本不存在，所以补零只能在服务层做；补齐后前端画折线不用再处理缺口。
   */
  public List<Api.MonthlyReport> monthly(Scope scope, Integer months) {
    int window = normalize(months);
    var to = YearMonth.now();
    var from = to.minusMonths(window - 1L);
    var rows =
        reports.monthly(scope, from, to).stream()
            .collect(Collectors.toMap(Api.MonthlyReport::month, m -> m, (a, b) -> b));
    var filled = new ArrayList<Api.MonthlyReport>(window);
    for (int i = 0; i < window; i++) {
      String key = from.plusMonths(i).toString();
      filled.add(rows.getOrDefault(key, new Api.MonthlyReport(key, 0, BigDecimal.ZERO)));
    }
    return filled;
  }

  /** 窗口长度归一：缺省 6 个月，下限 1、上限 24，超出压到边界而不是报错。 */
  static int normalize(Integer months) {
    int wanted = months == null ? DEFAULT_MONTHS : months;
    return Math.min(Math.max(wanted, 1), MAX_MONTHS);
  }

  /** 员工排行是团队口径，只有管理员看得到全貌；其他人返回空列表，页面据此隐藏该区块。 */
  private List<Api.EmployeeRank> rank(Account account) {
    return account.role() == Role.ADMIN
        ? reports.employeeRank(TOP)
        : Collections.emptyList();
  }
}
