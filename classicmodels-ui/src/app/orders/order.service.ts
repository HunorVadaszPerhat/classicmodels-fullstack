import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';

export interface Order {
  orderNumber?: number;
  orderDate: string;
  requiredDate: string;
  shippedDate?: string;
  status: string;
  comments?: string;
  customerNumber: number;
}

@Injectable({ providedIn: 'root' })
export class OrderService {
  private http = inject(HttpClient);
  readonly baseUrl = '/api/orders';

  list(params?: HttpParams) {
    return this.http.get<Order[]>(this.baseUrl, { params });
  }
  get(id: number) {
    return this.http.get<Order>(`${this.baseUrl}/${id}`);
  }
  create(payload: Order) {
    return this.http.post<Order>(this.baseUrl, payload);
  }
  update(id: number, payload: Order) {
    return this.http.put<Order>(`${this.baseUrl}/${id}`, payload);
  }
  delete(id: number) {
    return this.http.delete(`${this.baseUrl}/${id}`);
  }
}