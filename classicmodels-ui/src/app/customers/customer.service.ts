import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Customer } from './customer.model';

/**
 * RFM segment names. Mirrors the Java enum
 * com.hunor.classicmodelsbackend.dto.customer.CustomerLifetimeValueDTO.Segment.
 */
export type CustomerSegment =
  | 'CHAMPIONS'
  | 'LOYAL'
  | 'POTENTIAL_LOYALISTS'
  | 'NEW_CUSTOMERS'
  | 'AT_RISK'
  | 'CANT_LOSE'
  | 'HIBERNATING'
  | 'LOST';

/**
 * Snapshot returned by GET /customers/{id}/lifetime-value.
 * Mirrors com.hunor.classicmodelsbackend.dto.customer.CustomerLifetimeValueDTO.
 */
export interface CustomerLifetimeValue {
  customerNumber: number;
  customerName: string;

  firstOrderDate: string;
  lastOrderDate: string;
  tenureDays: number;
  recencyDays: number;

  totalOrders: number;
  totalRevenue: number;
  averageOrderValue: number;
  predictedClv: number;

  rfm: { recency: number; frequency: number; monetary: number };
  segment: CustomerSegment;

  orderHistory: {
    orderNumber: number;
    orderDate: string;
    status: string;
    amount: number;
  }[];
}

/**
 * Server-side page envelope. Mirrors backend's
 * com.hunor.classicmodelsbackend.response.PageResponse.
 */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Mirror of the backend `DeleteStrategy` enum, narrowed to the values
 * the customer endpoint actually returns. Customer never returns
 * NULLIFY / CASCADE / REASSIGN_DELETE — those aren't applicable
 * because orders.customerNumber and payments.customerNumber are NOT NULL.
 */
export type CustomerDeleteStrategy = 'SOFT' | 'DEEP_CASCADE';

/**
 * Mirrors com.hunor.classicmodelsbackend.dto.customer.CustomerBulkOperationResultDTO.
 * One envelope summarizing the outcome of any per-item bulk operation
 * on customers.
 */
export interface BulkOperationResult {
  requested: number;
  successCount: number;
  failureCount: number;
  failures: { id: number; reason: string }[];
}

/**
 * One row in the unified Customer Activity timeline (C11). Mirrors
 * com.hunor.classicmodelsbackend.dto.customer.CustomerActivityItemDTO.
 *
 * <p>Discriminated union via the {@code kind} field — TypeScript can
 * narrow the type by checking {@code item.kind === 'ORDER'}, after
 * which the order-specific fields are non-null.</p>
 *
 * <p>Numeric fields arrive as strings on the wire (Jackson serialises
 * BigDecimal that way); the activity component parses to numbers
 * once when rendering.</p>
 */
export interface CustomerActivityItem {
  kind: 'ORDER' | 'PAYMENT';
  activityDate: string;          // ISO yyyy-mm-dd
  // Order-specific fields. Present only when kind === 'ORDER'.
  orderNumber: number | null;
  orderStatus: string | null;
  orderTotal: string | null;     // BigDecimal as string
  itemCount: number | null;
  // Payment-specific fields. Present only when kind === 'PAYMENT'.
  checkNumber: string | null;
  paymentAmount: string | null;  // BigDecimal as string
}

/** Header aggregations for the activity page (C11). */
export interface CustomerActivitySummary {
  lifetimeSpend: string;          // BigDecimal
  totalPaid: string;              // BigDecimal
  outstandingBalance: string;     // BigDecimal
  orderCount: number;
  paymentCount: number;
  firstActivityDate: string | null;
  lastActivityDate: string | null;
}

/** Wrapper response shape for the activity endpoint. */
export interface CustomerActivity {
  summary: CustomerActivitySummary;
  items: CustomerActivityItem[];
}

/**
 * One potential duplicate returned by C12's merge-candidates endpoint.
 * Mirrors com.hunor.classicmodelsbackend.dto.customer.CustomerMergeCandidateDTO.
 */
