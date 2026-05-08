-- =====================================================================
-- V8 — Add geographic coordinates to the customers table.
--
-- Why these aren't pre-seeded (vs. V3 for offices)
--   We have 122 customers across many countries. Hand-curating accurate
--   coordinates for all of them is a lot of busy-work for a learning
--   project. Instead the columns ship NULL and the application backfills
--   them lazily via the C9 "Geocode address" button on the detail page.
--
--   This trades upfront completeness for incremental, opt-in geocoding —
--   a fine fit for Nominatim's 1-req/sec/IP fair-use policy. C10's
--   all-customers map will need to either backfill every row first or
--   handle missing coordinates gracefully; we'll decide there.
--
-- Precision:
--   DECIMAL(10, 7) gives 7 fractional digits, accurate to ~1 cm at
--   the equator. Way more than needed for "where is this customer?"
--   but it's the conventional choice and costs nothing. Matches V3
--   for consistency.
--
-- Nullable:
--   Coordinates are NULL for any customer that hasn't been geocoded
--   yet. The detail page hides the map for those rows; the C9
--   "Geocode address" button populates them on demand.
-- =====================================================================

ALTER TABLE customers
    ADD COLUMN lat DECIMAL(10, 7) NULL,
    ADD COLUMN lng DECIMAL(10, 7) NULL;
