import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';

export interface Product {
  productCode: string;
  productName: string;
  productLine: string;
  productScale: string;
  productVendor: string;
  productDescription: string;
  quantityInStock: number;
  buyPrice: number;
  MSRP: number;
}

@Injectable({ providedIn: 'root' })
export class ProductService {
  private http = inject(HttpClient);
  readonly baseUrl = '/api/products';

  list(params?: HttpParams) {
    return this.http.get<Product[]>(this.baseUrl, { params });
  }
  get(id: string) {
    return this.http.get<Product>(`${this.baseUrl}/${id}`);
  }
  create(payload: Product) {
    return this.http.post<Product>(this.baseUrl, payload);
  }
  update(id: string, payload: Product) {
    return this.http.put<Product>(`${this.baseUrl}/${id}`, payload);
  }
  delete(id: string) {
    return this.http.delete(`${this.baseUrl}/${id}`);
  }
}