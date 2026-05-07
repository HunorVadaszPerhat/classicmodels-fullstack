package com.hunor.classicmodelsbackend.controller;

import com.hunor.classicmodelsbackend.dto.orderdetail.OrderDetailRequestDTO;
import com.hunor.classicmodelsbackend.dto.orderdetail.OrderDetailResponseDTO;
import com.hunor.classicmodelsbackend.response.PageResponse;
import com.hunor.classicmodelsbackend.service.OrderDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.parameters.RequestBody;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/orderdetails")
@Tag(name = "OrderDetail", description = "Order detail CRUD operations")
public class OrderDetailController {

    private final OrderDetailService service;
    public OrderDetailController(OrderDetailService service) { this.service = service; }

    @GetMapping
    @Operation(
            summary = "Find all order details",
            description = "Retrieve a list of all order details",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of order details",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = OrderDetailResponseDTO.class))))
            }
    )
    public ResponseEntity<List<OrderDetailResponseDTO>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{orderNumber}/{productCode}")
    @Operation(
            summary = "Find order detail by composite key",
            description = "Retrieve a single order detail using order number and product code",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order detail found",
                            content = @Content(schema = @Schema(implementation = OrderDetailResponseDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Order detail not found")
            }
    )
    public ResponseEntity<OrderDetailResponseDTO> findById(
            @Parameter(description = "Order number") @PathVariable int orderNumber,
            @Parameter(description = "Product code") @PathVariable String productCode) {
        return ResponseEntity.ok(service.findById(orderNumber, productCode));
    }

    @PostMapping
    @Operation(
            summary = "Create order detail",
            description = "Create a new order detail",
            requestBody = @RequestBody(
                    description = "Order detail to create",
                    required = true,
                    content = @Content(schema = @Schema(implementation = OrderDetailRequestDTO.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Order detail created",
                            content = @Content(schema = @Schema(implementation = OrderDetailResponseDTO.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input")
            }
    )
    public ResponseEntity<OrderDetailResponseDTO> create(@RequestBody OrderDetailRequestDTO dto) {
        OrderDetailResponseDTO created = service.create(dto);
        var location = URI.create("/api/orderdetails/" + created.orderNumber() + "/" + created.productCode());
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{orderNumber}/{productCode}")
    @Operation(
            summary = "Update order detail",
            description = "Update an existing order detail",
            requestBody = @RequestBody(
                    description = "Updated order detail data",
                    required = true,
                    content = @Content(schema = @Schema(implementation = OrderDetailRequestDTO.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order detail updated",
                            content = @Content(schema = @Schema(implementation = OrderDetailResponseDTO.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input"),
                    @ApiResponse(responseCode = "404", description = "Order detail not found")
            }
    )
    public ResponseEntity<OrderDetailResponseDTO> update(
            @Parameter(description = "Order number") @PathVariable int orderNumber,
            @Parameter(description = "Product code") @PathVariable String productCode,
            @RequestBody OrderDetailRequestDTO dto) {
        return ResponseEntity.ok(service.update(orderNumber, productCode, dto));
    }

    @DeleteMapping("/{orderNumber}/{productCode}")
    @Operation(
            summary = "Delete order detail",
            description = "Delete an order detail by its composite key",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Order detail deleted"),
                    @ApiResponse(responseCode = "404", description = "Order detail not found")
            }
    )
    public ResponseEntity<Void> delete(
            @Parameter(description = "Order number") @PathVariable int orderNumber,
            @Parameter(description = "Product code") @PathVariable String productCode) {
        service.delete(orderNumber, productCode);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/find-all-paged")
    @Operation(
            summary = "Find all order details (paged)",
            description = "Retrieve order details using pagination",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Paged order details retrieved",
                            content = @Content(schema = @Schema(implementation = PageResponse.class)))
            }
    )
    public ResponseEntity<PageResponse<OrderDetailResponseDTO>> findAllPaged(
            @Parameter(description = "Page number, starting from 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of records per page") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Field to sort by") @RequestParam(defaultValue = "orderNumber") String sort,
            @Parameter(description = "Sort direction (asc or desc)") @RequestParam(defaultValue = "asc") String dir
    ) {
        boolean asc = !"desc".equalsIgnoreCase(dir);
        return ResponseEntity.ok(service.findAllPaged(page, size, sort, asc));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Create order details in bulk",
            description = "Create multiple order details at once",
            requestBody = @RequestBody(
                    description = "List of order details to create",
                    required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = OrderDetailRequestDTO.class)))
            ),
            responses = {
                    @ApiResponse(responseCode = "202", description = "Bulk creation accepted"),
                    @ApiResponse(responseCode = "400", description = "Invalid input")
            }
    )
    public ResponseEntity<Void> createBulk(@RequestBody List<OrderDetailRequestDTO> dtos) {
        service.createBulk(dtos);
        return ResponseEntity.accepted().build();
    }
}
