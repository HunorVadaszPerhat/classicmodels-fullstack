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
 * One pin's worth of data. Caller-friendly shape that's deliberately
 * decoupled from Leaflet's API so the rest of the app doesn't have to
 * know about Leaflet types.
 */
export interface MapPoint {
  /** Stable identifier — used as the {@code track-by} key for re-renders. */
  id: string | number;
  lat: number;
  lng: number;
  /**
   * HTML rendered inside the marker's popup when clicked. Same SECURITY
   * NOTE as MiniMapComponent: caller is responsible for escaping any
   * data from outside their own code.
   */
  popupHtml?: string;
}

/**
 * Reusable map that renders many markers and auto-zooms to fit them all.
 *
 * <p>The single-marker MiniMapComponent does its job nicely; this is
 * its sibling for "show me everything." Used on the office list page to
 * paint all seven offices on one map; could equally be used to plot
 * customers, sales-rep territories, anything geographic.</p>
 *
 * <h3>Why a separate component?</h3>
 *
 * <p>Could MiniMapComponent have been extended to accept either a
 * single point or an array? Yes. The split is a deliberate trade-off:
 * each component has one clear contract and a tiny API surface, which
 * makes them simpler to reason about than one component with two
 * modes. If you ever needed both single AND multi semantics in the
 * same place you could merge them; until then, two crisp components
 * beat one fuzzy one.</p>
 */
@Component({
  standalone: true,
  selector: 'app-markers-map',
  imports: [CommonModule],
  template: `<div class="map-host" #host></div>`,
  styles: [`
    :host { display: block; }
    .map-host {
      width: 100%;
      height: 360px;
      border-radius: 4px;
      overflow: hidden;
      border: 1px solid rgba(0, 0, 0, 0.12);
    }
  `],
})
export class MarkersMapComponent implements AfterViewInit, OnChanges, OnDestroy {

  @Input({ required: true }) points: MapPoint[] = [];

  /** Padding (as a Leaflet `[x, y]` pixel offset) around the fitted bounds. */
  @Input() boundsPadding: [number, number] = [40, 40];

  /** Maximum zoom when fitting bounds (prevents over-zooming a single point). */
  @Input() maxFitZoom = 12;

  @ViewChild('host', { static: true }) hostEl!: ElementRef<HTMLDivElement>;

  private map?: L.Map;
  /**
   * Holds every marker added to the map. {@link L.FeatureGroup} is
   * Leaflet's idiom for "a collection of layers I want to manipulate
   * as one" — handy for clearing all markers at once and for getting
   * a combined bounding box.
   */
  private markersGroup?: L.FeatureGroup;

  ngAfterViewInit(): void {
    this.initMap();
    this.renderMarkers();
  }

  ngOnChanges(changes: SimpleChanges): void {
    // The component might re-render when the parent's data arrives a
    // tick after init. Re-paint markers on every input change.
    if (this.map && changes['points']) {
      this.renderMarkers();
    }
  }

  ngOnDestroy(): void {
    this.map?.remove();
    this.map = undefined;
    this.markersGroup = undefined;
  }

  private initMap(): void {
    // Centre on (0, 0) at zoom 2 as a fallback. fitBounds() below will
    // override this immediately when there's at least one point.
    this.map = L.map(this.hostEl.nativeElement, {
      zoomControl: true,
      scrollWheelZoom: false,
    }).setView([0, 0], 2);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    }).addTo(this.map);
  }

  private renderMarkers(): void {
    if (!this.map) return;

    // Clear previous markers (if any) so the map reflects the latest
    // input rather than accumulating stale pins.
    this.markersGroup?.clearLayers();
    this.markersGroup = L.featureGroup().addTo(this.map);

    if (this.points.length === 0) return;

    const icon = L.icon({
      iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
      iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
      shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
      iconSize: [25, 41],
      iconAnchor: [12, 41],
      popupAnchor: [1, -34],
      shadowSize: [41, 41],
    });

    for (const p of this.points) {
      const marker = L.marker([p.lat, p.lng], { icon });
      if (p.popupHtml) marker.bindPopup(p.popupHtml);
      marker.addTo(this.markersGroup);
    }

    // fitBounds receives a LatLngBounds and pans+zooms the map so the
    // entire group is visible. The bounds are computed automatically by
    // FeatureGroup. maxZoom prevents over-zooming when all points
    // happen to be very close together (e.g., a single point would
    // otherwise zoom to street level).
    const bounds = this.markersGroup.getBounds();
    this.map.fitBounds(bounds, {
      padding: this.boundsPadding,
      maxZoom: this.maxFitZoom,
    });
  }
}
