package com.hunor.classicmodelsbackend.dto.employee;

import java.util.List;

/**
 * Outcome of a bulk operation that processed each id independently.
 *
 * <p>The fundamental design choice: <b>per-item success/failure</b>
 * rather than all-or-nothing. If you bulk-delete 10 employees and 7
 * succeed but 3 fail (e.g. one has a foreign-key constraint we didn't
 * anticipate), you get a 200 response with
 * {@code successCount=7, failureCount=3, failures=[...]} — not a 500
 * that rolls everything back.</p>
 *
 * <p>The trade-off is real:</p>
 *
 * <ul>
 *   <li><b>Per-item</b> (this DTO): more work for the client to
 *   reason about partial states; lets administrators make progress
 *   even when one row is broken.</li>
 *   <li><b>Atomic</b> (single transaction, all-or-nothing): simpler
 *   semantics; but a single bad row blocks the whole batch.</li>
 * </ul>
 *
 * <p>For UI-driven bulk operations on small sets (10s of rows), the
 * per-item shape is almost always the right choice — it lets the
 * frontend show a "5 succeeded, 2 failed" summary that lets the
 * user fix and retry the failures.</p>
 */
public record BulkOperationResultDTO(
        int requested,
        int successCount,
        int failureCount,
        List<Failure> failures
) {

    /** One per-id failure, with a short human-readable reason. */
    public record Failure(int id, String reason) {}
}
