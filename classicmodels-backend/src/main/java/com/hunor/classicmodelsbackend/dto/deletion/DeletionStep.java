package com.hunor.classicmodelsbackend.dto.deletion;

/**
 * One step in a {@link DeletionPlan}: a single SQL statement the planner
 * believes would have to run, in this position, if a generic deep cascade
 * deletion were executed against the root entity.
 *
 * <p>This is a <em>read-only</em> view. The planner never executes these
 * statements — it only generates them and reports their projected impact
 * (the {@code expectedRows} value comes from a corresponding
 * {@code SELECT COUNT(*)} executed against the same WHERE clause).</p>
 *
 * @param order        1-based execution order. The first row is the deepest leaf,
 *                     the last row is the root entity itself.
 * @param kind         what kind of statement this step represents
 *                     ({@link Kind#DELETE}, {@link Kind#NULLIFY}, {@link Kind#WARNING}).
 * @param tableName    the table this step targets (the table being modified,
 *                     not any tables joined into the WHERE clause).
 * @param depth        distance from the root in the FK tree. Root = 0, direct
 *                     children = 1, grandchildren = 2, etc. Useful for
 *                     indenting the plan in the UI.
 * @param sql          the exact SQL the executor would run. Exactly one
 *                     {@code ?} placeholder, which the executor binds to the
 *                     root id (the outermost {@code IN (subquery)} chain
 *                     anchors at the root).
 * @param expectedRows projected number of rows the step would affect, computed
 *                     by running the SELECT-COUNT analogue of the same WHERE.
 * @param summary      human-readable one-liner. The UI can format it nicer
 *                     than the planner does.
 */
public record DeletionStep(
        int order,
        Kind kind,
        String tableName,
        int depth,
        String sql,
        long expectedRows,
        String summary
) {
    /** What sort of operation a step represents. */
    public enum Kind {
        /**
         * Hard delete: rows matching the WHERE clause are removed.
         */
        DELETE,

        /**
         * Self-reference handling: the FK column is set to NULL in matching
         * rows; the rows themselves are kept. The planner emits NULLIFY
         * (not DELETE) whenever a child table equals its parent table,
         * because cascading a self-reference would either delete the
         * parent's siblings (organisationally meaningless) or recurse
         * forever (algorithmically broken).
         */
        NULLIFY,

        /**
         * Diagnostic placeholder: something in the schema couldn't be
         * planned (currently: composite foreign keys, which this
         * implementation doesn't support). The {@code sql} field for a
         * WARNING step is a SQL comment, not an executable statement.
         */
        WARNING
    }
}
