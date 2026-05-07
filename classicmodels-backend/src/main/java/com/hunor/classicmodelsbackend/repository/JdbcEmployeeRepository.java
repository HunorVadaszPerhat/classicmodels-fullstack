package com.hunor.classicmodelsbackend.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.hunor.classicmodelsbackend.audit.CurrentUser;
import com.hunor.classicmodelsbackend.dto.employee.EmployeeDependentDTO;
import com.hunor.classicmodelsbackend.model.Employee;
import com.hunor.classicmodelsbackend.tx.MyDataSourceUtils;
import com.hunor.classicmodelsbackend.tx.MyTimed;
import com.hunor.classicmodelsbackend.tx.MyTransactional;
import com.hunor.classicmodelsbackend.tx.aspect.AspectTransactional;

/**
 * JDBC implementation of {@link EmployeeRepository}. The bean is given a
 * deliberately implementation-specific name; everything else in the codebase
 * injects the interface, so swapping in (say) a Mockito-backed test double
 * or a JPA-backed implementation later is a one-line change.
 *
 * <p>Marked {@code @Repository} so Spring picks it up during component
 * scanning. The {@link com.hunor.classicmodelsbackend.tx.MyTransactionalBeanPostProcessor}
 * sees the bean has {@code @MyTransactional} methods and an interface, and
 * substitutes a JDK dynamic proxy for it. Anyone autowiring
 * {@link EmployeeRepository} from now on receives the proxy.</p>
 */
@Repository
public class JdbcEmployeeRepository implements EmployeeRepository {

    private final DataSource dataSource;

