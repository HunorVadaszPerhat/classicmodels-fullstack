package com.hunor.classicmodelsbackend.controller;

import com.hunor.classicmodelsbackend.dto.payment.PaymentRequestDTO;
import com.hunor.classicmodelsbackend.dto.payment.PaymentResponseDTO;
import com.hunor.classicmodelsbackend.response.PageResponse;
import com.hunor.classicmodelsbackend.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/payments")
@Tag(name = "Payment", description = "Payment CRUD operations")
public class PaymentController {

    private final PaymentService service;
    public PaymentController(PaymentService service) { this.service = service; }

    @GetMapping
    @Operation(
            summary = "Find all payments",
            description = "Retrieve a list of all payments",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of payments",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = PaymentResponseDTO.class))))
            }
    )
    public ResponseEntity<List<PaymentResponseDTO>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{customerNumber}/{checkNumber}")
    @Operation(
            summary = "Find payment by composite key",
            description = "Retrieve a single payment by its customer number and check number",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Payment found",
                            content = @Content(schema = @Schema(implementation = PaymentResponseDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Payment not found")
            }
    )
    public ResponseEntity<PaymentResponseDTO> findById(
            @Parameter(description = "Customer identifier") @PathVariable int customerNumber,
            @Parameter(description = "Check identifier") @PathVariable String checkNumber) {
        return ResponseEntity.ok(service.findById(customerNumber, checkNumber));
    }

    @PostMapping
    @Operation(
            summary = "Create new payment",
            description = "Create a new payment",
            requestBody = @RequestBody(
                    description = "Payment to create",
                    required = true,
                    content = @Content(schema = @Schema(implementation = PaymentRequestDTO.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Payment created",
                            content = @Content(schema = @Schema(implementation = PaymentResponseDTO.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input")
            }
    )
    public ResponseEntity<PaymentResponseDTO> create(@RequestBody PaymentRequestDTO dto) {
        PaymentResponseDTO created = service.create(dto);
        var location = URI.create("/api/payments/" + created.customerNumber() + "/" + created.checkNumber());
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{customerNumber}/{checkNumber}")
    @Operation(
            summary = "Update payment",
            description = "Update an existing payment",
            requestBody = @RequestBody(
                    description = "Updated payment data",
                    required = true,
                    content = @Content(schema = @Schema(implementation = PaymentRequestDTO.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Payment updated",
                            content = @Content(schema = @Schema(implementation = PaymentResponseDTO.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input"),
                    @ApiResponse(responseCode = "404", description = "Payment not found")
            }
    )
    public ResponseEntity<PaymentResponseDTO> update(
            @Parameter(description = "Customer identifier") @PathVariable int customerNumber,
            @Parameter(description = "Check identifier") @PathVariable String checkNumber,
            @RequestBody PaymentRequestDTO dto) {
        return ResponseEntity.ok(service.update(customerNumber, checkNumber, dto));
    }

    @DeleteMapping("/{customerNumber}/{checkNumber}")
    @Operation(
            summary = "Delete payment",
            description = "Delete a payment by its customer number and check number",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Payment deleted"),
                    @ApiResponse(responseCode = "404", description = "Payment not found")
            }
    )
    public ResponseEntity<Void> delete(
            @Parameter(description = "Customer identifier") @PathVariable int customerNumber,
            @Parameter(description = "Check identifier") @PathVariable String checkNumber) {
        service.delete(customerNumber, checkNumber);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/find-all-paged")
    @Operation(
            summary = "Find all payments (paged)",
            description = "Retrieve payments using pagination",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Paged payments retrieved",
                            content = @Content(schema = @Schema(implementation = PageResponse.class)))
            }
    )
    public ResponseEntity<PageResponse<PaymentResponseDTO>> findAllPaged(
            @Parameter(description = "Page number, starting from 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of records per page") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Field to sort by") @RequestParam(defaultValue = "paymentDate") String sort,
            @Parameter(description = "Sort direction (asc or desc)") @RequestParam(defaultValue = "asc") String dir
    ) {
        boolean asc = !"desc".equalsIgnoreCase(dir);
        return ResponseEntity.ok(service.findAllPaged(page, size, sort, asc));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Create payments in bulk",
            description = "Create multiple payments at once",
            requestBody = @RequestBody(
                    description = "List of payments to create",
                    required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PaymentRequestDTO.class)))
            ),
            responses = {
                    @ApiResponse(responseCode = "202", description = "Bulk creation accepted"),
                    @ApiResponse(responseCode = "400", description = "Invalid input")
            }
    )
    public ResponseEntity<Void> createBulk(@RequestBody List<PaymentRequestDTO> dtos) {
        service.createBulk(dtos);
        return ResponseEntity.accepted().build();
    }
}
