package com.hunor.classicmodelsbackend.model;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Domain model for an employee row.
 *
 * <p>Field groups:</p>
 * <ul>
 *   <li><b>Identity</b> — employeeNumber, names, contact</li>
 *   <li><b>Role</b> — officeCode, reportsTo, jobTitle</li>
 *   <li><b>Soft delete</b> — active, terminatedDate (added in V2)</li>
 *   <li><b>Audit</b> — createdAt/createdBy/updatedAt/updatedBy
 *       (added in V4); auto-populated by the repository on every
 *       save/update.</li>
 * </ul>
 */
@Data
public class Employee {
    // Identity
    private int employeeNumber;
    private String lastName;
    private String firstName;
    private String extension;
    private String email;

    // Role
    private String officeCode;
    private Integer reportsTo;
    private String jobTitle;

    // Soft delete (V2)
    private boolean active = true;
    private LocalDate terminatedDate;

    /**
     * Optimistic-lock version (V5). Incremented on every UPDATE.
     * Clients send back the version they read; if it doesn't match the
     * DB's current version, the UPDATE fails as a 409 Conflict.
     */
    private int version;

    // Audit (V4)
    /** When the row was first inserted. Set by the repository on save. */
    private Instant createdAt;

    /** When the row was last modified. Refreshed on every update. */
    private Instant updatedAt;

    /** Username of the user who created the row, or {@code "system"} if no auth context. */
    private String createdBy;

    /** Username of the user who most recently updated the row. */
    private String updatedBy;
}
