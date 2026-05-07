package com.hunor.classicmodelsbackend.dto.employee;

import java.util.List;

import com.hunor.classicmodelsbackend.service.DeleteStrategy;

/**
 * Body shape for {@code POST /employees/bulk-delete}.
 *
 * <p>One request describes "delete every employee in {@code ids} using
 * {@code strategy}." The strategy applies uniformly — there's no
 * per-row override — because mixed strategies inside a single bulk
 * call would make the failure-reporting semantics very hard to reason
 * about. Users who need different strategies for different employees
 * should send multiple requests.</p>
 */
public record BulkDeleteRequestDTO(
        List<Integer> ids,
        DeleteStrategy strategy
) {}
