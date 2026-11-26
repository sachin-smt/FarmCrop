-- FarmCrop schema verification. READ-ONLY: only SELECT queries against the farmcrop schema and
-- information_schema. It creates, changes and removes nothing.
--
-- Run from the repository root in PowerShell after the backend has started once (Flyway creates the tables).
-- You are prompted for the password; it is never shown:
--   & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p --table farmcrop -e "source backend/scripts/verify-schema.sql"
--
-- Output contains schema metadata, role/category names and record counts only (no personal data).

-- 1. Database and server
SELECT DATABASE() AS database_name, VERSION() AS mysql_version,
       @@character_set_database AS charset, @@collation_database AS collation_name,
       @@default_storage_engine AS default_engine, @@foreign_key_checks AS foreign_key_checks;

-- 2. Tables (expected: 31 application tables + flyway_schema_history)
SELECT COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';

SELECT table_name, engine, table_collation, table_rows AS approx_rows
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
ORDER BY table_name;

-- 3. Flyway migration history (expect versions 1..12, all success = 1, no failures)
SELECT installed_rank, version, description, script, success, installed_on, execution_time AS ms
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT COUNT(*) AS failed_migrations FROM flyway_schema_history WHERE success = 0;

-- 4. Columns and types
SELECT table_name, column_name, column_type, is_nullable, column_default, column_key
FROM information_schema.columns
WHERE table_schema = DATABASE()
ORDER BY table_name, ordinal_position;

-- 5. Primary keys, unique constraints and foreign keys
SELECT tc.table_name, tc.constraint_type, tc.constraint_name,
       GROUP_CONCAT(kcu.column_name ORDER BY kcu.ordinal_position) AS columns_list,
       MAX(kcu.referenced_table_name) AS references_table,
       GROUP_CONCAT(kcu.referenced_column_name ORDER BY kcu.ordinal_position) AS references_columns
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON kcu.constraint_schema = tc.constraint_schema
 AND kcu.constraint_name = tc.constraint_name
 AND kcu.table_name = tc.table_name
WHERE tc.table_schema = DATABASE()
GROUP BY tc.table_name, tc.constraint_type, tc.constraint_name
ORDER BY tc.table_name, FIELD(tc.constraint_type, 'PRIMARY KEY', 'UNIQUE', 'FOREIGN KEY'), tc.constraint_name;

-- Foreign key delete behaviour (CASCADE only on dependent data such as tokens, cart items, notifications)
SELECT rc.table_name, rc.constraint_name, rc.referenced_table_name, rc.delete_rule
FROM information_schema.referential_constraints rc
WHERE rc.constraint_schema = DATABASE()
ORDER BY rc.table_name, rc.constraint_name;

-- 6. CHECK constraints (MySQL 8.0.16+ enforces them)
SELECT tc.table_name, cc.constraint_name, cc.check_clause
FROM information_schema.check_constraints cc
JOIN information_schema.table_constraints tc
  ON tc.constraint_schema = cc.constraint_schema AND tc.constraint_name = cc.constraint_name
WHERE cc.constraint_schema = DATABASE()
ORDER BY tc.table_name, cc.constraint_name;

-- 7. Indexes
SELECT table_name, index_name, non_unique, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_list
FROM information_schema.statistics
WHERE table_schema = DATABASE()
GROUP BY table_name, index_name, non_unique
ORDER BY table_name, index_name;

-- 8. Totals
SELECT
  (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND constraint_type = 'PRIMARY KEY') AS primary_keys,
  (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND constraint_type = 'FOREIGN KEY') AS foreign_keys,
  (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND constraint_type = 'UNIQUE') AS unique_constraints,
  (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND constraint_type = 'CHECK') AS check_constraints,
  (SELECT COUNT(DISTINCT table_name, index_name) FROM information_schema.statistics WHERE table_schema = DATABASE()) AS indexes;

-- 9. Reference data
SELECT id, role_name FROM roles ORDER BY id;
SELECT id, name, slug, is_active FROM categories ORDER BY display_order;

-- 10. Record counts
SELECT
  (SELECT COUNT(*) FROM users) AS users,
  (SELECT COUNT(*) FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE r.role_name = 'ADMIN') AS admin_accounts,
  (SELECT COUNT(*) FROM seller_applications) AS seller_applications,
  (SELECT COUNT(*) FROM seller_profiles) AS seller_profiles,
  (SELECT COUNT(*) FROM products) AS products,
  (SELECT COUNT(*) FROM orders) AS orders,
  (SELECT COUNT(*) FROM payments) AS payments,
  (SELECT COUNT(*) FROM refresh_tokens) AS refresh_tokens;

-- 11. Data integrity checks (every value should be 0)
SELECT
  (SELECT COUNT(*) FROM inventory WHERE on_hand_quantity < 0 OR reserved_quantity < 0) AS negative_inventory,
  (SELECT COUNT(*) FROM inventory WHERE reserved_quantity > on_hand_quantity) AS over_reserved_inventory,
  (SELECT COUNT(*) FROM products p LEFT JOIN inventory i ON i.product_id = p.id WHERE i.product_id IS NULL) AS products_without_inventory,
  (SELECT COUNT(*) FROM (SELECT user_id, role_id FROM user_roles GROUP BY user_id, role_id HAVING COUNT(*) > 1) d) AS duplicate_role_assignments,
  (SELECT COUNT(*) FROM order_items oi JOIN seller_orders so ON so.id = oi.seller_order_id WHERE so.order_id <> oi.order_id) AS items_in_wrong_seller_order,
  (SELECT COUNT(*) FROM payments p JOIN orders o ON o.id = p.order_id WHERE p.buyer_id <> o.buyer_id) AS payments_with_wrong_buyer,
  (SELECT COUNT(*) FROM orders WHERE grand_total <> subtotal + shipping_total - discount_total) AS orders_with_bad_totals;
