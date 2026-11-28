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
            "chat_conversations", "chat_messages", "audit_logs",
            // V14 Google / phone sign-in
            "user_auth_identities", "consumed_identity_tokens",
            // V15 email registration OTP
            "pending_registrations",
            // V16 shared rate limiting
            "rate_limit_keys", "rate_limit_hits");

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
        assertThat(versions)
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16");
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
        long sellerOrderId = insertSellerOrder(orderId, sellerId, "FC-ORD-T4-S1", 50);
        assertThatThrownBy(() -> insertOrderItem(orderId, sellerOrderId, productId, sellerId, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertOrderItem(orderId, sellerOrderId, productId, sellerId, 2);

        jdbc.update("DELETE FROM inventory WHERE product_id = ?", productId);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM products WHERE id = ?", productId))
                .as("order history protects the product row").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id = ?", buyerId))
                .as("order history protects the buyer row").isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void orderItemsMustMatchTheirParentOrderAndProductSeller() {
        long otherSellerUserId = insertUser("schema_other_seller");
        long otherSellerId = insertSellerProfile(otherSellerUserId, "Other Schema Farm");
        long otherProductId = insertProduct(otherSellerId, "Other Schema Potato");

        long orderId = insertOrder("FC-ORD-V13-A", "key-v13-a", 25, 0, 0, 25);
        long otherOrderId = insertOrder("FC-ORD-V13-B", "key-v13-b", 25, 0, 0, 25);
        long sellerOrderId = insertSellerOrder(orderId, sellerId, "FC-ORD-V13-A-S1", 25);
        long otherSellerOrderId = insertSellerOrder(otherOrderId, otherSellerId, "FC-ORD-V13-B-S1", 25);

        assertThatThrownBy(() -> insertOrderItem(orderId, otherSellerOrderId, productId, sellerId, 1))
                .as("a seller order must belong to the same parent order")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertOrderItem(orderId, sellerOrderId, otherProductId, otherSellerId, 1))
                .as("the purchased product and seller order must have the same seller")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void paymentsMustMatchOrderBuyerAndAttemptNumbersMustBeUnique() {
        long otherBuyerId = insertUser("schema_other_buyer");
        long orderId = insertOrder("FC-ORD-V13-P", "key-v13-p", 25, 0, 0, 25);
        String insertPayment = """
                INSERT INTO payments (order_id, buyer_id, gateway_order_id, amount, status, attempt_number, created_at, updated_at)
                VALUES (?, ?, ?, 25, 'CREATED', ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""";

        assertThatThrownBy(() -> jdbc.update(insertPayment, orderId, otherBuyerId, "gw-v13-mismatch", 1))
                .as("payment buyer must match its parent order")
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update(insertPayment, orderId, buyerId, "gw-v13-attempt-1", 1);
        assertThatThrownBy(() -> jdbc.update(insertPayment, orderId, buyerId, "gw-v13-attempt-1b", 1))
                .as("payment attempt number must be unique within an order")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refundsMustMatchTheirPaymentAndSellerOrderParents() {
        long orderId = insertOrder("FC-ORD-V13-R1", "key-v13-r1", 25, 0, 0, 25);
        long otherOrderId = insertOrder("FC-ORD-V13-R2", "key-v13-r2", 25, 0, 0, 25);
        jdbc.update("""
                INSERT INTO payments (order_id, buyer_id, gateway_order_id, amount, status, created_at, updated_at)
                VALUES (?, ?, 'gw-v13-refund', 25, 'SUCCESS', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""",
                orderId, buyerId);
        long paymentId = jdbc.queryForObject("SELECT id FROM payments WHERE gateway_order_id = 'gw-v13-refund'", Long.class);
        long otherSellerOrderId = insertSellerOrder(otherOrderId, sellerId, "FC-ORD-V13-R2-S1", 25);
        String insertRefund = """
                INSERT INTO refunds (payment_id, order_id, seller_order_id, amount, status, created_at, updated_at)
                VALUES (?, ?, ?, 10, 'PENDING', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""";

        assertThatThrownBy(() -> jdbc.update(insertRefund, paymentId, otherOrderId, null))
                .as("refund order must match its payment order")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertRefund, paymentId, orderId, otherSellerOrderId))
                .as("refund seller order must belong to the refund parent order")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void inventoryReservationProductMustMatchItsOrderItem() {
        long orderId = insertOrder("FC-ORD-V13-I", "key-v13-i", 25, 0, 0, 25);
        long sellerOrderId = insertSellerOrder(orderId, sellerId, "FC-ORD-V13-I-S1", 25);
        long orderItemId = insertOrderItem(orderId, sellerOrderId, productId, sellerId, 1);
        long otherProductId = insertProduct(sellerId, "Second Schema Potato");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO inventory_reservations (order_item_id, product_id, quantity, status, expires_at, created_at)
                VALUES (?, ?, 1, 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""",
                orderItemId, otherProductId))
                .as("reservation product must match its order item product")
                .isInstanceOf(DataIntegrityViolationException.class);
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

    // ---------- V14: Google / phone sign-in ----------

    @Test
    void phoneOnlyAccountsMayOmitEmailButEmailsStayUnique() {
        String insert = "INSERT INTO users (full_name, username, email, phone, password_hash, account_status, created_at, updated_at) "
                + "VALUES ('Phone User', ?, ?, ?, '{bcrypt}x', 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))";
        jdbc.update(insert, "schema_phone_a", null, "+917000000001");
        jdbc.update(insert, "schema_phone_b", null, "+917000000002");
        assertThatThrownBy(() -> jdbc.update(insert, "schema_phone_c", "schema_buyer@example.test", "+917000000003"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anExternalIdentityBelongsToOneUserAndAUserHasOneLinkPerProvider() {
        String insert = "INSERT INTO user_auth_identities (user_id, provider, provider_subject, created_at) VALUES (?, 'GOOGLE', ?, CURRENT_TIMESTAMP(6))";
        jdbc.update(insert, buyerId, "google-sub-1");
        assertThatThrownBy(() -> jdbc.update(insert, sellerUserId, "google-sub-1")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, buyerId, "google-sub-2")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aSignInTokenCanBeConsumedOnlyOnce() {
        String insert = "INSERT INTO consumed_identity_tokens (token_hash, expires_at, created_at) VALUES (?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))";
        jdbc.update(insert, "a".repeat(64));
        assertThatThrownBy(() -> jdbc.update(insert, "a".repeat(64))).isInstanceOf(DataIntegrityViolationException.class);
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

    private long insertSellerProfile(long userId, String farmName) {
        jdbc.update("""
                INSERT INTO seller_profiles (user_id, farm_name, seller_status, approved_at, created_at, updated_at)
                VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""",
                userId, farmName);
        return jdbc.queryForObject("SELECT id FROM seller_profiles WHERE user_id = ?", Long.class, userId);
    }

    private long insertProduct(long sellerId, String name) {
        long categoryId = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'vegetables'", Long.class);
        jdbc.update("""
                INSERT INTO products (seller_id, category_id, name, unit, price, status, created_at, updated_at)
                VALUES (?, ?, ?, 'KG', 25, 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""",
                sellerId, categoryId, name);
        return jdbc.queryForObject("SELECT id FROM products WHERE name = ?", Long.class, name);
    }

    private long insertSellerOrder(long orderId, long sellerId, String number, int subtotal) {
        jdbc.update("""
                INSERT INTO seller_orders (order_id, seller_id, seller_order_number, status, subtotal, shipping_amount, total, created_at, updated_at)
                VALUES (?, ?, ?, 'PENDING', ?, 0, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""",
                orderId, sellerId, number, subtotal, subtotal);
        return jdbc.queryForObject("SELECT id FROM seller_orders WHERE seller_order_number = ?", Long.class, number);
    }

    private long insertOrderItem(long orderId, long sellerOrderId, long productId, long sellerId, int quantity) {
        jdbc.update("""
                INSERT INTO order_items (order_id, seller_order_id, product_id, seller_id, product_name_snapshot, unit_snapshot, unit_price, quantity, line_total, created_at)
                VALUES (?, ?, ?, ?, 'Schema Potato', 'KG', 25, ?, ?, CURRENT_TIMESTAMP(6))""",
                orderId, sellerOrderId, productId, sellerId, quantity, quantity * 25);
        return jdbc.queryForObject("SELECT id FROM order_items WHERE order_id = ? AND product_id = ?", Long.class, orderId, productId);
    }
}
