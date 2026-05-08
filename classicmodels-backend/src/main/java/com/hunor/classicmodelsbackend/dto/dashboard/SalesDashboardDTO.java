package com.hunor.classicmodelsbackend.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Top-level envelope for the sales dashboard.
 *
 * <p>Why a single DTO with everything inside, instead of one endpoint
 * per chart? Two reasons:</p>
 *
 * <ol>
 *   <li><b>Network round-trips.</b> Six fetches in parallel plus six
 *   loading states is a worse user experience than one fetch with a
 *   single spinner. The aggregations all run in &lt;100 ms on this
 *   dataset.</li>
 *   <li><b>Consistency.</b> If two queries ran a few milliseconds
 *   apart and an order was inserted in between, the totals could
 *   mismatch the chart breakdowns. Folding everything into one logical
 *   "snapshot" eliminates that whole class of bug.</li>
 * </ol>
 *
 * <p>Each nested record below corresponds to one chart on the
 * frontend. The shapes are deliberately minimal — Chart.js wants
 * "labels" and "values" arrays, so the records are essentially that.</p>
 */
public record SalesDashboardDTO(
        // ---- KPI tiles -----------------------------------------------
        BigDecimal totalRevenue,
        long totalOrders,
        BigDecimal averageOrderValue,
        long activeCustomers,

        // ---- Credit alerts (C13) -------------------------------------
        CreditAlerts creditAlerts,

        // ---- Chart datasets ------------------------------------------
        List<MonthlyRevenue>     revenueByMonth,
        List<ProductLineRevenue> revenueByProductLine,
        List<CustomerRevenue>    topCustomers,
        Map<String, Long>        ordersByStatus,
        List<CountryRevenue>     revenueByCountry
) {

    /**
     * Counts of customers near or over their credit limit (C13).
     * Drives the "Credit alerts" KPI tile on the dashboard, which
     * links to the full alerts list at /customers/credit-alerts.
     */
    public record CreditAlerts(long overLimitCount, long nearLimitCount) {}

    /**
     * One bucket of the month-over-month revenue series. {@code month}
     * is in ISO {@code yyyy-MM} format, parsed cheaply on the frontend.
     */
    public record MonthlyRevenue(String month, BigDecimal revenue) {}

    /** One slice of the product-line breakdown. */
    public record ProductLineRevenue(String productLine, BigDecimal revenue) {}

    /** One row of the top-N customer ranking. */
    public record CustomerRevenue(int customerNumber, String customerName, BigDecimal revenue) {}

    /**
     * One country bucket for the C14 "revenue by country" chart.
     * Carries both revenue (the bar height) and customerCount
     * (the tooltip / drill-into-list helper) so the chart can show
     * "France: $1.2M / 12 customers" without a second query.
     */
    public record CountryRevenue(String country, BigDecimal revenue, long customerCount) {}
}
