package com.hunor.classicmodelsbackend.dto.employee;

import java.time.Instant;
import java.time.LocalDate;

public record EmployeeResponseDTO(
        int employeeNumber,
        String lastName,
        String firstName,
        String extension,
        String email,
        String officeCode,
        Integer reportsTo,
        String jobTitle,
        boolean active,
        LocalDate terminatedDate,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        /**
         * Optimistic-lock version, added in V5. The frontend stores
         * this when it reads an employee and sends it back on update;
         * a mismatch with the DB's current version causes a 409.
         */
        int version
) {}
