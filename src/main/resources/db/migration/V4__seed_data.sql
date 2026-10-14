-- Datos semilla (§13). Se cargan en todos los perfiles.
-- Reglas:
--   * NO se escriben ids: el orden de inserción produce 1, 2, 3... y la secuencia de identidad avanza.
--     Si se escribieran a mano, el primer registro creado por la API chocaría con el id 1.
--   * Las referencias se resuelven por email, sku o fecha, nunca por id fijo.
--   * Sin sentencias propias de Flyway: /test/reset vuelve a ejecutar este script.
--   * Contraseña de todos los usuarios: Secret_123 (hash BCrypt, coste 10).

INSERT INTO users (email, password_hash, first_name, last_name, phone, birth_date, country, plan,
                   experience_level, newsletter, terms_accepted_at, status, created_at)
VALUES ('standard@shoplab.dev', '$2a$10$aiX8ntqrjgjlPVm.d6o8w.ZRBNTw1O2KJMc3AUlMmEXMJ8TIRXn7i', 'Standard', 'User', NULL,
        DATE '1990-05-20', 'CO', 'BASIC', 3, false, TIMESTAMPTZ '2026-08-01T09:00:00Z', 'ACTIVE', TIMESTAMPTZ '2026-08-01T09:00:00Z'),
       ('locked@shoplab.dev', '$2a$10$aiX8ntqrjgjlPVm.d6o8w.ZRBNTw1O2KJMc3AUlMmEXMJ8TIRXn7i', 'Locked', 'User', NULL,
        DATE '1988-03-15', 'CO', 'BASIC', 3, false, TIMESTAMPTZ '2026-08-01T09:05:00Z', 'LOCKED', TIMESTAMPTZ '2026-08-01T09:05:00Z'),
       ('buyer@shoplab.dev', '$2a$10$aiX8ntqrjgjlPVm.d6o8w.ZRBNTw1O2KJMc3AUlMmEXMJ8TIRXn7i', 'Buyer', 'User', NULL,
        DATE '1995-11-02', 'CO', 'BASIC', 3, false, TIMESTAMPTZ '2026-08-01T09:10:00Z', 'ACTIVE', TIMESTAMPTZ '2026-08-01T09:10:00Z');

INSERT INTO products (sku, name, description, price, stock, image_key, created_at)
VALUES ('SL-HEAD-001', 'Wireless Headphones', 'Over-ear bluetooth headphones', 59.99, 10, 'headphones', TIMESTAMPTZ '2026-08-01T08:00:00Z'),
       ('SL-KEYB-002', 'Mechanical Keyboard', 'Backlit mechanical keyboard with blue switches', 89.90, 15, 'keyboard', TIMESTAMPTZ '2026-08-01T08:00:00Z'),
       ('SL-WATC-003', 'Smart Watch', 'Fitness smart watch with heart rate monitor', 149.00, 0, 'smartwatch', TIMESTAMPTZ '2026-08-01T08:00:00Z'),
       ('SL-MOUS-004', 'Gaming Mouse', 'Ergonomic gaming mouse with 6 buttons', 25.00, 20, 'mouse', TIMESTAMPTZ '2026-08-01T08:00:00Z'),
       ('SL-SPKR-005', 'Bluetooth Speaker', 'Portable waterproof bluetooth speaker', 39.50, 1, 'speaker', TIMESTAMPTZ '2026-08-01T08:00:00Z'),
       ('SL-BACK-006', 'Laptop Backpack', 'Backpack for laptops up to 15 inches', 45.00, 8, 'backpack', TIMESTAMPTZ '2026-08-01T08:00:00Z');

-- Historial de buyer: 2 órdenes. No descuentan stock (el stock de arriba ya es el actual).
INSERT INTO orders (user_id, status, payment_method, total, shipping_address, shipping_city,
                    shipping_postal_code, shipping_country, created_at)
VALUES ((SELECT id FROM users WHERE email = 'buyer@shoplab.dev'), 'CONFIRMED', 'CARD', 109.99,
        'Calle 10 #20-30', 'Bogotá', '110111', 'CO', TIMESTAMPTZ '2026-09-01T10:00:00Z'),
       ((SELECT id FROM users WHERE email = 'buyer@shoplab.dev'), 'CONFIRMED', 'CASH', 45.00,
        'Calle 10 #20-30', 'Bogotá', '110111', 'CO', TIMESTAMPTZ '2026-09-15T16:30:00Z');

INSERT INTO order_items (order_id, product_id, quantity, unit_price)
VALUES ((SELECT id FROM orders WHERE created_at = TIMESTAMPTZ '2026-09-01T10:00:00Z'),
        (SELECT id FROM products WHERE sku = 'SL-HEAD-001'), 1, 59.99),
       ((SELECT id FROM orders WHERE created_at = TIMESTAMPTZ '2026-09-01T10:00:00Z'),
        (SELECT id FROM products WHERE sku = 'SL-MOUS-004'), 2, 25.00),
       ((SELECT id FROM orders WHERE created_at = TIMESTAMPTZ '2026-09-15T16:30:00Z'),
        (SELECT id FROM products WHERE sku = 'SL-BACK-006'), 1, 45.00);
