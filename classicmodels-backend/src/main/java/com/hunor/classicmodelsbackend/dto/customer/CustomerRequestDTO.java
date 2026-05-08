package com.hunor.classicmodelsbackend.dto.customer;

import java.math.BigDecimal;

/**
 * What the API accepts for create/update of a customer.
 *
 * <p>{@code version} is required for UPDATE (the client must echo back
 * whatever version it read so the server can detect concurrent edits)
 * and ignored for CREATE (the new row starts at version 0). On CREATE
 * the client should send {@code null} or 0 — the repository's INSERT
 * doesn't include the version column at all (the DB DEFAULT 0 fills
 * it in).</p>
 *
 * <p>C4 adds the field; C5 wires the optimistic-lock check on update.</p>
 */
public record CustomerRequestDTO(
        String customerName,
        String contactLastName,
        String contactFirstName,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,
        Integer salesRepEmployeeNumber,
        BigDecimal creditLimit,
        Integer version
) {}
