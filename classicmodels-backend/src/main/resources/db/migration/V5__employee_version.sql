-- =====================================================================
-- V5 — Optimistic-lock version column on employees.
--
-- The pattern:
--   * Every row carries an integer 'version', starting at 0.
--   * Every UPDATE includes "AND version = ?" in its WHERE clause and
--     "version = version + 1" in its SET clause.
--   * If two clients both read v=3 and both try to UPDATE, one of them
--     succeeds (rowcount=1, version becomes 4); the other's UPDATE
--     matches no rows (rowcount=0) because the version is now 4 in
--     the DB. The application sees rowcount=0 and throws a 409.
--   * The losing client gets a "stale data" dialog and reloads.
--
-- Why "optimistic"?
--   The pattern assumes conflicts are rare — most reads-then-writes
--   succeed without contention. Cheaper than pessimistic locking
--   (SELECT ... FOR UPDATE) which serialises every read with a row
--   lock, but the trade-off is that conflicts surface to the user
--   instead of being invisible.
--
-- INT NOT NULL DEFAULT 0:
--   No nulls — every row has a version. Default 0 means existing
--   seed rows get 0 and brand-new rows start there too.
-- =====================================================================

ALTER TABLE employees
    ADD COLUMN version INT NOT NULL DEFAULT 0;
