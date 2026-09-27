CREATE TABLE app_users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(40) NOT NULL UNIQUE,
  display_name VARCHAR(60) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  phone VARCHAR(25) NOT NULL,
  role VARCHAR(20) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_user_role CHECK (role IN ('ADMIN','EMPLOYEE','CUSTOMER'))
);
CREATE TABLE travel_routes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  destination VARCHAR(60) NOT NULL,
  description VARCHAR(2000) NOT NULL,
  start_date DATE NOT NULL,
  duration_days INT NOT NULL,
  price DECIMAL(12,2) NOT NULL,
  capacity INT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  image_key VARCHAR(30) NOT NULL,
  highlights VARCHAR(2000) NOT NULL,
  CONSTRAINT ck_route_price CHECK (price > 0),
  CONSTRAINT ck_route_capacity CHECK (capacity > 0),
  CONSTRAINT ck_route_duration CHECK (duration_days > 0)
);
CREATE TABLE bookings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  employee_id BIGINT,
  route_id BIGINT NOT NULL,
  traveler_count INT NOT NULL,
  unit_price DECIMAL(12,2) NOT NULL,
  total_amount DECIMAL(14,2) NOT NULL,
  status VARCHAR(20) NOT NULL,
  contact_name VARCHAR(60) NOT NULL,
  contact_phone VARCHAR(25) NOT NULL,
  remark VARCHAR(500) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_booking_customer FOREIGN KEY (customer_id) REFERENCES app_users(id),
  CONSTRAINT fk_booking_employee FOREIGN KEY (employee_id) REFERENCES app_users(id),
  CONSTRAINT fk_booking_route FOREIGN KEY (route_id) REFERENCES travel_routes(id),
  CONSTRAINT ck_booking_status CHECK (status IN ('PENDING','ASSIGNED','COMPLETED','CANCELLED')),
  CONSTRAINT ck_booking_travelers CHECK (traveler_count > 0),
  CONSTRAINT ck_booking_price CHECK (unit_price > 0 AND total_amount > 0)
);
CREATE INDEX idx_booking_customer ON bookings(customer_id);
CREATE INDEX idx_booking_employee_status ON bookings(employee_id, status);
CREATE INDEX idx_booking_route_status ON bookings(route_id, status);
CREATE INDEX idx_route_departure ON travel_routes(active, start_date);
