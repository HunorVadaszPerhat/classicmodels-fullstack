import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';

export interface OrderDetail {
  orderNumber: number;
  productCode: string;
  quantityOrdered: number;
  priceEach: number;
  orderLineNumber: number;
}

@Injectable({ providedIn: 'root' })
export class OrderDetailService {
  private http = inject(HttpClient);
  readonly baseUrl = '/api/order-details';

  list(params?: HttpParams) {
    return this.http.get<OrderDetail[]>(this.baseUrl, { params });
  }
  get(orderNumber: number, productCode: string) {
    return this.http.get<OrderDetail>(`${this.baseUrl}/${orderNumber}/${productCode}`);
  }
  create(payload: OrderDetail) {
    return this.http.post<OrderDetail>(this.baseUrl, payload);
  }
  update(orderNumber: number, productCode: string, payload: OrderDetail) {
    return this.http.put<OrderDetail>(`${this.baseUrl}/${orderNumber}/${productCode}`, payload);
  }
  delete(orderNumber: number, productCode: string) {
    return this.http.delete(`${this.baseUrl}/${orderNumber}/${productCode}`);
  }
}