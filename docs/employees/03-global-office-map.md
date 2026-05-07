# Feature 03 — Global office overview map

## What we built

A single map at the top of the offices list page showing all seven
offices as markers, with the map auto-zooming to fit them all in view.
Each marker pops up the city, country, address line, and a link
through to the office detail page from Feature 2.

To do this cleanly we built a new reusable component —
`MarkersMapComponent` — that's the multi-marker sibling of the
single-marker `MiniMapComponent` from earlier features. Same Leaflet
+ OSM stack, just plotting an array of points and auto-fitting the
viewport around them.

Files touched:

- `classicmodels-ui/src/app/shared/markers-map.component.ts` (new)
- `classicmodels-ui/src/app/offices/office-list.component.ts`
- `classicmodels-ui/src/app/offices/office-list.component.html`

## Why this is worth learning

Three concepts stack here. **Component composition** — taking the
ideas from one component and writing a sibling for a related but
distinct use case, rather than overloading one component with two
modes. **`computed()` signals** — Angular's "derive a value from
other signals" primitive, which makes the marker list automatically
update when the offices list arrives. **Leaflet's `FeatureGroup` and
`fitBounds()`** — the canonical pattern for "show me everything,
zoom appropriately."

## Background

### Angular's `computed()`

A signal whose value is derived from one or more other signals. The
function you give it re-runs (and the result is cached) whenever any
signal it reads changes.

```ts
const count = signal(0);
const doubled = computed(() => count() * 2);
console.log(doubled());   // 0
count.set(5);
console.log(doubled());   // 10 — recomputed automatically
```

In this feature we use it to turn the `offices` signal into a
`markers` signal:

```ts
readonly markers = computed<MapPoint[]>(() =>
  this.offices()
    .filter(o => o.lat != null && o.lng != null)
    .map(o => ({ id: o.officeCode, lat: o.lat!, lng: o.lng!, ... })),
);
```

When the API call finishes and the offices signal is set, the
markers signal automatically updates, the template's `[points]`
binding sees the new value, and Leaflet re-renders. No `subscribe`,
no manual `markers = ...` assignment in the success callback.

References:

