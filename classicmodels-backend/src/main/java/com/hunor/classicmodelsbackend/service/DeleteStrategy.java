package com.hunor.classicmodelsbackend.service;

/**
 * How a delete request should be carried out.
 *
 * <p>Surfacing this as an enum (instead of a boolean or magic string) is
 * a small but important best practice:
 * <ul>
 *   <li>The set of valid options is a closed list — no typos at the
 *       controller boundary.</li>
 *   <li>Adding a fourth strategy later means adding one enum value plus
 *       one switch arm; the compiler tells you everywhere it has to be
 *       handled.</li>
 *   <li>Spring will deserialize a query param like {@code ?strategy=SOFT}
 *       into this enum automatically (case-insensitive on Spring 6+).</li>
 * </ul></p>
 */
public enum DeleteStrategy {

    /**
     * Mark the employee as terminated (active=0, terminatedDate=today).
     * No row is removed; all FK references stay valid. Recommended for
     * employees and most HR-style data.
     */
    SOFT,

    /**
     * Hard delete the employee, after first NULL-ing every FK that
     * references it. Customers and direct reports survive — they just
     * lose the link. Preserves business history but loses the audit
     * trail of "who was the rep / manager."
     */
    NULLIFY,

    /**
     * Hard delete the employee AND the customer rows that referenced
     * them. Will fail if those customers have orders or payments — by
     * design, to prevent accidental destruction of financial history.
     * Direct reports are nulled, never cascaded (deleting the org chart
     * is never the right answer).
     */
    CASCADE,

    /**
     * Hard delete the employee AND every descendant row in the entire
     * dependency tree underneath their customers — orders, order details,
     * payments. Direct reports are still nulled, never cascaded.
     *
     * <p><b>Destroys financial history.</b> This is the option you almost
     * never want in a real product. It exists so that:
     * <ul>
     *   <li>botched data imports / test fixtures can be cleaned up;</li>
     *   <li>students can see the full FK chain at work;</li>
     *   <li>operations have an emergency "scorched earth" tool when
     *       absolutely required.</li>
     * </ul></p>
     *
     * <p>For safety it is gated by the {@code app.delete.allow-deep-cascade}
     * application property, which defaults to {@code false}. The service
     * layer rejects the strategy with a clear error message when the
     * flag is off, so the operation cannot be executed by accident
     * even via direct API call. The frontend additionally requires the
     * user to type the literal phrase {@code DELETE EVERYTHING} to
     * enable the button.</p>
     */
    DEEP_CASCADE
}
