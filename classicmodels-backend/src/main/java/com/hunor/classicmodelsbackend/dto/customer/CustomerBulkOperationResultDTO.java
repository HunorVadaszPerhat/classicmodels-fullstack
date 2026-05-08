package com.hunor.classicmodelsbackend.dto.customer;

import java.util.List;

/**
 * Outcome of a customer bulk operation that processed each id
 * independently.
 *
 * <p>Same shape as the employee equivalent — kept duplicated rather
 * than pulled into a shared {@code response/} package because each
 * entity owning its own DTO keeps the package boundary clean. If a
 * future refactor consolidates these into a generic
 * {@code BulkOperationResult<T>} that's a fine change to make then;
 * the wire format won't change.</p>
 *
 * <p>The fundamental design choice mirrors employees:
 * <b>per-item success/failure</b> rather than all-or-nothing. If you
 * bulk-delete 10 customers and 7 succeed but 3 fail (e.g. one trips
 * an unanticipated FK violation), you get a 200 response with
 * {@code successCount=7, failureCount=3, failures=[...]} — not a 500
 * that rolls everything back.</p>
 */
public record CustomerBulkOperationResultDTO(
        int requested,
        int successCount,
        int failureCount,
        List<Failure> failures
) {

    /** One per-id failure, with a short human-readable reason. */
    public record Failure(int id, String reason) {}
}
