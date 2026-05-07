package com.hunor.classicmodelsbackend.service;

import com.hunor.classicmodelsbackend.dto.employee.BulkOperationResultDTO;
import com.hunor.classicmodelsbackend.dto.employee.EmployeeDependentDTO;
import com.hunor.classicmodelsbackend.dto.employee.EmployeeRequestDTO;
import com.hunor.classicmodelsbackend.dto.employee.EmployeeResponseDTO;
import com.hunor.classicmodelsbackend.metrics.AppMetrics;
import com.hunor.classicmodelsbackend.mapper.EmployeeMapper;
import com.hunor.classicmodelsbackend.model.Employee;
import com.hunor.classicmodelsbackend.repository.EmployeeRepository;
import com.hunor.classicmodelsbackend.repository.OfficeRepository;
import com.hunor.classicmodelsbackend.response.PageResponse;
import com.hunor.classicmodelsbackend.realtime.EmployeeEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class EmployeeService {

    private final EmployeeRepository repo;
    private final EmployeeMapper mapper;
    private final OfficeRepository officeRepo;

    /**
     * Feature flag gating the destructive {@link DeleteStrategy#DEEP_CASCADE}
     * strategy. Bound from {@code app.delete.allow-deep-cascade} in
     * application.yml; defaults to {@code false} so the strategy cannot be
     * executed unless an operator deliberately turns it on.
     *
     * <p>Best practice: any operation that destroys data not directly
     * owned by the entity being deleted (here: orders, order details,
     * payments) should sit behind a flag like this. Code paths that exist
     * but are off-by-default are easier to enable for emergency cleanup
     * than they are to add under pressure.</p>
     */
    private final boolean allowDeepCascade;

    /** Custom Micrometer metrics — counter for deletions, etc. */
    private final AppMetrics metrics;

    /**
     * Used to push {@link EmployeeEvent}s out to subscribed WebSocket
     * clients after every successful mutation. Auto-wired by Spring
     * because we have spring-boot-starter-websocket on the classpath.
     */
    private final SimpMessagingTemplate events;

    public EmployeeService(EmployeeRepository repo,
                           EmployeeMapper mapper,
                           OfficeRepository officeRepo,
                           @Value("${app.delete.allow-deep-cascade:false}") boolean allowDeepCascade,
                           SimpMessagingTemplate events,
                           AppMetrics metrics) {
        this.repo = repo;
        this.mapper = mapper;
        this.officeRepo = officeRepo;
        this.allowDeepCascade = allowDeepCascade;
        this.events = events;
        this.metrics = metrics;
    }

    /**
     * Push an event to {@code /topic/employees}. Every connected
     * client gets a copy.
     */
    private void broadcast(EmployeeEvent.Type type, int employeeNumber) {
        events.convertAndSend("/topic/employees",
                new EmployeeEvent(type, employeeNumber));
    }
    /**
     * Active employees at a given office. Used by the office detail
     * page to render the team list. Not cached — the team will rarely
     * be hit and adding cache complexity for one-off lookups isn't
     * worth it; if this becomes hot, slot it in the same way findAll
     * is cached above.
     */
    public List<EmployeeResponseDTO> findByOffice(String officeCode) {
        return repo.findByOfficeCode(officeCode).stream()
                .map(mapper::toResponseDTO)
                .toList();
    }

    @Cacheable(cacheNames = "employeesAll")
    public List<EmployeeResponseDTO> findAll() {
        log.info("Finding all employees");
        long startTime = System.currentTimeMillis();

        var result = repo.findAll().stream().map(mapper::toResponseDTO).toList();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Found {} employees in {} ms", result.size(), duration);

        return result;
    }

    @Cacheable(cacheNames = "employees", key = "#id")
    public EmployeeResponseDTO findById(int id) {
        log.info("Finding employee by id {}", id);
        long startTime = System.currentTimeMillis();

        var result = repo.findById(id)
                .map(mapper::toResponseDTO)
                .orElseThrow(() -> new RuntimeException("Employee " + id + " not found"));

        long duration = System.currentTimeMillis() - startTime;
        log.info("Found employee {} in {} ms", id, duration);

        return result;
    }

     @CachePut(cacheNames = "employees", key = "#result.employeeNumber()")
     @CacheEvict(cacheNames = "employeesAll", allEntries = true)
     public EmployeeResponseDTO create(EmployeeRequestDTO dto) {
        log.info("Creating employee {}", dto.firstName());
        long startTime = System.currentTimeMillis();

        officeRepo.findById(dto.officeCode())
                .orElseThrow(() -> new RuntimeException("Office " + dto.officeCode() + " not found"));

        if (dto.reportsTo() != null) {
            repo.findById(dto.reportsTo())
                    .orElseThrow(() -> new RuntimeException("Manager " + dto.reportsTo() + " not found"));
        }

        Employee saved = repo.save(mapper.toEntity(dto));
        var response = mapper.toResponseDTO(saved);

        broadcast(EmployeeEvent.Type.CREATED, response.employeeNumber());

        long duration = System.currentTimeMillis() - startTime;
        log.info("Created employee {} in {} ms", response.employeeNumber(), duration);

        return response;
    }

     @CachePut(cacheNames = "employees", key = "#id")
     @CacheEvict(cacheNames = "employeesAll", allEntries = true)
    public EmployeeResponseDTO update(int id, EmployeeRequestDTO dto) {
        log.info("Updating employee {}", id);
        long startTime = System.currentTimeMillis();

        var existing = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee " + id + " not found"));

        officeRepo.findById(dto.officeCode())
                .orElseThrow(() -> new RuntimeException("Office " + dto.officeCode() + " not found"));

        if (dto.reportsTo() != null) {
            repo.findById(dto.reportsTo())
                    .orElseThrow(() -> new RuntimeException("Manager " + dto.reportsTo() + " not found"));
        }

        mapper.copyToEntity(dto, existing);
        existing.setEmployeeNumber(id);
        repo.update(existing);

        broadcast(EmployeeEvent.Type.UPDATED, id);

        var response = mapper.toResponseDTO(existing);
        long duration = System.currentTimeMillis() - startTime;
        log.info("Updated employee {} in {} ms", id, duration);

        return response;
    }

    /**
     * Delete an employee using a chosen strategy. See {@link DeleteStrategy}
     * for the semantics of each option.
     *
     * <h4>Why centralise the dispatch here?</h4>
     * <p>The controller stays dumb — it only validates input and shapes
     * HTTP responses. The service layer owns the business rule of "what
     * does delete actually mean for this entity." Keeping the rule in
     * one place makes it easy to add audit logging, permission checks,
     * notifications, etc., without touching the controller or repository.</p>
     */
    @CacheEvict(cacheNames = {"employees", "employeesAll"}, allEntries = true)
    public void delete(int id, DeleteStrategy strategy) {
        log.info("Deleting employee {} with strategy {}", id, strategy);
        long startTime = System.currentTimeMillis();

        // Verify the employee exists first. This produces a clean 404-shaped
        // failure instead of a silent no-op when the id is wrong.
        repo.findById(id).orElseThrow(() -> new RuntimeException("Employee " + id + " not found"));

        switch (strategy) {
            case SOFT    -> repo.softDelete(id);
            case NULLIFY -> repo.deleteAndNullify(id);
            case CASCADE -> repo.deleteAndCascade(id);
            case DEEP_CASCADE -> {
                // Defensive gate: even though the controller binds the
                // enum from a query param, we re-check here so that any
                // future caller (a test, a scheduled job, another service)
                // also obeys the feature flag.
                if (!allowDeepCascade) {
                    throw new IllegalStateException(
                            "DEEP_CASCADE is disabled in this environment. " +
                            "Set app.delete.allow-deep-cascade=true to enable it.");
                }
                repo.deleteAndDeepCascade(id);
            }
        }

        // The DELETED event covers all four hard-delete strategies and
        // SOFT (which is technically an update — but from a UI
        // perspective the row disappears from active lists). Clients
        // re-fetch and the row is gone either way.
        broadcast(EmployeeEvent.Type.DELETED, id);

        // Custom metric: tag the counter with the strategy used so
        // /actuator/prometheus emits one timeseries per strategy.
        // Useful for spotting "we're doing way more CASCADE deletes
        // than expected" via Grafana later.
        metrics.employeeDeletions(strategy.name()).increment();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Deleted employee {} in {} ms (strategy {})", id, duration, strategy);
    }

    /**
     * Reassign the source employee's customers + direct reports to the
     * target employee, then delete the source. Runs in one transaction
     * via the repository's {@code @MyTransactional}.
     *
     * <p>Verifies both employees exist before running; throws cleanly
     * if either is missing. Errors propagate to the controller, which
     * lets the global advice translate to an HTTP response.</p>
     */
    @CacheEvict(cacheNames = {"employees", "employeesAll", "employeesPaged"}, allEntries = true)
    public void reassignAndDelete(int sourceId, int targetId) {
        log.info("Reassigning employee {} to employee {} and deleting", sourceId, targetId);

        repo.findById(sourceId).orElseThrow(() ->
                new RuntimeException("Source employee " + sourceId + " not found"));
        repo.findById(targetId).orElseThrow(() ->
                new RuntimeException("Target employee " + targetId + " not found"));

        repo.reassignAndDelete(sourceId, targetId);

        // Two events: source is gone, target had its customers/reports
        // updated. UPDATED on the target lets clients refresh the
        // target's detail page if they happen to be viewing it.
        broadcast(EmployeeEvent.Type.DELETED, sourceId);
        broadcast(EmployeeEvent.Type.UPDATED, targetId);
    }

    /**
     * Backwards-compatible single-arg variant. Defaults to {@link DeleteStrategy#NULLIFY}
     * — the closest match to the original behavior of "remove the row" while
     * keeping referential integrity. New callers should pass a strategy explicitly.
     */
    @CacheEvict(cacheNames = {"employees", "employeesAll"}, allEntries = true)
    public void delete(int id) {
        delete(id, DeleteStrategy.NULLIFY);
    }

    /**
     * Delete a batch of employees, one at a time, collecting per-id
     * outcomes into a single {@link BulkOperationResultDTO}.
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
     *       the message recorded as a {@link BulkOperationResultDTO.Failure},
     *       and processing continues with the next id.</li>
     * </ul>
     *
     * <p>This means a partially successful bulk delete is a normal
     * outcome, returned with HTTP 200. The frontend reads the result
     * envelope and surfaces a "5 succeeded, 2 failed" toast.</p>
     *
     * <p>The alternative — wrapping the whole batch in one transaction —
     * would mean any single bad id rolls everything back. Cleaner
     * semantics for "transfer money between accounts," but wrong for
     * "let me clean out these dormant employees." Bulk admin actions
     * almost always want partial success.</p>
     *
     * <h3>Live events</h3>
     *
     * <p>One DELETED event is broadcast per successful delete (inside
     * the per-item delete call), so dashboards and lists update
     * incrementally as the loop runs. We don't add an extra "bulk-
     * complete" event — clients already see N individual deletions
     * within ~250ms (debounced into one redraw on the consumer side).</p>
     */
    @CacheEvict(cacheNames = {"employees", "employeesAll"}, allEntries = true)
    public BulkOperationResultDTO bulkDelete(List<Integer> ids, DeleteStrategy strategy) {
        if (ids == null || ids.isEmpty()) {
            return new BulkOperationResultDTO(0, 0, 0, List.of());
        }

        // Distinct: protects against duplicates from the client (e.g. a
        // checkbox bug that double-counted). Doesn't change semantics
        // because deleting a row twice is a no-op-then-error anyway.
        List<Integer> distinct = ids.stream().distinct().toList();

        log.info("Bulk-delete {} employee(s) using strategy {}", distinct.size(), strategy);
        long start = System.currentTimeMillis();

        int successCount = 0;
        var failures = new ArrayList<BulkOperationResultDTO.Failure>();
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
                failures.add(new BulkOperationResultDTO.Failure(id, reason));
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("Bulk-delete done: {} succeeded, {} failed in {} ms",
                successCount, failures.size(), duration);

        return new BulkOperationResultDTO(distinct.size(), successCount, failures.size(), failures);
    }

    public List<EmployeeDependentDTO> findDependents(int id) {
        log.info("Finding dependents for employee {}", id);
        long startTime = System.currentTimeMillis();

        repo.findById(id).orElseThrow(() -> new RuntimeException("Employee " + id + " not found"));
        var result = repo.findDependents(id);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Found {} dependent table(s) for employee {} in {} ms", result.size(), id, duration);
        return result;
    }

    /**
     * Server-side paged + sorted + searchable employee list.
     *
     * <p>Cached on the full set of (page, size, sortBy, asc, search) so
     * the same query repeated within the cache window doesn't hit the
     * DB twice. Cache is evicted on any write to employees (see the
     * {@code @CacheEvict} on save/update/delete).</p>
     */
    @Cacheable(cacheNames = "employeesPaged", key = "{#page, #size, #sortBy, #asc, #search}")
    public PageResponse<EmployeeResponseDTO> findAllPaged(
            int page, int size, String sortBy, boolean asc, String search) {
        log.info("Finding employees page={} size={} sortBy={} asc={} search='{}'",
                page, size, sortBy, asc, search);
        long startTime = System.currentTimeMillis();

        var items = repo.findAllPaged(page, size, sortBy, asc, search).stream()
                .map(mapper::toResponseDTO)
                .toList();
        long total = repo.countAll(search);
        int totalPages = (int) Math.ceil((double) total / size);
        var response = new PageResponse<>(items, page, size, total, totalPages);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Found {} employees (page {}, total {}) in {} ms",
                items.size(), page, total, duration);

        return response;
    }

    /**
     * Returns the strategies the client is allowed to choose. DEEP_CASCADE
     * is hidden when the feature flag is off, so the UI never surfaces
     * an option the backend would refuse to execute.
     */
    public List<DeleteStrategy> availableDeleteStrategies() {
        List<DeleteStrategy> out = new ArrayList<>();
        out.add(DeleteStrategy.SOFT);
        out.add(DeleteStrategy.NULLIFY);
        out.add(DeleteStrategy.CASCADE);
        if (allowDeepCascade) {
            out.add(DeleteStrategy.DEEP_CASCADE);
        }
        return out;
    }

    @CacheEvict(cacheNames = "employeesAll", allEntries = true)
    public void createBulk(List<EmployeeRequestDTO> dtos) {
        log.info("Bulk creating {} employees", dtos.size());
        long startTime = System.currentTimeMillis();

        var entities = dtos.stream().map(mapper::toEntity).toList();
        repo.saveAll(entities);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Bulk created {} employees in {} ms", entities.size(), duration);
    }
}
