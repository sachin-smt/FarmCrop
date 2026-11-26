# FarmCrop implementation plan

Based on an inspection of the repository at commit `487f5c0` (2026-11-26).

## 1. Current state

### Backend (`backend/`)
- Spring Boot 4.0.10, Java 25, Maven (no wrapper committed). Starters already present:
  webmvc, data-jpa, security, validation, actuator, flyway (+ flyway-mysql), springdoc 3, H2 (test).
- Every Java class under `com.farmcrop.*` is an empty stub (`public class X {}`); packages
  `admin, auth, buyer, chatbot, common, config, crop, notification, order, payment, security,
  seller, user, verification` exist only as `package-info.java`.
- Flyway is disabled; `V1`..`V6` migrations are comment-only placeholders that have never been applied.
- One test (`FarmCropApplicationTests`) boots the context on H2. It passes.

### Frontend (`frontend/`)
- React 19 + TypeScript + Vite 8 + Tailwind 4 + react-router 7 + i18next + vitest. Build and 7 tests pass.
- `hooks/useAuth.tsx`: every method throws "Authentication is awaiting backend integration".
  Login form posts `{ usernameOrEmail, password }`. OTP and social login buttons exist (no provider).
- `features/portals/PortalContext.tsx` + `portalDataService.ts`: seller applications and seller
  crops are stored in `sessionStorage` (demo). Admin pages read the same demo store.
- Cart is React state in `App.tsx`; marketplace uses 5 hardcoded crops from `data.ts`.
- Chatbot (`features/chatbot/services/aiService.ts`) returns a canned development response.
- `/admin/*` and `/seller/*` routes are **not guarded**; `SellerAccessGuard` exists but is unused.
- **Pages that do not exist yet** and must be built in the existing design language:
  registration, product details, checkout, buyer orders/order details, payment result,
  buyer notifications, address management.

### Repository
- Remote `origin` = `github.com/sachin-smt/FarmCrop`, branch `main`, clean tree.
- `FarmCrop.old-main.zip` is tracked in Git (left untouched; owner to decide).
- Root `.gitignore` pattern `.env.*` also hid `.env.example` files (fixed in M1).

## 2. Architecture decisions

| Topic | Decision |
|---|---|
| Structure | Modular monolith, one Maven module, packages per business module, `controller/service/repository/entity/dto` layers |
| IDs | `BIGINT AUTO_INCREMENT` internally; public numbers for orders/applications (`FC-ORD-…`, `FC-APP-…`) |
| Money | `DECIMAL(12,2)` / `BigDecimal`; quantities `DECIMAL(12,3)` (kg fractions) |
| Schema | Flyway only (`ddl-auto=validate`). SQL kept portable across MySQL 8 and H2 (MySQL mode) so migrations are exercised by tests |
| Tests DB | H2 in MySQL mode (Docker is not installed, so Testcontainers is not available) + optional manual run against a local `farmcrop_test` MySQL schema |
| Auth | Short-lived JWT access token (in memory on the client) + rotating refresh token in an `HttpOnly; Secure; SameSite=Strict` cookie scoped to `/api/v1/auth`, stored hashed server-side for revocation |
| Roles | `BUYER` (every account), `SELLER` (granted only by admin approval), `ADMIN` (bootstrap only) |
| Admin bootstrap | Created once from `FARMCROP_ADMIN_*` environment variables if no admin exists; never reset on restart |
| Secrets | Environment variables or git-ignored `backend/.env` (loaded via `spring.config.import`) |
| Files | `StorageService` abstraction; local directory outside the source tree; documents private (admin/owner streamed via API), product images public through a controlled endpoint |
| Inventory | Atomic conditional `UPDATE … SET reserved = reserved + ? WHERE available - reserved >= ?`; reservations expire via a scheduled job |
| Orders | One parent order per checkout; `order_items` carry `seller_id` and their own fulfilment status, giving per-seller fulfilment |
| Payments | Razorpay Java SDK; server-computed amount; HMAC signature verification; webhook with signature check + unique `gateway_event_id` for idempotency |
| Notifications | Persisted in MySQL; REST + polling (the UI has no real-time requirement) |
| Chatbot | `AiProvider` interface; provider chosen by config; a `dev-echo` provider only active when explicitly enabled |
| API | `/api/v1/**`, response envelope `{ success, data, message, errors }`, OpenAPI at `/swagger-ui.html` (dev only) |

## 3. Milestones

Each milestone ends with: backend tests + frontend build/tests green → diff review → secret scan → commit → push.

1. **Foundation & MySQL**: config/profiles, secret loading, Flyway enabled, response envelope,
   global error handling, CORS, OpenAPI, stateless security skeleton, health endpoint, frontend API client.
2. **Auth & users**: users/roles/addresses schema, register, login (username or email), JWT + refresh cookie,
   logout/revocation, `/users/me`, admin bootstrap, frontend `useAuth` + registration page + route guards.
3. **Seller application & documents**: applications, profiles, documents, secure upload, status page.
4. **Admin review & notifications**: review queue, approve/reject/resubmission, role grant, notifications, audit log.
5. **Crop listing & inventory**: categories, products, images, inventory movements, seller CRUD.
6. **Buyer marketplace**: public listing/search/filter/pagination, product details page.
7. **Cart & checkout**: server cart, addresses, server-side totals, checkout page.
8. **Orders & fulfilment**: order creation with reservations, seller fulfilment, buyer tracking, cancellation.
9. **Razorpay**: create order, verify signature, webhooks, refunds (test mode).
10. **Dashboards**: real stats for buyer/seller/admin.
11. **Chatbot & notifications completion**.
12. **Hardening**: security tests, responsive pass at 360/390/768/1024/1440, production config.

## 4. Open business decisions (need owner input before the affected milestone)

1. **Listing moderation** (M5): do listings from approved sellers go live immediately, or need admin approval?
2. **Delivery charge** (M7): flat fee, free above a threshold, or per-seller?
3. **Reservation expiry** (M8): proposed 15 minutes for unpaid orders.
4. **Cancellation/refund rules** (M8/M9): proposed: buyer may cancel until the seller marks the item shipped; full refund.
5. **Platform commission** (M10 earnings): proposed 0% unless specified.
6. **Delivered status** (M8): proposed: seller marks delivered; buyer can raise a dispute.
7. **OTP / social login** (M2): no SMS or OAuth provider is configured; these buttons will stay
   visibly "unavailable" rather than fake a login.