    public JdbcEmployeeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Employee save(Employee e) {
        // Audit fields are populated automatically. We DON'T put them in
        // the SQL because the DB defaults already do the right thing for
        // the timestamps (CURRENT_TIMESTAMP) — we only need to set the
        // user fields. Setting timestamps explicitly would also work
        // and might be more explicit; either way is correct.
        final String currentUser = CurrentUser.username();
        final String insertAuto = """
            INSERT INTO employees (
                lastName, firstName, extension, email,
                officeCode, reportsTo, jobTitle,
                createdBy, updatedBy
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = MyDataSourceUtils.getConnection(dataSource);
             PreparedStatement ps = conn.prepareStatement(
                     insertAuto,
                     PreparedStatement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, e.getLastName());
            ps.setString(2, e.getFirstName());
            ps.setString(3, e.getExtension());
            ps.setString(4, e.getEmail());
            ps.setString(5, e.getOfficeCode());
            ps.setObject(6, e.getReportsTo());
            ps.setString(7, e.getJobTitle());
            ps.setString(8, currentUser);  // createdBy
            ps.setString(9, currentUser);  // updatedBy (same on first insert)

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) e.setEmployeeNumber(keys.getInt(1));
            }
            return e;
        } catch (SQLException ex) {
            throw new RuntimeException("Insert failed", ex);
        }
    }

    /**
     * Find an employee by ID. Returns soft-deleted (terminated) employees too,
     * because the dependents check and "view detail" flows still need to read
     * them. The list endpoints below filter by {@code active = 1}.
     */
    public Optional<Employee> findById(int id) {
        String sql = "SELECT * FROM employees WHERE employeeNumber = ?";
        try (Connection conn = MyDataSourceUtils.getConnection(dataSource);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Find by ID failed", ex);
        }
    }

    /**
     * Find all <em>active</em> employees. Soft-deleted rows are filtered
     * out so terminated employees disappear from default UI lists.
     * If you ever need an HR audit view that includes terminated rows,
     * add a sibling method (e.g. {@code findAllIncludingTerminated()})
     * with the same SQL minus the {@code WHERE active = 1} clause.
     *
     * <p>Annotated with {@link MyTimed} alone (no transaction) to show
     * timing works on read-only methods too — the chain is just
     * [TimedAdvice, target] in this case.</p>
     */
    @MyTimed
    public List<Employee> findAll() {
        String sql = "SELECT * FROM employees WHERE active = 1";
        List<Employee> list = new ArrayList<>();
        try (Connection conn = MyDataSourceUtils.getConnection(dataSource);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(mapRow(rs));
            return list;
        } catch (SQLException ex) {
            throw new RuntimeException("Find all failed", ex);
        }
    }

    /**
     * Find active employees assigned to a particular office. Same shape
     * as {@link #findAll()}, plus one parameterised WHERE predicate.
     */
    @Override
    public List<Employee> findByOfficeCode(String officeCode) {
        String sql = "SELECT * FROM employees WHERE active = 1 AND officeCode = ?";
        List<Employee> list = new ArrayList<>();
        try (Connection conn = MyDataSourceUtils.getConnection(dataSource);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, officeCode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException ex) {
            throw new RuntimeException("Find by office failed", ex);
        }
    }

    /**
     * Optimistic-lock-aware update.
     *
     * <p>Three new pieces compared to the pre-V5 version:</p>
     * <ul>
     *   <li>{@code AND version = ?} in the WHERE — the row must still
     *       be at the version the client read.</li>
     *   <li>{@code version = version + 1} in the SET — bumping the
     *       version is part of the same atomic UPDATE.</li>
     *   <li>{@code executeUpdate()} returns the affected-row count.
     *       0 means "WHERE matched no rows," which under our schema
     *       almost always means "version didn't match" — i.e., someone
     *       else updated this row in the meantime. We translate that to
     *       Spring's {@link OptimisticLockingFailureException}, which
     *       a controller advice maps to HTTP 409 Conflict.</li>
     * </ul>
     */
    public void update(Employee e) {
        final String currentUser = CurrentUser.username();
        String sql = """
            UPDATE employees SET
                lastName = ?, firstName = ?, extension = ?, email = ?,
                officeCode = ?, reportsTo = ?, jobTitle = ?,
                updatedBy = ?,
                version = version + 1
            WHERE employeeNumber = ?
              AND version = ?
            """;
        try (Connection conn = MyDataSourceUtils.getConnection(dataSource);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, e.getLastName());
            ps.setString(2, e.getFirstName());
            ps.setString(3, e.getExtension());
            ps.setString(4, e.getEmail());
            ps.setString(5, e.getOfficeCode());
            ps.setObject(6, e.getReportsTo());
            ps.setString(7, e.getJobTitle());
            ps.setString(8, currentUser);
            ps.setInt(9, e.getEmployeeNumber());
            ps.setInt(10, e.getVersion());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                // Could be one of two things: row doesn't exist, or
                // version mismatch. We disambiguate by checking
                // existence — that gives a more accurate error message
                // and matches what Spring Data JPA does internally.
                boolean exists = findById(e.getEmployeeNumber()).isPresent();
                if (!exists) {
                    throw new RuntimeException(
                            "Employee " + e.getEmployeeNumber() + " not found");
                }
                throw new org.springframework.dao.OptimisticLockingFailureException(
                        "Employee " + e.getEmployeeNumber() + " was modified by someone else "
                        + "(stale version " + e.getVersion() + "). Reload and try again.");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Update failed", ex);
        }
    }

    /**
     * Plain hard delete. Will throw if any FK still points at the row,
     * which is exactly the situation the new strategy methods solve.
     * Kept for completeness — callers should generally use the strategy
     * methods below instead.
     */
    public void delete(int id) {
        String sql = "DELETE FROM employees WHERE employeeNumber = ?";
        try (Connection conn = MyDataSourceUtils.getConnection(dataSource);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Delete failed", ex);
        }
    }

    // -----------------------------------------------------------------
    //  Strategy 1: SOFT DELETE
    // -----------------------------------------------------------------
    /**
     * Mark the employee as terminated. No row is removed, no FK is touched.
     * This is the recommended option for HR-style data — preserves history,
     * keeps customers/orders/payments analytically valid, fully reversible.
     */
    public void softDelete(int id) {
        String sql = """
            UPDATE employees
               SET active = 0,
                   terminatedDate = CURRENT_DATE
             WHERE employeeNumber = ?
            """;
        try (Connection conn = MyDataSourceUtils.getConnection(dataSource);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Soft delete failed", ex);
        }
    }

    // -----------------------------------------------------------------
    //  Strategy 2: HARD DELETE + NULLIFY REFERENCES
    // -----------------------------------------------------------------
    /**
     * Hard delete the employee, after first NULL-ing any FK that points
     * at them. Customers lose their sales rep link; direct reports lose
     * their manager link. No child rows are removed, so orders/payments
     * history stays intact.
     *
     * <p>Notice the body: just three SQL statements. The transaction
     * choreography (autoCommit=false, commit on success, rollback on
     * exception, restore autoCommit, close connection) has moved into
     * {@link com.hunor.classicmodelsbackend.tx.MyTransactionInterceptor}.
     * This is exactly the boilerplate-removal payoff of @Transactional —
     * what was 25 lines is now 12, and the intent is no longer hidden
     * behind nested try/catch/finally.</p>
     */
    /**
     * Both annotations on the same method demonstrate the chain in
     * action: timing wraps the transaction, so the elapsed-time log
     * line includes the cost of {@code commit()} (or {@code rollback()}
     * on failure).
     */
    @MyTimed
    @MyTransactional
    public void deleteAndNullify(int id) {
        try (Connection conn = MyDataSourceUtils.getConnection(dataSource)) {

            // 1. Detach customers from this sales rep.
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE customers SET salesRepEmployeeNumber = NULL WHERE salesRepEmployeeNumber = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            // 2. Detach direct reports.
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE employees SET reportsTo = NULL WHERE reportsTo = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            // 3. Now the row is unreferenced and the DELETE can succeed.
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM employees WHERE employeeNumber = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            // The interceptor sees this re-thrown and rolls back the tx.
            throw new RuntimeException("Delete (nullify) failed", ex);
        }
    }

    // -----------------------------------------------------------------
    //  Strategy 3: HARD DELETE + CASCADE
    // -----------------------------------------------------------------
    /**
     * Hard delete the employee AND the customers that referenced them.
     * Direct reports are NOT deleted (cascading the org chart by
     * accident is never what you want); their {@code reportsTo} is
     * nulled out, same as the nullify strategy.
     *
     * <h4>Why this can fail — and why that's intentional</h4>
     * <p>Customers themselves have children: {@code orders.customerNumber}
     * and {@code payments.customerNumber}. Trying to DELETE a customer
     * row that still has orders or payments will raise an
     * {@code SQLIntegrityConstraintViolationException}. We deliberately
     * do not propagate the cascade further — financial history must
     * never be silently destroyed by a "cleanup" UI button.</p>
     *
     * <p>So the actual contract of this method is: succeeds only when
     * the employee's customers have no orders and no payments. In every
     * other case the entire transaction rolls back and the caller sees
     * an error. The frontend translates that into a useful message.</p>
     */
    /**
     * Demonstrates side-by-side coexistence: this method's transaction
     * is managed by Spring AOP via {@link AspectTransactional}, while
     * its siblings ({@code deleteAndNullify}, {@code deleteAndDeepCascade})
     * still go through our hand-rolled {@code @MyTransactional} chain.
     * Both use {@link MyDataSourceUtils} for connection binding, so the
     * underlying JDBC code is unchanged.
     */
    @AspectTransactional
    public void deleteAndCascade(int id) {
        try (Connection conn = MyDataSourceUtils.getConnection(dataSource)) {

            // 1. Delete the customer rows that reference this employee.
            //    If those customers have orders/payments, this throws
            //    a constraint violation and the interceptor rolls back.
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM customers WHERE salesRepEmployeeNumber = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            // 2. Null out the self-reference for direct reports.
            //    Cascading the org chart (deleting subordinates) would
            //    destroy historically-meaningful data. Nullifying
            //    leaves them as "no manager" — a recoverable state.
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE employees SET reportsTo = NULL WHERE reportsTo = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            // 3. Finally, remove the employee.
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM employees WHERE employeeNumber = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Delete (cascade) failed", ex);
        }
    }

    // -----------------------------------------------------------------
    //  Strategy 4: HARD DELETE + DEEP CASCADE  (destructive)
    // -----------------------------------------------------------------
    /**
     * Walk the entire foreign-key tree under this employee and delete
     * every row found, in dependency order:
     *
     * <pre>
     *   employee
     *     └─ customers (where salesRepEmployeeNumber = ?)
     *          ├─ orders        (where customerNumber = c.customerNumber)
     *          │    └─ orderdetails (where orderNumber = o.orderNumber)
     *          └─ payments      (where customerNumber = c.customerNumber)
     *   employees.reportsTo  → set to NULL (subordinates kept)
     *   employee row         → deleted
     * </pre>
     *
     * <h4>Why the order matters</h4>
     * <p>Children first, parents last. {@code orderdetails} reference
     * {@code orders} so they must be deleted before orders can go.
     * {@code orders} and {@code payments} both reference {@code customers}
     * so they must be deleted before customers. {@code customers} reference
     * {@code employees} so they must be deleted before the employee row.
     * Get this order wrong and InnoDB will reject the delete with the
     * same FK constraint violation we've been working around all along.</p>
     *
     * <h4>Why set-based, not row-by-row</h4>
     * <p>We could iterate through customers in Java and delete each one's
     * orders. Instead we use a single nested {@code IN (subquery)}
     * statement per layer. That's both faster (one round trip per layer
     * instead of N) and atomic — there's no intermediate state where
     * "some orders for customer X are gone, others remain" if the JVM
     * dies mid-iteration. The whole thing is also wrapped in one
     * transaction so a failure at any layer rolls everything back.</p>
     *
     * <h4>Direct reports</h4>
     * <p>Even in this destructive mode we deliberately do NOT cascade
     * the {@code employees.reportsTo} self-reference. Deleting an
     * employee's subordinates because their manager left would be a
     * bug, never a feature. They are nulled out, same as the gentler
     * strategies.</p>
     *
     * <h4>Use very sparingly</h4>
     * <p>This destroys financial history. The {@code DeleteStrategy}
     * Javadoc and the service-layer feature flag both reinforce why
     * this should almost never run in a real environment.</p>
     */
    /**
     * Strategy 5 — reassign + delete.
     *
     * <p>Three statements, one transaction. The reassignments run first
     * (so the source employee has no FK references), then the DELETE.
     * If anything fails at any step, the whole transaction rolls back
     * and nothing changes.</p>
     *
     * <p>Worth noting: there's no version-check here. Reassignment
     * isn't a content edit, it's a structural change. We could add
     * optimistic locking on the source employee if we wanted to (and
     * for production-grade code you would), but for now the simpler
     * "just do it" semantics keeps the code easy to read.</p>
     */
    @MyTransactional
    public void reassignAndDelete(int sourceId, int targetId) {
        if (sourceId == targetId) {
            throw new IllegalArgumentException(
                    "Cannot reassign an employee to themselves");
        }
        try (Connection conn = MyDataSourceUtils.getConnection(dataSource)) {

            // 1. Hand customers to the new sales rep.
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE customers SET salesRepEmployeeNumber = ? WHERE salesRepEmployeeNumber = ?")) {
                ps.setInt(1, targetId);
                ps.setInt(2, sourceId);
                ps.executeUpdate();
            }

            // 2. Hand direct reports to the new manager.
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE employees SET reportsTo = ? WHERE reportsTo = ?")) {
                ps.setInt(1, targetId);
                ps.setInt(2, sourceId);
                ps.executeUpdate();
            }

            // 3. Now safe to delete — no FKs reference the source row.
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM employees WHERE employeeNumber = ?")) {
                ps.setInt(1, sourceId);
                int affected = ps.executeUpdate();
                if (affected == 0) {
                    // Either the source didn't exist to begin with, or
                    // someone else deleted it between our two queries.
                    // Either way, the transaction will be rolled back.
                    throw new RuntimeException(
                            "Employee " + sourceId + " not found for deletion");
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Reassign-and-delete failed", ex);
        }
    }

    @MyTimed
    @MyTransactional
    public void deleteAndDeepCascade(int id) {
        // The customer subquery is reused across orderdetails, orders,
        // and payments deletes. Defining it once keeps the layers in sync.
        final String customerSubquery =
                "(SELECT customerNumber FROM customers WHERE salesRepEmployeeNumber = ?)";

        try (Connection conn = MyDataSourceUtils.getConnection(dataSource)) {

            // 1. orderdetails — grandchildren of customers via orders.
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM orderdetails " +
                    "WHERE orderNumber IN (" +
                    "  SELECT orderNumber FROM orders " +
                    "  WHERE customerNumber IN " + customerSubquery +
                    ")")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            // 2. orders — children of customers.
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM orders WHERE customerNumber IN " + customerSubquery)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            // 3. payments — siblings of orders, also children of customers.
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM payments WHERE customerNumber IN " + customerSubquery)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            // 4. customers themselves. Now safe — orders/payments cleared.
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM customers WHERE salesRepEmployeeNumber = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            // 5. Self-referential FK: subordinates kept, link severed.
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE employees SET reportsTo = NULL WHERE reportsTo = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            // 6. Finally the employee row itself.
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM employees WHERE employeeNumber = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Delete (deep cascade) failed", ex);
        }
    }

    /** Maximum number of preview rows fetched per child table. */
    private static final int RECORD_PREVIEW_LIMIT = 25;

    /**
     * Returns every child table that has at least one row referencing
     * the given employee, along with the count and a small sample of
     * actual rows (id + human-readable label).
     *
     * <h4>Discovery vs. labeling</h4>
     * <p>Discovery (which tables/columns reference the employee) stays
     * fully dynamic via {@code INFORMATION_SCHEMA.KEY_COLUMN_USAGE}, so
     * adding a new FK to the schema is automatically reflected here.</p>
     *
     * <p>Labeling (which column to display) cannot be inferred — different
     * tables have different "name" columns. We keep a small hardcoded
     * map of {@code table -> SELECT clause} for tables we know about. If
     * a previously-unknown FK shows up the count is still returned, but
     * with an empty record list and an "(unknown table — extend
     * EmployeeRepository.previewQueryFor to add labels)" placeholder.</p>
     */
    public List<EmployeeDependentDTO> findDependents(int id) {
        final String fkLookupSql = """
            SELECT TABLE_NAME, COLUMN_NAME
            FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
            WHERE REFERENCED_TABLE_SCHEMA = DATABASE()
              AND REFERENCED_TABLE_NAME   = 'employees'
              AND REFERENCED_COLUMN_NAME  = 'employeeNumber'
            """;

        List<EmployeeDependentDTO> dependents = new ArrayList<>();

        try (Connection conn = MyDataSourceUtils.getConnection(dataSource)) {

            // Step 1: find all child tables/columns referencing employeeNumber.
            List<String[]> fks = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(fkLookupSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    fks.add(new String[]{
                            rs.getString("TABLE_NAME"),
                            rs.getString("COLUMN_NAME")
                    });
                }
            }

            // Step 2: for each child table, count rows AND fetch up to
            // RECORD_PREVIEW_LIMIT sample rows for display in the UI.
            // Table/column names come from INFORMATION_SCHEMA (trusted),
            // but they're concatenated into SQL, so we still defensively
            // whitelist them with isSafeIdentifier.
            for (String[] fk : fks) {
                String table = fk[0];
                String column = fk[1];

                if (!isSafeIdentifier(table) || !isSafeIdentifier(column)) {
                    continue;
                }

                String countSql = "SELECT COUNT(*) FROM `" + table + "` WHERE `" + column + "` = ?";
                long count;
                try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) continue;
                        count = rs.getLong(1);
                    }
                }
                if (count == 0) continue;

                List<EmployeeDependentDTO.DependentRecord> records =
                        fetchPreview(conn, table, column, id);

                dependents.add(new EmployeeDependentDTO(table, column, count, records));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Find dependents failed", ex);
        }
        return dependents;
    }

    /**
     * Pull a small preview of rows from a child table that reference the
     * given employee, returning each row as {@code (id, label)} for the UI.
     *
     * <p>The {@code SELECT idColumn, labelExpression} clause is table-specific
     * — see {@link #previewQueryFor}. The {@code WHERE column = ?} part is
     * dynamic but parameterised (the id is bound, not concatenated).</p>
     */
    private List<EmployeeDependentDTO.DependentRecord> fetchPreview(
            Connection conn, String table, String column, int id) throws SQLException {

        String selectClause = previewQueryFor(table);
        if (selectClause == null) {
            // Unknown table — return one synthetic placeholder row so the UI
            // can render something meaningful instead of an empty list.
            return List.of(new EmployeeDependentDTO.DependentRecord(
                    -1, "(no preview available for table '" + table + "')"));
        }

        String sql = "SELECT " + selectClause
                + " FROM `" + table + "`"
                + " WHERE `" + column + "` = ?"
                + " LIMIT " + RECORD_PREVIEW_LIMIT;

        List<EmployeeDependentDTO.DependentRecord> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int recordId = rs.getInt(1);
                    String label = rs.getString(2);
                    out.add(new EmployeeDependentDTO.DependentRecord(recordId, label));
                }
            }
        }
        return out;
    }

    /**
     * Returns the {@code "idColumn, labelExpression"} fragment to plug
     * into {@code SELECT ... FROM <table>} for previewing rows of that
     * table. {@code null} means we don't know how to label that table.
     *
     * <p>Add a case here whenever a new FK to {@code employees.employeeNumber}
     * is introduced. Keeping this short table is intentional — it makes
     * the "what does the UI show?" decision explicit per table.</p>
     *
     * <p>The lookup is case-insensitive because MySQL's
     * {@code INFORMATION_SCHEMA.KEY_COLUMN_USAGE.TABLE_NAME} can come back
     * in different cases depending on the {@code lower_case_table_names}
     * server setting and OS — relying on exact case is brittle.</p>
     */
    private static String previewQueryFor(String tableName) {
        if (tableName == null) return null;
        return switch (tableName.trim().toLowerCase()) {
            case "customers" ->
                    "customerNumber, customerName";
            case "employees" ->
                    "employeeNumber, CONCAT(firstName, ' ', lastName)";
            default -> null;
        };
    }

    private static boolean isSafeIdentifier(String identifier) {
        return identifier != null && identifier.matches("[A-Za-z0-9_]+");
    }

    private Employee mapRow(ResultSet rs) throws SQLException {
        var e = new Employee();
        e.setEmployeeNumber(rs.getInt("employeeNumber"));
        e.setLastName(rs.getString("lastName"));
        e.setFirstName(rs.getString("firstName"));
        e.setExtension(rs.getString("extension"));
        e.setEmail(rs.getString("email"));
        e.setOfficeCode(rs.getString("officeCode"));
        e.setReportsTo((Integer) rs.getObject("reportsTo"));
        e.setJobTitle(rs.getString("jobTitle"));
        // The soft-delete columns are added by Flyway V2.
        // getBoolean() returns false for both NULL and 0, but the column is
        // NOT NULL with default 1, so this is always a real value.
        e.setActive(rs.getBoolean("active"));
        // getDate() can return null when the column is NULL — convert via
        // toLocalDate() only when present.
        java.sql.Date td = rs.getDate("terminatedDate");
        e.setTerminatedDate(td == null ? null : td.toLocalDate());

        // Audit fields (V4). Timestamp → Instant uses toInstant() which
        // anchors at the JVM's system time zone; for UTC-stored
        // timestamps (which is what we want) this round-trips correctly.
        java.sql.Timestamp createdTs = rs.getTimestamp("createdAt");
        java.sql.Timestamp updatedTs = rs.getTimestamp("updatedAt");
        e.setCreatedAt(createdTs == null ? null : createdTs.toInstant());
        e.setUpdatedAt(updatedTs == null ? null : updatedTs.toInstant());
        e.setCreatedBy(rs.getString("createdBy"));
        e.setUpdatedBy(rs.getString("updatedBy"));

        // Optimistic-lock version (V5).
        e.setVersion(rs.getInt("version"));
        return e;
    }

    /**
     * Server-side pagination + sorting + search.
     *
     * <h4>Why a method this long?</h4>
     *
     * <p>The query is dynamic: depending on whether {@code search} is
     * provided, the WHERE clause includes an extra LIKE predicate. We
     * build the SQL string in two halves (WHERE clause first, then ORDER
     * BY + LIMIT) and bind parameters in the same order we appended them.
     * This is the JDBC way to do "optional WHERE clauses" — verbose but
     * dead simple to debug.</p>
     *
     * <h4>SQL injection — three different patterns</h4>
     *
     * <ul>
     *   <li>{@code search} is a free-form string from the user: bind it
     *       via {@code ps.setString(...)} so MySQL escapes it. Never
     *       concatenate.</li>
     *   <li>{@code sortBy} is also free-form-ish (comes from a query
     *       param) but BIND PARAMETERS DO NOT WORK FOR ORDER BY. So we
     *       use an allow-list switch: only known column names are
     *       accepted, anything else falls back to the default. This is
     *       the standard mitigation.</li>
     *   <li>{@code asc} is just a boolean → "ASC" / "DESC" — no
     *       injection surface.</li>
     * </ul>
     */
    @Override
    public List<Employee> findAllPaged(int page, int size, String sortBy, boolean asc, String search) {
        if (page < 0 || size <= 0) throw new IllegalArgumentException("Invalid page/size");

        String order = asc ? "ASC" : "DESC";
        String sortColumn = switch (sortBy) {
            case "lastName", "firstName", "jobTitle", "officeCode" -> sortBy;
            default -> "lastName";
        };

        // Build the WHERE clause dynamically. We collect bound values in
        // a List in the same order they appear in the SQL so the
        // ps.setString(i, ...) calls below match positions correctly.
        StringBuilder where = new StringBuilder("WHERE active = 1");
        List<Object> params = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            where.append(" AND (lastName LIKE ? OR firstName LIKE ? OR email LIKE ?)");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }

        String sql = ("SELECT * FROM employees %s ORDER BY %s %s LIMIT ? OFFSET ?")
                .formatted(where, sortColumn, order);

        List<Employee> list = new ArrayList<>();
        try (Connection c = MyDataSourceUtils.getConnection(dataSource);
             PreparedStatement ps = c.prepareStatement(sql)) {

            // Bind the search parameters first (in the order they appear
            // in the WHERE), then size + offset for the LIMIT clause.
            int idx = 1;
            for (Object p : params) ps.setObject(idx++, p);
            ps.setInt(idx++, size);
            ps.setInt(idx, page * size);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find paged failed", e);
        }
        return list;
    }

    /**
     * Count active employees matching {@code search}. Same dynamic-WHERE
     * pattern as {@link #findAllPaged}.
     */
    @Override
    public long countAll(String search) {
        StringBuilder where = new StringBuilder("WHERE active = 1");
        List<Object> params = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            where.append(" AND (lastName LIKE ? OR firstName LIKE ? OR email LIKE ?)");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }

        String sql = "SELECT COUNT(*) FROM employees " + where;
        try (Connection c = MyDataSourceUtils.getConnection(dataSource);
             PreparedStatement ps = c.prepareStatement(sql)) {
            int idx = 1;
            for (Object p : params) ps.setObject(idx++, p);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Count failed", e);
        }
    }

    public void saveAll(List<Employee> employees) {
        // Same audit-fields treatment as save() — populate createdBy
        // and updatedBy from the security context. We resolve the user
        // ONCE outside the loop because a bulk insert is logically a
        // single user action, not N separate user actions.
        final String currentUser = CurrentUser.username();
        final String insertAuto = """
            INSERT INTO employees (
                lastName, firstName, extension, email,
                officeCode, reportsTo, jobTitle,
                createdBy, updatedBy
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = MyDataSourceUtils.getConnection(dataSource)) {
            for (Employee e : employees) {
                try (PreparedStatement ps = conn.prepareStatement(
                        insertAuto,
                        PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, e.getLastName());
                    ps.setString(2, e.getFirstName());
                    ps.setString(3, e.getExtension());
                    ps.setString(4, e.getEmail());
                    ps.setString(5, e.getOfficeCode());
                    ps.setObject(6, e.getReportsTo());
                    ps.setString(7, e.getJobTitle());
                    ps.setString(8, currentUser);
                    ps.setString(9, currentUser);

                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) e.setEmployeeNumber(keys.getInt(1));
                    }
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Batch insert (employees) failed", ex);
        }
    }
}
