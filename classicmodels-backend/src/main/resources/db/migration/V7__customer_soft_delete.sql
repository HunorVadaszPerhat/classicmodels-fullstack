-- =====================================================================
-- V7 — Add soft-delete support to the customers table.
--
-- Why soft delete for customers?
--   The customers table is referenced by orders.customerNumber and
--   payments.customerNumber, both NOT NULL. Hard-deleting a customer
--   that has any order or payment is impossible without also deleting
--   those rows — and deleting financial history because of an HR-style
--   "this customer left" event is exactly the wrong default.
--
--   Soft delete sidesteps the problem: the customer row stays, the FKs
--   stay valid, the orders/payments tables remain analytically correct,
--   and the row simply disappears from the active-customers list.
--
-- New columns:
--   active          TINYINT(1) NOT NULL DEFAULT 1
--                   0 = inactive/terminated, 1 = current. TINYINT(1) is
--                   MySQL's idiom for boolean. NOT NULL with default 1
--                   means existing rows backfill to "still active."
--
--   terminatedDate  DATE NULL
--                   When the customer was deactivated. NULL while active.
--                   DATE not DATETIME because the precise time-of-day
--                   isn't operationally useful for this kind of event.
--
-- Index on `active`:
--   The most common query becomes "WHERE active = 1". An index on a
--   low-cardinality column (only two values) is debatable; with ~122
--   rows in the seed data it's pure overhead. Included for parity with
--   V2 (employees) and as a teaching placeholder — in a real system,
--   profile first, index second.
-- =====================================================================

ALTER TABLE customers
    ADD COLUMN active         TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN terminatedDate DATE       NULL,
    ADD INDEX idx_customers_active (active);
