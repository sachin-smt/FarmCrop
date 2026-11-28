---
name: farmcrop-migrations
description: Use when adding or changing FarmCrop database tables, columns, indexes, seed data or JPA entities. Covers Flyway naming, MySQL 8 + H2 portable SQL, money/time types and entity mapping rules.
---

# FarmCrop database migrations

Schema is owned by Flyway; Hibernate runs with `ddl-auto=validate`. Tests run the same migrations on
H2 (`MODE=MySQL;DATABASE_TO_LOWER=TRUE`), so every script must work on **both** MySQL 8 and H2.

## Workflow
1. List existing files in `backend/src/main/resources/db/migration` and take the next number: `V<n>__<verb>_<what>.sql`.
2. Never edit a migration that may have been applied anywhere (anything already pushed). Add a new one instead.
3. Write the SQL (rules below), then the JPA entity, then a repository test.
4. Run `./mvnw -B verify` — schema validation failures show up as context-load errors.
5. Destructive changes (DROP, data-deleting UPDATE/DELETE, narrowing columns) need explicit owner approval first.

## Portable SQL rules
- `BIGINT NOT NULL AUTO_INCREMENT` ids; name constraints: `pk_<table>`, `fk_<table>_<ref>`, `uk_<table>_<cols>`, `idx_<table>_<cols>`.
- Create indexes with separate `CREATE INDEX` statements (not inline `INDEX` / `KEY` clauses).
- One `INSERT` per row for seed data. No `ENGINE=`, `CHARSET=`, `COMMENT=`, `ON UPDATE CURRENT_TIMESTAMP`, `ENUM(...)`, triggers or stored procedures.
- Money: `DECIMAL(12,2)`; quantities: `DECIMAL(12,3)`; never `FLOAT`/`DOUBLE`.
- Timestamps: `DATETIME(6)` in UTC (`hibernate.jdbc.time_zone=UTC`); set them from the application/auditing.
- Strings are always `VARCHAR(n)`, never `CHAR(n)` (Hibernate validation expects VARCHAR for String fields).
- Status columns: `VARCHAR(30)` + Java enum with `@Enumerated(EnumType.STRING)`.
- Booleans: `BOOLEAN NOT NULL DEFAULT FALSE`.
- Add an index for every foreign key and every column used in list filters/sorting.
- Optimistic locking: `version BIGINT NOT NULL DEFAULT 0` on rows edited concurrently (users, products, orders).

## Entity rules
- `@Table(name = "...")` and `@Column(name = "...")` matching the SQL exactly; `BigDecimal` for money.
- Use `Instant` for timestamps. If validation reports a timestamp type mismatch, map with
  `@JdbcTypeCode(SqlTypes.TIMESTAMP)` rather than changing the SQL.
- `FetchType.LAZY` on all associations; no entity is ever serialized directly — map to DTO records.
- Inventory changes use conditional atomic updates (`UPDATE ... WHERE available_quantity - reserved_quantity >= :qty`)
  and check the affected row count, never read-modify-write.
