package com.hunor.classicmodelsbackend.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.hunor.classicmodelsbackend.dto.dashboard.SalesDashboardDTO.CountryRevenue;
import com.hunor.classicmodelsbackend.dto.dashboard.SalesDashboardDTO.CustomerRevenue;
import com.hunor.classicmodelsbackend.dto.dashboard.SalesDashboardDTO.MonthlyRevenue;
import com.hunor.classicmodelsbackend.dto.dashboard.SalesDashboardDTO.ProductLineRevenue;

/**
 * Read-only aggregation queries that power the sales dashboard.
 *
 * <h3>Why aggregations belong in SQL, not Java</h3>
 *
 * <p>It's tempting to "just fetch all order details and group them in
 * Java." Don't. The database is purpose-built for SUM / COUNT /
 * GROUP BY: it does the work in C against on-disk indexes, returns a
 * handful of rows, and avoids streaming millions of records through
 * the JDBC driver. Doing it in Java means pulling the entire
 * orderdetails table into memory on every dashboard load — fine at
 * 3000 rows, catastrophic at 3 million.</p>
 *
 * <p>The classic models dataset is small (~3000 order detail rows),
 * so the absolute numbers don't matter much, but the principle is
 * worth internalizing: <b>push aggregation as close to the data as
 * possible.</b> Modern databases also let you do windowing, rollups,
 * and percentiles in SQL — anything you'd do with a pandas DataFrame,
 * MySQL/Postgres can usually do in one query.</p>
 *
 * <h3>Why raw JDBC and not Spring Data?</h3>
 *
 * <p>Consistency with the rest of this project — the existing
 * repositories all use {@link DataSource} directly so the
 * {@code @MyTransactional} annotation we built earlier can intercept
 * connections. Spring Data JPA would also work fine here; the SQL
 * would just live in {@code @Query} annotations on a JpaRepository.</p>
 */
@Repository
public class DashboardRepository {

    private final DataSource dataSource;

    public DashboardRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Sum of {@code quantityOrdered * priceEach} across every line item.
     * MySQL returns NULL when the table is empty, which JDBC maps to a
     * Java {@code null} BigDecimal — guarded with COALESCE so callers
     * always get zero.
     */
    public BigDecimal totalRevenue() {
        return queryForBigDecimal("""
            SELECT COALESCE(SUM(quantityOrdered * priceEach), 0)
            FROM orderdetails
            """);
    }

    /** Number of distinct orders in the system (one row per order header). */
    public long totalOrders() {
        return queryForLong("SELECT COUNT(*) FROM orders");
    }

    /** Number of distinct customers who have placed at least one order. */
    public long activeCustomers() {
        return queryForLong("SELECT COUNT(DISTINCT customerNumber) FROM orders");
    }

