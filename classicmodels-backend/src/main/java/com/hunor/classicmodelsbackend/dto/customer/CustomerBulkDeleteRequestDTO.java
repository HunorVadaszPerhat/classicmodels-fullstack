package com.hunor.classicmodelsbackend.dto.customer;

import java.util.List;

import com.hunor.classicmodelsbackend.service.DeleteStrategy;

/**
 * Body shape for {@code POST /customers/bulk-delete}.
 *
 * <p>One request describes "delete every customer in {@code ids}
 * using {@code strategy}." The strategy applies uniformly — there's
 * no per-row override — because mixed strategies inside a single bulk
 * call would make the failure-reporting semantics very hard to reason
 * about. Users who need different strategies for different customers
 * should send multiple requests.</p>
 *
 * <p>For customers, valid strategies are SOFT (the default) and
 * DEEP_CASCADE (gated by the same feature flag as single-row
 * delete). NULLIFY / CASCADE / REASSIGN_DELETE aren't applicable.</p>
 */
public record CustomerBulkDeleteRequestDTO(
        List<Integer> ids,
        DeleteStrategy strategy
) {}
