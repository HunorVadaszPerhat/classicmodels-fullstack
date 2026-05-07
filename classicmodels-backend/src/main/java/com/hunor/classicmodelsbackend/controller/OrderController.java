package com.hunor.classicmodelsbackend.controller;

import com.hunor.classicmodelsbackend.dto.order.OrderRequestDTO;
import com.hunor.classicmodelsbackend.dto.order.OrderResponseDTO;
import com.hunor.classicmodelsbackend.response.PageResponse;
import com.hunor.classicmodelsbackend.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/orders")
@Tag(name = "Order", description = "Order CRUD operations")
public class OrderController {

    private final OrderService service;
    public OrderController(OrderService service) { this.service = service; }

    @GetMapping
    @Operation(
            summary = "Find all orders",
            description = "Retrieve a list of all orders",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of orders",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = OrderResponseDTO.class))))
            }
    )
    public ResponseEntity<List<OrderResponseDTO>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Find order by ID",
            description = "Retrieve a single order by its identifier",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order found",
                            content = @Content(schema = @Schema(implementation = OrderResponseDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Order not found")
            }
    )
    public ResponseEntity<OrderResponseDTO> findById(
            @Parameter(description = "Order identifier") @PathVariable int id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @Operation(
            summary = "Create new order",
            description = "Create a new order",
            requestBody = @RequestBody(
                    description = "Order to create",
                    required = true,
                    content = @Content(schema = @Schema(implementation = OrderRequestDTO.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Order created",
                            content = @Content(schema = @Schema(implementation = OrderResponseDTO.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input")
            }
    )
    public ResponseEntity<OrderResponseDTO> create(@RequestBody OrderRequestDTO dto) {
        OrderResponseDTO created = service.create(dto);
        return ResponseEntity.created(URI.create("/api/orders/" + created.orderNumber()))
                .body(created);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update order",
            description = "Update an existing order",
            requestBody = @RequestBody(
                    description = "Updated order data",
                    required = true,
                    content = @Content(schema = @Schema(implementation = OrderRequestDTO.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order updated",
                            content = @Content(schema = @Schema(implementation = OrderResponseDTO.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input"),
                    @ApiResponse(responseCode = "404", description = "Order not found")
            }
    )
    public ResponseEntity<OrderResponseDTO> update(
            @Parameter(description = "Order identifier") @PathVariable int id,
            @RequestBody OrderRequestDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete order",
            description = "Delete an order by its identifier",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Order deleted"),
                    @ApiResponse(responseCode = "404", description = "Order not found")
            }
    )
    public ResponseEntity<Void> delete(
            @Parameter(description = "Order identifier") @PathVariable int id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/find-all-paged")
    @Operation(
            summary = "Find all orders (paged)",
            description = "Retrieve orders using pagination",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Paged orders retrieved",
                            content = @Content(schema = @Schema(implementation = PageResponse.class)))
            }
    )
    public ResponseEntity<PageResponse<OrderResponseDTO>> findAllPaged(
            @Parameter(description = "Page number, starting from 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of records per page") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Field to sort by") @RequestParam(defaultValue = "orderDate") String sort,
            @Parameter(description = "Sort direction (asc or desc)") @RequestParam(defaultValue = "asc") String dir
    ) {
        boolean asc = !"desc".equalsIgnoreCase(dir);
        return ResponseEntity.ok(service.findAllPaged(page, size, sort, asc));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Create orders in bulk",
            description = "Create multiple orders at once",
            requestBody = @RequestBody(
                    description = "List of orders to create",
                    required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = OrderRequestDTO.class)))
            ),
            responses = {
                    @ApiResponse(responseCode = "202", description = "Bulk creation accepted"),
                    @ApiResponse(responseCode = "400", description = "Invalid input")
            }
    )
    public ResponseEntity<Void> createBulk(@RequestBody List<OrderRequestDTO> dtos) {
        service.createBulk(dtos);
        return ResponseEntity.accepted().build();
    }
}
