package com.hunor.classicmodelsbackend.controller;

import com.hunor.classicmodelsbackend.dto.product.ProductRequestDTO;
import com.hunor.classicmodelsbackend.dto.product.ProductResponseDTO;
import com.hunor.classicmodelsbackend.response.PageResponse;
import com.hunor.classicmodelsbackend.service.ProductService;
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
@RequestMapping("/products")
@Tag(name = "Product", description = "Product CRUD operations")
public class ProductController {

    private final ProductService service;
    public ProductController(ProductService service) { this.service = service; }

    @GetMapping
    @Operation(
            summary = "Find all products",
            description = "Retrieve a list of all products",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of products",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = ProductResponseDTO.class))))
            }
    )
    public ResponseEntity<List<ProductResponseDTO>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{code}")
    @Operation(
            summary = "Find product by code",
            description = "Retrieve a single product by its code",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Product found",
                            content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Product not found")
            }
    )
    public ResponseEntity<ProductResponseDTO> findById(
            @Parameter(description = "Product code") @PathVariable String code) {
        return ResponseEntity.ok(service.findById(code));
    }

    @PostMapping
    @Operation(
            summary = "Create new product",
            description = "Create a new product",
            requestBody = @RequestBody(
                    description = "Product to create",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ProductRequestDTO.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Product created",
                            content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input")
            }
    )
    public ResponseEntity<ProductResponseDTO> create(@RequestBody ProductRequestDTO dto) {
        ProductResponseDTO created = service.create(dto);
        return ResponseEntity.created(URI.create("/api/products/" + created.productCode()))
                .body(created);
    }

    @PutMapping("/{code}")
    @Operation(
            summary = "Update product",
            description = "Update an existing product",
            requestBody = @RequestBody(
                    description = "Updated product data",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ProductRequestDTO.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Product updated",
                            content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input"),
                    @ApiResponse(responseCode = "404", description = "Product not found")
            }
    )
    public ResponseEntity<ProductResponseDTO> update(
            @Parameter(description = "Product code") @PathVariable String code,
            @RequestBody ProductRequestDTO dto) {
        return ResponseEntity.ok(service.update(code, dto));
    }

    @DeleteMapping("/{code}")
    @Operation(
            summary = "Delete product",
            description = "Delete a product by its code",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Product deleted"),
                    @ApiResponse(responseCode = "404", description = "Product not found")
            }
    )
    public ResponseEntity<Void> delete(
            @Parameter(description = "Product code") @PathVariable String code) {
        service.delete(code);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/find-all-paged")
    @Operation(
            summary = "Find all products (paged)",
            description = "Retrieve products using pagination",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Paged products retrieved",
                            content = @Content(schema = @Schema(implementation = PageResponse.class)))
            }
    )
    public ResponseEntity<PageResponse<ProductResponseDTO>> findAllPaged(
            @Parameter(description = "Page number, starting from 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of records per page") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Field to sort by") @RequestParam(defaultValue = "productName") String sort,
            @Parameter(description = "Sort direction (asc or desc)") @RequestParam(defaultValue = "asc") String dir
    ) {
        boolean asc = !"desc".equalsIgnoreCase(dir);
        return ResponseEntity.ok(service.findAllPaged(page, size, sort, asc));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Create products in bulk",
            description = "Create multiple products at once",
            requestBody = @RequestBody(
                    description = "List of products to create",
                    required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProductRequestDTO.class)))
            ),
            responses = {
                    @ApiResponse(responseCode = "202", description = "Bulk creation accepted"),
                    @ApiResponse(responseCode = "400", description = "Invalid input")
            }
    )
    public ResponseEntity<Void> createBulk(@RequestBody List<ProductRequestDTO> dtos) {
        service.createBulk(dtos);
        return ResponseEntity.accepted().build();
    }
}
