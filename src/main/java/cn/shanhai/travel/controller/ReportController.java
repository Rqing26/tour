package cn.shanhai.travel.controller;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.service.ReportService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ReportController {
  private final ReportService reports;

  public ReportController(ReportService reports) {
    this.reports = reports;
  }

  /**
   * 报表汇总。统计范围由会话角色决定（见 {@link ReportService}），请求只能调月份窗口，
   * 不能指定看谁的数字；{@code months} 缺省 6，超出 1–24 的按边界生效。
   */
  @GetMapping("/reports/summary")
  public Api.ReportSummary summary(@RequestParam(required = false) Integer months) {
    return reports.summary(months);
  }
}
