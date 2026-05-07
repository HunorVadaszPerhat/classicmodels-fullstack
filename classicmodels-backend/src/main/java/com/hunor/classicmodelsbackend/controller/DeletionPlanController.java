package com.hunor.classicmodelsbackend.controller;

import com.hunor.classicmodelsbackend.dto.deletion.DeletionPlan;
import com.hunor.classicmodelsbackend.service.DeletionPlannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only diagnostic endpoint exposing {@link DeletionPlannerService}.
 *
 * <h3>Why is this under {@code /admin/}?</h3>
 * <p>The endpoint is harmless on its own — it never modifies data — but
 * it does reveal the schema's foreign-key topology and lets callers
 * count rows under arbitrary FK chains. In a real deployment you'd want
 * this gated behind authentication and likely an admin-role check. The
 * URL prefix is a marker for "this is operational tooling, not part of
 * the public API." A {@code SecurityFilterChain} bean restricting
 * {@code /admin/**} would be the natural follow-up.</p>
 *
 * <p>Example:
 * <pre>
 *   GET /api/v1/admin/deletion-plan?table=employees&id-column=employeeNumber&id=1370
 * </pre></p>
 */
@RestController
@RequestMapping("/admin/deletion-plan")
@Tag(name = "Admin / Deletion Planner",
        description = "Read-only preview of what a deep cascade delete would touch.")
public class DeletionPlanController {

    private final DeletionPlannerService planner;

    public DeletionPlanController(DeletionPlannerService planner) {
        this.planner = planner;
    }

    @GetMapping
    @Operation(
            summary = "Plan a deep-cascade deletion (read-only)",
            description = "Walks INFORMATION_SCHEMA from the given (table, idColumn, id) and " +
                    "returns the ordered list of SQL statements that would execute, plus a " +
                    "row-count projection per step. Self-referencing foreign keys produce a " +
                    "NULLIFY step rather than a cascading DELETE. Composite foreign keys are " +
                    "skipped with a WARNING step. Nothing is actually executed.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Plan produced",
                            content = @Content(schema = @Schema(implementation = DeletionPlan.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid table/column identifier")
            }
    )
    public ResponseEntity<DeletionPlan> plan(
            @Parameter(description = "Root table whose row would be deleted",
                    example = "employees")
            @RequestParam("table") String table,

            @Parameter(description = "Primary-key column of the root table",
                    example = "employeeNumber")
            @RequestParam(name = "id-column") String idColumn,

            @Parameter(description = "Identifier value (numeric or string; the planner binds it as a parameter)",
                    example = "1370")
            @RequestParam("id") String id) {

        // Coerce numeric ids to Long so they bind to integer columns
        // without driver-level type juggling. String ids (e.g.
        // products.productCode) flow through unchanged.
        Object boundId = coerceId(id);

        return ResponseEntity.ok(planner.plan(table, idColumn, boundId));
    }

    /**
     * Best-effort numeric coercion: try {@code Long.parseLong}, fall back
     * to the raw string. Avoids surprises like binding the literal string
     * "1370" to an INT column on databases with strict type checking.
     */
    private static Object coerceId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return raw;
        }
    }
}
