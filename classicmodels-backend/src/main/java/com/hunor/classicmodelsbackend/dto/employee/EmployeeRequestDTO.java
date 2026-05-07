package com.hunor.classicmodelsbackend.dto.employee;

/**
 * What the API accepts for create/update of an employee.
 *
 * <p>{@code version} is required for UPDATE (the client must echo back
 * whatever version it read so the server can detect concurrent edits)
 * and ignored for CREATE (the new row starts at version 0). On CREATE
 * the client should send {@code null} or 0 — the controller doesn't
 * care, because the repository's INSERT doesn't include the version
 * column at all (the DB DEFAULT 0 fills it in).</p>
 */
public record EmployeeRequestDTO(
        String lastName,
        String firstName,
        String extension,
        String email,
        String officeCode,
        Integer reportsTo,
        String jobTitle,
        Integer version
) {}
