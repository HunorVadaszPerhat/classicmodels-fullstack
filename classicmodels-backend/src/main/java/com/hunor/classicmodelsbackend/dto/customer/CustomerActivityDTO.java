package com.hunor.classicmodelsbackend.dto.customer;

import java.util.List;

/**
 * Wrapper combining the timeline items and the header aggregations for
 * the Customer Activity page (C11). One endpoint, one response — the
 * frontend doesn't have to coordinate two parallel requests just to
 * render one page.
 *
 * <p>{@code items} is sorted by {@code activityDate DESC} (most recent
 * first), with the orders and payments interleaved into a single
 * stream. The frontend filters the same list client-side via the
 * "All / Orders / Payments" chip group — no second request needed
 * to switch views.</p>
 */
public record CustomerActivityDTO(
        CustomerActivitySummaryDTO summary,
        List<CustomerActivityItemDTO> items
) {}
