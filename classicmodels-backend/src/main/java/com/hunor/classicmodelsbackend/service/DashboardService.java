package com.hunor.classicmodelsbackend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.hunor.classicmodelsbackend.dto.dashboard.SalesDashboardDTO;
import com.hunor.classicmodelsbackend.repository.DashboardRepository;

/**
 * Assembles the sales-dashboard snapshot from a handful of read-only
 * aggregation queries.
 *
 * <p>The service is intentionally thin — most of the work is SQL in
 * {@link DashboardRepository}. The Java layer's job is to:</p>
 *
 * <ul>
 *   <li>Compute derived KPIs (average order value = revenue / orders).</li>
 *   <li>Apply business defaults (top-N = 10).</li>
 *   <li>Bundle everything into one DTO so the controller can return it
 *       in a single response.</li>
 * </ul>
 *
 * <p>No caching is wired up here, but it would be a good follow-on
 * exercise: {@code @Cacheable("dashboard")} on this method, paired with
 * a {@code @CacheEvict} call from the order/orderdetail mutation paths
 * (the only writes that affect these numbers), would let us serve
 * the dashboard from memory at near-zero cost.</p>
 */
@Service
public class DashboardService {

    /** Top-N customers shown on the leaderboard. Could be configurable. */
    private static final int TOP_CUSTOMERS_LIMIT = 10;

    private final DashboardRepository repo;
    /**
     * Source of the credit-alert counts shown on the dashboard's
     * "Credit alerts" KPI tile (C13). The dashboard composes the
     * snapshot; the credit service owns the calculation.
     */
    private final CustomerCreditService creditService;

    public DashboardService(DashboardRepository repo,
                            CustomerCreditService creditService) {
        this.repo = repo;
        this.creditService = creditService;
    }

    public SalesDashboardDTO getSalesDashboard() {
        BigDecimal totalRevenue = repo.totalRevenue();
        long       totalOrders  = repo.totalOrders();

        // Avg order value = revenue / orders. Guard against div-by-zero
        // (happens only on a brand-new database with no orders, but
        // catching it keeps the dashboard from 500ing in that state).
        BigDecimal avg = totalOrders == 0
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);

        // Credit alert counts (C13). Pulled from CustomerCreditService
        // so the same numbers appear here and on the per-customer chip
        // and the alerts list page — single source of truth.
        var alertCounts = creditService.countAlerts();

        return new SalesDashboardDTO(
                totalRevenue,
                totalOrders,
                avg,
                repo.activeCustomers(),
                new SalesDashboardDTO.CreditAlerts(
                        alertCounts.overLimitCount(),
                        alertCounts.nearLimitCount()),
                repo.revenueByMonth(),
                repo.revenueByProductLine(),
                repo.topCustomers(TOP_CUSTOMERS_LIMIT),
                repo.ordersByStatus(),
                repo.revenueByCountry()
        );
    }
}
