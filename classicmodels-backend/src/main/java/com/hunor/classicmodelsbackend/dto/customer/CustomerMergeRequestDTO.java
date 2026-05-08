package com.hunor.classicmodelsbackend.dto.customer;

import java.util.Map;

/**
 * Body shape for {@code POST /customers/{winnerId}/merge}.
 *
 * <p>The {@code winnerId} (in the URL) keeps its row; the {@code loserId}
 * (in this body) gets its FK references reassigned to the winner and
 * is then deleted. The {@code fieldOverrides} map controls which
 * customer's value to keep for each field where the two records
 * differ.</p>
 *
 * <h3>Field-override semantics</h3>
 *
 * <p>Each entry in {@code fieldOverrides} is a customer-table column
 * name → {@code "WINNER"} or {@code "LOSER"}. Any field NOT in the
 * map keeps the winner's value (the safe default — the surviving
 * row stays unchanged unless the user explicitly elected otherwise).
 * "WINNER" entries are technically redundant but accepting them
 * keeps the frontend simple (it can always send the full map).</p>
 *
 * <p>Recognised field names match the customer-table columns:
 * customerName, contactFirstName, contactLastName, phone,
 * addressLine1, addressLine2, city, state, postalCode, country,
 * salesRepEmployeeNumber, creditLimit. Unknown keys are ignored.</p>
 *
 * <p>The winner's structural fields — customerNumber, version, audit
 * columns — are never overridable. Merging two rows must always
 * produce one row's identity, not a frankenstein hybrid of both.</p>
 */
public record CustomerMergeRequestDTO(
        int loserId,
        Map<String, String> fieldOverrides
) {}
