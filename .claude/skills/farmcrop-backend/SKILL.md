---
name: farmcrop-backend
description: Use when implementing or changing FarmCrop Spring Boot backend features - controllers, services, DTOs, security rules, ownership checks, file uploads, payments - and their MockMvc/JUnit tests.
---

# FarmCrop backend development

## Before coding
- Read the module's existing classes and `docs/implementation-plan.md` (milestone scope + open decisions).
- Check versions in `backend/pom.xml` (Spring Boot 4.0.x → Spring Framework 7, Security 7, Hibernate 7, Jackson 3,
  springdoc 3). Use Context7 to confirm APIs for these exact major versions; Boot 4 moved many test/auto-config
  packages (e.g. `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`).
- If a business rule touching money, stock, roles or seller approval is undecided, stop and ask.

## Module shape (`com.farmcrop.<module>`)
`controller` (thin, `@Valid` request records, returns `ApiResponse<T>`) → `service` (`@Transactional`, all rules)
→ `repository` (Spring Data) → `entity`. DTOs are Java records in `dto`. Cross-module calls go through the
other module's service, never its repository.

## Security checklist for every endpoint
- Public? Add it explicitly to `SecurityConfig`; everything else is authenticated by default.
- Role: `@PreAuthorize("hasRole('ADMIN')")` / `SELLER`, plus the seller must be APPROVED and not suspended.
- Ownership: load resources by `(id, currentUserId)` or compare owner ids in the service; return 404 (not 403)
  for other users' private resources to avoid leaking existence.
- Never accept `userId`, `sellerId`, `price`, `total`, `status` from the client when the server can derive it.
- Validate sizes/ranges with Bean Validation; positive quantities; max lengths matching the SQL columns.
- Uploads: check magic bytes (not just extension/Content-Type), size limits, server-generated names,
  storage outside the source tree, documents served only through authorized endpoints.
- Log no secrets, tokens, passwords or full documents. Record admin actions in the audit log.

## Tests (required with each feature)
- Service unit tests (JUnit 5 + Mockito + AssertJ) for rules and state transitions.
- `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")` API tests covering: success, validation
  error envelope, 401 anonymous, 403 wrong role, 404 for another user's resource, and duplicate/idempotent calls.
- Use `spring-security-test` (`jwt()`/`user()` post-processors or real login tokens) — never disable security in tests.
- Run `./mvnw -B verify` and read the surefire summary before claiming success.

## Payments (Razorpay)
Server computes the amount, creates the Razorpay order, verifies `razorpay_signature` with HMAC-SHA256 using the
key secret, and processes webhooks only after verifying `X-Razorpay-Signature` with the webhook secret.
Store gateway event ids with a unique constraint for idempotency. Test keys only.
