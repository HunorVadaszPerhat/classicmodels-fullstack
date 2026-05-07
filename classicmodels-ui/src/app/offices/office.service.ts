import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';

export interface Office {
  officeCode: string;
  city: string;
  phone: string;
  addressLine1: string;
  addressLine2?: string;
  state?: string;
  country: string;
  postalCode: string;
  territory: string;
  // Geographic coordinates added by Flyway V3. Both nullable: an
  // un-geocoded office leaves the map hidden in the UI.
  lat?: number | null;
  lng?: number | null;
}

@Injectable({ providedIn: 'root' })
export class OfficeService {
  private http = inject(HttpClient);
  // The backend's context-path is /api/v1; every other entity service
  // (employees, customers, …) targets /api/v1/<entity>. This was missing
  // the /v1 segment which made every call 404 silently.
  readonly baseUrl = '/api/v1/offices';

  list(params?: HttpParams) {
    return this.http.get<Office[]>(this.baseUrl, { params });
  }
  get(id: string) {
    return this.http.get<Office>(`${this.baseUrl}/${id}`);
  }
  create(payload: Office) {
    return this.http.post<Office>(this.baseUrl, payload);
  }
  update(id: string, payload: Office) {
    return this.http.put<Office>(`${this.baseUrl}/${id}`, payload);
  }
  delete(id: string) {
    return this.http.delete(`${this.baseUrl}/${id}`);
  }
  /**
   * Trigger backend geocoding for the given office. The backend calls
   * Nominatim, parses the result, saves lat/lng to the database, and
   * returns the updated office.
   */
  geocode(id: string) {
    return this.http.post<Office>(`${this.baseUrl}/${id}/geocode`, {});
  }
}