    /**
     * Revenue per calendar month, ordered chronologically.
     *
     * <p>{@code DATE_FORMAT(orderDate, '%Y-%m')} bucketizes by year+month.
     * The {@code GROUP BY} on the same expression collapses every order
     * inside a month into one row.</p>
     *
     * <p>Note we join orders → orderdetails: revenue lives on the line
     * items, but the date lives on the order header. {@code orderdate}
     * (lowercase, no underscore) is the column name in the seed schema.</p>
     */
    public List<MonthlyRevenue> revenueByMonth() {
        final String sql = """
            SELECT DATE_FORMAT(o.orderDate, '%Y-%m') AS month,
                   SUM(od.quantityOrdered * od.priceEach) AS revenue
              FROM orders o
              JOIN orderdetails od ON o.orderNumber = od.orderNumber
             GROUP BY month
             ORDER BY month
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<MonthlyRevenue> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new MonthlyRevenue(rs.getString("month"), rs.getBigDecimal("revenue")));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("revenueByMonth failed", e);
        }
    }

    /**
     * Revenue grouped by product line, ranked highest-first. Used for
     * the doughnut chart that shows which categories dominate sales.
     */
    public List<ProductLineRevenue> revenueByProductLine() {
        final String sql = """
            SELECT p.productLine,
                   SUM(od.quantityOrdered * od.priceEach) AS revenue
              FROM orderdetails od
              JOIN products p ON od.productCode = p.productCode
             GROUP BY p.productLine
             ORDER BY revenue DESC
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<ProductLineRevenue> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new ProductLineRevenue(
                        rs.getString("productLine"),
                        rs.getBigDecimal("revenue")));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("revenueByProductLine failed", e);
        }
    }

    /**
     * Top-N customers by total revenue, descending. {@code LIMIT 10} is
     * applied in SQL so we only pay for moving 10 rows through the
     * driver, not the whole customer table.
     */
    public List<CustomerRevenue> topCustomers(int limit) {
        final String sql = """
            SELECT c.customerNumber,
                   c.customerName,
                   SUM(od.quantityOrdered * od.priceEach) AS revenue
              FROM customers c
              JOIN orders o      ON c.customerNumber = o.customerNumber
              JOIN orderdetails od ON o.orderNumber  = od.orderNumber
             GROUP BY c.customerNumber, c.customerName
             ORDER BY revenue DESC
             LIMIT ?
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<CustomerRevenue> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new CustomerRevenue(
                            rs.getInt("customerNumber"),
                            rs.getString("customerName"),
                            rs.getBigDecimal("revenue")));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("topCustomers failed", e);
        }
    }

    /**
     * Order count grouped by status (Shipped / In Process / Cancelled /
     * Resolved / On Hold / Disputed). Returned as a {@link LinkedHashMap}
     * so iteration order is stable across requests, which matters for
     * the doughnut chart's legend ordering on the frontend.
     */
    /**
     * Revenue + customer count grouped by country (C14). Used by the
     * dashboard's geographic breakdown chart and as the link target
     * for "drill into customers in this country."
     *
     * <p>Filters out inactive customers (the same {@code WHERE active = 1}
     * filter used everywhere else) so terminated customers don't
     * contribute to a country's apparent revenue. Groups by country
     * literal (NOT by region) — the customer-table column is the
     * authoritative source for "where is this customer." Region-level
     * grouping (Europe, APAC, etc.) is application-layer logic; could
     * be added on top of this query if a follow-on feature wanted it.</p>
     */
    public List<CountryRevenue> revenueByCountry() {
        final String sql = """
            SELECT c.country,
                   SUM(od.quantityOrdered * od.priceEach) AS revenue,
                   COUNT(DISTINCT c.customerNumber)        AS customerCount
              FROM customers c
              JOIN orders o      ON o.customerNumber = c.customerNumber
              JOIN orderdetails od ON od.orderNumber  = o.orderNumber
             WHERE c.active = 1
             GROUP BY c.country
             ORDER BY revenue DESC
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<CountryRevenue> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new CountryRevenue(
                        rs.getString("country"),
                        rs.getBigDecimal("revenue"),
                        rs.getLong("customerCount")));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("revenueByCountry failed", e);
        }
    }

    public Map<String, Long> ordersByStatus() {
        final String sql = """
            SELECT status, COUNT(*) AS cnt
              FROM orders
             GROUP BY status
             ORDER BY cnt DESC
            """;
        Map<String, Long> out = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                out.put(rs.getString("status"), rs.getLong("cnt"));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("ordersByStatus failed", e);
        }
    }

    // ---- helpers ----------------------------------------------------------

    private BigDecimal queryForBigDecimal(String sql) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                BigDecimal v = rs.getBigDecimal(1);
                return v != null ? v : BigDecimal.ZERO;
            }
            return BigDecimal.ZERO;
        } catch (SQLException e) {
            throw new RuntimeException("query failed: " + sql, e);
        }
    }

    private long queryForLong(String sql) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new RuntimeException("query failed: " + sql, e);
        }
    }
}
