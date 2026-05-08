package com.hunor.classicmodelsbackend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.hunor.classicmodelsbackend.dto.customer.CustomerCreditStatusDTO;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Credit-utilisation calculations for the C13 alerts feature.
 *
 * <h3>Threshold constants</h3>
 *
 * <p>Hardcoded for now. In a real system these would be configurable
 * — most likely per-customer (premium accounts get tighter watches)
 * or at least via {@code @Value} from application.yml. We've kept
 * them constant here so the alerts feature is testable and
 * deterministic out of the box.</p>
 *
 * <h3>Why a separate service</h3>
 *
 * <p>The credit calculation reads from three tables (customers,
 * orders + orderdetails, payments) and produces a derived view that
 * doesn't fit neatly inside any one entity's CRUD service. A
 * dedicated service keeps the cross-table aggregation logic in one
 * place — same shape as {@link CustomerActivityService} (C11) or
 * {@link CustomerLifetimeValueService}.</p>
 */
@Service
@Slf4j
public class CustomerCreditService {

    /** Utilisation strictly above this means OVER_LIMIT. */
    public static final BigDecimal OVER_LIMIT_THRESHOLD = BigDecimal.ONE; // 1.0
    /** Utilisation at or above this (and ≤ 1.0) means NEAR_LIMIT. */
    public static final BigDecimal NEAR_LIMIT_THRESHOLD = new BigDecimal("0.80");

    private final DataSource dataSource;

    public CustomerCreditService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Compute credit status for one customer.
     *
     * <p>Cached because the detail page calls it on every load and the
     * underlying aggregation hits three tables. Eviction lives in
     * {@link CustomerService}, {@link OrderService}, and
     * {@link PaymentService} via the shared {@code customerCreditStatus}
     * cache name.</p>
     */
    @Cacheable(cacheNames = "customerCreditStatus", key = "#customerNumber")
    public CustomerCreditStatusDTO findStatus(int customerNumber) {
        log.debug("Computing credit status for customer {}", customerNumber);
        // Same aggregation logic as the alerts query, scoped to one customer.
        String sql = baseQuery() + " AND c.customerNumber = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new RuntimeException("Customer " + customerNumber + " not found or inactive");
                }
                return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Compute credit status failed", e);
        }
    }

    /**
     * Find every active customer whose credit utilisation is at or
     * above the alert threshold (0.80 by default).
     *
     * <p>Returned sorted by utilisation descending — over-limit
     * customers come first, near-limit after, neither below the
     * threshold.</p>
     */
    @Cacheable(cacheNames = "customerCreditAlerts")
    public List<CustomerCreditStatusDTO> findAlerts() {
        log.info("Computing credit alerts");
        long start = System.currentTimeMillis();

        // Adding the threshold filter to the HAVING clause means the DB
        // returns only at-risk customers — the wire payload stays small
        // even if the customer table grows. Sort there too so we don't
        // re-sort in Java.
        String sql = baseQuery()
                + " HAVING utilization >= ?"
                + " ORDER BY utilization DESC";

        List<CustomerCreditStatusDTO> out = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, NEAR_LIMIT_THRESHOLD);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find credit alerts failed", e);
        }

        log.info("Found {} credit alerts in {} ms", out.size(),
                System.currentTimeMillis() - start);
        return out;
    }

    /**
     * Counts of over-limit and near-limit customers. Used by the
     * sales dashboard to render the credit-alerts KPI tile without
     * pulling the whole alerts list.
     */
    @Cacheable(cacheNames = "customerCreditAlertCounts")
    public AlertCounts countAlerts() {
        // Run the alerts query once and partition in Java rather than
        // running two separate COUNT queries — same cost, half the SQL.
        var alerts = findAlerts();
        long over = alerts.stream()
                .filter(a -> a.status() == CustomerCreditStatusDTO.Status.OVER_LIMIT)
                .count();
        long near = alerts.stream()
                .filter(a -> a.status() == CustomerCreditStatusDTO.Status.NEAR_LIMIT)
                .count();
        return new AlertCounts(over, near);
    }

    /** Tiny aggregation tuple for {@link #countAlerts()}. */
    public record AlertCounts(long overLimitCount, long nearLimitCount) {}

    /**
     * Shared SELECT body for the per-customer and alerts-list queries.
     *
     * <p>Two LEFT JOIN'd subqueries pull the per-customer order total
     * and per-customer payment total. Subtraction gives the outstanding
     * balance. Division by creditLimit gives utilisation; we guard
     * against null/zero creditLimit by emitting NULL utilisation in
     * those cases, which the {@code mapRow} helper turns into
     * {@code Status.NO_LIMIT}.</p>
     *
     * <p>The query stops short of the WHERE / HAVING / ORDER clauses so
     * callers can append their own. Returning a query <em>fragment</em>
     * is unusual; the alternative is two near-identical multi-line
     * SQL strings, which drift out of sync the moment one gets edited.</p>
     */
    private String baseQuery() {
        return """
            SELECT c.customerNumber,
                   c.customerName,
                   c.creditLimit,
                   COALESCE(orders_total.total, 0)
                       - COALESCE(payments_total.total, 0)         AS outstandingBalance,
                   CASE
                       WHEN c.creditLimit IS NULL OR c.creditLimit = 0 THEN NULL
                       ELSE (COALESCE(orders_total.total, 0)
                             - COALESCE(payments_total.total, 0)) / c.creditLimit
                   END                                              AS utilization
              FROM customers c
              LEFT JOIN (
                  SELECT o.customerNumber,
                         SUM(od.priceEach * od.quantityOrdered) AS total
                    FROM orders o
                    JOIN orderdetails od ON od.orderNumber = o.orderNumber
                   GROUP BY o.customerNumber
              ) orders_total ON orders_total.customerNumber = c.customerNumber
              LEFT JOIN (
                  SELECT customerNumber, SUM(amount) AS total
                    FROM payments
                   GROUP BY customerNumber
              ) payments_total ON payments_total.customerNumber = c.customerNumber
             WHERE c.active = 1
            """;
    }

    /**
     * Build a DTO row from the result set, deriving the {@link
     * CustomerCreditStatusDTO.Status} category from the utilisation
     * value (or absence thereof).
     */
    private CustomerCreditStatusDTO mapRow(ResultSet rs) throws SQLException {
        int customerNumber = rs.getInt("customerNumber");
        String customerName = rs.getString("customerName");
        BigDecimal creditLimit = rs.getBigDecimal("creditLimit");
        BigDecimal outstandingBalance = rs.getBigDecimal("outstandingBalance");
        BigDecimal utilization = rs.getBigDecimal("utilization");

        CustomerCreditStatusDTO.Status status;
        if (utilization == null) {
            status = CustomerCreditStatusDTO.Status.NO_LIMIT;
        } else if (utilization.compareTo(OVER_LIMIT_THRESHOLD) > 0) {
            status = CustomerCreditStatusDTO.Status.OVER_LIMIT;
        } else if (utilization.compareTo(NEAR_LIMIT_THRESHOLD) >= 0) {
            status = CustomerCreditStatusDTO.Status.NEAR_LIMIT;
        } else {
            status = CustomerCreditStatusDTO.Status.OK;
        }

        // Round utilisation to 4 decimal places — enough precision for
        // a percentage display, doesn't bloat JSON with 12 trailing
        // digits from BigDecimal's default scale.
        BigDecimal rounded = utilization == null
                ? null
                : utilization.setScale(4, RoundingMode.HALF_UP);

        return new CustomerCreditStatusDTO(
                customerNumber, customerName, creditLimit,
                outstandingBalance, rounded, status);
    }
}
