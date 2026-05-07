import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';

export interface Payment {
  customerNumber: number;
  checkNumber: string;
  paymentDate: string;
  amount: number;
}

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private http = inject(HttpClient);
  readonly baseUrl = '/api/payments';

  list(params?: HttpParams) {
    return this.http.get<Payment[]>(this.baseUrl, { params });
  }
  get(customerNumber: number, checkNumber: string) {
    return this.http.get<Payment>(`${this.baseUrl}/${customerNumber}/${checkNumber}`);
  }
  create(payload: Payment) {
    return this.http.post<Payment>(this.baseUrl, payload);
  }
  update(customerNumber: number, checkNumber: string, payload: Payment) {
    return this.http.put<Payment>(`${this.baseUrl}/${customerNumber}/${checkNumber}`, payload);
  }
  delete(customerNumber: number, checkNumber: string) {
    return this.http.delete(`${this.baseUrl}/${customerNumber}/${checkNumber}`);
  }
}