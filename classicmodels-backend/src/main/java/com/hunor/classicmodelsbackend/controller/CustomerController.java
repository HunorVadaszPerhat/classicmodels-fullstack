package com.hunor.classicmodelsbackend.controller;

import com.hunor.classicmodelsbackend.dto.customer.CustomerActivityDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerBulkDeleteRequestDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerBulkOperationResultDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerCreditStatusDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerLifetimeValueDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerMapPointDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerMergeCandidateDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerMergeRequestDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerRequestDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerResponseDTO;
import com.hunor.classicmodelsbackend.response.PageResponse;
import com.hunor.classicmodelsbackend.service.CustomerActivityService;
import com.hunor.classicmodelsbackend.service.CustomerCreditService;
import com.hunor.classicmodelsbackend.service.CustomerLifetimeValueService;
import com.hunor.classicmodelsbackend.service.CustomerMergeService;
import com.hunor.classicmodelsbackend.service.CustomerService;
import com.hunor.classicmodelsbackend.service.DeleteStrategy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/customers")
@Tag(name = "Customer", description = "Customer CRUD operations")
public class CustomerController {

    private final CustomerService service;
    private final CustomerLifetimeValueService clvService;
    private final CustomerActivityService activityService;
    private final CustomerMergeService mergeService;
    private final CustomerCreditService creditService;

    public CustomerController(CustomerService service,
                              CustomerLifetimeValueService clvService,
                              CustomerActivityService activityService,
                              CustomerMergeService mergeService,
                              CustomerCreditService creditService) {
        this.service = service;
        this.clvService = clvService;
        this.activityService = activityService;
        this.mergeService = mergeService;
        this.creditService = creditService;
    }

