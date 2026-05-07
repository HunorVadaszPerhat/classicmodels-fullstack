package com.hunor.classicmodelsbackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hunor.classicmodelsbackend.dto.dashboard.SalesDashboardDTO;
import com.hunor.classicmodelsbackend.service.DashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Read-only endpoints powering the sales dashboard page.
 *
 * <p>Lives at {@code /dashboard/**}. No mutations — these endpoints
 * only aggregate existing data, so there's no need for {@code POST},
 * optimistic locking, or live-events broadcasting.</p>
 */
@RestController
@RequestMapping("/dashboard")
@Tag(name = "Dashboard", description = "Read-only aggregations for the sales dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/sales")
    @Operation(
            summary = "Sales dashboard snapshot",
            description = "Returns KPIs and chart datasets in a single response. " +
                          "Computed on-demand from the orders / orderdetails / customers tables.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Snapshot",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = SalesDashboardDTO.class)))
            }
    )
    public ResponseEntity<SalesDashboardDTO> getSalesDashboard() {
        return ResponseEntity.ok(service.getSalesDashboard());
    }
}
