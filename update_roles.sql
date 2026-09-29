INSERT INTO staff_roles (role_id, role_name, permissions, created_at, updated_at) VALUES 
('ACCOUNTANT', 'Kế toán', '["FINANCE_MANAGE", "USER_VIEW"]', '2026-09-24 00:00:00', '2026-09-24 00:00:00'),
('TREASURER', 'Thủ quỹ', '["FINANCE_APPROVE", "USER_VIEW"]', '2026-09-24 00:00:00', '2026-09-24 00:00:00');

INSERT INTO users (user_id, role_id, email, password_hash, full_name, user_type, is_first_login, created_at, updated_at) VALUES 
('00000000-0000-0000-0000-000000000004', 'ACCOUNTANT', 'accountant@huit.edu.vn', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HCGFGLn14HcoG7bE6/Wl6', 'Nguyễn Thị Kế Toán', 'STAFF', false, '2026-09-24 00:00:00', '2026-09-24 00:00:00'),
('00000000-0000-0000-0000-000000000005', 'TREASURER', 'treasurer@huit.edu.vn', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HCGFGLn14HcoG7bE6/Wl6', 'Lê Văn Thủ Quỹ', 'STAFF', false, '2026-09-24 00:00:00', '2026-09-24 00:00:00');
