-- =====================================================================
-- V6 — Audit columns AND optimistic-lock version on the customers table.
--
-- Why combined?
--   For employees we did the audit columns (V4) and the version column
--   (V5) as two separate migrations. Hindsight: that's two round trips
--   through "ALTER TABLE, redeploy, update repo, redeploy" when one
--   would have done. Audit and version always travel together — both
--   are infrastructure for "who changed what when, and is what I'm
--   editing still current?" So this migration installs both at once.
--
-- The audit columns (mirrors V4 for employees):
--   * createdAt/updatedAt (TIMESTAMP NOT NULL with DB-side defaults)
--   * createdBy/updatedBy (VARCHAR(50) NULL)
--
--   The DB defaults give us a safety net: any INSERT that omits
--   createdAt gets CURRENT_TIMESTAMP automatically. Any UPDATE that
--   omits updatedAt gets ON UPDATE CURRENT_TIMESTAMP. The application
--   sets both anyway, so the defaults stay dormant unless someone
--   runs ad-hoc SQL — in which case audit data still stays accurate.
--
-- The version column (mirrors V5 for employees):
--   * INT NOT NULL DEFAULT 0
--
--   Optimistic-lock pattern:
--     * Every UPDATE will (in C5) carry "AND version = ?" in WHERE and
--       "version = version + 1" in SET.
--     * If two clients both read v=3 and both try to UPDATE, one wins
--       (rowcount=1, version becomes 4); the other matches no rows
--       (rowcount=0) and the controller advice translates that into a
--       409 Conflict with a "stale data" message.
--   C4 only ADDS the column. The repository's update() in C4 doesn't
--   yet check or bump it — that's C5's job. Splitting the two phases
--   keeps each commit's blast radius small.
--
-- Backfill:
--   We don't actually know when each seed customer was "created" — the
--   closest truth we have is "imported as part of the seed dataset," so
--   we mark them with a synthetic 'seed' username. createdAt/updatedAt
--   got their default values from the ALTER above (current time at
--   migration), which is the best approximation we have.
-- =====================================================================

ALTER TABLE customers
    ADD COLUMN createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    ADD COLUMN createdBy VARCHAR(50) NULL,
    ADD COLUMN updatedBy VARCHAR(50) NULL,
    ADD COLUMN version   INT         NOT NULL DEFAULT 0;

UPDATE customers
SET createdBy = 'seed',
    updatedBy = 'seed'
WHERE createdBy IS NULL;
