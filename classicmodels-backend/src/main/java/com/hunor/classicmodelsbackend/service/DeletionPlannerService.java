package com.hunor.classicmodelsbackend.service;

import com.hunor.classicmodelsbackend.dto.deletion.DeletionPlan;
import com.hunor.classicmodelsbackend.dto.deletion.DeletionStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only deletion planner.
 *
 * <p>Given any {@code (table, idColumn, id)} triple, this service walks
 * {@code INFORMATION_SCHEMA} recursively and produces a topologically-sorted
 * list of SQL statements that <em>would</em> run if a deep cascade delete
 * were executed against that entity. Nothing is actually executed; the only
 * database calls made are metadata lookups against
 * {@code INFORMATION_SCHEMA.KEY_COLUMN_USAGE} plus a {@code SELECT COUNT(*)}
 * per discovered step to populate the projected row counts.</p>
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>For the root entity, list every FK that references it
 *       (children).</li>
 *   <li>For each child, recurse first (so its grandchildren are emitted
 *       before its own delete), then emit a DELETE step targeting the
 *       child filtered by an {@code IN (subquery)} that traces back
 *       through the parent chain to the root id.</li>
 *   <li>Self-referencing FKs ({@code childTable == parentTable}) are
 *       handled with a NULLIFY step, never a DELETE — cascading the
 *       org chart is never the right answer, and a literal recursion
 *       would loop forever anyway.</li>
 *   <li>Composite FKs are skipped with a WARNING step. Real schemas
 *       use them; this implementation deliberately doesn't, to keep
 *       the SQL generation readable.</li>
 *   <li>The final step is the DELETE of the root row itself.</li>
 * </ol>
 *
 * <h3>Why generate, why not execute?</h3>
 * <p>The dangerous part of deep cascade is that the blast radius is
 * invisible until runtime. Producing a plan as data, separate from any
 * executor, makes the consequences inspectable: the UI (or your debug
 * console, or a test) can render the plan, sum the rows, and confirm
 * with a human <em>before</em> wiring it into anything that destroys.</p>
 */
@Service
@Slf4j
public class DeletionPlannerService {

    private final DataSource dataSource;

    public DeletionPlannerService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Build a deletion plan for the given entity. See class-level docs.
     *
     * @param rootTable   the table the entity lives in
     * @param rootColumn  the PK column used to identify the row (typically
     *                    the {@code id}, but accepted explicitly so the
     *                    caller can pick a unique-key column too)
     * @param rootId      the value to filter on; bound as a parameter to
     *                    every generated statement
     */
    public DeletionPlan plan(String rootTable, String rootColumn, Object rootId) {
        // Defensive whitelist on identifiers. They go straight into SQL —
        // never trust client-supplied table/column names blindly, even
        // though any sane caller already validates them upstream.
        if (!isSafeIdentifier(rootTable) || !isSafeIdentifier(rootColumn)) {
            throw new IllegalArgumentException(
                    "Table and column names must match [A-Za-z0-9_]+: got "
                            + rootTable + "/" + rootColumn);
        }

        try (Connection conn = dataSource.getConnection()) {
            // The visited set is keyed by FK CONSTRAINT_NAME so each FK is
            // discovered at most once even if the same table is reachable
            // via multiple paths (diamond shape).
            Set<String> visitedConstraints = new HashSet<>();

            // Steps are appended in execution order: deepest leaves first,
            // accumulated by recursion, with the root's own DELETE pushed
            // on at the end below.
            List<DeletionStep> steps = new ArrayList<>();

            // The "match where" is the SQL fragment that identifies which
            // rows in `parentTable` we're talking about. For the root, it's
            // simply `<idCol> = ?`. As we descend, each child wraps it in
            // an `IN (SELECT ... FROM parent WHERE <parent-match>)`.
            String rootMatchWhere = "`" + rootColumn + "` = ?";

            collectCascadeSteps(conn, rootTable, rootMatchWhere, /*depth*/ 0,
                    visitedConstraints, steps, rootId);

            // Root's own delete comes last. The WHERE clause is the same
            // single-column predicate; one ? placeholder, bound to rootId.
            String rootSql = "DELETE FROM `" + rootTable + "` WHERE " + rootMatchWhere;
            long rootCount = countRows(conn,
                    "SELECT COUNT(*) FROM `" + rootTable + "` WHERE " + rootMatchWhere,
                    rootId);
            steps.add(new DeletionStep(
                    /*order – fixed up below*/ 0,
                    DeletionStep.Kind.DELETE,
                    rootTable,
                    /*depth*/ 0,
                    rootSql,
                    rootCount,
                    "Delete the root row from " + rootTable
            ));

            // Renumber steps with a stable 1-based order. Done as a final
            // pass so we don't have to thread a counter through recursion.
            List<DeletionStep> ordered = new ArrayList<>(steps.size());
            for (int i = 0; i < steps.size(); i++) {
                DeletionStep s = steps.get(i);
                ordered.add(new DeletionStep(
                        i + 1, s.kind(), s.tableName(), s.depth(),
                        s.sql(), s.expectedRows(), s.summary()));
            }

            // Total affected = sum across DELETE+NULLIFY only. WARNING
            // steps are diagnostics, they wouldn't run.
            long total = ordered.stream()
                    .filter(s -> s.kind() != DeletionStep.Kind.WARNING)
                    .mapToLong(DeletionStep::expectedRows)
                    .sum();

            return new DeletionPlan(rootTable, rootColumn, rootId,
                    ordered.size(), total, ordered);

        } catch (SQLException ex) {
            throw new RuntimeException("Plan generation failed", ex);
        }
    }

