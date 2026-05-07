# Feature 11 — Org chart with D3

## What we built

A new page at `/employees/org-chart` that renders the entire reporting
hierarchy as a top-down tree. Every employee is a small card showing
their name + title; cards are connected by smooth curves to their
manager. You can pan the chart by dragging, zoom with the scroll
wheel, and click any card to jump to that employee's detail page.

The whole thing is computed and drawn with **D3** —
[d3.stratify](https://d3js.org/d3-hierarchy/stratify) builds the
hierarchy from a flat array using each employee's `reportsTo`,
[d3.tree](https://d3js.org/d3-hierarchy/tree) lays nodes out in a
tidy parent-above-children pattern, and we render the result as an
SVG by hand.

Files touched:

- `classicmodels-ui/src/app/employees/org-chart.component.ts` (new)
- `classicmodels-ui/src/app/employees/employee.routes.ts` — new
  `/employees/org-chart` route registered **before** `:id`
- `classicmodels-ui/src/app/employees/employee-list.component.html` —
  adds the "Org chart" button to the toolbar

No backend changes. The existing `GET /api/v1/employees` already returns
every employee plus their `reportsTo`, which is all the chart needs.

## Why this is worth learning

D3 is the lingua franca of data visualization on the web. Every chart
library you've heard of — Chart.js, Plotly, Vega, Observable Plot,
even Tableau's web embed — either uses D3 or solves the same problems
D3 solves. Learning D3's primitives (selections, joins, scales,
hierarchies, layouts) makes every other charting tool make sense.

This feature touches the most useful subset of D3:

- **Hierarchies** — the algorithms behind every tree, treemap,
  partition, sunburst, dendrogram.
- **Layouts** — the idea that "where things go" is decoupled from
  "what they look like." `d3.tree` returns numbers; you decide whether
  those numbers become rectangles, circles, photos, or anything else.
- **Selections + data joins** — D3's signature pattern. We use a
  light version (just `.join('path')`) but the same pattern scales to
  thousands of nodes with enter/update/exit transitions.
- **Behaviors** — `d3.zoom` is a *behavior*: a reusable bundle of
  event handlers you attach to a selection. `d3.drag`, `d3.brush`
  follow the same pattern.

## Background

### What D3 actually is

D3 stands for "Data-Driven Documents." It is **not** a chart library.
It's a collection of utilities that, taken together, let you map data
to DOM. The library has no "chart" type at all — there's no
`d3.barChart()` constructor. Instead it gives you:

- Helpers to **load** data (CSV, JSON, TSV).
- **Scales** that map data ranges to pixel ranges
  (`d3.scaleLinear`, `d3.scaleTime`).
- **Layout algorithms** that turn data into geometric coordinates
  (`d3.tree`, `d3.pack`, `d3.forceSimulation`).
- **Shape generators** that turn coordinates into SVG path strings
  (`d3.line`, `d3.arc`, `d3.linkVertical`).
- **Selections** for binding data to DOM elements.
- **Behaviors** like zoom, drag, brush.

When you understand the pieces you can build literally any chart.
The cost: more code than a library that hands you `<BarChart data={…}/>`.
The benefit: total control, and a transferable mental model.

References:

- [D3 — Getting started](https://d3js.org/getting-started)
- [Observable's D3 collection](https://observablehq.com/@d3/learn-d3) — runnable examples
- [Mike Bostock — Three Little Circles](https://bost.ocks.org/mike/circles/) — the canonical "what is a data join?" tutorial

### `d3.hierarchy` and `d3.stratify`

Hierarchies in D3 are represented by `d3.HierarchyNode` objects: each
node has `.data` (the original record), `.parent`, `.children`, plus
calculated `.depth` and `.height`. There are two ways to build one:

- `d3.hierarchy(rootObject)` — when your data is **already nested**
  (a JSON object with `children` arrays). Common for file systems,
  category trees, etc.
- `d3.stratify()` — when your data is **flat** with id + parentId
  references. Exactly our case: the employees table is a flat list,
  each row pointing to its manager.

Calling stratify:

```ts
const root = d3.stratify<Employee>()
  .id(d => String(d.employeeNumber))
  .parentId(d => d.reportsTo != null ? String(d.reportsTo) : null)
  (employees);
```

A row whose `parentId` returns `null` is a root. Stratify will throw
if there's more than one root or if a parent id doesn't resolve.
Treat both as data integrity bugs and surface them clearly.

References:

- [D3 — d3-hierarchy](https://d3js.org/d3-hierarchy)
- [D3 — d3.stratify](https://d3js.org/d3-hierarchy/stratify)

### `d3.tree` — the Reingold-Tilford algorithm

Once you have a hierarchy, you need (x, y) coordinates. `d3.tree`
implements the **Reingold-Tilford "tidy tree"** algorithm, which
produces a layout that minimizes total width while keeping siblings
horizontally adjacent and parents centered above their children.

```ts
const tree = d3.tree<Employee>().nodeSize([NODE_W + GAP, NODE_H + GAP]);
tree(root);
// every node now has .x and .y populated
```

Two sizing modes:

| Mode | Behavior |
|---|---|
| `.size([w, h])` | Scales the whole tree to fit a fixed bounding box. |
| `.nodeSize([w, h])` | Each node gets a fixed coordinate footprint; tree extent grows to fit. |

`nodeSize` is almost always what you want. It survives window
resizes (the layout is stable), and it lets you pan/zoom without
re-running the layout.

The classic algorithm is from Reingold and Tilford's 1981 paper
"Tidier Drawings of Trees." Worth reading if you want to know why
tree drawing is harder than it looks; in short, naive recursive
layouts produce sub-trees that overlap once you go more than two
levels deep.

References:

- [D3 — d3.tree](https://d3js.org/d3-hierarchy/tree)
- [Reingold & Tilford — Tidier Drawings of Trees (1981)](http://reingold.co/tidier-drawings.pdf)

### Selections and the join pattern

D3 selections look superficially like jQuery, but the killer feature
is the **data join**. The minimal form:

```ts
g.selectAll('path')
  .data(links)
  .join('path')      // creates / updates / removes paths to match `links`
  .attr('d', linkGenerator);
```

`.data(array)` binds an array to a selection of DOM elements. `.join`
reconciles: for each datum without a matching element it creates one
(*enter*); for each existing element it keeps it (*update*); for each
element without a matching datum it removes it (*exit*). The
selection becomes "the merged enter+update set."

For our org chart we use the **three-callback form**, which lets us
distinguish what to do for newly-added vs. existing vs. removed nodes:

```ts
const nodeSel = nodeGroup.selectAll('g.node')
  .data(nodes, d => d.data.id);          // KEY function — id-stable

const nodeEnter = nodeSel.enter().append('g')
  .style('opacity', 0)                   // start invisible
  .attr('transform', n => transformFor(n));
nodeEnter.append('rect')...               // build sub-elements once

nodeSel.exit()
  .transition().duration(300)             // fade out before removing
  .style('opacity', 0)
  .remove();

const nodeAll = nodeEnter.merge(nodeSel); // both new and existing
nodeAll.transition().duration(400)        // animate to current position
  .attr('transform', n => transformFor(n))
  .style('opacity', 1);
nodeAll.select('text.name').text(n => n.data.label);  // update labels
```

The **key function** (`d => d.data.id`) is what makes this work. It
tells D3 "node 1612 is the SAME node across renders, just maybe at a
different position now." Without one, D3 keys by array index — every
node "is" whatever index it landed at, and you'd see chaotic shuffles
on every update.

References:

- [Mike Bostock — Thinking with Joins](https://bost.ocks.org/mike/join/) — the seminal explainer
- [D3 — selection.join](https://d3js.org/d3-selection/joining)

### `d3.zoom` — pan + zoom as a reusable behavior

Implementing pan + zoom on SVG by hand is fiddly: you have to track
mouse-down state, compute deltas, clamp scale, transform the
viewport. `d3.zoom` is a packaged **behavior** that handles all of it:

```ts
const zoom = d3.zoom<SVGSVGElement, unknown>()
  .scaleExtent([0.3, 3])
  .on('zoom', event => g.attr('transform', event.transform.toString()));
svg.call(zoom);
```

`svg.call(zoom)` installs wheel + drag listeners on the SVG. On every
interaction it computes a new `event.transform` (a `d3.ZoomTransform`
with `.x`, `.y`, `.k`) and fires the `'zoom'` event. We translate
the inner `<g>` so the layout itself never changes — the SVG just
shows a window into it.

The same pattern works for `d3.drag` (drag-to-move) and `d3.brush`
(drag-to-select-region).

References:

- [D3 — d3.zoom](https://d3js.org/d3-zoom)

### SVG vs. Canvas vs. WebGL

Three ways to draw graphics in a browser:

| Technology | Best for | Trade-offs |
|---|---|---|
| **SVG** | Up to ~10k DOM nodes; charts, diagrams, anything where you want pointer events / accessibility / inspectability for free. | One DOM node per data point; renderer slows down past ~10k. |
| **Canvas** | 10k–1M points; scatter plots, heatmaps, real-time visualizations. | No DOM; have to reimplement hit-testing, accessibility, animations. |
| **WebGL** | 1M+ points; geographic data, 3D, GPU-accelerated visualizations. | Even more setup; shader code; tooling like Three.js or deck.gl helps. |

Our org chart has ≈ 25 nodes. SVG is the obvious choice. If we were
visualizing every order in the database (~300k rows), we'd switch to
Canvas; for terabyte-scale geo data (Uber's deck.gl uses cases) you
go WebGL.

References:

- [MDN — SVG tutorial](https://developer.mozilla.org/en-US/docs/Web/SVG/Tutorial)
- [Mike Bostock — Working with Canvas in D3](https://www.youtube.com/watch?v=czmd66XMyhY)

## The code, walked through

### Fetching + filtering the data

```ts
this.employeeService.list().subscribe({
  next: data => {
    this.employees = data.filter(e => e.active !== false);
    this.render();
  },
});
```

We reuse the existing `list()` endpoint, which returns every employee
in the database. Soft-deleted rows (active = false) are filtered out;
they'd otherwise appear in the chart with broken parent links.

### Building the hierarchy

```ts
const root = d3.stratify<Employee>()
  .id(d => String(d.employeeNumber))
  .parentId(d => d.reportsTo != null ? String(d.reportsTo) : null)
  (this.employees);
```

The `id`s are strings because that's what stratify requires. We
catch the throw — if someone seeds the database wrong and there are
multiple roots, we want a clear error in the UI rather than a stack
trace in the console.

### Running the tree layout

```ts
const NODE_W = 180; const NODE_H = 70;
const X_GAP = 40;   const Y_GAP = 60;

const tree = d3.tree<Employee>()
  .nodeSize([NODE_W + X_GAP, NODE_H + Y_GAP]);
tree(root);
```

`nodeSize` takes `[horizontalSlot, verticalSlot]`. Each slot is the
total footprint a node consumes (its own size plus the gap to its
neighbor). After this call, every node has `.x` (horizontal position)
and `.y` (vertical position) set.

### Drawing the links

```ts
const linkGenerator = d3.linkVertical<HierarchyLink<Employee>, HierarchyNode<Employee>>()
  .x(d => d.x as number)
  .y(d => d.y as number);

g.append('g')
  .selectAll('path')
  .data(root.links())
  .join('path')
  .attr('d', linkGenerator);
```

`d3.linkVertical()` is a *path generator* — call it with a
parent/child pair and it returns a cubic-bezier `d` attribute string.
The generator only knows about the (x, y) coordinates; the colors,
stroke widths, etc. are plain attributes you set yourself.

### Drawing the cards

```ts
const node = g.append('g')
  .selectAll('g.node')
  .data(root.descendants())
  .join('g')
  .attr('transform', n => `translate(${n.x! - NODE_W/2}, ${n.y! - NODE_H/2})`)
  .style('cursor', 'pointer')
  .on('click', (_event, n) => this.router.navigate(['/employees', n.data.employeeNumber]));

node.append('rect').attr('width', NODE_W).attr('height', NODE_H) /* ... */;
node.append('text').text(n => `${n.data.firstName} ${n.data.lastName}`);
```

The `transform` translates each `<g>` so the card's top-left lands
where the layout placed its center. SVG attribute strings are just
template literals — no special API needed.

The click handler calls Angular's router; we get D3's coordinates,
Angular's navigation, both happy.

### Pan + zoom

```ts
const g = svg.append('g').attr('class', 'zoom-target');

this.zoomBehavior = d3.zoom<SVGSVGElement, unknown>()
  .scaleExtent([0.3, 3])
  .on('zoom', event => g.attr('transform', event.transform.toString()));
svg.call(this.zoomBehavior);
```

The `zoom-target` `<g>` is the only thing that moves. The outer SVG
stays at its CSS-controlled size. `event.transform` is a
`d3.ZoomTransform`; calling `.toString()` produces the SVG transform
string `translate(x,y) scale(k)`.

### Initial fit-to-screen

```ts
const scale = Math.min(
  (width - margin*2) / treeWidth,
  (height - margin*2) / treeHeight,
  1,
);
const initial = d3.zoomIdentity
  .translate(width/2 - centerX*scale, margin - top*scale)
  .scale(scale);
svg.call(this.zoomBehavior.transform, initial);
```

`d3.zoomIdentity` is the no-op transform (translate 0,0; scale 1).
Building from there ensures the zoom behavior knows about our initial
state — without that call, the user's first scroll-wheel snap would
"jump" because d3.zoom would think we'd started at the identity
transform.

## How to test

1. Make sure the backend is running and you're logged in.
2. Go to `/employees`.
3. Click the new **Org chart** button in the toolbar.
4. You should see a tidy tree, with the company president at the top
   and ICs at the leaves.
5. Hover the SVG and **scroll** — the chart zooms about the cursor.
6. **Drag** the background — the chart pans.
7. **Click** any card — you land on that employee's detail page.
8. Click **Fit to screen** — the chart re-renders centered.
9. Resize the browser window — the chart re-renders to fit.

To verify the live-update integration, open `/employees/org-chart` in
Window A and `/employees/<some id>/edit` in Window B. Change the name
or job title in B and Save. Within ~½ second, Window A's chart
updates with the new label — and crucially, **your pan/zoom doesn't
reset.** Try it: pan to a corner of the chart, then trigger an edit
in B. Only the affected card relabels; the viewport stays put. That's
the data-join + persistent-SVG-structure pattern paying off.

Same for create/delete: drop a new employee or remove one in B, and
the chart in A reshapes itself. New cards fade in, removed ones fade
out, and existing cards glide to their new positions. Cascading
deletes that fire many events in quick succession are coalesced into
a single render by the 250ms debounce on the events stream (otherwise
each affected row would trigger its own refetch).

## What you just learned

- **D3 is a toolkit, not a chart library.** Layout algorithms +
  shape generators + selections, you assemble the result.
- **Hierarchies and trees** as a first-class data structure, with
  the Reingold-Tilford algorithm for tidy layout.
- **`d3.stratify`** for turning a flat parent-id table into a
  hierarchy.
- **`d3.linkVertical`** as one of many path generators that take
  (x, y) and produce SVG path strings.
- **The data join (`.join`)** as D3's central pattern for binding
  data to DOM.
- **`d3.zoom` as a behavior** — a reusable bundle of pan/zoom
  handlers you attach to any SVG.
- **SVG vs. Canvas** trade-offs and when to switch.

## Study materials

### Getting your bearings in D3

- [D3 — official site](https://d3js.org)
- [Observable — Learn D3](https://observablehq.com/@d3/learn-d3) — runnable, beginner-friendly tutorial series
- [Amelia Wattenberger — Fullstack D3 and Data Visualization](https://www.newline.co/fullstack-d3) — paid book, but the free first chapter is excellent
- [Curran Kelleher — Introduction to D3](https://www.youtube.com/watch?v=_8V5o2UHG0E) — 9-hour video course, free

### Hierarchies and trees

- [D3 — d3-hierarchy](https://d3js.org/d3-hierarchy)
- [Observable — d3.tree examples](https://observablehq.com/@d3/tree)
- [Reingold & Tilford — Tidier Drawings of Trees](http://reingold.co/tidier-drawings.pdf) — original 1981 paper

### Selections and joins

- [Mike Bostock — Thinking with Joins](https://bost.ocks.org/mike/join/) — the seminal explainer
- [Mike Bostock — Three Little Circles](https://bost.ocks.org/mike/circles/) — interactive enter/update/exit walkthrough
- [D3 — d3-selection](https://d3js.org/d3-selection)

### Pan / zoom / drag

- [D3 — d3-zoom](https://d3js.org/d3-zoom)
- [Observable — Zoomable Tree](https://observablehq.com/@d3/zoomable-tree) — same idea as ours but with smooth transitions

### SVG fundamentals

- [MDN — SVG tutorial](https://developer.mozilla.org/en-US/docs/Web/SVG/Tutorial)
- [MDN — SVG transforms](https://developer.mozilla.org/en-US/docs/Web/SVG/Attribute/transform)

### Beyond trees — other D3 layouts to explore next

- [d3.pack](https://d3js.org/d3-hierarchy/pack) — circle packing
- [d3.partition / treemap](https://d3js.org/d3-hierarchy/partition) — sunbursts and treemaps
- [d3.forceSimulation](https://d3js.org/d3-force) — physics-based layouts for graphs
- [d3.geo](https://d3js.org/d3-geo) — map projections

### When to reach for higher-level libraries instead

- [Observable Plot](https://observablehq.com/plot/) — D3's authors' "good defaults" wrapper for quick statistical charts
- [Vega-Lite](https://vega.github.io/vega-lite/) — declarative grammar of graphics on top of D3
- [Chart.js](https://www.chartjs.org/) — what we'll use in Feature 12 for the sales dashboard

### React/Angular + D3

- [Amelia Wattenberger — React + D3](https://wattenberger.com/blog/react-and-d3) — clean discussion of the boundary between the two
- [Angular + D3 patterns](https://medium.com/@bcnzer/angular-d3-line-chart-c3aae37c8baf) — same ideas in Angular
