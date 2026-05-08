package com.hunor.classicmodelsbackend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import lombok.Data;

/**
 * Domain model for a customer row.
 *
 * <p>Field groups:</p>
 * <ul>
 *   <li><b>Identity</b> — customerNumber, customerName, contact</li>
 *   <li><b>Address</b> — addressLine1/2, city, state, postalCode, country</li>
 *   <li><b>Account</b> — salesRepEmployeeNumber, creditLimit</li>
 *   <li><b>Audit</b> — createdAt/createdBy/updatedAt/updatedBy
 *       (added in V6); auto-populated by the repository on every
 *       save/update.</li>
 *   <li><b>Optimistic lock</b> — version (added in V6); incremented on
 *       every UPDATE in C5.</li>
 * </ul>
 */
@Data
public class Customer {
    private int customerNumber;
    private String customerName;
    private String contactLastName;
    private String contactFirstName;
    private String phone;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private Integer salesRepEmployeeNumber;
    private BigDecimal creditLimit;

    // Geographic coordinates (V8). NULL until the customer has been
    // geocoded via the C9 button. BigDecimal preserves the DECIMAL(10,7)
    // precision; the frontend converts to plain numbers for Leaflet.
    private BigDecimal lat;
    private BigDecimal lng;

    // Soft delete (V7). active=true is the default; soft-deleted rows
    // get active=false and a terminatedDate.
    private boolean active = true;
    private LocalDate terminatedDate;

    // Audit (V6). Auto-populated on save/update.
    /** When the row was first inserted. */
    private Instant createdAt;
    /** When the row was last modified. Refreshed on every update. */
    private Instant updatedAt;
    /** Username of the user who created the row, or {@code "seed"} for migrated rows. */
    private String createdBy;
    /** Username of the user who most recently updated the row. */
    private String updatedBy;

    /**
     * Optimistic-lock version (V6). Incremented on every UPDATE starting
     * in C5. Clients send back the version they read; if it doesn't match
     * the DB's current version, the UPDATE fails as a 409 Conflict.
     */
    private int version;
}
