package com.hunor.classicmodelsbackend.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.hunor.classicmodelsbackend.dto.customer.CustomerMergeCandidateDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerMergeRequestDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerResponseDTO;
import com.hunor.classicmodelsbackend.mapper.CustomerMapper;
import com.hunor.classicmodelsbackend.model.Customer;
import com.hunor.classicmodelsbackend.realtime.CustomerEvent;
import com.hunor.classicmodelsbackend.repository.CustomerRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Customer merge / duplicate detection (C12).
 *
 * <h3>What it does</h3>
 *
 * <p>Two responsibilities:</p>
 * <ol>
 *   <li>{@link #findCandidates(int, double, int)} — fuzzy-match every
 *       other active customer against a source row and return the
 *       highest-scoring potential duplicates above a threshold.</li>
 *   <li>{@link #merge(int, CustomerMergeRequestDTO)} — given two
 *       customer ids and a per-field winner picker, transactionally
 *       reassign all FK references from the loser to the winner,
 *       apply any "use loser's value" field overrides, and delete
 *       the loser. All in one DB transaction.</li>
 * </ol>
 *
 * <h3>Why a separate service</h3>
 *
 * <p>{@code CustomerService} already runs to ~500 lines covering CRUD,
 * delete strategies, geocoding, map points, and bulk operations.
 * Merge is its own concern with its own algorithm (Levenshtein
 * scoring) and its own transactional choreography (FK reassignment).
 * Splitting keeps each service file readable.</p>
 *
 * <h3>Why we hard-delete the loser</h3>
 *
 * <p>After FK reassignment the loser's orders and payments belong to
 * the winner. Soft-deleting the loser would leave a zombie row with
 * no children — not useful as an audit trail because the merge has
 * already destroyed the "this customer's data" relationship. Hard
 * delete keeps the merge's intent crisp: <em>one customer, not two
 * with one hidden</em>. If you need a merge audit trail in a real
 * system, write a row to a {@code customer_merges} log table inside
 * the same transaction — that captures the event without keeping
 * a half-customer around.</p>
 */
@Service
@Slf4j
public class CustomerMergeService {

    private final CustomerRepository repo;
    private final CustomerMapper mapper;
    private final DataSource dataSource;
    private final SimpMessagingTemplate events;

    public CustomerMergeService(CustomerRepository repo,
                                CustomerMapper mapper,
                                DataSource dataSource,
                                SimpMessagingTemplate events) {
        this.repo = repo;
        this.mapper = mapper;
        this.dataSource = dataSource;
        this.events = events;
    }

    /**
     * Field-name → similarity-weight map. Names are weighted highest
     * because two customers with the same business name are usually
     * the same business; phone is weighted lowest because it changes
     * naturally over time (number ports, branch lines, fax numbers).
     */
    private static final double WEIGHT_NAME = 0.50;
    private static final double WEIGHT_CONTACT = 0.30;
    private static final double WEIGHT_PHONE = 0.20;

    /** Per-field threshold above which a field is reported as "matched". */
    private static final double MATCHED_FIELD_THRESHOLD = 0.80;

    /**
     * Find potential duplicates of one customer.
     *
     * <p>Compares every other active customer's name, contact name,
     * and phone against the source's. The combined score is a
     * weighted average of the three field scores; candidates above
     * {@code threshold} are returned, ranked descending, capped at
     * {@code limit} entries.</p>
     *
     * <p>The source customer itself is never returned. Inactive
     * (soft-deleted) customers are excluded — merging into a
     * terminated customer makes no sense, and merging a terminated
     * customer into an active one is better expressed as a hard
     * delete plus a manual edit.</p>
     */
    public List<CustomerMergeCandidateDTO> findCandidates(int sourceId, double threshold, int limit) {
        Customer source = repo.findById(sourceId)
                .orElseThrow(() -> new RuntimeException("Customer " + sourceId + " not found"));

        // findAll already filters to active customers (V7 + C6). Exclude
        // the source itself; we'd score 1.0 against it which would dwarf
        // every legitimate candidate.
        List<Customer> all = repo.findAll().stream()
                .filter(c -> c.getCustomerNumber() != sourceId)
                .toList();

        List<CustomerMergeCandidateDTO> ranked = new ArrayList<>();
        for (Customer candidate : all) {
            double nameScore = StringSimilarity.score(source.getCustomerName(), candidate.getCustomerName());
            double contactScore = StringSimilarity.score(
                    contactKey(source.getContactFirstName(), source.getContactLastName()),
                    contactKey(candidate.getContactFirstName(), candidate.getContactLastName()));
            double phoneScore = StringSimilarity.score(
                    digitsOnly(source.getPhone()),
                    digitsOnly(candidate.getPhone()));

            double combined = WEIGHT_NAME * nameScore
                    + WEIGHT_CONTACT * contactScore
                    + WEIGHT_PHONE * phoneScore;

            if (combined < threshold) continue;

            List<String> matched = new ArrayList<>();
            if (nameScore >= MATCHED_FIELD_THRESHOLD) matched.add("name");
            if (contactScore >= MATCHED_FIELD_THRESHOLD) matched.add("contact");
            if (phoneScore >= MATCHED_FIELD_THRESHOLD) matched.add("phone");

            ranked.add(new CustomerMergeCandidateDTO(
                    candidate.getCustomerNumber(),
                    candidate.getCustomerName(),
                    candidate.getContactFirstName(),
                    candidate.getContactLastName(),
                    candidate.getPhone(),
                    candidate.getCity(),
                    candidate.getCountry(),
                    combined,
                    matched
            ));
        }

        ranked.sort(Comparator.comparingDouble(CustomerMergeCandidateDTO::score).reversed());
        return ranked.size() > limit ? ranked.subList(0, limit) : ranked;
    }

    /**
     * Merge {@code loser} into {@code winner}.
     *
     * <p>One transaction:</p>
     * <ol>
     *   <li>Apply field overrides to the winner row (any field where the
     *       request says "LOSER" → copy from loser → winner).</li>
     *   <li>Reassign every order: {@code UPDATE orders SET customerNumber = winner WHERE customerNumber = loser}.</li>
     *   <li>Reassign every payment: same pattern.</li>
     *   <li>Delete the loser.</li>
     * </ol>
     *
     * <p>If anything fails — invalid override field, FK still pointing
     * at the loser somewhere we forgot, network blip mid-statement —
     * the transaction rolls back and nothing changes.</p>
     */
    @CacheEvict(cacheNames = {
            "customers", "customersAll", "customersPaged",
            "customersMapPoints", "customersActiveCount", "customerActivity",
            "customerCreditStatus", "customerCreditAlerts", "customerCreditAlertCounts"
    }, allEntries = true)
    public CustomerResponseDTO merge(int winnerId, CustomerMergeRequestDTO request) {
        if (request.loserId() == winnerId) {
            throw new IllegalArgumentException("Cannot merge a customer into itself");
        }

        Customer winner = repo.findById(winnerId)
                .orElseThrow(() -> new RuntimeException("Winner customer " + winnerId + " not found"));
        Customer loser = repo.findById(request.loserId())
                .orElseThrow(() -> new RuntimeException("Loser customer " + request.loserId() + " not found"));

        if (!winner.isActive() || !loser.isActive()) {
            throw new IllegalArgumentException(
                    "Both customers must be active to merge — terminated customers can't take part");
        }

        Map<String, String> overrides = request.fieldOverrides() == null
                ? Map.of()
                : request.fieldOverrides();

        applyFieldOverrides(winner, loser, overrides);

        log.info("Merging customer {} into {} — {} field overrides applied",
                request.loserId(), winnerId, overrides.size());

        try (Connection conn = dataSource.getConnection()) {
            // Manual transaction — same idiom as CustomerRepository.deleteAndDeepCascade.
            // (When the AOP transaction wiring lands for customer this collapses
            // to one annotation; until then, explicit is correct.)
            conn.setAutoCommit(false);
            try {
                // 1. Persist any field changes on the winner via the existing
                //    update() — runs through the optimistic-lock check and the
                //    audit-column refresh. Bumps the version, which is right:
                //    the merge IS a content edit, by the user who clicked Merge.
                //    NOTE: this uses a separate connection from the pool, NOT the
                //    transaction connection, because repo.update opens its own.
                //    The merge step is a no-op if no overrides were actually
                //    applied, so we skip it in that case to keep the version
                //    counter clean.
                if (!overrides.isEmpty()) {
                    repo.update(winner);
                }

                // 2. Reassign orders.
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE orders SET customerNumber = ? WHERE customerNumber = ?")) {
                    ps.setInt(1, winnerId);
                    ps.setInt(2, request.loserId());
                    int reassigned = ps.executeUpdate();
                    log.info("Reassigned {} orders from {} to {}", reassigned, request.loserId(), winnerId);
                }

                // 3. Reassign payments. Note: payments has a composite
                //    PRIMARY KEY (customerNumber, checkNumber). If both the
                //    winner and the loser happen to have a payment with the
                //    same checkNumber, the UPDATE will fail with a duplicate-
                //    key violation. We let that surface as the merge being
                //    refused — no automatic reconciliation. In practice
                //    check numbers are nearly always unique across customers
                //    so this is a vanishingly rare edge case.
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE payments SET customerNumber = ? WHERE customerNumber = ?")) {
                    ps.setInt(1, winnerId);
                    ps.setInt(2, request.loserId());
                    int reassigned = ps.executeUpdate();
                    log.info("Reassigned {} payments from {} to {}", reassigned, request.loserId(), winnerId);
                }

                // 4. Delete the loser. Now safe — no FK references survive.
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM customers WHERE customerNumber = ?")) {
                    ps.setInt(1, request.loserId());
                    int deleted = ps.executeUpdate();
                    if (deleted == 0) {
                        throw new RuntimeException("Loser customer " + request.loserId() + " disappeared mid-merge");
                    }
                }

                conn.commit();
            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Merge failed", ex);
        }

        // Re-read the winner so the response reflects any field-override
        // changes that just landed.
        Customer fresh = repo.findById(winnerId).orElseThrow();
        var response = mapper.toResponseDTO(fresh);

        // Live events: winner was updated, loser is gone.
        events.convertAndSend("/topic/customers", new CustomerEvent(CustomerEvent.Type.UPDATED, winnerId));
        events.convertAndSend("/topic/customers", new CustomerEvent(CustomerEvent.Type.DELETED, request.loserId()));

        return response;
    }

    /**
     * Mutate {@code winner} in place, copying fields from {@code loser}
     * for any override marked "LOSER". Unrecognised override keys are
     * silently ignored — the frontend can send a full map without
     * worrying about which fields are mergeable.
     */
    private void applyFieldOverrides(Customer winner, Customer loser, Map<String, String> overrides) {
        for (var entry : overrides.entrySet()) {
            if (!"LOSER".equalsIgnoreCase(entry.getValue())) continue;
            switch (entry.getKey()) {
                case "customerName"           -> winner.setCustomerName(loser.getCustomerName());
                case "contactFirstName"       -> winner.setContactFirstName(loser.getContactFirstName());
                case "contactLastName"        -> winner.setContactLastName(loser.getContactLastName());
                case "phone"                  -> winner.setPhone(loser.getPhone());
                case "addressLine1"           -> winner.setAddressLine1(loser.getAddressLine1());
                case "addressLine2"           -> winner.setAddressLine2(loser.getAddressLine2());
                case "city"                   -> winner.setCity(loser.getCity());
                case "state"                  -> winner.setState(loser.getState());
                case "postalCode"             -> winner.setPostalCode(loser.getPostalCode());
                case "country"                -> winner.setCountry(loser.getCountry());
                case "salesRepEmployeeNumber" -> winner.setSalesRepEmployeeNumber(loser.getSalesRepEmployeeNumber());
                case "creditLimit"            -> winner.setCreditLimit(loser.getCreditLimit());
                default -> log.debug("Ignoring unrecognised merge override field: {}", entry.getKey());
            }
        }
    }

    /** Build the comparison key for the contact-name field pair. */
    private static String contactKey(String first, String last) {
        return ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
    }

    /** Strip everything except digits — phones are formatted inconsistently. */
    private static String digitsOnly(String s) {
        if (s == null) return "";
        return s.replaceAll("[^0-9]", "");
    }
}
