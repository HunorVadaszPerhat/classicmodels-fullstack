package com.hunor.classicmodelsbackend.dto.employee;

import java.util.List;

/**
 * Describes one foreign-key reference to {@code employees.employeeNumber}.
 *
 * <p>Discovery is dynamic via {@code INFORMATION_SCHEMA.KEY_COLUMN_USAGE}
 * so we don't have to hardcode the list of child tables. For every child
 * table found, we additionally fetch up to {@link #records} sample rows
 * with a human-friendly label so the UI can name the actual customers
 * and direct reports — not just say "12 rows".</p>
 *
 * <p>Why a {@code count} field separate from {@code records.size()}?
 * Because we only fetch a small preview (e.g. 25 rows) but want the UI
 * to be able to say "12 of 87 customers shown". For small datasets they
 * happen to be equal; for large ones the count is the source of truth.</p>
 */
public record EmployeeDependentDTO(
        String tableName,
        String columnName,
        long count,
        List<DependentRecord> records
) {
    /**
     * One row from a child table, formatted for human display.
     *
     * @param id    the primary key of the row in the child table.
     *              Useful so the UI can deep-link to the record.
     * @param label a friendly text representation, e.g. the customer
     *              name or the employee's full name.
     */
    public record DependentRecord(int id, String label) {}
}
