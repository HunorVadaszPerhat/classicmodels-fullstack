package com.hunor.classicmodelsbackend.dto.customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CustomerResponseDTO(
        int customerNumber,
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
        // Geographic coordinates (V8). NULL until geocoded.
        BigDecimal lat,
        BigDecimal lng,
        // Soft delete (V7).
        boolean active,
        LocalDate terminatedDate,
        // Audit (V6). Populated by the repository on read.
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        /**
         * Optimistic-lock version, added in V6. The frontend stores
         * this when it reads a customer and sends it back on update;
         * a mismatch with the DB's current version causes a 409 (wired
         * in C5).
         */
        int version
) {}
