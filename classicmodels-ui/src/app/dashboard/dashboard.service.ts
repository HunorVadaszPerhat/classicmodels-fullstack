import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

/**
 * Mirrors com.hunor.classicmodelsbackend.dto.dashboard.SalesDashboardDTO.
 *
 * <p>BigDecimal serializes as a JSON number by default, so the four
 * money fields come through as plain JS numbers. That's fine for the
 * scale of this dataset (max ~10M); for serious financial reporting
 * you'd want them as strings to avoid float rounding.</p>
 */
export interface SalesDashboard {
  totalRevenue: number;
  totalOrders: number;
  averageOrderValue: number;
  activeCustomers: number;

  /**
   * Counts of customers near or over their credit limit (C13).
   * Drives the "Credit alerts" KPI tile, which links to
   * /customers/credit-alerts.
   */
  creditAlerts: { overLimitCount: number; nearLimitCount: number };

  revenueByMonth:        { month: string; revenue: number }[];
  revenueByProductLine:  { productLine: string; revenue: number }[];
  topCustomers:          { customerNumber: number; customerName: string; revenue: number }[];
  ordersByStatus:        Record<string, number>;
  /**
   * Revenue + customer count grouped by country (C14). Drives the
   * "Revenue by country" chart and supports drill-back into the
   * customer list filtered to one country.
   */
  revenueByCountry:      { country: string; revenue: number; customerCount: number }[];
}

/** Service-layer wrapper around GET /dashboard/sales. */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/dashboard';

  getSalesDashboard() {
    return this.http.get<SalesDashboard>(`${this.baseUrl}/sales`);
  }
}
