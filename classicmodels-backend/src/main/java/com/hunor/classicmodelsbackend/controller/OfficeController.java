package com.hunor.classicmodelsbackend.controller;

import com.hunor.classicmodelsbackend.dto.office.OfficeRequestDTO;
import com.hunor.classicmodelsbackend.dto.office.OfficeResponseDTO;
import com.hunor.classicmodelsbackend.response.PageResponse;
import com.hunor.classicmodelsbackend.service.OfficeService;
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
@RequestMapping("/offices")
@Tag(name = "Office", description = "Office CRUD operations")
public class OfficeController {

    private final OfficeService service;
    public OfficeController(OfficeService service) { this.service = service; }

    @GetMapping
    @Operation(
            summary = "Find all offices",
            description = "Retrieve a list of all offices",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of offices",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = OfficeResponseDTO.class))))
            }
    )
    public ResponseEntity<List<OfficeResponseDTO>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{code}")
    @Operation(
            summary = "Find office by code",
            description = "Retrieve a single office by its code",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Office found",
                            content = @Content(schema = @Schema(implementation = OfficeResponseDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Office not found")
            }
    )
    public ResponseEntity<OfficeResponseDTO> findById(
            @Parameter(description = "Office code") @PathVariable String code) {
        return ResponseEntity.ok(service.findById(code));
    }

    @PostMapping
    @Operation(
            summary = "Create new office",
            description = "Create a new office",
            requestBody = @RequestBody(
                    description = "Office to create",
                    required = true,
                    content = @Content(schema = @Schema(implementation = OfficeRequestDTO.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Office created",
                            content = @Content(schema = @Schema(implementation = OfficeResponseDTO.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input")
            }
    )
    public ResponseEntity<OfficeResponseDTO> create(@RequestBody OfficeRequestDTO dto) {
        OfficeResponseDTO created = service.create(dto);
        return ResponseEntity.created(URI.create("/api/offices/" + created.officeCode()))
                .body(created);
    }

    @PutMapping("/{code}")
    @Operation(
            summary = "Update office",
            description = "Update an existing office",
            requestBody = @RequestBody(
                    description = "Updated office data",
                    required = true,
                    content = @Content(schema = @Schema(implementation = OfficeRequestDTO.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Office updated",
                            content = @Content(schema = @Schema(implementation = OfficeResponseDTO.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input"),
                    @ApiResponse(responseCode = "404", description = "Office not found")
            }
    )
    public ResponseEntity<OfficeResponseDTO> update(
            @Parameter(description = "Office code") @PathVariable String code,
            @RequestBody OfficeRequestDTO dto) {
        return ResponseEntity.ok(service.update(code, dto));
    }

    @PostMapping("/{code}/geocode")
    @Operation(
            summary = "Geocode this office's address",
            description = "Calls Nominatim (OpenStreetMap's free geocoding service) with the " +
                    "office's address fields, parses the result, and saves the resulting lat/lng " +
                    "back to the row. Returns the updated office. Idempotent: re-geocoding an " +
                    "already-geocoded office just refreshes the coordinates.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Office geocoded and saved",
                            content = @Content(schema = @Schema(implementation = OfficeResponseDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Office not found"),
                    @ApiResponse(responseCode = "500", description = "Geocoder returned no result or call failed")
            }
    )
    public ResponseEntity<OfficeResponseDTO> geocode(
            @Parameter(description = "Office code") @PathVariable String code) {
        return ResponseEntity.ok(service.geocode(code));
    }

    @DeleteMapping("/{code}")
    @Operation(
            summary = "Delete office",
            description = "Delete an office by its code",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Office deleted"),
                    @ApiResponse(responseCode = "404", description = "Office not found")
            }
    )
    public ResponseEntity<Void> delete(
            @Parameter(description = "Office code") @PathVariable String code) {
        service.delete(code);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/find-all-paged")
    @Operation(
            summary = "Find all offices (paged)",
            description = "Retrieve offices using pagination",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Paged offices retrieved",
                            content = @Content(schema = @Schema(implementation = PageResponse.class)))
            }
    )
    public ResponseEntity<PageResponse<OfficeResponseDTO>> findAllPaged(
            @Parameter(description = "Page number, starting from 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of records per page") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Field to sort by") @RequestParam(defaultValue = "officeCode") String sort,
            @Parameter(description = "Sort direction (asc or desc)") @RequestParam(defaultValue = "asc") String dir
    ) {
        boolean asc = !"desc".equalsIgnoreCase(dir);
        return ResponseEntity.ok(service.findAllPaged(page, size, sort, asc));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Create offices in bulk",
            description = "Create multiple offices at once",
            requestBody = @RequestBody(
                    description = "List of offices to create",
                    required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = OfficeRequestDTO.class)))
            ),
            responses = {
                    @ApiResponse(responseCode = "202", description = "Bulk creation accepted"),
                    @ApiResponse(responseCode = "400", description = "Invalid input")
            }
    )
    public ResponseEntity<Void> createBulk(@RequestBody List<OfficeRequestDTO> dtos) {
        service.createBulk(dtos);
        return ResponseEntity.accepted().build();
    }
}