    @GetMapping
    @Operation(
            summary = "Find all customers",
            description = "Retrieve a list of all customers",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of customers",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = CustomerResponseDTO.class))))
            }
    )
    public ResponseEntity<List<CustomerResponseDTO>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id:\\d+}")
    @Operation(
            summary = "Find customer by ID",
            description = "Retrieve a single customer by its identifier",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Customer found",
                            content = @Content(schema = @Schema(implementation = CustomerResponseDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Customer not found")
            }
    )
    public ResponseEntity<CustomerResponseDTO> findById(
            @Parameter(description = "Customer identifier") @PathVariable int id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @Operation(
            summary = "Create new customer",
            description = "Create a new customer",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Customer to create",
                    required = true,
                    content = @Content(schema = @Schema(implementation = CustomerRequestDTO.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Customer created",
                            content = @Content(schema = @Schema(implementation = CustomerResponseDTO.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input")
            }
    )
    public ResponseEntity<CustomerResponseDTO> create(@RequestBody CustomerRequestDTO dto) {
        CustomerResponseDTO created = service.create(dto);
        return ResponseEntity.created(Objects.requireNonNull(URI.create("/api/customers/" + created.customerNumber())))
                .body(created);
    }

    @PutMapping("/{id:\\d+}")
    @Operation(
            summary = "Update customer",
            description = "Update an existing customer",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Updated customer data",
                    required = true,
                    content = @Content(schema = @Schema(implementation = CustomerRequestDTO.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Customer updated",
                            content = @Content(schema = @Schema(implementation = CustomerResponseDTO.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input"),
                    @ApiResponse(responseCode = "404", description = "Customer not found")
            }
    )
    public ResponseEntity<CustomerResponseDTO> update(
            @Parameter(description = "Customer identifier") @PathVariable int id,
            @RequestBody CustomerRequestDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    /**
     * Delete a customer using the supplied strategy.
     *
     * <p>Default is {@code SOFT} — the safest choice for customer data
     * because orders and payments retain valid FKs into the customer
     * row. Use {@code DEEP_CASCADE} only when you genuinely want to
     * erase the customer's entire order/payment history; the strategy
     * is gated by {@code app.delete.allow-deep-cascade}.</p>
     *
     * <p>Examples:
     * <pre>
     *   DELETE /customers/103?strategy=SOFT
     *   DELETE /customers/103?strategy=DEEP_CASCADE  (only when flag is on)
     * </pre></p>
     */
    @DeleteMapping("/{id:\\d+}")
    @Operation(
            summary = "Delete customer",
            description = "Delete a customer using a chosen strategy: SOFT (mark as terminated, " +
                          "preserves orders/payments — recommended) or DEEP_CASCADE (hard delete; " +
                          "removes the customer + every order, order detail, and payment underneath).",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Customer deleted"),
                    @ApiResponse(responseCode = "400", description = "Unknown or unsupported strategy"),
                    @ApiResponse(responseCode = "404", description = "Customer not found")
            }
    )
    public ResponseEntity<Void> delete(
            @Parameter(description = "Customer identifier") @PathVariable int id,
            @Parameter(description = "Delete strategy: SOFT (default) or DEEP_CASCADE")
            @RequestParam(name = "strategy", defaultValue = "SOFT") DeleteStrategy strategy) {
        service.delete(id, strategy);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/delete-strategies")
    @Operation(
            summary = "List allowed delete strategies for customer",
            description = "Returns the DeleteStrategy values the server is willing to execute for " +
                          "customer deletion. SOFT is always available; DEEP_CASCADE is only " +
                          "included when the app.delete.allow-deep-cascade feature flag is on. " +
                          "NULLIFY/CASCADE/REASSIGN_DELETE are not applicable to customers and " +
                          "never returned.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of enabled strategies",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = DeleteStrategy.class))))
            }
    )
    public ResponseEntity<List<DeleteStrategy>> deleteStrategies() {
        return ResponseEntity.ok(service.availableDeleteStrategies());
    }

    @PostMapping("/bulk-delete")
    @Operation(
            summary = "Bulk-delete customers",
            description = "Delete every customer whose id appears in the request body, " +
                          "using the chosen strategy. Per-item failures are returned in " +
                          "the response envelope rather than rolling back the whole batch.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Outcome — see body for per-item results",
                            content = @Content(schema = @Schema(implementation = CustomerBulkOperationResultDTO.class)))
            }
    )
    public ResponseEntity<CustomerBulkOperationResultDTO> bulkDelete(
            @RequestBody CustomerBulkDeleteRequestDTO request) {
        // Default to SOFT — the safest option for customer data, mirroring
        // the single-row endpoint's default. (Vs employees, where the
        // default is NULLIFY because customer FKs there are nullable.)
        DeleteStrategy strategy = request.strategy() != null
                ? request.strategy()
                : DeleteStrategy.SOFT;
        return ResponseEntity.ok(service.bulkDelete(request.ids(), strategy));
    }

    @PostMapping("/{id:\\d+}/geocode")
    @Operation(
            summary = "Geocode a customer's address",
            description = "Looks up the customer's address via Nominatim and persists the " +
                          "resulting lat/lng. Uses a fallback chain — full address, then " +
                          "city + country, then country — so non-Western or imperfect " +
                          "addresses still resolve to something sensible. Returns the " +
                          "freshly-read customer with the new coordinates.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Customer geocoded",
                            content = @Content(schema = @Schema(implementation = CustomerResponseDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Customer not found"),
                    @ApiResponse(responseCode = "500", description = "Geocoding failed (Nominatim unreachable, all fallback queries empty)")
            }
    )
    public ResponseEntity<CustomerResponseDTO> geocode(
            @Parameter(description = "Customer identifier") @PathVariable int id) {
        return ResponseEntity.ok(service.geocode(id));
    }

    @GetMapping("/map-points")
    @Operation(
            summary = "Lightweight projection for the all-customers map",
            description = "Returns one row per active, geocoded customer with just the fields " +
                          "the map needs (id, name, city, country, lat, lng, hasSalesRep). " +
                          "Un-geocoded customers are excluded — use the per-customer geocode " +
                          "button on the detail page to fill them in.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Map points",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = CustomerMapPointDTO.class))))
            }
    )
    public ResponseEntity<List<CustomerMapPointDTO>> mapPoints() {
        return ResponseEntity.ok(service.findAllMapPoints());
    }

    @GetMapping("/credit-alerts")
    @Operation(
            summary = "Customers at or near their credit limit (C13)",
            description = "Returns active customers whose outstanding balance is ≥ 80% of their " +
                          "credit limit, sorted by utilisation descending. NEAR_LIMIT (utilisation " +
                          "0.80–1.00) and OVER_LIMIT (> 1.00) are both included; OK and NO_LIMIT " +
                          "customers are excluded server-side.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Alerts",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = CustomerCreditStatusDTO.class))))
            }
    )
    public ResponseEntity<List<CustomerCreditStatusDTO>> creditAlerts() {
        return ResponseEntity.ok(creditService.findAlerts());
    }

    @GetMapping("/active-count")
    @Operation(
            summary = "Total active customers (geocoded or not)",
            description = "Used by the all-customers map page to render the 'X of Y geocoded' " +
                          "indicator so users understand how many customers don't appear on the map.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Count",
                            content = @Content(schema = @Schema(implementation = Long.class)))
            }
    )
    public ResponseEntity<Long> activeCount() {
        return ResponseEntity.ok(service.countActiveCustomers());
    }

    @GetMapping("/find-all-paged")
    @Operation(
            summary = "Find all customers (paged)",
            description = "Retrieve customers using pagination",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Paged customers retrieved",
                            content = @Content(schema = @Schema(implementation = PageResponse.class)))
            }
    )
    public ResponseEntity<PageResponse<CustomerResponseDTO>> findAllPaged(
            @Parameter(description = "Page number, starting from 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of records per page") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Field to sort by") @RequestParam(defaultValue = "customerName") String sort,
            @Parameter(description = "Sort direction (asc or desc)") @RequestParam(defaultValue = "asc") String dir,
            @Parameter(description = "Optional: substring search across customerName, contactLastName, contactFirstName")
            @RequestParam(name = "search", required = false) String search,
            @Parameter(description = "Optional: exact-match country filter (case-insensitive). Used by the C14 dashboard drill-down.")
            @RequestParam(name = "country", required = false) String country
    ) {
        boolean asc = !"desc".equalsIgnoreCase(dir);
        return ResponseEntity.ok(service.findAllPaged(page, size, sort, asc, search, country));
    }

    @GetMapping("/{id:\\d+}/lifetime-value")
    @Operation(
            summary = "Customer lifetime value snapshot",
            description = "Returns CLV metrics + RFM scores + segment classification + " +
                          "order history for a single customer.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Snapshot",
                            content = @Content(schema = @Schema(implementation = CustomerLifetimeValueDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Customer has no order history")
            }
    )
    public ResponseEntity<CustomerLifetimeValueDTO> lifetimeValue(
            @Parameter(description = "Customer identifier") @PathVariable int id) {
        return ResponseEntity.ok(clvService.computeForCustomer(id));
    }

    @GetMapping("/{id:\\d+}/activity")
    @Operation(
            summary = "Unified customer activity timeline",
            description = "Returns all orders and payments for one customer interleaved by date, " +
                          "plus header aggregations (lifetime spend, total paid, outstanding balance, " +
                          "order/payment counts, first/last activity dates). One round trip; the " +
                          "frontend filters client-side via the All / Orders / Payments chip group.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Activity",
                            content = @Content(schema = @Schema(implementation = CustomerActivityDTO.class)))
            }
    )
    public ResponseEntity<CustomerActivityDTO> activity(
            @Parameter(description = "Customer identifier") @PathVariable int id) {
        return ResponseEntity.ok(activityService.findActivityForCustomer(id));
    }

    @GetMapping("/{id:\\d+}/credit-status")
    @Operation(
            summary = "Credit utilisation snapshot for one customer",
            description = "Returns the customer's credit limit, outstanding balance " +
                          "(lifetime spend − total paid), utilisation ratio, and category " +
                          "(OK / NEAR_LIMIT / OVER_LIMIT / NO_LIMIT).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Snapshot",
                            content = @Content(schema = @Schema(implementation = CustomerCreditStatusDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Customer not found or inactive")
            }
    )
    public ResponseEntity<CustomerCreditStatusDTO> creditStatus(
            @Parameter(description = "Customer identifier") @PathVariable int id) {
        return ResponseEntity.ok(creditService.findStatus(id));
    }

    @GetMapping("/{id:\\d+}/merge-candidates")
    @Operation(
            summary = "Find potential duplicates of one customer",
            description = "Fuzzy-matches every other active customer's name, contact name, and " +
                          "phone against the source's. Returns candidates above a similarity " +
                          "threshold, ranked descending. Each entry includes a 0.0–1.0 score and " +
                          "the list of fields whose individual similarity exceeded the per-field " +
                          "threshold ('name', 'contact', 'phone').",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Candidates",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = CustomerMergeCandidateDTO.class)))),
                    @ApiResponse(responseCode = "404", description = "Source customer not found")
            }
    )
    public ResponseEntity<List<CustomerMergeCandidateDTO>> mergeCandidates(
            @Parameter(description = "Source customer identifier") @PathVariable int id,
            @Parameter(description = "Minimum combined similarity (0.0–1.0)")
            @RequestParam(name = "threshold", defaultValue = "0.7") double threshold,
            @Parameter(description = "Maximum number of candidates to return")
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(mergeService.findCandidates(id, threshold, limit));
    }

    @PostMapping("/{winnerId:\\d+}/merge")
    @Operation(
            summary = "Merge one customer into another",
            description = "Reassigns every order and payment from the loser to the winner, " +
                          "applies any per-field 'use loser's value' overrides, then deletes " +
                          "the loser — all in one transaction. The winner's id and version " +
                          "stay the same; its content fields may change based on the override " +
                          "map.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Merge succeeded; returns the surviving (winner) customer",
                            content = @Content(schema = @Schema(implementation = CustomerResponseDTO.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request — same id, inactive customer, or override of a structural field"),
                    @ApiResponse(responseCode = "404", description = "Winner or loser not found")
            }
    )
    public ResponseEntity<CustomerResponseDTO> merge(
            @Parameter(description = "Winner customer identifier (the one that survives)") @PathVariable int winnerId,
            @RequestBody CustomerMergeRequestDTO request) {
        return ResponseEntity.ok(mergeService.merge(winnerId, request));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Create customers in bulk",
            description = "Create multiple customers at once",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "List of customers to create",
                    required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CustomerRequestDTO.class)))
            ),
            responses = {
                    @ApiResponse(responseCode = "202", description = "Bulk creation accepted"),
                    @ApiResponse(responseCode = "400", description = "Invalid input")
            }
    )
    public ResponseEntity<Void> createBulk(@RequestBody List<CustomerRequestDTO> dtos) {
        service.createBulk(dtos);
        return ResponseEntity.accepted().build();
    }
}
