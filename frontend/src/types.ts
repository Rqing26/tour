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
}
export type RouteInput = Omit<Route, "id" | "availableSeats">;
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
export type View = "dashboard" | "routes" | "orders" | "users" | "account";
export type Notify = (message: string, kind?: "success" | "error") => void;
