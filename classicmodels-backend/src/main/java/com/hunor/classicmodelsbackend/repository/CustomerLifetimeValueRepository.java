package com.hunor.classicmodelsbackend.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.hunor.classicmodelsbackend.dto.customer.CustomerLifetimeValueDTO.OrderHistoryItem;

/**
 * Aggregations behind the customer-lifetime-value page.
 *
 * <h3>Why two queries?</h3>
 *
 * <p>The first query computes summary metrics + RFM quartile scores
 * for the requested customer. The second query pulls the per-order
 * timeline. Folding the timeline into the same query would require a
 * row-per-order shape, blowing up the result set; clean separation is
 * cheaper.</p>
 *
 * <h3>About the RFM query</h3>
 *
 * <p>The interesting query is {@link #computeForCustomer(int)}. It
 * uses a Common Table Expression (CTE) plus the {@code NTILE(4)}
 * window function — the canonical SQL recipe for RFM segmentation.</p>
 *
 * <p>Read the SQL alongside this comment:</p>
 *
 * <pre>
 *     WITH per_customer AS (
 *         SELECT customerNumber,
 *                MIN(orderDate)  AS firstOrderDate,
 *                MAX(orderDate)  AS lastOrderDate,
 *                COUNT(*)        AS frequency,
 *                SUM(...)        AS monetary,
 *                DATEDIFF(...)   AS recencyDays
 *           FROM ...
 *          GROUP BY customerNumber
 *     )
 *     SELECT *,
 *            NTILE(4) OVER (ORDER BY recencyDays  ASC)  AS recencyScore,
 *            NTILE(4) OVER (ORDER BY frequency    DESC) AS frequencyScore,
 *            NTILE(4) OVER (ORDER BY monetary     DESC) AS monetaryScore
 *       FROM per_customer
 * </pre>
 *
 * <p>{@code NTILE(4) OVER (ORDER BY ...)} sorts every customer along
 * the given dimension and divides them into 4 equal-sized buckets,
 * labelling each row 1..4. By choosing the sort direction we make
 * "1 = worst, 4 = best" consistent across all three dimensions.</p>
 *
 * <p>For Recency we sort ASC (smaller days = better). For Frequency
 * and Monetary we sort DESC (larger = better). After NTILE returns,
 * we reverse the Recency score on the way out
 * ({@code 5 - rawNtile}) so all three are uniformly "higher = better."</p>
 *
 * <p>The CTE first reduces N orders to N customers, then NTILE runs
 * across the customers — so even the largest dataset is only sorted
 * a few times across the customer-cardinality space.</p>
 */
@Repository
public class CustomerLifetimeValueRepository {

    private final DataSource dataSource;

