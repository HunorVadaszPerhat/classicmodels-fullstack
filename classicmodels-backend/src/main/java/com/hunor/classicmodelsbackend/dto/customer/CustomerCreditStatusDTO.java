package com.hunor.classicmodelsbackend.dto.customer;

import java.math.BigDecimal;

/**
 * Credit utilisation snapshot for one customer (C13).
 *
 * <p>Computed from the customer's {@code creditLimit} (a static field
 * set on the customer record) and their <em>outstanding balance</em>
 * (lifetime spend minus total paid, derived from orders + payments).
 * The {@code utilization} ratio and {@code status} category are
 * server-derived so all consumers — single-customer chip, dashboard
 * counter, alerts list — see the same number for the same row.</p>
 *
 * <h3>Threshold semantics</h3>
 *
 * <ul>
 *   <li>{@link Status#OVER_LIMIT}: utilization > 1.0. The customer
 *       owes more than their credit limit allows. Action needed.</li>
 *   <li>{@link Status#NEAR_LIMIT}: utilization between 0.8 and 1.0
 *       (inclusive of 1.0 boundaries — a customer right at the
 *       limit reads as "near," not "over"). Worth a watch.</li>
 *   <li>{@link Status#OK}: utilization ≤ 0.8. Plenty of headroom.</li>
 *   <li>{@link Status#NO_LIMIT}: customer has no credit limit set
 *       (creditLimit IS NULL or 0). Utilization is undefined.</li>
 * </ul>
 *
 * <p>The 0.8 / 1.0 thresholds are conventional but somewhat arbitrary.
 * In a real system they'd be configurable per-customer (some
 * partners get tighter watches than others) and per-tenant.</p>
 *
 * @param customerNumber       row id
 * @param customerName         for the alerts list display
 * @param creditLimit          static field from the customer record; nullable
 * @param outstandingBalance   lifetime spend minus total paid
 * @param utilization          outstanding / creditLimit, or null if no limit
 * @param status               categorised status (see above)
 */
public record CustomerCreditStatusDTO(
        int customerNumber,
        String customerName,
        BigDecimal creditLimit,
        BigDecimal outstandingBalance,
        BigDecimal utilization,
        Status status
) {

    public enum Status {
        OK,
        NEAR_LIMIT,
        OVER_LIMIT,
        NO_LIMIT
    }
}
