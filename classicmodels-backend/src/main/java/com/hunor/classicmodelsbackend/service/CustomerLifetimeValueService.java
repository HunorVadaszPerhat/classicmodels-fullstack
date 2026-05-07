package com.hunor.classicmodelsbackend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

import com.hunor.classicmodelsbackend.dto.customer.CustomerLifetimeValueDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerLifetimeValueDTO.RfmScore;
import com.hunor.classicmodelsbackend.dto.customer.CustomerLifetimeValueDTO.Segment;
import com.hunor.classicmodelsbackend.repository.CustomerLifetimeValueRepository;
import com.hunor.classicmodelsbackend.repository.CustomerLifetimeValueRepository.CustomerSummaryRow;

/**
 * Computes the per-customer CLV / RFM snapshot.
 *
 * <p>The repository handles the heavy SQL — the GROUP BY + NTILE that
 * gives us per-customer rollups and quartile scores. This service:</p>
 *
 * <ul>
 *   <li>Derives Average Order Value, tenure, and predicted CLV.</li>
 *   <li>Classifies the customer into one of the named segments based
 *       on their RFM combination (see {@link #classifySegment}).</li>
 *   <li>Stitches the summary, RFM scores, and order timeline into one
 *       DTO for the controller.</li>
 * </ul>
 *
 * <h3>Predicted CLV — the simple formula</h3>
 *
 * <pre>
 *     Predicted CLV = AOV × Purchase Frequency × Estimated Lifespan
 * </pre>
 *
 * <p>where:</p>
 *
 * <ul>
 *   <li><b>AOV</b> — average revenue per order.</li>
 *   <li><b>Purchase Frequency</b> — orders per year (extrapolated
 *       from the customer's tenure).</li>
 *   <li><b>Estimated Lifespan</b> — a simple constant, currently
 *       3 years. Real models vary this per industry; some derive it
 *       from cohort retention curves.</li>
 * </ul>
 *
 * <p>This is the "naïve historical" CLV — it assumes the customer's
 * past behavior continues unchanged. Production models layer in
 * churn probability, discount rate, and per-customer projection.
 * For learning the concept, the simple version is enough.</p>
 */
@Service
public class CustomerLifetimeValueService {

    /** Hard-coded "expected customer lifespan" for predicted CLV. */
    private static final int ASSUMED_LIFESPAN_YEARS = 3;

    private final CustomerLifetimeValueRepository repo;

    public CustomerLifetimeValueService(CustomerLifetimeValueRepository repo) {
        this.repo = repo;
    }

    public CustomerLifetimeValueDTO computeForCustomer(int customerNumber) {
        CustomerSummaryRow row = repo.computeForCustomer(customerNumber)
                .orElseThrow(() -> new NoSuchElementException(
                        "No order history found for customer " + customerNumber));

        // ---- Derived metrics -----------------------------------------
        long tenureDays = ChronoUnit.DAYS.between(row.firstOrderDate(), row.lastOrderDate());
        // "How many orders per year on average?" — guard for the
        // common case where the customer's first and last order are
        // the same day (tenure = 0).
        BigDecimal ordersPerYear = tenureDays > 0
                ? BigDecimal.valueOf(row.frequency())
                    .multiply(BigDecimal.valueOf(365))
                    .divide(BigDecimal.valueOf(tenureDays), 2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(row.frequency()); // 1 day of activity → freq orders/year

        BigDecimal aov = row.frequency() == 0
                ? BigDecimal.ZERO
                : row.monetary().divide(BigDecimal.valueOf(row.frequency()), 2, RoundingMode.HALF_UP);

        BigDecimal predictedClv = aov
                .multiply(ordersPerYear)
                .multiply(BigDecimal.valueOf(ASSUMED_LIFESPAN_YEARS))
                .setScale(2, RoundingMode.HALF_UP);

        RfmScore rfm = new RfmScore(
                row.recencyScore(),
                row.frequencyScore(),
                row.monetaryScore());
        Segment segment = classifySegment(rfm);

        return new CustomerLifetimeValueDTO(
                row.customerNumber(),
                row.customerName(),
                row.firstOrderDate(),
                row.lastOrderDate(),
                tenureDays,
                row.recencyDays(),
                row.frequency(),
                row.monetary(),
                aov,
                predictedClv,
                rfm,
                segment,
                repo.orderHistory(row.customerNumber())
        );
    }

    /**
     * Map an RFM triple to a named segment.
     *
     * <p>The mapping is the textbook "RFM segmentation matrix" you'll
     * find in any marketing-analytics blog or course. We use the
     * common 8-segment variant; some teams expand to 11 (adding
     * "About to sleep," "Promising," etc.) for more granularity.</p>
     *
     * <p>The order of these checks matters — we go from most-specific
     * (Champions) to most-fallback (Lost). The first matching rule
     * wins.</p>
     */
    private Segment classifySegment(RfmScore rfm) {
        int r = rfm.recency();
        int f = rfm.frequency();
        int m = rfm.monetary();

        // Champions — best across all three axes.
        if (r >= 4 && f >= 4 && m >= 4) return Segment.CHAMPIONS;

        // Loyal — frequent + big spenders, even if they bought a bit
        // less recently. The "(f + m) >= 7" lets one of the two be a 4
        // and the other a 3.
        if (r >= 3 && (f + m) >= 7) return Segment.LOYAL;

        // Can't lose — used to be high-value, gone quiet. The classic
        // "win-back" target. Recency is bad (1-2) but historic spend
        // and frequency are top-tier.
        if (r <= 2 && f >= 4 && m >= 4) return Segment.CANT_LOSE;

        // At risk — declining engagement, but historically good.
        if (r <= 2 && f >= 3 && m >= 3) return Segment.AT_RISK;

        // Potential loyalists — recent, decent activity. Push them
        // toward Champions with a nudge campaign.
        if (r >= 3 && f >= 2 && m >= 2) return Segment.POTENTIAL_LOYALISTS;

        // New customers — recent, but only one or two purchases.
        if (r >= 4 && f <= 2) return Segment.NEW_CUSTOMERS;

        // Hibernating — low scores everywhere; cheap to email, low ROI.
        if (r <= 2 && f <= 2 && m <= 2) return Segment.HIBERNATING;

        // Lost — the rest, by elimination.
        return Segment.LOST;
    }
}
