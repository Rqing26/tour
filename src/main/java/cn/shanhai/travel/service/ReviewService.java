package cn.shanhai.travel.service;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.model.Role;
import cn.shanhai.travel.repository.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评价提交：只有"下单客户本人 + 已完成订单"可以评，一笔订单只评一次。
 *
 * <p>三道判断的顺序是有意的：先看角色与归属，再看状态。别人的订单不该因为"还没完成"而透露状态；
 * 重复评价则交给唯一键，服务层只把冲突翻成 409——先查再插会留下并发窗口。
 */
@Service
public class ReviewService {
  private final ReviewRepository reviews;
  private final OrderRepository orders;
  private final UserService users;

  public ReviewService(ReviewRepository reviews, OrderRepository orders, UserService users) {
    this.reviews = reviews;
    this.orders = orders;
    this.users = users;
  }

  @Transactional
  public Api.Review submit(long bookingId, Api.ReviewInput d) {
    var a = users.lockCurrent();
    if (a.role() != Role.CUSTOMER) throw ApiException.forbidden();
    var o = orders.find(bookingId).orElseThrow(() -> ApiException.missing("订单不存在"));
    if (o.customerId() != a.id()) throw ApiException.forbidden();
    // 行程没走完就没有可评的体验，也不该让未成行订单拉高线路均分。
    if (!o.status().equals("COMPLETED")) throw ApiException.forbidden("只有已完成的订单可以评价");
    try {
      long id = reviews.create(bookingId, o.routeId(), a.id(), d);
      return reviews.find(id).orElseThrow();
    } catch (DuplicateKeyException e) {
      // 唯一键是唯一闸门：两个请求同时提交时，输的那个在这里拿到 409，而不是 500。
      throw ApiException.conflict("这笔订单已经评价过了");
    }
  }
}
