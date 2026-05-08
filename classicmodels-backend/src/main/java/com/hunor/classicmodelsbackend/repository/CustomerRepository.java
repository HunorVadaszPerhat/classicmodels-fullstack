package com.hunor.classicmodelsbackend.repository;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import com.hunor.classicmodelsbackend.audit.CurrentUser;
import com.hunor.classicmodelsbackend.dto.customer.CustomerMapPointDTO;
import com.hunor.classicmodelsbackend.model.Customer;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepository {

    private final DataSource dataSource;
    public CustomerRepository(DataSource dataSource) { this.dataSource = dataSource; }

    public Customer save(Customer c) {
        // Audit fields are populated automatically. createdAt / updatedAt
        // get sensible DB defaults (CURRENT_TIMESTAMP), so we omit them
        // from the INSERT and let MySQL fill them in. We DO set
        // createdBy / updatedBy from the current user — the DB has no
        // way to know that. version is left to its DB default of 0.
        final String currentUser = CurrentUser.username();
        String sql = """
            INSERT INTO customers (
                customerName, contactLastName, contactFirstName,
                phone, addressLine1, addressLine2, city, state, postalCode,
                country, salesRepEmployeeNumber, creditLimit,
                createdBy, updatedBy
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, c.getCustomerName());
            pstmt.setString(2, c.getContactLastName());
            pstmt.setString(3, c.getContactFirstName());
            pstmt.setString(4, c.getPhone());
            pstmt.setString(5, c.getAddressLine1());
            pstmt.setString(6, c.getAddressLine2());
            pstmt.setString(7, c.getCity());
            pstmt.setString(8, c.getState());
            pstmt.setString(9, c.getPostalCode());
            pstmt.setString(10, c.getCountry());
            pstmt.setObject(11, c.getSalesRepEmployeeNumber());
            pstmt.setBigDecimal(12, c.getCreditLimit());
            pstmt.setString(13, currentUser);  // createdBy
            pstmt.setString(14, currentUser);  // updatedBy (same on first insert)

            pstmt.executeUpdate();

            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    c.setCustomerNumber(keys.getInt(1));
                }
            }
            return c;
        }

        catch (SQLException e) {
            throw new RuntimeException("Insert failed", e);
        }
    }

    public Optional<Customer> findById(int id) {
        String sql = "SELECT * FROM customers WHERE customerNumber = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find by ID failed", e);
        }
    }

    /**
     * Find all <em>active</em> customers. Soft-deleted (terminated) rows
     * are filtered out so they disappear from default UI lists.
     * If a callsite ever needs an "include terminated" view, add a
     * sibling method (e.g. {@code findAllIncludingTerminated()}) with
     * the same SQL minus the {@code WHERE active = 1} clause.
     */
    public List<Customer> findAll() {
        String sql = "SELECT * FROM customers WHERE active = 1";
        List<Customer> customers = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                customers.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find all failed", e);
        }

        return customers;
    }

    /**
     * Optimistic-lock-aware update.
     *
     * <p>Three pieces compared to the pre-C5 version:</p>
     * <ul>
     *   <li>{@code AND version = ?} in the WHERE — the row must still be
     *       at the version the client read.</li>
     *   <li>{@code version = version + 1} in the SET — bumping the
     *       version is part of the same atomic UPDATE.</li>
     *   <li>{@code executeUpdate()} returns the affected-row count.
     *       0 means "WHERE matched no rows," which under our schema
     *       almost always means "version didn't match" — i.e. someone
     *       else updated this row in the meantime. We translate that to
     *       Spring's {@link org.springframework.dao.OptimisticLockingFailureException},
     *       which {@code GlobalExceptionHandler} maps to HTTP 409 Conflict.</li>
     * </ul>
     *
     * <p>{@code updatedAt} still refreshes automatically thanks to
     * {@code ON UPDATE CURRENT_TIMESTAMP} on the column. {@code updatedBy}
     * is set from the current user.</p>
     */
    public void update(Customer c) {
        final String currentUser = CurrentUser.username();
        String sql = """
            UPDATE customers SET
                customerName = ?, contactLastName = ?, contactFirstName = ?,
                phone = ?, addressLine1 = ?, addressLine2 = ?, city = ?, state = ?,
                postalCode = ?, country = ?, salesRepEmployeeNumber = ?, creditLimit = ?,
                updatedBy = ?,
                version = version + 1
            WHERE customerNumber = ?
              AND version = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, c.getCustomerName());
            pstmt.setString(2, c.getContactLastName());
            pstmt.setString(3, c.getContactFirstName());
            pstmt.setString(4, c.getPhone());
            pstmt.setString(5, c.getAddressLine1());
            pstmt.setString(6, c.getAddressLine2());
            pstmt.setString(7, c.getCity());
            pstmt.setString(8, c.getState());
            pstmt.setString(9, c.getPostalCode());
            pstmt.setString(10, c.getCountry());
            pstmt.setObject(11, c.getSalesRepEmployeeNumber());
            pstmt.setBigDecimal(12, c.getCreditLimit());
            pstmt.setString(13, currentUser);
            pstmt.setInt(14, c.getCustomerNumber());
            pstmt.setInt(15, c.getVersion());

            int affected = pstmt.executeUpdate();
            if (affected == 0) {
                // Could be one of two things: row doesn't exist, or
                // version mismatch. We disambiguate by checking
                // existence — that gives a more accurate error message
                // and matches what Spring Data JPA does internally.
                boolean exists = findById(c.getCustomerNumber()).isPresent();
                if (!exists) {
                    throw new RuntimeException(
                            "Customer " + c.getCustomerNumber() + " not found");
                }
                throw new org.springframework.dao.OptimisticLockingFailureException(
                        "Customer " + c.getCustomerNumber() + " was modified by someone else "
                        + "(stale version " + c.getVersion() + "). Reload and try again.");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Update failed", e);
        }
    }

    /**
     * Targeted UPDATE for the lat/lng columns only. Avoids re-reading
     * the row + running a full update just to set two fields, and
     * deliberately doesn't touch {@code version} — a geocode is not a
     * content edit, so the optimistic-lock contract doesn't apply.
     */
    public boolean updateCoordinates(int id, double lat, double lng) {
        String sql = "UPDATE customers SET lat = ?, lng = ? WHERE customerNumber = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, lat);
            ps.setDouble(2, lng);
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Update coordinates failed", e);
        }
    }

    /**
     * Plain hard delete. Will fail if any FK still points at the row
     * (orders, payments — both NOT NULL). Kept for completeness; callers
     * should generally use the strategy methods below instead.
     */
    public void delete(int id) {
        String sql = "DELETE FROM customers WHERE customerNumber = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Delete failed", e);
        }
    }

    // -----------------------------------------------------------------
    //  Strategy 1: SOFT DELETE
    // -----------------------------------------------------------------
    /**
     * Mark the customer as terminated. No row is removed, no FK is
     * touched. This is the recommended option for customer data —
     * preserves orders/payments history, leaves analytics queries
     * valid, fully reversible.
     */
    public void softDelete(int id) {
        String sql = """
            UPDATE customers
               SET active = 0,
                   terminatedDate = CURRENT_DATE
             WHERE customerNumber = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Soft delete failed", ex);
        }
    }

    // -----------------------------------------------------------------
    //  Strategy 2: HARD DELETE + DEEP CASCADE  (destructive)
    // -----------------------------------------------------------------
    /**
     * Walk the entire foreign-key tree under this customer and delete
     * every row found, in dependency order:
     *
     * <pre>
     *   customer
     *     ├─ orders
     *     │    └─ orderdetails  (where orderNumber = o.orderNumber)
     *     └─ payments
     *   customer row → deleted
     * </pre>
     *
     * <h4>Why no shallow CASCADE for customer?</h4>
     *
     * <p>For employees, regular CASCADE deletes the customer rows that
     * reference them but stops at the orders/payments boundary —
     * relying on the FK constraint to fail loudly if those exist.
     * That natural fail-loud point doesn't exist for customer because
     * orders.customerNumber and payments.customerNumber are NOT NULL
     * — there's no way to "shallow" cascade. Either you walk the tree
     * down to the leaves, or you soft-delete. Hence DEEP_CASCADE is
     * the only available hard-delete strategy for customers.</p>
     *
     * <h4>Why no NULLIFY?</h4>
     *
     * <p>Same reason — orders.customerNumber and payments.customerNumber
     * are NOT NULL, so you can't NULL them out. The only hard-delete
     * path requires removing the rows.</p>
     *
     * <h4>Use very sparingly</h4>
     *
     * <p>This destroys financial history. The {@link DeleteStrategy}
     * Javadoc and the service-layer feature flag both reinforce why
     * this should almost never run in a real environment.</p>
     */
    public void deleteAndDeepCascade(int id) {
        try (Connection conn = dataSource.getConnection()) {
            // Manual transaction handling — Customer doesn't yet have
            // the @MyTransactional / @AspectTransactional infrastructure
            // wired in, so we set autoCommit=false and commit/rollback
            // explicitly. (When the AOP wiring lands for customer, this
            // boilerplate becomes a single annotation.)
            conn.setAutoCommit(false);
            try {
                // 1. orderdetails — grandchildren of customer via orders.
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM orderdetails " +
                        "WHERE orderNumber IN (" +
                        "  SELECT orderNumber FROM orders WHERE customerNumber = ?" +
                        ")")) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }

                // 2. orders — direct children of customer.
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM orders WHERE customerNumber = ?")) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }

                // 3. payments — siblings of orders, also children of customer.
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM payments WHERE customerNumber = ?")) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }

                // 4. customer itself. Now safe — all child FKs cleared.
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM customers WHERE customerNumber = ?")) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Delete (deep cascade) failed", ex);
        }
    }

    /**
     * Cheap projection query for the all-customers map.
     *
     * <p>Returns only the columns the map needs (id, name, city,
     * country, lat, lng, and whether a sales rep is assigned), and only
     * for active customers that have actually been geocoded — un-geocoded
     * rows can't be plotted, so dragging them across the wire is wasteful.
     * The frontend reports "X of Y" geocoded so users understand why
     * some customers don't appear.</p>
     *
     * <p>The {@code salesRepEmployeeNumber IS NOT NULL} flag is computed
     * in the SELECT (returning a tinyint 0/1), so we don't read the FK
     * itself just to throw it away. Same idea for any future "status"
     * fields — keep the projection narrow to keep the response small.</p>
     */
    public List<CustomerMapPointDTO> findAllMapPoints() {
        String sql = """
            SELECT customerNumber, customerName, city, country, lat, lng,
                   (salesRepEmployeeNumber IS NOT NULL) AS hasSalesRep
            FROM customers
            WHERE active = 1
              AND lat IS NOT NULL
              AND lng IS NOT NULL
            """;
        List<CustomerMapPointDTO> points = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                points.add(new CustomerMapPointDTO(
                        rs.getInt("customerNumber"),
                        rs.getString("customerName"),
                        rs.getString("city"),
                        rs.getString("country"),
                        rs.getBigDecimal("lat"),
                        rs.getBigDecimal("lng"),
                        rs.getBoolean("hasSalesRep")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find map points failed", e);
        }
        return points;
    }

    /**
     * Count active customers (regardless of geocode status). Used by
     * the map page to show "X of Y geocoded" so users know how many
     * customers don't appear and can fill them in via the per-customer
     * geocode button on the detail page.
     */
    public long countAllActive() {
        String sql = "SELECT COUNT(*) FROM customers WHERE active = 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Count active failed", e);
        }
    }

    private Customer mapRow(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setCustomerNumber(rs.getInt("customerNumber"));
        c.setCustomerName(rs.getString("customerName"));
        c.setContactLastName(rs.getString("contactLastName"));
        c.setContactFirstName(rs.getString("contactFirstName"));
        c.setPhone(rs.getString("phone"));
        c.setAddressLine1(rs.getString("addressLine1"));
        c.setAddressLine2(rs.getString("addressLine2"));
        c.setCity(rs.getString("city"));
        c.setState(rs.getString("state"));
        c.setPostalCode(rs.getString("postalCode"));
        c.setCountry(rs.getString("country"));
        c.setSalesRepEmployeeNumber((Integer) rs.getObject("salesRepEmployeeNumber"));
        c.setCreditLimit(rs.getBigDecimal("creditLimit"));

        // Geographic coordinates (V8). getBigDecimal returns null when
        // the column is NULL (un-geocoded customer). The frontend
        // hides the map for those rows.
        c.setLat(rs.getBigDecimal("lat"));
        c.setLng(rs.getBigDecimal("lng"));

        // Soft-delete columns (V7). getBoolean returns false for both
        // NULL and 0; the column is NOT NULL with default 1, so this
        // is always a real value.
        c.setActive(rs.getBoolean("active"));
        // getDate() returns null for SQL NULL; convert via toLocalDate()
        // only when present.
        Date td = rs.getDate("terminatedDate");
        c.setTerminatedDate(td == null ? null : td.toLocalDate());

        // Audit fields (V6). Timestamp -> Instant uses toInstant(); for
        // UTC-stored timestamps this round-trips correctly.
        Timestamp createdTs = rs.getTimestamp("createdAt");
        Timestamp updatedTs = rs.getTimestamp("updatedAt");
        c.setCreatedAt(createdTs == null ? null : createdTs.toInstant());
        c.setUpdatedAt(updatedTs == null ? null : updatedTs.toInstant());
        c.setCreatedBy(rs.getString("createdBy"));
        c.setUpdatedBy(rs.getString("updatedBy"));

        // Optimistic-lock version (V6).
        c.setVersion(rs.getInt("version"));
        return c;
    }

    /**
     * Server-side paged + sorted + searched fetch.
     *
     * <p>The WHERE clause is built dynamically: empty when no search term is
     * given, a three-column LIKE filter when one is. Bindings are collected
     * in {@code params} in the same order they appear in the SQL so the
     * positional {@code ps.setObject(idx, ...)} calls below stay in sync.</p>
     *
     * <h3>SQL-injection mitigations</h3>
     *
     * <ul>
     *   <li>{@code search} is bound as a parameter — JDBC escapes it.</li>
     *   <li>{@code sortBy} is filtered through an allow-list switch
     *       because column names cannot be parameterised.</li>
     *   <li>{@code asc} maps to a hard-coded "ASC" / "DESC" literal —
     *       no injection surface.</li>
     * </ul>
     */
    public List<Customer> findAllPaged(int page, int size, String sortBy, boolean asc, String search, String country) {
        if (page < 0 || size <= 0) throw new IllegalArgumentException("Invalid page/size");

        String order = asc ? "ASC" : "DESC";
        String sortColumn = switch (sortBy) {
            case "customerName", "contactLastName", "city", "country", "creditLimit" -> sortBy;
            default -> "customerName";
        };

        // Build WHERE dynamically. Same shape as before: start from
        // active=1, AND search if present, AND country if present.
        // Each filter is independent and order in WHERE doesn't matter
        // for correctness — but the bindings must be appended in the
        // same order as the placeholders.
        StringBuilder where = new StringBuilder("WHERE active = 1");
        List<Object> params = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            where.append(" AND (customerName LIKE ? OR contactLastName LIKE ? OR contactFirstName LIKE ?)");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (country != null && !country.isBlank()) {
            // Exact match on country — no LIKE. Country names in this
            // dataset are inconsistent on case ("USA" vs "Usa") so we
            // case-fold both sides for a forgiving match.
            where.append(" AND LOWER(country) = LOWER(?)");
            params.add(country.trim());
        }

        String sql = ("SELECT * FROM customers %s ORDER BY %s %s LIMIT ? OFFSET ?")
                .formatted(where, sortColumn, order);

        List<Customer> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            // Bind WHERE parameters first (matching their order in the
            // SQL), then size + offset for the LIMIT clause.
            int idx = 1;
            for (Object p : params) ps.setObject(idx++, p);
            ps.setInt(idx++, size);
            ps.setInt(idx, page * size);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Find paged failed", e); }
        return list;
    }

    /**
     * Count customers matching {@code search} and {@code country}. Same
     * dynamic-WHERE pattern as {@link #findAllPaged}, used by the page
     * envelope to populate {@code totalElements}.
     */
    public long countAll(String search, String country) {
        StringBuilder where = new StringBuilder("WHERE active = 1");
        List<Object> params = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            where.append(" AND (customerName LIKE ? OR contactLastName LIKE ? OR contactFirstName LIKE ?)");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (country != null && !country.isBlank()) {
            where.append(" AND LOWER(country) = LOWER(?)");
            params.add(country.trim());
        }

        String sql = "SELECT COUNT(*) FROM customers " + where;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int idx = 1;
            for (Object p : params) ps.setObject(idx++, p);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) { throw new RuntimeException("Count failed", e); }
    }

    public void saveAll(List<Customer> customers) {
        // Same audit-fields treatment as save() — populate createdBy
        // and updatedBy from the security context. We resolve the user
        // ONCE outside the loop because a bulk insert is logically a
        // single user action, not N separate user actions.
        final String currentUser = CurrentUser.username();
        String sql = """
        INSERT INTO customers (
          customerName, contactLastName, contactFirstName,
          phone, addressLine1, addressLine2, city, state, postalCode,
          country, salesRepEmployeeNumber, creditLimit,
          createdBy, updatedBy
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            int batch = 0;
            for (Customer x : customers) {
                ps.setString(1, x.getCustomerName());
                ps.setString(2, x.getContactLastName());
                ps.setString(3, x.getContactFirstName());
                ps.setString(4, x.getPhone());
                ps.setString(5, x.getAddressLine1());
                ps.setString(6, x.getAddressLine2());
                ps.setString(7, x.getCity());
                ps.setString(8, x.getState());
                ps.setString(9, x.getPostalCode());
                ps.setString(10, x.getCountry());
                ps.setObject(11, x.getSalesRepEmployeeNumber());
                ps.setBigDecimal(12, x.getCreditLimit());
                ps.setString(13, currentUser);
                ps.setString(14, currentUser);
                ps.addBatch();
                if (++batch % 500 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Batch insert failed", e);
        }
    }
}
