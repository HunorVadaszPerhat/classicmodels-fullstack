-- =====================================================================
-- V3 — Add geographic coordinates to the offices table.
--
-- Why pre-store coordinates instead of geocoding at runtime?
--   The free geocoding services (Nominatim, etc.) impose strict rate
--   limits (~1 req/sec/IP) and ask politely that you cache results.
--   With only 7 offices that essentially never change, baking the
--   coordinates into the schema is faster, more reliable, and keeps
--   the front-end simple — it gets lat/lng straight from the API
--   response and feeds it to the map widget without an extra hop.
--
-- Precision:
--   DECIMAL(10, 7) gives 7 fractional digits, accurate to ~1 cm at
--   the equator. Way more than we need for "where is this office?"
--   but it's the conventional choice and costs nothing.
--
-- Nullable:
--   Coordinates are NULL for any future office that hasn't been
--   geocoded yet. The UI hides the map for those rows.
-- =====================================================================

ALTER TABLE offices
    ADD COLUMN lat DECIMAL(10, 7) NULL,
    ADD COLUMN lng DECIMAL(10, 7) NULL;

-- City-centre coordinates for the 7 seed-data offices. Sources are
-- standard public-domain references (Wikipedia / Wikidata).

UPDATE offices SET lat =  37.7749295, lng = -122.4194155 WHERE officeCode = '1'; -- San Francisco
UPDATE offices SET lat =  42.3600825, lng =  -71.0588801 WHERE officeCode = '2'; -- Boston
UPDATE offices SET lat =  40.7127753, lng =  -74.0059728 WHERE officeCode = '3'; -- New York
UPDATE offices SET lat =  48.8566140, lng =    2.3522220 WHERE officeCode = '4'; -- Paris
UPDATE offices SET lat =  35.6894875, lng =  139.6917064 WHERE officeCode = '5'; -- Tokyo
UPDATE offices SET lat = -33.8688197, lng =  151.2092955 WHERE officeCode = '6'; -- Sydney
UPDATE offices SET lat =  51.5073509, lng =   -0.1277583 WHERE officeCode = '7'; -- London
