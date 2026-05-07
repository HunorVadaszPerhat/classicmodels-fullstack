package com.hunor.classicmodelsbackend.controller;

import com.hunor.classicmodelsbackend.dto.customer.CustomerLifetimeValueDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerRequestDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerResponseDTO;
import com.hunor.classicmodelsbackend.response.PageResponse;
import com.hunor.classicmodelsbackend.service.CustomerLifetimeValueService;
import com.hunor.classicmodelsbackend.service.CustomerService;
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
    public CustomerController(CustomerService service,
                              CustomerLifetimeValueService clvService) {
        this.service = service;
        this.clvService = clvService;
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

    @GetMapping("/{id}")
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

    @PutMapping("/{id}")
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

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete customer",
            description = "Delete a customer by its identifier",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Customer deleted"),
                    @ApiResponse(responseCode = "404", description = "Customer not found")
            }
    )
    public ResponseEntity<Void> delete(
            @Parameter(description = "Customer identifier") @PathVariable int id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
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
            @Parameter(description = "Sort direction (asc or desc)") @RequestParam(defaultValue = "asc") String dir
    ) {
        boolean asc = !"desc".equalsIgnoreCase(dir);
        return ResponseEntity.ok(service.findAllPaged(page, size, sort, asc));
    }

    @GetMapping("/{id}/lifetime-value")
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