    public CustomerLifetimeValueRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Compute the per-customer summary + RFM scores for one customer.
     * Returns {@code Optional.empty()} if the customer doesn't exist
     * or has never placed an order.
     */
    public Optional<CustomerSummaryRow> computeForCustomer(int customerNumber) {
        // Three things to notice in the SQL below:
        //
        //   1. The CTE (the WITH clause) computes per-customer rollups
        //      using GROUP BY customerNumber. After the CTE, "1 row =
        //      1 customer."
        //
        //   2. NTILE(4) OVER (ORDER BY ...) turns the rollup column
        //      into a quartile rank. Since the window is unpartitioned,
        //      the ranking is across ALL customers — exactly what we
        //      want for cross-customer comparison.
        //
        //   3. The outer SELECT filters down to the requested customer
        //      AFTER the window function has fired. We can't filter
        //      first, because then NTILE would be ranking a single row
        //      against itself.
        final String sql = """
            WITH per_customer AS (
                SELECT
                    o.customerNumber,
                    c.customerName,
                    MIN(o.orderDate) AS firstOrderDate,
                    MAX(o.orderDate) AS lastOrderDate,
                    COUNT(DISTINCT o.orderNumber) AS frequency,
                    COALESCE(SUM(od.quantityOrdered * od.priceEach), 0) AS monetary,
                    DATEDIFF(CURDATE(), MAX(o.orderDate)) AS recencyDays
                  FROM orders o
                  JOIN customers c ON o.customerNumber = c.customerNumber
                  JOIN orderdetails od ON o.orderNumber = od.orderNumber
                 GROUP BY o.customerNumber, c.customerName
            )
            SELECT customerNumber,
                   customerName,
                   firstOrderDate,
                   lastOrderDate,
                   frequency,
                   monetary,
                   recencyDays,
                   NTILE(4) OVER (ORDER BY recencyDays ASC)  AS recencyNtile,
                   NTILE(4) OVER (ORDER BY frequency  DESC) AS frequencyNtile,
                   NTILE(4) OVER (ORDER BY monetary   DESC) AS monetaryNtile
              FROM per_customer
            """;
        // We grab every row, then pick the matching one in Java. That's
        // ~120 rows worst-case in the classic models dataset; the cost
        // is dwarfed by the JDBC roundtrip. If this dataset were large
        // we'd wrap this in another SELECT ... WHERE customerNumber = ?
        // around the CTE.
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                if (rs.getInt("customerNumber") != customerNumber) continue;

                int recencyNtile   = rs.getInt("recencyNtile");
                int frequencyNtile = rs.getInt("frequencyNtile");
                int monetaryNtile  = rs.getInt("monetaryNtile");

                // NTILE returns 1 = "first quartile when sorted by the
                // ORDER BY column." For Recency sorted ASC, that means
                // "smallest recencyDays" = "most recent" = best. For
                // Frequency / Monetary sorted DESC, 1 = "largest" =
                // best. We want the OPPOSITE convention for downstream
                // (4 = best), so we flip with `5 - n`.
                int recencyScore   = 5 - recencyNtile;
                int frequencyScore = 5 - frequencyNtile;
                int monetaryScore  = 5 - monetaryNtile;

                return Optional.of(new CustomerSummaryRow(
                        rs.getInt("customerNumber"),
                        rs.getString("customerName"),
                        rs.getDate("firstOrderDate").toLocalDate(),
                        rs.getDate("lastOrderDate").toLocalDate(),
                        rs.getLong("frequency"),
                        rs.getBigDecimal("monetary"),
                        rs.getLong("recencyDays"),
                        recencyScore,
                        frequencyScore,
                        monetaryScore
                ));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("computeForCustomer failed", e);
        }
    }

    /**
     * Order timeline for one customer. We compute the per-order
     * amount on the fly with SUM over orderdetails so the frontend
     * doesn't have to re-aggregate.
     */
    public List<OrderHistoryItem> orderHistory(int customerNumber) {
        final String sql = """
            SELECT o.orderNumber,
                   o.orderDate,
                   o.status,
                   COALESCE(SUM(od.quantityOrdered * od.priceEach), 0) AS amount
              FROM orders o
              JOIN orderdetails od ON o.orderNumber = od.orderNumber
             WHERE o.customerNumber = ?
             GROUP BY o.orderNumber, o.orderDate, o.status
             ORDER BY o.orderDate ASC
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, customerNumber);
            try (ResultSet rs = ps.executeQuery()) {
                List<OrderHistoryItem> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new OrderHistoryItem(
                            rs.getInt("orderNumber"),
                            rs.getDate("orderDate").toLocalDate(),
                            rs.getString("status"),
                            rs.getBigDecimal("amount")));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("orderHistory failed", e);
        }
    }

    /**
     * Internal row shape returned by {@link #computeForCustomer}.
     * Exposed as a record so the service layer can do its own
     * derived calculations (predicted CLV, AOV, segment) without
     * depending on the SQL column names.
     */
    public record CustomerSummaryRow(
            int customerNumber,
            String customerName,
            LocalDate firstOrderDate,
            LocalDate lastOrderDate,
            long frequency,
            BigDecimal monetary,
            long recencyDays,
            int recencyScore,
            int frequencyScore,
            int monetaryScore
    ) {}
}
