export interface Customer {
  customerNumber: number;
  customerName: string;
  contactLastName: string;
  contactFirstName: string;
  phone: string;
  addressLine1: string;
  addressLine2?: string;
  city: string;
  state?: string;
  postalCode?: string;
  country: string;
  salesRepEmployeeNumber?: number;
  creditLimit?: number;

  // Geographic coordinates (added in Flyway V8). NULL until the
  // customer has been geocoded via the C9 button. The wire format is
  // string (Jackson serialises BigDecimal that way); the detail page
  // parses to number for Leaflet.
  lat?: string | null;
  lng?: string | null;

  // Soft-delete fields (added in Flyway V7). Optional here so the
  // create form (which doesn't send them) still type-checks.
  active?: boolean;
  terminatedDate?: string | null;

  // Audit fields (added in Flyway V6). Always populated for rows
  // returned by the API; optional here because the create-form payload
  // doesn't include them.
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  updatedBy?: string;

  /**
   * Optimistic-lock version (added in Flyway V6). The form preserves
   * this when it loads a customer and sends it back on save; if the
   * DB's version has moved on, the backend returns 409 (wired in C5).
   */
  version?: number;
}
