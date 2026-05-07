package com.hunor.classicmodelsbackend.metrics;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom application metrics, exposed alongside the JVM/HTTP/DB metrics
 * Spring Boot Actuator already publishes by default.
 *
 * <h3>Three meter types, one of each</h3>
 *
 * <ul>
 *   <li><b>{@link Counter}</b> ({@link #employeeDeletions(String)}) —
 *       a strictly-increasing whole-number meter. We tag each datapoint
 *       with the deletion strategy ({@code SOFT}, {@code NULLIFY},
 *       {@code CASCADE}, {@code DEEP_CASCADE}, {@code REASSIGN_DELETE})
 *       so Prometheus / Grafana can group by tag. Counters are the
 *       right tool for "how many things have happened" — never reset.</li>
 *
 *   <li><b>{@link Timer}</b> ({@link #photoUploadTimer}) — records
 *       both the COUNT and the DURATION distribution of an operation.
 *       One line of code per measurement gets you median, p95, p99,
 *       and total count out of the same meter. Use any time you
 *       want to know how slow something is or how often it fires.</li>
 *
 *   <li><b>Gauge</b> ({@link #customerCount}) — a sampleable value
 *       that can go up or down. Registered via the registry's
 *       {@code .gauge(...)} factory; Micrometer reads from the
 *       supplied AtomicInteger every scrape. Use for "current size
 *       of X" or "how many active Y's" — anything where you don't
 *       count events, you measure a state.</li>
 * </ul>
 *
 * <h3>What's already there for free</h3>
 *
 * <p>Spring Boot Actuator + Micrometer auto-register meters for:</p>
 *
 * <ul>
 *   <li>JVM memory pools, GC, threads, classloader, file descriptors.</li>
 *   <li>HTTP server requests (count, latency, status codes — by URI tag).</li>
 *   <li>HikariCP connection pool stats.</li>
 *   <li>Tomcat thread pool, sessions.</li>
 *   <li>Logback log event counts by level.</li>
 * </ul>
 *
 * <p>So before you add custom metrics, look at {@code GET /actuator/metrics}
 * and check whether what you want is already exposed. Most "boring"
 * production observability is satisfied by the built-ins.</p>
 */
@Component
@Slf4j
public class AppMetrics {

    private final MeterRegistry registry;
    private final DataSource dataSource;

    /**
     * Backing value for the customers-count gauge. We update it on a
     * schedule (or could on every customer create/delete event); the
     * gauge reads the current value at scrape time.
     */
    private final AtomicInteger customerCount = new AtomicInteger(0);

    /**
     * Photo-upload duration timer, registered once and reused for
     * every recording. Pre-fetched out of the registry so the hot
     * path doesn't pay the lookup cost each call.
     */
    private final Timer photoUploadTimer;

    public AppMetrics(MeterRegistry registry, DataSource dataSource) {
        this.registry = registry;
        this.dataSource = dataSource;

        // Register the gauge once. The registry calls customerCount.get()
        // every time a metrics scrape happens.
        registry.gauge("classicmodels.customers.count", customerCount);

        // Pre-build the timer. Description and tags can be set here;
        // the percentiles config controls which quantiles to publish
        // alongside the count + sum (we ask for p50, p95, p99).
        this.photoUploadTimer = Timer.builder("classicmodels.photo.upload.duration")
                .description("Time taken to write an uploaded employee photo to disk")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    @PostConstruct
    void initSnapshot() {
        // Initial population so the first scrape after startup has a
        // non-zero gauge. Updated by refreshCustomerCount() below; in
        // a real app you'd also hook customer create/delete to bump
        // the value rather than re-querying.
        refreshCustomerCount();
    }

    /**
     * Increment the deletions counter, tagged by strategy.
     *
     * <p>Counters created with {@code .tag(...)} produce one timeseries
     * per unique tag value. Result in Prometheus:</p>
     *
     * <pre>
     *   classicmodels_employee_deletions_total{strategy="SOFT"} 7
     *   classicmodels_employee_deletions_total{strategy="CASCADE"} 2
     * </pre>
     */
    public Counter employeeDeletions(String strategy) {
        // Counter.builder + .register is the canonical way; the
        // registry caches by name+tags, so calling this from a hot
        // path is fine — we only build the meter once per strategy.
        return Counter.builder("classicmodels.employee.deletions")
                .description("Total employees deleted, tagged by strategy")
                .tag("strategy", strategy)
                .register(registry);
    }

    /** Run a block of code, recording its duration into the photo-upload timer. */
    public Timer photoUploadTimer() {
        return photoUploadTimer;
    }

    /**
     * Re-fetch the customer count from the database. Called once on
     * startup; in production you'd schedule this every minute via
     * {@code @Scheduled} or trigger it from create/delete handlers.
     */
    public void refreshCustomerCount() {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM customers")) {
            if (rs.next()) {
                customerCount.set(rs.getInt(1));
            }
        } catch (Exception e) {
            log.warn("Failed to refresh customer count metric", e);
        }
    }
}
