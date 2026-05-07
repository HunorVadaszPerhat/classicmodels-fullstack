-- =====================================================================
-- V4 — Audit columns on the employees table.
--
-- Why audit columns?
--   Every "real" application eventually needs to answer:
--     * When was this row created?
--     * Who created it?
--     * When was it last modified, and by whom?
--   The standard pattern is four columns: createdAt, updatedAt,
--   createdBy, updatedBy. They're populated automatically by the
--   application on every save/update, never by hand.
--
-- DEFAULT CURRENT_TIMESTAMP and ON UPDATE CURRENT_TIMESTAMP:
--   MySQL features that act as a safety net. If a row is INSERTed
--   without specifying createdAt, MySQL fills it in with the current
--   time. If a row is UPDATEd without specifying updatedAt, MySQL
--   automatically refreshes updatedAt. These triggers fire only when
--   the column is OMITTED from the SQL — if the application sets it
--   explicitly, the application's value wins.
--
--   So in practice:
--     * App always sets all four → DB triggers are dormant
--     * Someone runs ad-hoc SQL → DB triggers keep updatedAt accurate
--   Both layers cooperating means audit data stays correct even when
--   data is changed outside the app.
--
-- VARCHAR(50) for the user columns:
--   Username length cap consistent with what most enterprise SSO
--   systems impose. Email-based usernames fit; long display names
--   wouldn't. NULL allowed because not all writes have a user
--   context (system migrations, batch jobs, etc.).
-- =====================================================================

ALTER TABLE employees
    ADD COLUMN createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    ADD COLUMN createdBy VARCHAR(50) NULL,
    ADD COLUMN updatedBy VARCHAR(50) NULL;

-- Backfill existing rows. We don't actually know when each seed
-- employee was "created" in real life — the closest truth we have is
-- "they were imported as part of the seed data," so we mark them with
-- a synthetic 'seed' username. createdAt/updatedAt got their default
-- values from the ALTER above (current time at migration), which is
-- the best approximation we have.
UPDATE employees
SET createdBy = 'seed',
    updatedBy = 'seed'
WHERE createdBy IS NULL;
