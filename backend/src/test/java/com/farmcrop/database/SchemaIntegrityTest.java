package com.farmcrop.database;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real Flyway migrations (isolated in-memory H2, MySQL mode) and proves the database itself
 * enforces the critical integrity rules — independent of application code. Each test rolls back.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaIntegrityTest {

    private static final Set<String> EXPECTED_TABLES = Set.of(
            // V1-V3 users & auth
            "users", "roles", "user_roles", "user_addresses", "refresh_tokens",
            "password_reset_tokens", "email_verification_tokens",
            // V4 sellers
            "seller_applications", "seller_profiles", "seller_documents", "seller_application_history",
            // V5 catalog, V6 inventory
            "categories", "products", "product_images", "inventory", "inventory_movements",
            // V7 cart, V8 orders
            "carts", "cart_items", "orders", "seller_orders", "order_items", "order_status_history", "inventory_reservations",
            // V9 payments, V10 notifications, V11 chat, V12 audit
            "payments", "payment_events", "refunds", "notifications", "notification_preferences",
            "chat_conversations", "chat_messages", "audit_logs");

    @Autowired
    private JdbcTemplate jdbc;

    private long buyerId;
    private long sellerUserId;
    private long sellerId;
    private long productId;

    @BeforeEach
    void seedMinimalGraph() {
        buyerId = insertUser("schema_buyer");
        sellerUserId = insertUser("schema_seller");
        jdbc.update("""
                INSERT INTO seller_profiles (user_id, farm_name, seller_status, approved_at, created_at, updated_at)
                VALUES (?, 'Schema Farm', 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""", sellerUserId);
        sellerId = jdbc.queryForObject("SELECT id FROM seller_profiles WHERE user_id = ?", Long.class, sellerUserId);
        long categoryId = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'vegetables'", Long.class);
        jdbc.update("""
                INSERT INTO products (seller_id, category_id, name, unit, price, status, created_at, updated_at)
                VALUES (?, ?, 'Schema Potato', 'KG', 25.00, 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""", sellerId, categoryId);
        productId = jdbc.queryForObject("SELECT id FROM products WHERE name = 'Schema Potato'", Long.class);
        jdbc.update("INSERT INTO inventory (product_id, on_hand_quantity, reserved_quantity, updated_at) VALUES (?, 500, 0, CURRENT_TIMESTAMP(6))", productId);
    }

    @Test
    void migrationsCreateExactlyTheExpectedTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT LOWER(table_name) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class);
        Set<String> actual = new TreeSet<>(tables);
        actual.remove("flyway_schema_history");
        assertThat(actual).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
    }

    @Test
    void allMigrationsSucceededInOrder() {
        List<String> versions = jdbc.queryForList(
                // version IS NULL rows are Flyway's own schema-creation markers, not migrations.
                "SELECT \"version\" FROM \"flyway_schema_history\" WHERE \"success\" = TRUE AND \"version\" IS NOT NULL ORDER BY \"installed_rank\"",
                String.class);
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\" = FALSE", Integer.class)).isZero();
    }

    @Test
    void referenceDataIsSeededWithoutAnyUsers() {
        assertThat(jdbc.queryForList("SELECT role_name FROM roles ORDER BY role_name", String.class))
                .containsExactly("ADMIN", "BUYER", "SELLER");
        assertThat(jdbc.queryForList("SELECT slug FROM categories ORDER BY display_order", String.class))
                .containsExactly("grains", "vegetables", "fruits", "pulses", "oilseeds", "spices", "millets", "dairy");
    }

    // ---------- inventory: never negative, never over-reserved ----------

    @Test
    void inventoryCannotGoNegative() {
        assertThatThrownBy(() -> jdbc.update("UPDATE inventory SET on_hand_quantity = -1 WHERE product_id = ?", productId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reservationsCannotExceedStockOnHand() {
        assertThatThrownBy(() -> jdbc.update("UPDATE inventory SET reserved_quantity = 501 WHERE product_id = ?", productId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void conditionalReservationUpdateRefusesToOversell() {
        String reserve = "UPDATE inventory SET reserved_quantity = reserved_quantity + ? "
                + "WHERE product_id = ? AND on_hand_quantity - reserved_quantity >= ?";
        assertThat(jdbc.update(reserve, 400, productId, 400)).isEqualTo(1);
        assertThat(jdbc.update(reserve, 200, productId, 200)).isZero(); // only 100 left
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM inventory WHERE product_id = ?", java.math.BigDecimal.class, productId))
                .isEqualByComparingTo("400");
    }

    // ---------- catalog ----------

    @Test
    void productsRejectNonPositivePricesAndUnknownUnits() {
        long categoryId = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'grains'", Long.class);
        String insert = "INSERT INTO products (seller_id, category_id, name, unit, price, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 'DRAFT', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))";
        assertThatThrownBy(() -> jdbc.update(insert, sellerId, categoryId, "Free Wheat", "KG", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, sellerId, categoryId, "Odd Wheat", "LITRE", 10))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void productsMustBelongToAnExistingSellerProfile() {
        long categoryId = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'grains'", Long.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO products (seller_id, category_id, name, unit, price, status, created_at, updated_at) "
                + "VALUES (999999, ?, 'Orphan', 'KG', 10, 'DRAFT', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))", categoryId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void categoryNamesAreUnique() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO categories (name, slug, created_at, updated_at) "
                + "VALUES ('Grains', 'grains-2', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- users & sellers ----------

    @Test
    void aUserCannotHoldTheSameRoleTwiceOrTwoSellerProfiles() {
        long buyerRole = jdbc.queryForObject("SELECT id FROM roles WHERE role_name = 'BUYER'", Long.class);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", buyerId, buyerRole);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", buyerId, buyerRole))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO seller_profiles (user_id, farm_name, seller_status, approved_at, created_at, updated_at)
                VALUES (?, 'Second Farm', 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""", sellerUserId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- cart ----------

    @Test
    void aCartHoldsEachProductOnceWithPositiveQuantity() {
        jdbc.update("INSERT INTO carts (buyer_id, created_at, updated_at) VALUES (?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))", buyerId);
        long cartId = jdbc.queryForObject("SELECT id FROM carts WHERE buyer_id = ?", Long.class, buyerId);
        String insert = "INSERT INTO cart_items (cart_id, product_id, quantity, price_when_added, created_at, updated_at) "
                + "VALUES (?, ?, ?, 25.00, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))";
        jdbc.update(insert, cartId, productId, 2);
        assertThatThrownBy(() -> jdbc.update(insert, cartId, productId, 3)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO carts (buyer_id, created_at, updated_at) VALUES (?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))", buyerId))
                .as("one cart per buyer").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE cart_items SET quantity = 0 WHERE cart_id = ?", cartId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- orders ----------

    @Test
    void orderTotalsMustAddUpAndCheckoutKeysAreUniquePerBuyer() {
        insertOrder("FC-ORD-T1", "key-1", 100, 20, 0, 120);
        assertThatThrownBy(() -> insertOrder("FC-ORD-T2", "key-2", 100, 20, 0, 999))
                .as("grand total mismatch").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertOrder("FC-ORD-T3", "key-1", 100, 20, 0, 120))
                .as("duplicate checkout submission").isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void orderItemsRejectZeroQuantityAndDeletingAPurchasedProductIsBlocked() {
        long orderId = insertOrder("FC-ORD-T4", "key-4", 50, 0, 0, 50);
        jdbc.update("""
                INSERT INTO seller_orders (order_id, seller_id, seller_order_number, status, subtotal, shipping_amount, total, created_at, updated_at)
                VALUES (?, ?, 'FC-ORD-T4-S1', 'PENDING', 50, 0, 50, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""", orderId, sellerId);
        long sellerOrderId = jdbc.queryForObject("SELECT id FROM seller_orders WHERE seller_order_number = 'FC-ORD-T4-S1'", Long.class);
        String item = "INSERT INTO order_items (order_id, seller_order_id, product_id, product_name_snapshot, unit_snapshot, unit_price, quantity, line_total, created_at) "
                + "VALUES (?, ?, ?, 'Schema Potato', 'KG', 25.00, ?, ?, CURRENT_TIMESTAMP(6))";
        assertThatThrownBy(() -> jdbc.update(item, orderId, sellerOrderId, productId, 0, 0)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(item, orderId, sellerOrderId, productId, 2, 50);

        jdbc.update("DELETE FROM inventory WHERE product_id = ?", productId);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM products WHERE id = ?", productId))
                .as("order history protects the product row").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id = ?", buyerId))
                .as("order history protects the buyer row").isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- payments ----------

    @Test
    void duplicateGatewayEventsAreRejectedForIdempotency() {
        String insert = "INSERT INTO payment_events (gateway_event_id, event_type, signature_valid, processing_status, payload_sha256, received_at) "
                + "VALUES ('evt_123', 'payment.captured', TRUE, 'PROCESSED', ?, CURRENT_TIMESTAMP(6))";
        jdbc.update(insert, "a".repeat(64));
        assertThatThrownBy(() -> jdbc.update(insert, "b".repeat(64))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void paymentsRequireAPositiveAmountAndAUniqueGatewayOrder() {
        long orderId = insertOrder("FC-ORD-T5", "key-5", 75, 0, 0, 75);
        String insert = "INSERT INTO payments (order_id, buyer_id, gateway_order_id, amount, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, 'CREATED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))";
        jdbc.update(insert, orderId, buyerId, "order_A", 75);
        assertThatThrownBy(() -> jdbc.update(insert, orderId, buyerId, "order_A", 75)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, orderId, buyerId, "order_B", 0)).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- chat ----------

    @Test
    void chatConversationsOnlyAcceptBuyerOrSellerContexts() {
        String insert = "INSERT INTO chat_conversations (user_id, context_role, status, created_at, updated_at) "
                + "VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))";
        jdbc.update(insert, buyerId, "BUYER");
        assertThatThrownBy(() -> jdbc.update(insert, buyerId, "ADMIN")).isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertUser(String username) {
        jdbc.update("""
                INSERT INTO users (full_name, username, email, password_hash, account_status, created_at, updated_at)
                VALUES ('Schema Test', ?, ?, '{bcrypt}not-a-real-hash', 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""",
                username, username + "@example.test");
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    private long insertOrder(String number, String idempotencyKey, int subtotal, int shipping, int discount, int grandTotal) {
        jdbc.update("""
                INSERT INTO orders (order_number, buyer_id, status, payment_status, subtotal, shipping_total, discount_total, grand_total,
                                    delivery_recipient_name, delivery_phone, delivery_address_line, delivery_city, delivery_state,
                                    delivery_postal_code, delivery_country, idempotency_key, placed_at, created_at, updated_at)
                VALUES (?, ?, 'PENDING', 'CREATED', ?, ?, ?, ?, 'Asha', '9876543210', 'Village Road', 'Patna', 'Bihar', '800001', 'India', ?,
                        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""",
                number, buyerId, subtotal, shipping, discount, grandTotal, idempotencyKey);
        return jdbc.queryForObject("SELECT id FROM orders WHERE order_number = ?", Long.class, number);
    }
}