- [Angular — Signals overview](https://angular.dev/guide/signals)
- [Angular — `computed`](https://angular.dev/api/core/computed)
- [Angular signals deep dive (blog)](https://angular.dev/guide/signals#computed-signals)

### Leaflet `L.FeatureGroup`

A grouping of map layers (markers, polylines, etc.) you can manipulate
collectively. The two methods we use:

- `featureGroup.clearLayers()` — remove every member layer in one call.
- `featureGroup.getBounds()` — return the smallest `LatLngBounds`
  that contains every member. Pair with `map.fitBounds()` to auto-zoom.

```ts
const group = L.featureGroup().addTo(map);
points.forEach(p => L.marker([p.lat, p.lng]).addTo(group));
map.fitBounds(group.getBounds(), { padding: [40, 40], maxZoom: 12 });
```

`maxZoom` matters when all points are close together — without it,
fitting bounds around two points 1km apart would zoom to street level,
which usually isn't what you want.

References:

- [Leaflet — FeatureGroup](https://leafletjs.com/reference.html#featuregroup)
- [Leaflet — Map fitBounds](https://leafletjs.com/reference.html#map-fitbounds)
- [Leaflet tutorial — Layer Groups and Layers Control](https://leafletjs.com/examples/layers-control/)

### Component composition over component overload

When Feature 2 needed a single marker we used `MiniMapComponent`.
When Feature 3 needs many markers, we wrote a separate
`MarkersMapComponent` instead of bolting an "array of points" mode
onto the existing one. The result is two crisp, single-purpose
components rather than one fuzzy multi-mode one.

The trade-off: more files, slight code duplication (both initialise
a Leaflet map with OSM tiles). The payoff: each component is trivial
to reason about and trivial to use. If you find yourself toggling
behaviour with a flag like `mode: 'single' | 'multi'`, that's usually
the signal it's time to split.

This is just a special case of the [single-responsibility principle](https://en.wikipedia.org/wiki/Single-responsibility_principle)
applied to UI components. When it makes sense to share, extract a
common base — but two siblings with focused contracts often beats
one parent with options.

## The code, walked through

### MarkersMapComponent — the meat is in `renderMarkers()`

```ts
private renderMarkers(): void {
  if (!this.map) return;

  // Wipe and rebuild — simpler than diffing markers.
  this.markersGroup?.clearLayers();
  this.markersGroup = L.featureGroup().addTo(this.map);

  if (this.points.length === 0) return;

  for (const p of this.points) {
    const marker = L.marker([p.lat, p.lng], { icon });
    if (p.popupHtml) marker.bindPopup(p.popupHtml);
    marker.addTo(this.markersGroup);
  }

  // Pan + zoom so the entire group fits in view.
  this.map.fitBounds(this.markersGroup.getBounds(), {
    padding: this.boundsPadding,
    maxZoom: this.maxFitZoom,
  });
}
```

Three patterns to take away:

1. **Clear-and-rebuild on every change.** For small data sets, recreating
   markers is faster to write and easier to reason about than diffing.
   For 1000+ markers you'd diff, but for "all offices" it's overkill.
2. **`bindPopup` is conditional** so points without popup HTML get plain
   markers. The contract on `MapPoint.popupHtml` is `optional`.
3. **`fitBounds` is the last call.** Pin every marker first, then ask
   the map to fit them all. Doing it the other way leaves the viewport
   in whatever state Leaflet defaulted to.

### OfficeListComponent — derived markers via `computed()`

```ts
readonly markers = computed<MapPoint[]>(() =>
  this.offices()
    .filter(o => o.lat != null && o.lng != null)
    .map(o => ({
      id: o.officeCode,
      lat: o.lat!,
      lng: o.lng!,
      popupHtml: this.buildPopup(o),
    })),
);
```

The exclamation marks (`!`) are TypeScript's "non-null assertion"
operator — telling the compiler "I've already checked this isn't null
in the filter above, trust me." Without them, `lat: o.lat!` would
fail to type-check because `o.lat` is `number | null | undefined`.

The map and table both bind to signals (`markers()` and `offices()`
respectively). Once `load()` completes and sets `offices`, both
update reactively in the same change-detection pass.

### Template — map first, table second

```html
@if (markers().length > 0) {
  <section class="map-section">
    <h3>Worldwide ({{ markers().length }} offices)</h3>
    <app-markers-map [points]="markers()"></app-markers-map>
  </section>
}

<table mat-table [dataSource]="offices()" matSort>
  ...
</table>
```

The `@if` guard suppresses the section while loading (markers is
empty until the API responds). Once data arrives both views render
together.

## How to test

1. Save; the dev server hot-reloads.
2. Click **Offices** in the toolbar.
3. Above the offices table you should see a map showing all 7 cities
   (San Francisco, Boston, NYC, Paris, Tokyo, Sydney, London) with
   pins, auto-zoomed to encompass all of them.
4. Click any marker → popup with city, country, address line, and a
   "View office →" link.
5. Click the link → lands on that office's detail page (the rich one
   from Feature 2).

You can also verify the auto-fit by zooming/panning manually and then
refreshing — the map snaps back to fit all markers.

## What you just learned

- **`computed()` signals** as the reactive way to derive view-model
  data from source data, no manual subscriptions required.
- **`L.FeatureGroup` and `fitBounds()`** as the standard Leaflet idiom
  for "show all my points and zoom to fit."
- **Component composition** — splitting one fuzzy multi-mode component
  into two crisp single-mode siblings.
- **Non-null assertion (`!`)** as the TypeScript escape hatch for
  cases where you've already null-checked the value but the compiler
  can't follow the chain.

## Study materials

### Angular signals

- [Angular — Signals overview](https://angular.dev/guide/signals)
- [Angular — Computed signals](https://angular.dev/guide/signals#computed-signals)
- [Angular — Signal-based reactivity](https://angular.dev/guide/signals/inputs) — shows the new pattern of input signals

### Leaflet (multi-marker)

- [Leaflet — FeatureGroup](https://leafletjs.com/reference.html#featuregroup)
- [Leaflet — Map fitBounds](https://leafletjs.com/reference.html#map-fitbounds)
- [Leaflet — LatLngBounds](https://leafletjs.com/reference.html#latlngbounds)
- [Leaflet quick start tutorial (revisited)](https://leafletjs.com/examples/quick-start/)

### Reusable components

- [Wikipedia — Single Responsibility Principle](https://en.wikipedia.org/wiki/Single-responsibility_principle)
- [Refactoring Guru — Composition over inheritance](https://refactoring.guru/design-patterns/composition-over-inheritance) — narrative explanation of when to split vs share

### TypeScript

- [TypeScript — Non-null assertion operator (`!`)](https://www.typescriptlang.org/docs/handbook/2/everyday-types.html#non-null-assertion-operator-postfix-)
- [TypeScript — Narrowing](https://www.typescriptlang.org/docs/handbook/2/narrowing.html) — for when the compiler *can* follow the null-check, no `!` needed
