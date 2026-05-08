package com.hunor.classicmodelsbackend.dto.customer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Header aggregations for the Customer Activity page (C11).
 *
 * <p>Computed alongside the timeline items in a single pass over the
 * customer's orders and payments. Renders as a small "stat tile" row
 * above the timeline so the user can see the headline numbers
 * before scrolling through the events.</p>
 *
 * <p>Outstanding balance is the simple subtraction
 * {@code lifetimeSpend - totalPaid}. Real accounting systems track
 * this per-invoice with allocations, payment terms, refunds, and
 * disputes, but for a learning project the unallocated total is the
 * useful headline number — "does this customer owe us money?"</p>
 */
public record CustomerActivitySummaryDTO(
        /** Sum of all orders' line-item totals. */
        BigDecimal lifetimeSpend,
        /** Sum of all payments' amounts. */
        BigDecimal totalPaid,
        /** {@code lifetimeSpend - totalPaid}. Positive means customer owes us. */
        BigDecimal outstandingBalance,

        long orderCount,
        long paymentCount,

        /**
         * Earliest activity date across orders and payments. Null if the
         * customer has no orders and no payments (rare but possible —
         * e.g. a brand-new customer who hasn't ordered yet).
         */
        LocalDate firstActivityDate,
        /** Latest activity date — same null caveat. */
        LocalDate lastActivityDate
) {}
