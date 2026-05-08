package com.hunor.classicmodelsbackend.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.sql.DataSource;

import com.hunor.classicmodelsbackend.dto.customer.CustomerActivityDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerActivityItemDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerActivitySummaryDTO;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Builds the unified Customer Activity timeline (C11) — a single
 * stream of orders + payments, sorted by date, with header
 * aggregations.
 *
 * <h3>Why a separate service from CustomerService</h3>
 *
 * <p>{@code CustomerService} already owns CRUD plus a handful of
 * customer-specific actions (geocode, bulkDelete, deletion strategies,
 * map points). Adding the activity computation there would push the
 * file past the readable-in-one-screen threshold. A dedicated service
 * keeps each class focused: CustomerService for the customer entity
 * itself, CustomerActivityService for the activity-feed projection.</p>
 *
 * <h3>SQL strategy — two queries, merged in Java</h3>
 *
 * <p>Two natural alternatives for "give me orders and payments
 * interleaved":</p>
 *
 * <ol>
 *   <li><b>UNION ALL in SQL.</b> One round trip. Awkward because
 *       order rows want a {@code SUM(orderdetails.priceEach * quantity)}
 *       (computed via GROUP BY) while payment rows don't aggregate.
 *       Mixing those shapes in a UNION ALL means padding payment rows
 *       with NULL columns and the query reads like apologetic
 *       boilerplate.</li>
 *   <li><b>Two queries, merged client-side.</b> Two round trips, but
 *       each query does ONE thing — orders with their totals, payments
 *       with their amounts. The merge step is six lines of Java and
 *       reads exactly as intended. We picked this.</li>
 * </ol>
 *
 * <p>The two-query approach also makes adding a third event type
 * later (notes, status changes, support tickets) a one-liner — fetch
 * the new list, add to the merge, re-sort. UNION ALL would require
 * extending every NULL-pad column for every existing kind.</p>
 */
@Service
@Slf4j
public class CustomerActivityService {

    private final DataSource dataSource;

    public CustomerActivityService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Build the activity DTO for one customer: timeline items merged
     * and sorted, plus the header summary.
     *
     * <p>{@code @Cacheable} keyed on the customer id. Evictions fire
     * from {@link CustomerService}'s mutation methods (we added
     * {@code customerActivity} to those eviction lists in the same
     * pattern as {@code customersMapPoints}), so this never serves
     * stale data after a write. Pure-read calls within the cache
     * window hit the cache; first call hits the DB.</p>
     */
    @Cacheable(cacheNames = "customerActivity", key = "#customerNumber")
    public CustomerActivityDTO findActivityForCustomer(int customerNumber) {
        log.info("Building activity timeline for customer {}", customerNumber);
        long start = System.currentTimeMillis();

        List<CustomerActivityItemDTO> orders = findOrders(customerNumber);
        List<CustomerActivityItemDTO> payments = findPayments(customerNumber);

        // Merge and sort DESC (most recent first). The two input lists
        // are independently ordered by date in the SQL, but combining
        // them via Stream.sorted is the simplest correct merge.
        List<CustomerActivityItemDTO> items = new ArrayList<>(orders.size() + payments.size());
        items.addAll(orders);
        items.addAll(payments);
        items.sort(Comparator.comparing(CustomerActivityItemDTO::activityDate).reversed());

        CustomerActivitySummaryDTO summary = buildSummary(orders, payments);

        log.info("Activity for customer {} — {} orders, {} payments — built in {} ms",
                customerNumber, orders.size(), payments.size(),
                System.currentTimeMillis() - start);

        return new CustomerActivityDTO(summary, items);
    }

    /**
     * Find every order for one customer with its computed total
     * (sum of line items) and item count. Joined to {@code orderdetails}
     * via LEFT JOIN so an order with no line items still appears with
     * total = 0 and itemCount = 0 (a degenerate but possible state
     * between order creation and line entry).
     */
    private List<CustomerActivityItemDTO> findOrders(int customerNumber) {
        String sql = """
            SELECT o.orderNumber,
                   o.orderDate,
                   o.status,
                   COALESCE(SUM(od.priceEach * od.quantityOrdered), 0) AS orderTotal,
                   COUNT(od.productCode) AS itemCount
            FROM orders o
            LEFT JOIN orderdetails od ON od.orderNumber = o.orderNumber
            WHERE o.customerNumber = ?
            GROUP BY o.orderNumber, o.orderDate, o.status
            ORDER BY o.orderDate DESC
            """;
        List<CustomerActivityItemDTO> out = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerNumber);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(CustomerActivityItemDTO.order(
                            rs.getDate("orderDate").toLocalDate(),
                            rs.getInt("orderNumber"),
                            rs.getString("status"),
                            rs.getBigDecimal("orderTotal"),
                            rs.getInt("itemCount")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find orders for activity failed", e);
        }
        return out;
    }

    /**
     * Find every payment for one customer.
     */
    private List<CustomerActivityItemDTO> findPayments(int customerNumber) {
        String sql = """
            SELECT checkNumber, paymentDate, amount
            FROM payments
            WHERE customerNumber = ?
            ORDER BY paymentDate DESC
            """;
        List<CustomerActivityItemDTO> out = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerNumber);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(CustomerActivityItemDTO.payment(
                            rs.getDate("paymentDate").toLocalDate(),
                            rs.getString("checkNumber"),
                            rs.getBigDecimal("amount")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find payments for activity failed", e);
        }
        return out;
    }

    /**
     * Compute the header aggregations from the already-fetched lists.
     * One pass over each list, no extra DB queries.
     */
    private CustomerActivitySummaryDTO buildSummary(
            List<CustomerActivityItemDTO> orders,
            List<CustomerActivityItemDTO> payments) {

        BigDecimal lifetimeSpend = orders.stream()
                .map(CustomerActivityItemDTO::orderTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaid = payments.stream()
                .map(CustomerActivityItemDTO::paymentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstandingBalance = lifetimeSpend.subtract(totalPaid);

        // First and last activity across both lists. Nullable when the
        // customer has zero orders AND zero payments.
        LocalDate firstActivity = null;
        LocalDate lastActivity = null;
        for (var item : orders) {
            firstActivity = minDate(firstActivity, item.activityDate());
            lastActivity = maxDate(lastActivity, item.activityDate());
        }
        for (var item : payments) {
            firstActivity = minDate(firstActivity, item.activityDate());
            lastActivity = maxDate(lastActivity, item.activityDate());
        }

        return new CustomerActivitySummaryDTO(
                lifetimeSpend,
                totalPaid,
                outstandingBalance,
                orders.size(),
                payments.size(),
                firstActivity,
                lastActivity
        );
    }

    private static LocalDate minDate(LocalDate current, LocalDate candidate) {
        if (candidate == null) return current;
        if (current == null || candidate.isBefore(current)) return candidate;
        return current;
    }

    private static LocalDate maxDate(LocalDate current, LocalDate candidate) {
        if (candidate == null) return current;
        if (current == null || candidate.isAfter(current)) return candidate;
        return current;
    }
}
