import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';

export interface Employee {
  employeeNumber?: number;
  lastName: string;
  firstName: string;
  extension: string;
  email: string;
  officeCode: string;
  reportsTo?: number | null;
  jobTitle: string;
  // New soft-delete fields (added in Flyway V2). Optional here so that
  // the create form (which doesn't send them) still type-checks.
  active?: boolean;
  terminatedDate?: string | null;
  // Audit fields (added in Flyway V4). Always populated for rows
  // returned by the API; optional here because the create-form payload
  // doesn't include them.
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  updatedBy?: string;
  /**
   * Optimistic-lock version (added in Flyway V5). The form preserves
   * this when it loads an employee and sends it back on save; if the
   * DB's version has moved on, the backend returns 409 and the UI
   * shows "stale data" dialog.
   */
  version?: number;
}

/**
 * One row from a child table that references the employee, ready for
 * display. `id` is the child row's primary key (handy if we later add
 * "click through to view"), `label` is a human-readable summary
 * (customer name, employee full name, etc.).
 */
export interface DependentRecord {
  id: number;
  label: string;
}

/**
 * One foreign-key reference: which child table holds it, what column,
 * how many rows in total, and a small preview slice for the UI.
 *
 * `count` may exceed `records.length` when the employee is referenced
 * by more rows than the backend's preview limit (currently 25). The
 * dialog says "showing X of Y" in that case.
 */
export interface EmployeeDependent {
  tableName: string;
  columnName: string;
  count: number;
  records: DependentRecord[];
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
 * Mirror of the backend `DeleteStrategy` enum. Using a string-literal
 * union keeps the wire format human-readable and self-documenting.
 *
 * `DEEP_CASCADE` is the destructive option that walks the entire FK
 * tree (orders, order details, payments). The backend will only accept
 * it when the `app.delete.allow-deep-cascade` feature flag is on, and
 * the UI hides it unless the strategies endpoint reports it as allowed.
 */
export type DeleteStrategy = 'SOFT' | 'NULLIFY' | 'CASCADE' | 'DEEP_CASCADE' | 'REASSIGN_DELETE';

/**
 * One step in a deletion plan returned by the backend planner.
 * Mirrors com.hunor.classicmodelsbackend.dto.deletion.DeletionStep.
 */
export interface DeletionStep {
  order: number;
  kind: 'DELETE' | 'NULLIFY' | 'WARNING';
  tableName: string;
  depth: number;
  sql: string;
  expectedRows: number;
  summary: string;
}

/**
 * The shape returned by GET /admin/deletion-plan. Read-only —
 * generating a plan never modifies data.
 */
export interface DeletionPlan {
  rootTable: string;
  rootIdColumn: string;
  rootId: number | string;
  totalSteps: number;
  totalAffectedRows: number;
  steps: DeletionStep[];
}

@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private http = inject(HttpClient);
  readonly baseUrl = '/api/v1/employees';

  list(params?: HttpParams) {
    return this.http.get<Employee[]>(this.baseUrl, { params });
  }

  /**
   * Server-side paged list. Used by the employee list page now that
   * pagination, sorting, and filtering all happen on the backend.
   *
   * <p>Caller passes the current state of the table (page index, page
   * size, sort field + direction, optional search term). Returns a
   * page envelope including the total count so the paginator can
   * render "showing 1–10 of 87".</p>
   */
  listPaged(opts: {
    page: number;
    size: number;
    sort?: string;
    dir?: 'asc' | 'desc';
    search?: string;
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
    return this.http.get<PageResponse<Employee>>(`${this.baseUrl}/find-all-paged`, { params });
  }
  get(id: number) {
    return this.http.get<Employee>(`${this.baseUrl}/${id}`);
  }
  create(payload: Employee) {
    return this.http.post<Employee>(this.baseUrl, payload);
  }
  update(id: number, payload: Employee) {
    return this.http.put<Employee>(`${this.baseUrl}/${id}`, payload);
  }
  /**
   * Delete an employee using one of the three supported strategies.
   * Defaults to NULLIFY for backwards compatibility.
   */
  delete(id: number, strategy: DeleteStrategy = 'NULLIFY') {
    const params = new HttpParams().set('strategy', strategy);
    return this.http.delete(`${this.baseUrl}/${id}`, { params });
  }
  getDependents(id: number) {
    return this.http.get<EmployeeDependent[]>(`${this.baseUrl}/${id}/dependents`);
  }
  /**
   * Returns the strategies the backend is willing to execute. The UI
   * uses this to decide whether to expose the destructive DEEP_CASCADE
   * option. Calling it once per dialog open is fine — the response is
   * tiny and the data could change between requests if config is reloaded.
   */
  getAvailableStrategies() {
    return this.http.get<DeleteStrategy[]>(`${this.baseUrl}/delete-strategies`);
  }

  /**
   * Reassign source employee's customers + direct reports to {@code targetId},
   * then delete the source. Atomic on the backend.
   */
  reassignAndDelete(sourceId: number, targetEmployeeId: number) {
    return this.http.post(
      `${this.baseUrl}/${sourceId}/reassign-and-delete`,
      { targetEmployeeId },
    );
  }

  /**
   * Generate a read-only deletion plan from the admin planner. Tells the
   * UI exactly which SQL statements would run, in what order, and how
   * many rows each one would touch — without executing anything.
   *
   * <p>Used by the delete dialog when CASCADE or DEEP_CASCADE is chosen,
   * so the user can see the actual blast radius before confirming.</p>
   */
  getDeletionPlan(table: string, idColumn: string, id: number | string) {
    const params = new HttpParams()
      .set('table', table)
      .set('id-column', idColumn)
      .set('id', String(id));
    // Note: the planner endpoint lives under /admin, not under /employees.
    return this.http.get<DeletionPlan>('/api/v1/admin/deletion-plan', { params });
  }

  /**
   * Upload (or replace) the profile photo for an employee.
   * The browser builds the multipart body from the FormData object;
   * Angular's HttpClient passes it through unchanged.
   */
  uploadPhoto(id: number, file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.http.post(`${this.baseUrl}/${id}/photo`, body);
  }

  /**
   * Fetch the photo as a Blob. We can't just point an {@code <img>}
   * element at the URL because the browser doesn't include
   * Authorization headers on image requests, and our endpoint requires
   * the JWT bearer token. Fetching as a Blob and creating an object
   * URL is the standard workaround.
   */
  fetchPhoto(id: number) {
    return this.http.get(`${this.baseUrl}/${id}/photo`, { responseType: 'blob' });
  }

  deletePhoto(id: number) {
    return this.http.delete(`${this.baseUrl}/${id}/photo`);
  }

  /**
   * Delete a batch of employees in a single request.
   *
   * <p>The backend processes each id independently and returns a
   * {@link BulkOperationResult} envelope with success / failure
   * counts plus per-id failure reasons. A partially-successful batch
   * is a normal HTTP 200 response — the frontend reads the envelope
   * to decide what to show the user.</p>
   */
  bulkDelete(ids: number[], strategy: DeleteStrategy = 'NULLIFY') {
    return this.http.post<BulkOperationResult>(
      `${this.baseUrl}/bulk-delete`,
      { ids, strategy },
    );
  }
}

/**
 * Mirrors com.hunor.classicmodelsbackend.dto.employee.BulkOperationResultDTO.
 * One envelope summarizing the outcome of any per-item bulk operation.
 */
export interface BulkOperationResult {
  requested: number;
  successCount: number;
  failureCount: number;
  failures: { id: number; reason: string }[];
}