    // -----------------------------------------------------------------
    //  Recursion: collect cascade steps for descendants of `parentTable`.
    // -----------------------------------------------------------------
    /**
     * Append steps describing every descendant of {@code parentTable}
     * (children, grandchildren, etc.) into {@code outSteps}, in
     * execution order (deepest first). The parent's own DELETE step is
     * <em>not</em> added by this method — the caller appends it after
     * recursion finishes, ensuring children are deleted before the parent.
     */
    private void collectCascadeSteps(Connection conn,
                                     String parentTable,
                                     String parentMatchWhere,
                                     int depth,
                                     Set<String> visitedConstraints,
                                     List<DeletionStep> outSteps,
                                     Object rootId) throws SQLException {

        List<FkRef> fks = lookupFksReferencing(conn, parentTable);

        // Group FK columns by constraint name so composite FKs surface
        // as a single logical edge (with multiple columns) rather than
        // one edge per column.
        Map<String, List<FkRef>> byConstraint = new LinkedHashMap<>();
        for (FkRef fk : fks) {
            byConstraint.computeIfAbsent(fk.constraintName(), k -> new ArrayList<>()).add(fk);
        }

        for (Map.Entry<String, List<FkRef>> entry : byConstraint.entrySet()) {
            String constraintName = entry.getKey();
            List<FkRef> cols = entry.getValue();

            if (visitedConstraints.contains(constraintName)) continue;
            visitedConstraints.add(constraintName);

            // -------------------------------------------------------
            // Composite FK — emit a WARNING step and skip.
            //
            // Generating SQL for composite FKs requires row-constructor
            // IN clauses, e.g. `(a, b) IN (SELECT (x, y) FROM ...)`,
            // which not all dialects support uniformly. classicmodels
            // doesn't have any in the chains we care about, so the
            // educational version stops here rather than implementing
            // a feature it'd never exercise.
            // -------------------------------------------------------
            if (cols.size() > 1) {
                FkRef first = cols.get(0);
                outSteps.add(new DeletionStep(
                        0, DeletionStep.Kind.WARNING,
                        first.childTable(), depth + 1,
                        "/* skipped: composite FK '" + constraintName +
                                "' on " + first.childTable() +
                                " — planner does not support composite keys */",
                        0,
                        "Composite FK skipped (" + first.childTable() + ")"
                ));
                continue;
            }

            FkRef fk = cols.get(0);

            // The WHERE clause for any operation on this child table.
            // It says "match the rows of childTable whose FK column
            // points at a row of parentTable that is itself being
            // deleted." `parentMatchWhere` already encodes the chain
            // back to the root; we just nest one more level.
            String childWhereSql =
                    "`" + fk.childColumn() + "` IN (" +
                            "SELECT `" + fk.referencedColumn() + "` " +
                            "FROM `" + parentTable + "` " +
                            "WHERE " + parentMatchWhere +
                            ")";

            // Self-reference: NULLIFY rather than recurse-and-delete.
            if (fk.childTable().equals(parentTable)) {
                String sql = "UPDATE `" + fk.childTable() + "` " +
                        "SET `" + fk.childColumn() + "` = NULL " +
                        "WHERE " + childWhereSql;
                long count = countRows(conn,
                        "SELECT COUNT(*) FROM `" + fk.childTable() + "` WHERE " + childWhereSql,
                        rootId);
                outSteps.add(new DeletionStep(
                        0, DeletionStep.Kind.NULLIFY,
                        fk.childTable(), depth + 1,
                        sql, count,
                        "Null self-reference column " + fk.childColumn() +
                                " on " + fk.childTable()
                ));
                continue;
            }

            // Recurse to this child's children FIRST so their DELETE
            // steps land before this child's own DELETE (leaves first).
            collectCascadeSteps(conn, fk.childTable(), childWhereSql,
                    depth + 1, visitedConstraints, outSteps, rootId);

            // Then this child's own DELETE.
            String sql = "DELETE FROM `" + fk.childTable() + "` WHERE " + childWhereSql;
            long count = countRows(conn,
                    "SELECT COUNT(*) FROM `" + fk.childTable() + "` WHERE " + childWhereSql,
                    rootId);
            outSteps.add(new DeletionStep(
                    0, DeletionStep.Kind.DELETE,
                    fk.childTable(), depth + 1,
                    sql, count,
                    "Delete from " + fk.childTable() + " via " + fk.childColumn()
            ));
        }
    }

