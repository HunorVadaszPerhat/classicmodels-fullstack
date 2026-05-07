package com.hunor.classicmodelsbackend.dto.customer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Customer Lifetime Value (CLV) snapshot for a single customer.
 *
 * <p>This is a textbook RFM-segmented profile, computed on demand from
 * the orders / orderdetails tables. Three things are folded into one
 * payload:</p>
 *
 * <ol>
 *   <li><b>Headline KPIs</b> — total orders, revenue, AOV, tenure.</li>
 *   <li><b>RFM scores + segment</b> — the canonical "where does this
 *       customer fit" classification used in marketing analytics.</li>
 *   <li><b>Order history</b> — every order with its date and amount,
 *       so the frontend can render a per-customer revenue timeline.</li>
 * </ol>
 *
 * <h3>What is RFM?</h3>
 *
 * <p>RFM is a 50-year-old segmentation technique that scores each
 * customer on three dimensions:</p>
 *
 * <ul>
 *   <li><b>Recency</b> — how recently they last bought.</li>
 *   <li><b>Frequency</b> — how often they buy.</li>
 *   <li><b>Monetary</b> — how much they spend.</li>
 * </ul>
 *
 * <p>Each axis is bucketed (we use quartiles via SQL's
 * {@code NTILE(4)}), giving a score from 1 (worst) to 4 (best).
 * Combinations of those three scores yield <b>segments</b> —
 * Champions, Loyal, At-Risk, Lost, and so on. See
 * {@link Segment} for the segment names we use.</p>
 *
 * <h3>Historic vs. predictive CLV</h3>
 *
 * <p>{@code totalRevenue} is the <b>historic</b> CLV: every dollar
 * this customer has ever paid us. {@code predictedClv} is a simple
 * <b>predictive</b> CLV using the textbook formula:</p>
 *
 * <pre>
 *     Predicted CLV = AOV × purchase-frequency × estimated-lifespan
 * </pre>
 *
 * <p>Real-world CLV models layer in churn rates, discount rates, and
 * machine-learned propensity scores. We're keeping the formula simple
 * here — the goal is to teach the concept, not to replace your
 * marketing team's model.</p>
 */
public record CustomerLifetimeValueDTO(
        // ---- Identity ------------------------------------------------
        int customerNumber,
        String customerName,

        // ---- Tenure / activity ---------------------------------------
        LocalDate firstOrderDate,
        LocalDate lastOrderDate,
        long tenureDays,
        long recencyDays,

        // ---- Money / volume ------------------------------------------
        long totalOrders,
        BigDecimal totalRevenue,
        BigDecimal averageOrderValue,
        BigDecimal predictedClv,

        // ---- RFM -----------------------------------------------------
        RfmScore rfm,
        Segment segment,

        // ---- Drill-in ------------------------------------------------
        List<OrderHistoryItem> orderHistory
) {

    /** Three quartile scores, 1 (worst) to 4 (best). */
    public record RfmScore(int recency, int frequency, int monetary) {
        /** Combined "total" score, useful for sorting. */
        public int total() { return recency + frequency + monetary; }
    }

    /**
     * Marketing-flavored segment names. The mapping from RFM scores to
     * segments lives in CustomerLifetimeValueService — names alone
     * don't drive the math.
     *
     * <p>The classifications follow the de-facto standard from RFM
     * literature; if you read three blog posts on "RFM segmentation"
     * you'll see roughly these eight names every time.</p>
     */
    public enum Segment {
        CHAMPIONS,           // Recent + frequent + big spenders
        LOYAL,               // Frequent + big spenders, slightly less recent
        POTENTIAL_LOYALISTS, // Recent + medium frequency / spend
        NEW_CUSTOMERS,       // Recent, but only one or two purchases
        AT_RISK,             // Used to be valuable; haven't shown up lately
        CANT_LOSE,           // Big spenders going dormant — top priority
        HIBERNATING,         // Low scores everywhere; might re-activate
        LOST                 // No recent activity, low historic value
    }

    /** One row in the customer's order timeline. */
    public record OrderHistoryItem(
            int orderNumber,
            LocalDate orderDate,
            String status,
            BigDecimal amount
    ) {}
}
