import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import * as L from 'leaflet';

/**
 * A small, reusable Leaflet map.
 *
 * <p>Drop it anywhere with {@code <app-mini-map [lat]="..." [lng]="...">}
 * to show a single marker at the given coordinates. Tiles come from
 * OpenStreetMap (free, no API key needed); attribution text is shown in
 * the corner as required by their licence.</p>
 *
 * <h3>Why CDN icons?</h3>
 *
 * <p>Leaflet's default marker images live inside the npm package at
 * paths Leaflet generates at runtime. Bundlers don't preserve those
 * relative paths automatically, so the markers appear as broken
 * images. Loading them from a public CDN ({@code unpkg.com}) sidesteps
 * the bundling problem with one line of config — a recognised
 * workaround that's been the canonical fix for years.</p>
 *
 * <h3>Lifecycle</h3>
 *
 * <p>Initialised in {@code ngAfterViewInit} so the {@code <div>} the
 * map attaches to actually exists in the DOM. Re-pans on input changes
 * via {@code ngOnChanges}. Destroyed cleanly in {@code ngOnDestroy} —
 * Leaflet leaks event listeners if you forget this and the user navigates
 * away.</p>
 */
@Component({
  standalone: true,
  selector: 'app-mini-map',
  imports: [CommonModule],
  template: `<div class="map-host" #host></div>`,
  styles: [`
    :host { display: block; }
    .map-host {
      width: 100%;
      height: 240px;
      border-radius: 4px;
      overflow: hidden;
      border: 1px solid rgba(0, 0, 0, 0.12);
    }
  `],
})
export class MiniMapComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input({ required: true }) lat!: number;
  @Input({ required: true }) lng!: number;

  /**
   * HTML string shown inside the marker's popup when clicked.
   *
   * <p><b>SECURITY NOTE:</b> this string is rendered as raw HTML by
   * Leaflet, so any unescaped {@code < > & " '} characters would be
   * interpreted as markup. Callers are responsible for escaping any
   * user-provided data before passing it in. The convention in this
   * codebase is: build the popup string in the parent component using
   * a small {@code escapeHtml} helper, never concatenate raw data.</p>
   *
   * <p>If null or undefined, no popup is bound.</p>
   */
  @Input() popupHtml?: string;

  @Input() zoom = 12;

  @ViewChild('host', { static: true }) hostEl!: ElementRef<HTMLDivElement>;

  private map?: L.Map;
  private marker?: L.Marker;

  ngAfterViewInit(): void {
    this.initMap();
  }

  ngOnChanges(changes: SimpleChanges): void {
    // If the map is already up and the coordinates change (e.g. user
    // navigated to a different employee detail), re-pan rather than
    // tear down and rebuild.
    if (this.map && (changes['lat'] || changes['lng'])) {
      const latLng = L.latLng(this.lat, this.lng);
      this.map.setView(latLng, this.zoom);
      this.marker?.setLatLng(latLng);
    }
    // Re-bind popup any time its content changes — even if the marker
    // stayed put, the inner text might be different.
    if (this.map && this.marker && changes['popupHtml']) {
      if (this.popupHtml) {
        this.marker.bindPopup(this.popupHtml);
      } else {
        this.marker.unbindPopup();
      }
    }
  }

  ngOnDestroy(): void {
    this.map?.remove();
    this.map = undefined;
    this.marker = undefined;
  }

  private initMap(): void {
    this.map = L.map(this.hostEl.nativeElement, {
      // Hide the +/- zoom buttons for a tighter mini-map look. Users
      // can still pinch/scroll to zoom. Re-enable if you ever drop
      // this into a larger surface.
      zoomControl: false,
      // Keep the default scroll-wheel behaviour disabled too — surprises
      // users who scroll past the map and find the page stops scrolling.
      scrollWheelZoom: false,
    }).setView([this.lat, this.lng], this.zoom);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    }).addTo(this.map);

    const icon = L.icon({
      iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
      iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
      shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
      iconSize: [25, 41],
      iconAnchor: [12, 41],
      popupAnchor: [1, -34],
      shadowSize: [41, 41],
    });

    this.marker = L.marker([this.lat, this.lng], { icon }).addTo(this.map);
    if (this.popupHtml) {
      // bindPopup accepts either a string (rendered as HTML), an
      // HTMLElement, or a function returning either. We pass the HTML
      // string the parent built for us.
      this.marker.bindPopup(this.popupHtml);
    }
  }
}
