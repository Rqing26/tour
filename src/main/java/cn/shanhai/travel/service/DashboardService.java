package cn.shanhai.travel.service;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.model.Role;
import cn.shanhai.travel.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * 工作台：把订单与人员统计交给 SQL 聚合，只在内存里做"拼装"。
 *
 * <p>口径沿用订单列表的可见范围（{@link ReportService#visibleScope}）：管理员看全量，客户看自己的订单，
 * 员工看"待接池 + 自己名下"。所以换成聚合实现后，三种角色看到的数字与改之前完全一致。
 */
@Service
public class DashboardService {
  /** 工作台固定看最近 6 个月，与报表页的可调窗口不同。 */
  private static final int MONTHS = 6;

  /** 热门线路与最近订单各取 5 条。 */
  private static final int TOP = 5;

  private final OrderService orders;
  private final UserService users;
  private final UserRepository accounts;
  private final ReportService reports;

  public DashboardService(
      OrderService orders, UserService users, UserRepository accounts, ReportService reports) {
    this.orders = orders;
    this.users = users;
    this.accounts = accounts;
    this.reports = reports;
  }

  public Api.Dashboard get() {
    var a = users.current();
    var scope = reports.visibleScope(a);
    var totals = reports.totals(scope);
    return new Api.Dashboard(
        totals.total(),
        totals.pending(),
        totals.assigned(),
        totals.completed(),
        totals.cancelled(),
        totals.amount(),
        totals.completedAmount(),
        reports.activeRoutes(),
        a.role() == Role.ADMIN ? accounts.count(Role.CUSTOMER) : 0,
        a.role() == Role.ADMIN ? accounts.count(Role.EMPLOYEE) : 0,
        orders.recent(TOP),
        reports.popular(scope, TOP),
        // 工作台沿用既有的 {month,count} 形状（C4 不得删改字段）；营收只在报表页出现。
        reports.monthly(scope, MONTHS).stream()
            .map(m -> new Api.MonthCount(m.month(), m.orders()))
            .toList());
  }
}
