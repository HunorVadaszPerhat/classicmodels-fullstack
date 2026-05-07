package com.hunor.classicmodelsbackend.controller;

import com.hunor.classicmodelsbackend.dto.employee.BulkDeleteRequestDTO;
import com.hunor.classicmodelsbackend.dto.employee.BulkOperationResultDTO;
import com.hunor.classicmodelsbackend.dto.employee.EmployeeDependentDTO;
import com.hunor.classicmodelsbackend.dto.employee.EmployeeRequestDTO;
import com.hunor.classicmodelsbackend.dto.employee.EmployeeResponseDTO;
import com.hunor.classicmodelsbackend.photo.PhotoStorageService;
import com.hunor.classicmodelsbackend.service.DeleteStrategy;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import com.hunor.classicmodelsbackend.response.PageResponse;
import com.hunor.classicmodelsbackend.service.EmployeeService;
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

@RestController
@RequestMapping("/employees")
@Tag(name = "Employee", description = "Employee CRUD operations")
public class EmployeeController {

    private final EmployeeService service;
    private final PhotoStorageService photos;

    public EmployeeController(EmployeeService service, PhotoStorageService photos) {
        this.service = service;
        this.photos = photos;
    }

    @GetMapping
    @Operation(
            summary = "Find employees, optionally filtered by office",
            description = "Returns all active employees. " +
                    "If ?officeCode= is supplied, only employees assigned to that office are returned. " +
                    "Used by the office detail page to render the team list.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of employees",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = EmployeeResponseDTO.class))))
            }
    )
    public ResponseEntity<List<EmployeeResponseDTO>> findAll(
            @Parameter(description = "Optional: only return employees in this office")
            @RequestParam(name = "officeCode", required = false) String officeCode) {
        // The controller picks the right service method based on whether
        // the filter was supplied. This keeps the service API explicit —
        // findAll() means everyone, findByOffice() means a known subset.
        if (officeCode != null && !officeCode.isBlank()) {
            return ResponseEntity.ok(service.findByOffice(officeCode));
        }
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Find employee by ID",
            description = "Retrieve a single employee by its identifier",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Employee found",
                            content = @Content(schema = @Schema(implementation = EmployeeResponseDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Employee not found")
            }
    )
    public ResponseEntity<EmployeeResponseDTO> findById(
            @Parameter(description = "Employee identifier") @PathVariable int id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @Operation(
            summary = "Create new employee",
            description = "Create a new employee",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Employee to create",
                    required = true,
                    content = @Content(schema = @Schema(implementation = EmployeeRequestDTO.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Employee created",
                            content = @Content(schema = @Schema(implementation = EmployeeResponseDTO.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input")
            }
    )
    public ResponseEntity<EmployeeResponseDTO> create(@RequestBody EmployeeRequestDTO dto) {
        var created = service.create(dto);
        return ResponseEntity.created(URI.create("/api/employees/" + created.employeeNumber()))
                .body(created);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update employee",
            description = "Update an existing employee",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Updated employee data",
                    required = true,
                    content = @Content(schema = @Schema(implementation = EmployeeRequestDTO.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Employee updated",
                            content = @Content(schema = @Schema(implementation = EmployeeResponseDTO.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input"),
                    @ApiResponse(responseCode = "404", description = "Employee not found")
            }
    )
    public ResponseEntity<EmployeeResponseDTO> update(
            @Parameter(description = "Employee identifier") @PathVariable int id,
            @RequestBody EmployeeRequestDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    /**
     * Delete an employee using the supplied strategy.
     *
     * <p>The {@code strategy} query parameter is bound to the
     * {@link DeleteStrategy} enum directly. Spring will reject unknown
     * values with HTTP 400 automatically, so we don't need any manual
     * validation here. If the parameter is omitted, we default to
     * {@code NULLIFY} — the safest "remove the row" behaviour that
     * doesn't lose history.</p>
     *
     * <p>Examples:
     * <pre>
     *   DELETE /employees/1002?strategy=SOFT
     *   DELETE /employees/1002?strategy=NULLIFY
     *   DELETE /employees/1002?strategy=CASCADE
     * </pre></p>
     */
    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete employee",
            description = "Delete an employee using a chosen strategy: SOFT (mark as terminated, default for HR data), " +
                    "NULLIFY (hard delete; set referencing FKs to NULL), or CASCADE (hard delete; also " +
                    "remove customers that reference this employee — fails if those customers have orders or payments).",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Employee deleted"),
                    @ApiResponse(responseCode = "400", description = "Unknown strategy value"),
                    @ApiResponse(responseCode = "404", description = "Employee not found"),
                    @ApiResponse(responseCode = "500", description = "Cascade rejected — customer has orders/payments")
            }
    )
    public ResponseEntity<Void> delete(
            @Parameter(description = "Employee identifier") @PathVariable int id,
            @Parameter(description = "Delete strategy: SOFT, NULLIFY, or CASCADE")
            @RequestParam(name = "strategy", defaultValue = "NULLIFY") DeleteStrategy strategy) {
        service.delete(id, strategy);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/delete-strategies")
    @Operation(
            summary = "List allowed delete strategies",
            description = "Returns the DeleteStrategy values the server is willing to execute. " +
                    "DEEP_CASCADE is only included when the app.delete.allow-deep-cascade " +
                    "feature flag is enabled. The UI uses this to hide options that the " +
                    "backend would refuse, instead of showing them and erroring out.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of enabled strategies",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = DeleteStrategy.class))))
            }
    )
    public ResponseEntity<List<DeleteStrategy>> deleteStrategies() {
        return ResponseEntity.ok(service.availableDeleteStrategies());
    }

    @PostMapping("/{id}/reassign-and-delete")
    @Operation(
            summary = "Reassign customers + direct reports to another employee, then delete",
            description = "Atomically transfers every customer assigned to this employee, and " +
                    "every employee reporting to this employee, to {targetEmployeeId}. " +
                    "Then deletes the source employee. All three statements run in one transaction.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Reassigned and deleted"),
                    @ApiResponse(responseCode = "400", description = "Source equals target"),
                    @ApiResponse(responseCode = "404", description = "Source or target not found")
            }
    )
    public ResponseEntity<Void> reassignAndDelete(
            @Parameter(description = "Source employee identifier") @PathVariable int id,
            @RequestBody ReassignRequest body) {
        service.reassignAndDelete(id, body.targetEmployeeId());
        return ResponseEntity.noContent().build();
    }

    /** Request body for the reassign-and-delete endpoint. */
    public record ReassignRequest(int targetEmployeeId) {}

    @PostMapping("/bulk-delete")
    @Operation(
            summary = "Bulk-delete employees",
            description = "Delete every employee whose id appears in the request body, " +
                          "using the chosen strategy. Per-item failures are returned in " +
                          "the response envelope rather than rolling back the whole batch.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Outcome — see body for per-item results",
                            content = @Content(schema = @Schema(implementation = BulkOperationResultDTO.class)))
            }
    )
    public ResponseEntity<BulkOperationResultDTO> bulkDelete(
            @org.springframework.web.bind.annotation.RequestBody BulkDeleteRequestDTO request) {
        // Default to NULLIFY — same convention as the single-row endpoint —
        // if the client forgets to specify. Preserves backwards-compat with
        // earlier callers that may not know about the strategy enum.
        DeleteStrategy strategy = request.strategy() != null
                ? request.strategy()
                : DeleteStrategy.NULLIFY;
        return ResponseEntity.ok(service.bulkDelete(request.ids(), strategy));
    }

    @GetMapping("/{id}/dependents")
    @Operation(
            summary = "Find tables that reference this employee",
            description = "Returns each child table/column that has at least one row " +
                    "referencing the given employeeNumber via a foreign key. Used by the " +
                    "UI to warn the user before attempting a delete that would violate a constraint.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Dependents listed",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = EmployeeDependentDTO.class)))),
                    @ApiResponse(responseCode = "404", description = "Employee not found")
            }
    )
    public ResponseEntity<List<EmployeeDependentDTO>> findDependents(
            @Parameter(description = "Employee identifier") @PathVariable int id) {
        return ResponseEntity.ok(service.findDependents(id));
    }

    // ----------------------------------------------------------------
    //  Profile photo (Feature 9)
    // ----------------------------------------------------------------

    /**
     * Upload (or replace) the employee's profile photo.
     *
     * <p>The {@code @RequestParam("file")} part name is conventional —
     * the multipart form field has to be named {@code file} on the
     * client side. {@link MultipartFile} is Spring's wrapper around
     * the uploaded byte stream + metadata.</p>
     */
    @PostMapping(path = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload or replace profile photo",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Photo saved"),
                    @ApiResponse(responseCode = "400", description = "Unsupported image type or no file"),
                    @ApiResponse(responseCode = "404", description = "Employee not found"),
                    @ApiResponse(responseCode = "413", description = "File too large (limit 5MB)")
            }
    )
    public ResponseEntity<Void> uploadPhoto(
            @PathVariable int id,
            @RequestParam("file") MultipartFile file) throws java.io.IOException {

        // Refuse uploads for unknown employees rather than silently
        // creating an orphan file.
        service.findById(id);   // throws if not found
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded");
        }
        photos.save(id, file);
        return ResponseEntity.noContent().build();
    }

    /**
     * Stream the employee's profile photo as bytes. 404 if there
     * isn't one — the frontend handles that as "show a generic avatar."
     *
     * <p>{@link PathResource} is Spring's wrapper that reads from a
     * filesystem {@link Path} efficiently (streams chunks rather than
     * loading the entire file into memory).</p>
     */
    @GetMapping("/{id}/photo")
    @Operation(
            summary = "Fetch profile photo",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Photo bytes"),
                    @ApiResponse(responseCode = "404", description = "No photo on file")
            }
    )
    public ResponseEntity<Resource> getPhoto(@PathVariable int id) {
        return photos.find(id)
                .map(path -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(photos.contentTypeOf(path)))
                        .<Resource>body(new PathResource(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Delete the employee's profile photo. Idempotent — succeeds even
     * if there isn't one.
     */
    @DeleteMapping("/{id}/photo")
    @Operation(summary = "Delete profile photo")
    public ResponseEntity<Void> deletePhoto(@PathVariable int id) {
        photos.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/find-all-paged")
    @Operation(
            summary = "Find all employees (paged)",
            description = "Retrieve employees using pagination",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Paged employees retrieved",
                            content = @Content(schema = @Schema(implementation = PageResponse.class)))
            }
    )
    public ResponseEntity<PageResponse<EmployeeResponseDTO>> findAllPaged(
            @Parameter(description = "Page number, starting from 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of records per page") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Field to sort by") @RequestParam(defaultValue = "lastName") String sort,
            @Parameter(description = "Sort direction (asc or desc)") @RequestParam(defaultValue = "asc") String dir,
            @Parameter(description = "Optional: substring search across lastName, firstName, email")
            @RequestParam(name = "search", required = false) String search
    ) {
        boolean asc = !"desc".equalsIgnoreCase(dir);
        return ResponseEntity.ok(service.findAllPaged(page, size, sort, asc, search));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Create employees in bulk",
            description = "Create multiple employees at once",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "List of employees to create",
                    required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = EmployeeRequestDTO.class)))
            ),
            responses = {
                    @ApiResponse(responseCode = "202", description = "Bulk creation accepted"),
                    @ApiResponse(responseCode = "400", description = "Invalid input")
            }
    )
    public ResponseEntity<Void> createBulk(@RequestBody List<EmployeeRequestDTO> dtos) {
        service.createBulk(dtos);
        return ResponseEntity.accepted().build();
    }
}
