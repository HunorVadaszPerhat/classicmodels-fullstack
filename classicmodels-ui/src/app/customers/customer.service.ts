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

@Injectable({ providedIn: 'root' })
export class CustomerService {
  private http = inject(HttpClient);
  readonly baseUrl = '/api/v1/customers';

  list(params?: HttpParams) {
    return this.http.get<Customer[]>(this.baseUrl, { params });
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
  delete(id: number) {
    return this.http.delete(`${this.baseUrl}/${id}`);
  }

  /**
   * Customer lifetime value snapshot. Returns 404 if the customer has
   * never placed an order — frontend handles that case as "no data."
   */
  getLifetimeValue(id: number) {
    return this.http.get<CustomerLifetimeValue>(`${this.baseUrl}/${id}/lifetime-value`);
  }
}
