-- =====================================================================
-- V2 — Add soft-delete support to the employees table.
--
-- Why soft delete for employees?
--   The employees table is referenced by customers (salesRepEmployeeNumber)
--   and by itself (reportsTo). HARD deleting an employee either fails
--   (FK violation) or destroys history. Soft deletion sidesteps both:
--   the row stays, the FKs stay valid, and reports/audits can still
--   reference the (now terminated) employee.
--
-- New columns:
--   active          TINYINT(1) NOT NULL DEFAULT 1
--                   0 = terminated, 1 = current. We use TINYINT(1)
--                   because that's MySQL's idiom for boolean (BOOLEAN
--                   is just an alias). NOT NULL with a default means
--                   the migration won't break for existing rows: every
--                   pre-existing employee becomes active=1.
--
--   terminatedDate  DATE NULL
--                   When the employee was terminated. NULL while active.
--                   DATE (not DATETIME) because HR usually only cares
--                   about the day.
--
-- Index on `active`:
--   The most common query becomes "WHERE active = 1". An index on a
--   low-cardinality column (only two values) is debatable; with ~25
--   rows in the seed data it's pure overhead. Included as a teaching
--   placeholder — in a real system, profile first, index second. If
--   you're unsure, drop it.
-- =====================================================================

ALTER TABLE employees
    ADD COLUMN active         TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN terminatedDate DATE       NULL,
    ADD INDEX idx_employees_active (active);
