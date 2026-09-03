-- Tài khoản demo cho môi trường dev/thử nghiệm cục bộ, KHÔNG phải tài khoản thật.
-- Mật khẩu gốc: admin123! / planner123! (chỉ dùng để đăng nhập thử trên máy dev/demo bảo vệ).
INSERT INTO role (code, name) VALUES
    ('ADMIN', 'Quản trị viên'),
    ('PLANNER', 'Nhân viên kế hoạch');

INSERT INTO app_user (username, password_hash, role_id, enabled, created_at, updated_at) VALUES
    ('admin', '$2a$10$3ilSDXcDJaB.adUimSdjHeV3eF/C3ophiTEF68C0JE0qYS.O.tBMG', (SELECT id FROM role WHERE code = 'ADMIN'), TRUE, NOW(), NOW()),
    ('planner', '$2a$10$207gSgU9516DZlqTFduZn.nd1Uzt4RpVEDTbWWNtcFBa.Wd/k9Uxe', (SELECT id FROM role WHERE code = 'PLANNER'), TRUE, NOW(), NOW());