export interface CustomerMergeCandidate {
  customerNumber: number;
  customerName: string;
  contactFirstName: string;
  contactLastName: string;
  phone: string;
  city: string;
  country: string;
  /** Combined similarity score, 0.0–1.0. */
  score: number;
  /** Field names that individually exceeded the per-field threshold ("name", "contact", "phone"). */
  matchedFields: string[];
}

/**
 * Body for C12's merge endpoint. The winner's id is in the URL;
 * this body carries the loser's id and a per-field override map
 * ({@code 'WINNER'} or {@code 'LOSER'} per field). Any field NOT in
 * the map keeps the winner's existing value.
 */
export interface CustomerMergeRequest {
  loserId: number;
  fieldOverrides: { [field: string]: 'WINNER' | 'LOSER' };
}

/**
 * Credit utilisation snapshot for one customer (C13). Mirrors
 * com.hunor.classicmodelsbackend.dto.customer.CustomerCreditStatusDTO.
 *
 * <p>{@code creditLimit}, {@code outstandingBalance}, and
 * {@code utilization} arrive as Jackson-serialised BigDecimals
 * (string on the wire); the consumer formats them as currency or
 * percent. {@code utilization} is null when the customer has no
 * credit limit set ({@code status === 'NO_LIMIT'}).</p>
 */
export interface CustomerCreditStatus {
  customerNumber: number;
  customerName: string;
  creditLimit: string | null;
  outstandingBalance: string;
  utilization: string | null;
  status: 'OK' | 'NEAR_LIMIT' | 'OVER_LIMIT' | 'NO_LIMIT';
}

/**
 * Lightweight customer projection used by the all-customers map (C10).
 * Mirrors com.hunor.classicmodelsbackend.dto.customer.CustomerMapPointDTO.
 *
 * <p>{@code lat}/{@code lng} arrive as strings (Jackson serialises
 * BigDecimal that way); the map component parses to numbers. Only
 * active + geocoded customers appear in the response.</p>
 */
export interface CustomerMapPoint {
  customerNumber: number;
  customerName: string;
  city: string;
  country: string;
  lat: string;
  lng: string;
  hasSalesRep: boolean;
}

@Injectable({ providedIn: 'root' })
export class CustomerService {
  private http = inject(HttpClient);
  readonly baseUrl = '/api/v1/customers';

  list(params?: HttpParams) {
    return this.http.get<Customer[]>(this.baseUrl, { params });
  }

  /**
   * Server-side paged list. Used by the customer list page now that
   * pagination, sorting, and filtering all happen on the backend.
   *
   * <p>Caller passes the current state of the table (page index, page
   * size, sort field + direction, optional search term). Returns a
   * page envelope including the total count so the paginator can
   * render "showing 1–10 of 122".</p>
   */
  listPaged(opts: {
    page: number;
    size: number;
    sort?: string;
    dir?: 'asc' | 'desc';
    search?: string;
    /** C14: optional exact-match country filter (case-insensitive). */
    country?: string;
  }) {
    let params = new HttpParams()
      .set('page', String(opts.page))
      .set('size', String(opts.size));
    if (opts.sort) params = params.set('sort', opts.sort);
    if (opts.dir) params = params.set('dir', opts.dir);
    // Skip empty/whitespace-only search rather than sending ?search=
    if (opts.search && opts.search.trim()) {
      params = params.set('search', opts.search.trim());
    }
    if (opts.country && opts.country.trim()) {
      params = params.set('country', opts.country.trim());
    }
    return this.http.get<PageResponse<Customer>>(`${this.baseUrl}/find-all-paged`, { params });
  }

  get(id: number) {
    return this.http.get<Customer>(`${this.baseUrl}/${id}`);
  }
  create(payload: Customer) {
    return this.http.post<Customer>(this.baseUrl, payload);
  }
  update(id: number, payload: Customer) {
    return this.http.put<Customer>(`${this.baseUrl}/${id}`, payload);
  }
  /**
   * Delete a customer using one of the supported strategies. Defaults
   * to SOFT for backwards compatibility — the safest choice for
   * customer data because orders/payments retain valid FKs.
   */
  delete(id: number, strategy: CustomerDeleteStrategy = 'SOFT') {
    const params = new HttpParams().set('strategy', strategy);
    return this.http.delete(`${this.baseUrl}/${id}`, { params });
  }

