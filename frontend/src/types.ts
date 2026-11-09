export type Role = "ADMIN" | "EMPLOYEE" | "CUSTOMER";
export type OrderStatus = "PENDING" | "ASSIGNED" | "COMPLETED" | "CANCELLED";
export interface User {
  id: number;
  username: string;
  displayName: string;
  phone: string;
  role: Role;
  enabled: boolean;
}
export interface Route {
  id: number;
  name: string;
  destination: string;
  description: string;
  startDate: string;
  durationDays: number;
  price: number;
  capacity: number;
  availableSeats: number;
  active: boolean;
  imageKey: string;
  highlights: string[];
  imagePath?: string | null;
}
export type RouteInput = Omit<Route, "id" | "availableSeats" | "imagePath">;
export interface Order {
  id: number;
  orderNo: string;
  customerId: number;
  customerName: string;
  customerPhone: string;
  employeeId: number | null;
  employeeName: string | null;
  routeId: number;
  routeName: string;
  destination: string;
  startDate: string;
  durationDays: number;
  travelerCount: number;
  unitPrice: number;
  totalAmount: number;
  status: OrderStatus;
  createdAt: string;
  contactName: string;
  contactPhone: string;
  remark: string;
  /** 下单时的优惠码快照；没用优惠码为 null（旧订单也是）。 */
  couponCode?: string | null;
  /** 下单时的减免额快照，没用优惠码为 0。 */
  discountAmount?: number;
}
export interface OrderEvent {
  id: number;
  bookingId: number;
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  actorId: number;
  actorName: string;
  actorRole: Role;
  note: string;
  createdAt: string;
}
export interface DashboardData {
  totalOrders: number;
  pendingOrders: number;
  assignedOrders: number;
  completedOrders: number;
  cancelledOrders: number;
  totalAmount: number;
  completedAmount: number;
  activeRoutes: number;
  customerCount: number;
  employeeCount: number;
  recentOrders: Order[];
  popularRoutes: {
    routeId: number;
    name: string;
    destination: string;
    orderCount: number;
    travelerCount: number;
  }[];
  monthlyOrders: { month: string; count: number }[];
}
export type View =
  "dashboard" | "routes" | "orders" | "reports" | "users" | "account";
/** 月度报表行：缺月由服务端补 0，前端不必处理空洞。 */
export interface MonthlyReport {
  month: string;
  orders: number;
  revenue: number;
}
export interface PopularRoute {
  routeId: number;
  name: string;
  destination: string;
  orderCount: number;
  travelerCount: number;
}
export interface EmployeeRank {
  employeeId: number;
  employeeName: string;
  orderCount: number;
  amount: number;
}
/** 报表汇总：`scope` 是这份数字的口径，不是请求参数。 */
export interface ReportSummary {
  scope: Role;
  totalOrders: number;
  pendingOrders: number;
  assignedOrders: number;
  completedOrders: number;
  cancelledOrders: number;
  totalAmount: number;
  completedAmount: number;
  monthly: MonthlyReport[];
  popularRoutes: PopularRoute[];
  employeeRank: EmployeeRank[];
}
export type Notify = (message: string, kind?: "success" | "error") => void;
