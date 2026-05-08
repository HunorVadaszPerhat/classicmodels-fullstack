package com.hunor.classicmodelsbackend.service;

import java.util.ArrayList;
import java.util.List;

import com.hunor.classicmodelsbackend.dto.customer.CustomerBulkOperationResultDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerMapPointDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerRequestDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerResponseDTO;
import com.hunor.classicmodelsbackend.geocoding.GeocodingService;
import com.hunor.classicmodelsbackend.realtime.CustomerEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.hunor.classicmodelsbackend.mapper.CustomerMapper;
import com.hunor.classicmodelsbackend.model.Customer;
import com.hunor.classicmodelsbackend.repository.CustomerRepository;
import com.hunor.classicmodelsbackend.repository.EmployeeRepository;
import com.hunor.classicmodelsbackend.response.PageResponse;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CustomerService {

    private final CustomerRepository repo;
    private final CustomerMapper mapper;
    private final EmployeeRepository employeeRepo;

    /**
     * Feature flag gating the destructive {@link DeleteStrategy#DEEP_CASCADE}
     * strategy. Bound from {@code app.delete.allow-deep-cascade} in
     * application.yaml. Defaults to {@code false} so the dangerous path
     * is opt-in. Reuses the same flag as employees — both entities use
     * "DEEP_CASCADE means I'll erase financial history" semantics.
     */
    private final boolean allowDeepCascade;

    /**
     * Used to push {@link CustomerEvent}s out to subscribed WebSocket
     * clients after every successful mutation. Auto-wired by Spring
     * because we have spring-boot-starter-websocket on the classpath
     * (already pulled in for employees in F10).
     */
    private final SimpMessagingTemplate events;

    /**
     * Address-to-coordinates lookup via Nominatim. Same bean the
     * office service uses (F4); injected here so C9 can resolve a
     * customer's address into lat/lng.
     */
    private final GeocodingService geocoding;

    public CustomerService(CustomerRepository repo,
                           CustomerMapper mapper,
                           EmployeeRepository employeeRepo,
                           @Value("${app.delete.allow-deep-cascade:false}") boolean allowDeepCascade,
                           SimpMessagingTemplate events,
                           GeocodingService geocoding) {
        this.repo = repo;
        this.mapper = mapper;
        this.employeeRepo = employeeRepo;
        this.allowDeepCascade = allowDeepCascade;
        this.events = events;
        this.geocoding = geocoding;
    }

    /**
     * Push an event to {@code /topic/customers}. Every connected
     * client gets a copy.
     */
    private void broadcast(CustomerEvent.Type type, int customerNumber) {
        events.convertAndSend("/topic/customers",
                new CustomerEvent(type, customerNumber));
    }

    @Cacheable(cacheNames = "customersAll")
    public List<CustomerResponseDTO> findAll() {
        log.info("Finding all customers");
        long startTime = System.currentTimeMillis();

        var result = repo.findAll().stream().map(mapper::toResponseDTO).toList();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Found {} customers in {} ms", result.size(), duration);

        return result;
    }

    @Cacheable(cacheNames = "customers", key = "#id")
    public CustomerResponseDTO findById(int id) {
        log.info("Finding a customer by ID: {}", id);
        long startTime = System.currentTimeMillis();

        var result = repo.findById(id)
                .map(mapper::toResponseDTO)
                .orElseThrow(() -> new RuntimeException("Customer " + id + " not found"));

        long duration = System.currentTimeMillis() - startTime;
        log.info("Found a customer by ID {} in {} ms", id, duration);

        return result;
    }

    @CachePut(cacheNames = "customers", key = "#result.customerNumber()")
    @CacheEvict(cacheNames = {"customersAll", "customersPaged", "customersMapPoints", "customersActiveCount", "customerActivity", "customerCreditStatus", "customerCreditAlerts", "customerCreditAlertCounts"}, allEntries = true)
    public CustomerResponseDTO create(CustomerRequestDTO dto) {
        log.info("Creating a customer: {}", dto.customerName());
        long startTime = System.currentTimeMillis();

        if (dto.salesRepEmployeeNumber() != null) {
            log.debug("Validating sales rep employee {}", dto.salesRepEmployeeNumber());
            employeeRepo.findById(dto.salesRepEmployeeNumber())
                    .orElseThrow(() -> new RuntimeException(
                            "Employee " + dto.salesRepEmployeeNumber() + " (sales rep) not found"));
        }
        Customer saved = repo.save(mapper.toEntity(dto));
        CustomerResponseDTO responseDTO = mapper.toResponseDTO(saved);

        broadcast(CustomerEvent.Type.CREATED, responseDTO.customerNumber());

        long duration = System.currentTimeMillis() - startTime;
        log.info("Created customer {} in {} ms", responseDTO.customerNumber(), duration);
        return responseDTO;
    }

    @CachePut(cacheNames = "customers", key = "#id")
    @CacheEvict(cacheNames = {"customersAll", "customersPaged", "customersMapPoints", "customersActiveCount", "customerActivity", "customerCreditStatus", "customerCreditAlerts", "customerCreditAlertCounts"}, allEntries = true)
    public CustomerResponseDTO update(int id, CustomerRequestDTO dto) {
        log.info("Updating customer {}", id);
        long startTime = System.currentTimeMillis();

        var existing = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer " + id + " not found"));

        if (dto.salesRepEmployeeNumber() != null) {
            employeeRepo.findById(dto.salesRepEmployeeNumber())
                    .orElseThrow(() -> new RuntimeException(
                            "Employee " + dto.salesRepEmployeeNumber() + " (sales rep) not found"));
        }

        mapper.copyToEntity(dto, existing);
        existing.setCustomerNumber(id);
        repo.update(existing);
        var response = mapper.toResponseDTO(existing);

        broadcast(CustomerEvent.Type.UPDATED, id);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Updated customer {} in {} ms", id, duration);
        return response;
    }

    /**
     * Delete a customer using a chosen strategy. See {@link DeleteStrategy}
     * for the semantics of each option.
     *
     * <h4>Customer-specific strategy availability</h4>
     *
     * <p>Only {@link DeleteStrategy#SOFT} and {@link DeleteStrategy#DEEP_CASCADE}
     * are valid for customer. NULLIFY and CASCADE are rejected because
     * orders.customerNumber and payments.customerNumber are NOT NULL —
     * there's no way to "shallow" cascade a customer delete. Either the
     * row stays (SOFT) or every descendant row is removed (DEEP_CASCADE).
     * REASSIGN_DELETE isn't meaningful for customer (you can't transfer
     * historical orders/payments to a different customer).</p>
     */
    @CacheEvict(cacheNames = {"customers", "customersAll", "customersPaged", "customersMapPoints", "customersActiveCount", "customerActivity", "customerCreditStatus", "customerCreditAlerts", "customerCreditAlertCounts"}, allEntries = true)
    public void delete(int id, DeleteStrategy strategy) {
        log.info("Deleting customer {} with strategy {}", id, strategy);
        long startTime = System.currentTimeMillis();

        // Verify the customer exists first. This produces a clean 404-shaped
        // failure instead of a silent no-op when the id is wrong.
        repo.findById(id).orElseThrow(() -> new RuntimeException("Customer " + id + " not found"));

        switch (strategy) {
            case SOFT -> repo.softDelete(id);
            case DEEP_CASCADE -> {
                // Defensive gate: even though the controller binds the
                // enum from a query param, we re-check here so any
                // future caller (a test, a scheduled job, another service)
                // also obeys the feature flag.
                if (!allowDeepCascade) {
                    throw new IllegalStateException(
                            "DEEP_CASCADE is disabled in this environment. " +
                            "Set app.delete.allow-deep-cascade=true to enable it.");
                }
                repo.deleteAndDeepCascade(id);
            }
            case NULLIFY, CASCADE, REASSIGN_DELETE -> throw new IllegalArgumentException(
                    strategy + " is not supported for customers — orders/payments FKs are NOT NULL. "
                    + "Use SOFT (preserves history) or DEEP_CASCADE (destroys it).");
        }

        // Both SOFT and DEEP_CASCADE map to a DELETED event. From a
        // list-page perspective the row disappears either way (the
        // active=1 filter hides soft-deleted rows). Broadcasting DELETED
        // for SOFT specifically is a small simplification — clients don't
        // need to distinguish "row gone" from "row hidden" because both
        // require the same response: re-fetch the current page.
        broadcast(CustomerEvent.Type.DELETED, id);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Deleted customer {} in {} ms (strategy {})", id, duration, strategy);
    }

    /**
     * Backwards-compatible single-arg variant. Defaults to {@link DeleteStrategy#SOFT}
     * — for customers the safest choice is to preserve order/payment history,
     * so SOFT is the natural default (vs employees, where NULLIFY is the
     * default).
     */
    public void delete(int id) {
        delete(id, DeleteStrategy.SOFT);
    }

    /**
     * Bulk-delete customers using the supplied strategy.
     *
     * <h3>Semantics — per-item, not atomic</h3>
     *
     * <p>Each id is processed in its own logical sub-operation:</p>
     *
     * <ul>
     *   <li>If {@link #delete(int, DeleteStrategy)} succeeds, the id
     *       contributes to {@code successCount}.</li>
     *   <li>If it throws (FK violation, missing row, deep-cascade
     *       feature flag disabled, etc.), the exception is caught,
     *       the message recorded as a
     *       {@link CustomerBulkOperationResultDTO.Failure}, and
     *       processing continues with the next id.</li>
     * </ul>
     *
     * <p>This means a partially successful bulk delete is a normal
     * outcome, returned with HTTP 200. The frontend reads the result
     * envelope and surfaces a "5 succeeded, 2 failed" summary.</p>
     *
     * <h3>Live events</h3>
     *
     * <p>One DELETED event is broadcast per successful delete (inside
     * the per-item delete call), so dashboards and lists update
     * incrementally as the loop runs. The frontend's natural
     * debouncing (one re-fetch per event) keeps redraws sane even for
     * large batches — RxJS coalesces the rapid-fire events into a
     * sensible refresh cadence.</p>
     */
    @CacheEvict(cacheNames = {"customers", "customersAll", "customersPaged", "customersMapPoints", "customersActiveCount", "customerActivity", "customerCreditStatus", "customerCreditAlerts", "customerCreditAlertCounts"}, allEntries = true)
    public CustomerBulkOperationResultDTO bulkDelete(List<Integer> ids, DeleteStrategy strategy) {
        if (ids == null || ids.isEmpty()) {
            return new CustomerBulkOperationResultDTO(0, 0, 0, List.of());
        }

        // Distinct: protects against duplicates from the client (e.g. a
        // checkbox bug that double-counted). Doesn't change semantics
        // because deleting a row twice is a no-op-then-error anyway.
        List<Integer> distinct = ids.stream().distinct().toList();

        log.info("Bulk-delete {} customer(s) using strategy {}", distinct.size(), strategy);
        long start = System.currentTimeMillis();

        int successCount = 0;
        var failures = new ArrayList<CustomerBulkOperationResultDTO.Failure>();
        for (Integer id : distinct) {
            try {
                delete(id, strategy);
                successCount++;
            } catch (Exception e) {
                // Capture the message; full stack-trace stays in the log
                // for ops debugging, the wire response only carries the
                // short reason.
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("Bulk-delete: id={} failed: {}", id, reason);
                failures.add(new CustomerBulkOperationResultDTO.Failure(id, reason));
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("Bulk-delete done: {} succeeded, {} failed in {} ms",
                successCount, failures.size(), duration);

        return new CustomerBulkOperationResultDTO(distinct.size(), successCount, failures.size(), failures);
    }

    /**
     * Returns the strategies the client is allowed to choose for customer
     * deletion. Customer supports SOFT always; DEEP_CASCADE is hidden
     * when the feature flag is off, so the UI never surfaces an option
     * the backend would refuse to execute. NULLIFY / CASCADE /
     * REASSIGN_DELETE are not applicable to customers and never returned.
     */
    public List<DeleteStrategy> availableDeleteStrategies() {
        List<DeleteStrategy> out = new ArrayList<>();
        out.add(DeleteStrategy.SOFT);
        if (allowDeepCascade) {
            out.add(DeleteStrategy.DEEP_CASCADE);
        }
        return out;
    }

    /**
     * Geocode a customer's address via Nominatim and persist the
     * resulting lat/lng. Returns the freshly-read customer so the
     * client can render the new map without an extra round-trip.
     *
     * <h4>Why a cascade of queries?</h4>
     *
     * <p>Real-world addresses fail to geocode on the first try all the
     * time. Different countries format addresses in different orders;
     * typos, abbreviations, and unfamiliar street names also throw off
     * the matcher. The standard mitigation is a <em>fallback chain</em>:
     * most-specific query first, less-specific if that fails. Almost
     * every geocoder can find "Tokyo, Japan" even when it can't find
     * the exact street address.</p>
     *
     * <p>Same three-rung pattern as the office equivalent (F4):
     * <ol>
     *   <li>Full address (best precision)</li>
     *   <li>City + country (lands on the city centre)</li>
     *   <li>Country alone (last-ditch country centroid)</li>
     * </ol>
     * The first match wins. If even the country fails we throw —
     * usually means Nominatim is unreachable, not that the data is
     * bad.</p>
     */
    @CacheEvict(cacheNames = {"customers", "customersAll", "customersPaged", "customersMapPoints", "customersActiveCount", "customerActivity", "customerCreditStatus", "customerCreditAlerts", "customerCreditAlertCounts"}, allEntries = true)
    public CustomerResponseDTO geocode(int id) {
        log.info("Geocoding customer {}", id);

        Customer customer = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer " + id + " not found"));

        var result = geocodeWithFallbacks(customer);
        repo.updateCoordinates(id, result.lat(), result.lng());

        // Re-read so the response reflects the persisted state, not the
        // in-memory mutation we did locally.
        Customer updated = repo.findById(id).orElseThrow();
        var response = mapper.toResponseDTO(updated);

        // Treat a geocode as an UPDATE event — the row visibly changed
        // for any client showing this customer. Detail pages with the
        // map open will pick up the new coordinates on the next refresh.
        broadcast(CustomerEvent.Type.UPDATED, id);

        return response;
    }

    /**
     * Try a sequence of progressively-less-specific queries; return the
     * first successful match.
     */
    private GeocodingService.Coordinates geocodeWithFallbacks(Customer c) {
        List<String> queries = buildFallbackQueries(c);
        for (String query : queries) {
            log.info("Trying geocode query: '{}'", query);
            var attempt = geocoding.geocode(query);
            if (attempt.isPresent()) {
                log.info("Match found via '{}'", query);
                return attempt.get();
            }
        }
        throw new RuntimeException(
                "Geocoding returned no result for any of these queries: " + queries);
    }

    /**
     * Build the fallback chain for a customer, most-specific first.
     */
    private List<String> buildFallbackQueries(Customer c) {
        List<String> queries = new ArrayList<>();

        // 1. Full address — best precision when it parses.
        String full = buildAddressQuery(c);
        if (!full.isBlank()) queries.add(full);

        // 2. City + country — robust fallback. Lands on the city
        //    centre, which is good enough for "where in the world is
        //    this customer" purposes.
        String cityCountry = compose(c.getCity(), c.getCountry());
        if (!cityCountry.isBlank() && !cityCountry.equals(full)) {
            queries.add(cityCountry);
        }

        // 3. Country alone — last-ditch fallback. Returns the country
        //    centroid, which is too coarse for most uses but better
        //    than failing entirely.
        if (c.getCountry() != null && !c.getCountry().isBlank()
                && !queries.contains(c.getCountry())) {
            queries.add(c.getCountry());
        }
        return queries;
    }

    /**
     * Compose a Nominatim-friendly query from the customer's address.
     * Format: {@code addressLine1, addressLine2?, city, state? postalCode, country}.
     */
    private String buildAddressQuery(Customer c) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, c.getAddressLine1());
        appendIfPresent(sb, c.getAddressLine2());
        appendIfPresent(sb, c.getCity());
        String stateAndZip = (c.getState() != null ? c.getState() + " " : "")
                + (c.getPostalCode() != null ? c.getPostalCode() : "");
        appendIfPresent(sb, stateAndZip.trim());
        appendIfPresent(sb, c.getCountry());
        return sb.toString();
    }

    private static String compose(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) appendIfPresent(sb, p);
        return sb.toString();
    }

    private static void appendIfPresent(StringBuilder sb, String s) {
        if (s == null || s.isBlank()) return;
        if (sb.length() > 0) sb.append(", ");
        sb.append(s.trim());
    }

    /**
     * Lightweight projection for the all-customers map (C10).
     * Returns only active customers that have been geocoded — un-geocoded
     * rows can't be plotted. The frontend reports "X of Y geocoded" using
     * {@link #countActiveCustomers()} so users know how many customers
     * don't appear and can fill them in via per-customer geocoding.
     */
    @Cacheable(cacheNames = "customersMapPoints")
    public List<CustomerMapPointDTO> findAllMapPoints() {
        log.info("Loading customer map points");
        long start = System.currentTimeMillis();
        var points = repo.findAllMapPoints();
        log.info("Loaded {} map points in {} ms", points.size(), System.currentTimeMillis() - start);
        return points;
    }

    /**
     * Total number of active customers (geocoded or not). Cheap query;
     * the map page uses it to render the "X of Y geocoded" indicator.
     */
    @Cacheable(cacheNames = "customersActiveCount")
    public long countActiveCustomers() {
        return repo.countAllActive();
    }

    /**
     * Server-side paged + sorted + searched listing.
     *
     * <p>The cache key is the full input tuple so different combinations
     * of (page, size, sortBy, asc, search) cache independently. Same query
     * within the cache window hits the cache; first call hits the DB.
     * The {@code @CacheEvict(allEntries = true)} on save/update/delete
     * (already on those methods) clears the cache on every write, so
     * stale pages can't be served after a mutation.</p>
     */
    @Cacheable(cacheNames = "customersPaged", key = "{#page, #size, #sortBy, #asc, #search, #country}")
    public PageResponse<CustomerResponseDTO> findAllPaged(
            int page, int size, String sortBy, boolean asc, String search, String country) {
        log.info("Finding customers page={} size={} sortBy={} asc={} search='{}' country='{}'",
                page, size, sortBy, asc, search, country);
        long startTime = System.currentTimeMillis();

        var items = repo.findAllPaged(page, size, sortBy, asc, search, country).stream()
                .map(mapper::toResponseDTO)
                .toList();
        long total = repo.countAll(search, country);
        int totalPages = (int) Math.ceil((double) total / size);
        var response = new PageResponse<>(items, page, size, total, totalPages);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Found {} customers (page {} of {}) in {} ms",
                items.size(), page, totalPages, duration);
        return response;
    }

    @CacheEvict(cacheNames = {"customersAll", "customersPaged", "customersMapPoints", "customersActiveCount", "customerActivity", "customerCreditStatus", "customerCreditAlerts", "customerCreditAlertCounts"}, allEntries = true)
    public void createBulk(List<CustomerRequestDTO> dtos) {
        log.info("Creating {} customers in bulk", dtos.size());
        long startTime = System.currentTimeMillis();

        var entities = dtos.stream().map(mapper::toEntity).toList();
        repo.saveAll(entities);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Bulk created {} customers in {} ms", entities.size(), duration);
    }
}