    // -----------------------------------------------------------------
    //  INFORMATION_SCHEMA: list FKs that reference a given parent table.
    // -----------------------------------------------------------------
    /**
     * Return one {@link FkRef} per FK column that references {@code parentTable}
     * (composite FKs produce one row per column, with a shared CONSTRAINT_NAME).
     * Limited to the current database via {@code DATABASE()} to avoid
     * leaking metadata across schemas on shared MySQL instances.
     */
    private List<FkRef> lookupFksReferencing(Connection conn, String parentTable) throws SQLException {
        final String sql = """
            SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_COLUMN_NAME, CONSTRAINT_NAME, ORDINAL_POSITION
            FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
            WHERE REFERENCED_TABLE_SCHEMA = DATABASE()
              AND REFERENCED_TABLE_NAME = ?
            ORDER BY CONSTRAINT_NAME, ORDINAL_POSITION
            """;

        List<FkRef> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, parentTable);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String childTable = rs.getString("TABLE_NAME");
                    String childColumn = rs.getString("COLUMN_NAME");
                    String refColumn = rs.getString("REFERENCED_COLUMN_NAME");
                    String constraintName = rs.getString("CONSTRAINT_NAME");

                    // Defensive: skip any FK whose names don't pass the
                    // identifier whitelist. Should never happen against
                    // a real schema; here purely as a safety net.
                    if (!isSafeIdentifier(childTable) ||
                        !isSafeIdentifier(childColumn) ||
                        !isSafeIdentifier(refColumn)) {
                        log.warn("Skipping FK with unsafe identifier(s): {}.{} -> {}",
                                childTable, childColumn, refColumn);
                        continue;
                    }
                    out.add(new FkRef(childTable, childColumn, refColumn, constraintName));
                }
            }
        }
        return out;
    }

    // -----------------------------------------------------------------
    //  Count helper: run SELECT COUNT(*) on the same WHERE.
    // -----------------------------------------------------------------
    /**
     * Bind {@code rootId} (always exactly one ? placeholder) and return
     * {@code COUNT(*)}. If the WHERE clause produces zero rows, returns 0.
     */
    private long countRows(Connection conn, String countSql, Object rootId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(countSql)) {
            ps.setObject(1, rootId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    // -----------------------------------------------------------------
    //  Identifier whitelist — same rule used in EmployeeRepository.
    // -----------------------------------------------------------------
    private static boolean isSafeIdentifier(String identifier) {
        return identifier != null && identifier.matches("[A-Za-z0-9_]+");
    }

    // -----------------------------------------------------------------
    //  Internal record holding one FK column reference.
    // -----------------------------------------------------------------
    /**
     * One column of one FK constraint: the child table/column and the
     * parent column it references. The {@code constraintName} groups
     * columns that belong to the same composite FK.
     */
    private record FkRef(String childTable, String childColumn,
                         String referencedColumn, String constraintName) {}
}