  /**
   * Returns the strategies the backend is willing to execute. The UI
   * uses this to decide whether to expose the destructive DEEP_CASCADE
   * option. Cheap call; safe to fire on dialog open.
   */
  getAvailableStrategies() {
    return this.http.get<CustomerDeleteStrategy[]>(`${this.baseUrl}/delete-strategies`);
  }

  /**
   * Delete a batch of customers in a single request.
   *
   * <p>The backend processes each id independently and returns a
   * {@link BulkOperationResult} envelope with success / failure
   * counts plus per-id failure reasons. A partially-successful batch
   * is a normal HTTP 200 response — the frontend reads the envelope
   * to decide what to show the user.</p>
   */
  bulkDelete(ids: number[], strategy: CustomerDeleteStrategy = 'SOFT') {
    return this.http.post<BulkOperationResult>(
      `${this.baseUrl}/bulk-delete`,
      { ids, strategy },
    );
  }

  /**
   * Geocode the customer's address via Nominatim and persist the
   * resulting lat/lng. Returns the freshly-read customer with the
   * new coordinates so the caller can update its local state without
   * an extra GET.
   */
  geocode(id: number) {
    return this.http.post<Customer>(`${this.baseUrl}/${id}/geocode`, null);
  }

  /**
   * Lightweight projection for the all-customers map page.
   * Returns only active + geocoded customers — un-geocoded rows aren't
   * plottable and are excluded server-side.
   */
  mapPoints() {
    return this.http.get<CustomerMapPoint[]>(`${this.baseUrl}/map-points`);
  }

  /**
   * Total active customers (geocoded or not). Used to render the
   * "X of Y geocoded" indicator on the map page.
   */
  activeCount() {
    return this.http.get<number>(`${this.baseUrl}/active-count`);
  }

  /**
   * Customer lifetime value snapshot. Returns 404 if the customer has
   * never placed an order — frontend handles that case as "no data."
   */
  getLifetimeValue(id: number) {
    return this.http.get<CustomerLifetimeValue>(`${this.baseUrl}/${id}/lifetime-value`);
  }

  /**
   * Unified Customer Activity timeline (C11) — orders + payments
   * interleaved by date, plus header aggregations. One request,
   * one response; the page filters the items list client-side.
   */
  getActivity(id: number) {
    return this.http.get<CustomerActivity>(`${this.baseUrl}/${id}/activity`);
  }

  /**
   * Find potential duplicates of one customer (C12). Server-side
   * fuzzy match using Levenshtein on normalised name + contact name +
   * phone, weighted and thresholded.
   */
  getMergeCandidates(id: number, threshold = 0.7, limit = 10) {
    let params = new HttpParams()
      .set('threshold', String(threshold))
      .set('limit', String(limit));
    return this.http.get<CustomerMergeCandidate[]>(
      `${this.baseUrl}/${id}/merge-candidates`,
      { params },
    );
  }

  /**
   * Merge {@code loserId} into the winner (the URL's id) — reassigns
   * orders and payments, applies field overrides, deletes the loser,
   * all in one transaction. Returns the surviving customer.
   */
  merge(winnerId: number, request: CustomerMergeRequest) {
    return this.http.post<Customer>(`${this.baseUrl}/${winnerId}/merge`, request);
  }

  /**
   * Credit utilisation snapshot for one customer (C13). Used by the
   * detail page to render a status chip on the credit-limit row.
   */
  getCreditStatus(id: number) {
    return this.http.get<CustomerCreditStatus>(`${this.baseUrl}/${id}/credit-status`);
  }

  /**
   * Active customers near or over their credit limit (C13). Drives
   * the credit-alerts list page and the dashboard count.
   */
  getCreditAlerts() {
    return this.http.get<CustomerCreditStatus[]>(`${this.baseUrl}/credit-alerts`);
  }
}
