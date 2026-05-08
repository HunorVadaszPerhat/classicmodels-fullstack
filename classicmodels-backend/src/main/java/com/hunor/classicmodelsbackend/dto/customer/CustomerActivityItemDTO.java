package com.hunor.classicmodelsbackend.dto.customer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row in the unified Customer Activity timeline (C11).
 *
 * <h3>Why a flat record with a {@code kind} discriminator</h3>
 *
 * <p>Two natural shapes for "this is one of N kinds of thing":</p>
 *
 * <ol>
 *   <li><b>Sealed interface + per-kind record</b> — type-safe, but
 *       requires Jackson polymorphic-type wiring ({@code @JsonTypeInfo} +
 *       {@code @JsonSubTypes}) to round-trip through JSON, and the
 *       frontend has to handle the same discriminated-union pattern.</li>
 *   <li><b>Flat record with a {@code kind} discriminator and nullable
 *       per-kind fields</b> — what we use here. Jackson serialises it
 *       with no extra annotations; the frontend treats it as a TypeScript
 *       discriminated union via the {@code kind} field. Less type-safe
 *       on the Java side but dramatically simpler at the wire boundary.</li>
 * </ol>
 *
 * <p>For an internal API where one team controls both ends, the flat
 * shape wins on simplicity. If this DTO ever needs to grow to a
 * fifth or sixth kind, or if a public API consumer needs strict
 * typing, refactor to option 1.</p>
 *
 * <h3>Fields by kind</h3>
 *
 * <table>
 *   <tr><th>Field</th><th>ORDER</th><th>PAYMENT</th></tr>
 *   <tr><td>kind</td><td>"ORDER"</td><td>"PAYMENT"</td></tr>
 *   <tr><td>activityDate</td><td>orderDate</td><td>paymentDate</td></tr>
 *   <tr><td>orderNumber</td><td>set</td><td>null</td></tr>
 *   <tr><td>orderStatus</td><td>set</td><td>null</td></tr>
 *   <tr><td>orderTotal</td><td>set (sum of line items)</td><td>null</td></tr>
 *   <tr><td>itemCount</td><td>set (line-item count)</td><td>null</td></tr>
 *   <tr><td>checkNumber</td><td>null</td><td>set</td></tr>
 *   <tr><td>paymentAmount</td><td>null</td><td>set</td></tr>
 * </table>
 */
public record CustomerActivityItemDTO(
        /** "ORDER" or "PAYMENT". The frontend's discriminator. */
        String kind,

        /** orderDate for orders, paymentDate for payments. Used to sort the merged stream. */
        LocalDate activityDate,

        // Order-specific fields. Null when kind == "PAYMENT".
        Integer orderNumber,
        String orderStatus,
        BigDecimal orderTotal,
        Integer itemCount,

        // Payment-specific fields. Null when kind == "ORDER".
        String checkNumber,
        BigDecimal paymentAmount
) {

    /**
     * Constructor for an order-kind row. Keeps the call sites
     * symmetric and stops anyone accidentally setting payment fields
     * on an order row (or vice versa).
     */
    public static CustomerActivityItemDTO order(
            LocalDate orderDate, int orderNumber, String orderStatus,
            BigDecimal orderTotal, int itemCount) {
        return new CustomerActivityItemDTO(
                "ORDER", orderDate,
                orderNumber, orderStatus, orderTotal, itemCount,
                null, null);
    }

    /** Constructor for a payment-kind row. */
    public static CustomerActivityItemDTO payment(
            LocalDate paymentDate, String checkNumber, BigDecimal amount) {
        return new CustomerActivityItemDTO(
                "PAYMENT", paymentDate,
                null, null, null, null,
                checkNumber, amount);
    }
}
