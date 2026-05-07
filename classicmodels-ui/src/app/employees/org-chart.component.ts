import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subscription, debounceTime } from 'rxjs';
import * as d3 from 'd3';

import { Employee, EmployeeService } from './employee.service';
import { EmployeeEventsService } from '../realtime/employee-events.service';

/**
 * Internal shape we feed to d3.stratify. Wraps an Employee plus the
 * pre-computed chart-only fields (parentId after orphan handling, the
 * label strings, and the synthetic-root flag).
 *
 * <p>Using a wrapper instead of mutating Employee keeps the public type
 * clean — Employee is the wire shape, ChartNode is a private rendering
 * concern.</p>
 */
interface ChartNode {
  id: string;
  parentId: string | null;
  employee: Employee | null;   // null for the synthetic root
  label: string;
  sublabel: string;
  isSynthetic: boolean;
}

/** Sentinel id used by the synthetic "company" root, when one is needed. */
const SYNTHETIC_ROOT_ID = '__company_root__';

// Layout constants. Module-level because they're referenced from
// render(), centerView(), and resetZoom(). Kept together so it's
// obvious how they relate.
const NODE_W = 180;
const NODE_H = 70;
const X_GAP = 40;   // horizontal gap between sibling cards
const Y_GAP = 60;   // vertical gap between levels

/** d3.tree mutates each node to set .x and .y; this alias documents intent. */
type LaidOutNode = d3.HierarchyNode<ChartNode> & { x: number; y: number };

/**
 * Org chart of the entire company, drawn as a top-down tree.
 *
 * <p>This is the first time we use D3 in this project. D3's strengths
 * are layout algorithms ({@code d3.tree}, {@code d3.hierarchy}, force
 * simulations, etc.) and SVG generation; everything else (events,
 * navigation, lifecycle) we keep in Angular.</p>
 *
 * <p>The component fetches every employee with one list call, builds a
 * hierarchy via {@link d3.stratify} keyed on {@code reportsTo}, runs
 * {@link d3.tree} to compute (x, y) for each node, then renders an SVG
 * by hand. There are no D3 transitions and no entry/exit selections —
 * the data is small (≈ 25 rows in this dataset) and re-rendering on
 * change is cheap, so we just clear the SVG and redraw.</p>
 *
 * <h3>Why SVG, not Canvas?</h3>
 *
 * <p>SVG nodes are real DOM elements: they get pointer events for free,
 * are accessible to screen readers, can be styled with CSS, and inspect
 * cleanly in DevTools. Canvas would be faster for tens of thousands of
 * nodes, but the cost is reimplementing all of that yourself. For an
 * org chart of a few dozen people, SVG wins on every axis.</p>
 */
@Component({
  standalone: true,
  selector: 'app-org-chart',
  imports: [
    CommonModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="page">
      <a routerLink="/employees" class="back-link">
        <mat-icon>arrow_back</mat-icon> Back to employees
      </a>

      <div class="header">
        <h2>Organization chart</h2>
        <div class="actions">
          <button mat-stroked-button (click)="resetZoom()">
            <mat-icon>center_focus_strong</mat-icon> Fit to screen
          </button>
        </div>
      </div>

      @if (loading()) {
        <div class="loading">
          <mat-spinner diameter="32"></mat-spinner>
          <span>Loading employees…</span>
        </div>
      }
      @if (error()) {
        <div class="error">{{ error() }}</div>
      }

      <!--
        The SVG itself. We give it a fixed viewport (width 100%, fixed
        height) and let d3.zoom pan/scale the inner <g.zoom-target>
        without resizing the outer SVG.
      -->
      <div class="chart-wrap" [class.hidden]="loading() || error()">
        <svg #svg class="org-chart-svg"></svg>
      </div>

      <p class="hint">
        Click any card to view that employee. Drag the background to pan,
        scroll to zoom.
      </p>
    </div>
  `,
  styles: [`
    .page { padding: 0.5rem; }

    .back-link {
      display: inline-flex; align-items: center; gap: 0.25rem;
      margin-bottom: 0.75rem;
      text-decoration: none; color: rgba(0, 0, 0, 0.7);
    }
    .back-link mat-icon { font-size: 18px; height: 18px; width: 18px; }

    .header {
      display: flex; align-items: center; gap: 0.75rem;
      margin-bottom: 0.75rem;
    }
    .header h2 { margin: 0; flex: 1; }

    .loading {
      display: flex; align-items: center; gap: 0.75rem;
      padding: 1rem 0; color: rgba(0, 0, 0, 0.6);
    }
    .error {
      color: #b71c1c; padding: 0.75rem;
      border-left: 4px solid #b71c1c; background: #fdecea;
    }

    .chart-wrap {
      width: 100%; height: 70vh; min-height: 480px;
      border: 1px solid rgba(0, 0, 0, 0.08); border-radius: 4px;
      background: #fafafa;
      overflow: hidden;       /* zoom translates inside, no scrollbars */
    }
    .chart-wrap.hidden { display: none; }
    .org-chart-svg { width: 100%; height: 100%; display: block; cursor: grab; }
    .org-chart-svg:active { cursor: grabbing; }

    .hint {
      color: rgba(0, 0, 0, 0.55);
      font-size: 0.85rem;
      margin-top: 0.5rem;
    }
  `],
})
export class OrgChartComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly employeeService = inject(EmployeeService);
  private readonly router = inject(Router);
  private readonly liveEvents = inject(EmployeeEventsService);

  /** Subscription to live employee events; cleared in ngOnDestroy. */
  private eventsSub?: Subscription;

  /**
   * D3 needs a real DOM element to attach to. {@code @ViewChild} with
   * the template ref {@code #svg} gives us the {@code <svg>} as soon
   * as Angular has inserted it (i.e. in {@code ngAfterViewInit}, not
   * earlier).
   */
  @ViewChild('svg', { static: false }) svgRef!: ElementRef<SVGSVGElement>;

  loading = signal(false);
  error = signal<string | undefined>(undefined);

  /** Cached so we can re-render when the viewport resizes. */
  private employees: Employee[] = [];

  /** The d3-zoom behavior, kept so {@link resetZoom} can call it. */
  private zoomBehavior?: d3.ZoomBehavior<SVGSVGElement, unknown>;

  /**
   * True until the very first successful render finishes laying out the
   * SVG. Used to gate the auto-fit-to-screen logic — we only center on
   * the *first* render, never on subsequent re-renders triggered by
   * live employee events. That's the whole point of avoiding a full
   * re-render: we want to keep the user's pan/zoom intact when someone
   * else's edit comes in over the WebSocket.
   */
  private firstRender = true;

  /** Window resize listener, removed in ngOnDestroy. */
  private resizeListener = () => this.render();

  ngOnInit(): void {
    // Initial load — we want the spinner so the user sees something
    // is happening on first paint.
    this.loadAndRender({ showSpinner: true });

    // Live updates. EmployeeEventsService pushes a CREATED/UPDATED/
    // DELETED event over STOMP whenever ANYONE changes an employee.
    // Any of those can change the shape of the org tree, so we just
    // refetch + redraw on every event.
    //
    // debounceTime(250): if a bulk operation triggers a flurry of
    // events (e.g. cascade-deleting a manager fires DELETE for each
    // affected row), we coalesce them into a single refetch. 250ms
    // is well below "feels stale" but well above "individual events
    // arrive faster than the network roundtrip."
    //
    // We don't show the spinner on live updates — that would flash
    // the chart away every time someone elsewhere edits a name.
    this.eventsSub = this.liveEvents.events$
      .pipe(debounceTime(250))
      .subscribe(() => this.loadAndRender({ showSpinner: false }));
  }

  ngAfterViewInit(): void {
    // If the data arrived before the view did, render now (also via
    // rAF so the SVG has a chance to size itself).
    if (!this.loading() && this.employees.length) {
      requestAnimationFrame(() => this.render());
    }
    window.addEventListener('resize', this.resizeListener);
  }

  ngOnDestroy(): void {
    window.removeEventListener('resize', this.resizeListener);
    this.eventsSub?.unsubscribe();
  }

  /**
   * Fetch the full employee list and render the chart. Used both on
   * initial load and on every live event.
   *
   * @param showSpinner whether to flip the loading flag (and thus show
   *   the spinner while waiting). False on live updates to avoid
   *   flashing the UI on every keystroke someone else is making.
   */
  private loadAndRender({ showSpinner }: { showSpinner: boolean }): void {
    if (showSpinner) this.loading.set(true);
    this.employeeService.list().subscribe({
      next: data => {
        // Filter out soft-deleted rows. They'd otherwise dangle in the
        // tree without a manager (or be parents of nothing) and clutter
        // the diagram. The detail/list pages still show them — this is
        // just a display choice for the chart.
        this.employees = data.filter(e => e.active !== false);
        if (showSpinner) this.loading.set(false);
        // Defer to the next animation frame: setting loading=false has
        // just changed which DOM nodes are display:none, but the
        // browser hasn't done layout yet. If we called render() here,
        // getBoundingClientRect would return 0×0 and the early-out
        // check at the top of render() would silently abort. By the
        // time rAF fires, the chart-wrap is visible and has its real
        // CSS-driven size.
        requestAnimationFrame(() => this.render());
      },
      error: err => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Failed to load employees');
        if (showSpinner) this.loading.set(false);
      },
    });
  }

  /**
   * Build the hierarchy, run layout, then *incrementally* update the
   * SVG using D3's enter/update/exit join pattern.
   *
   * <h3>Why incremental matters</h3>
   *
   * <p>The previous version of this method called
   * {@code svg.selectAll('*').remove()} and rebuilt everything. That
   * works, but it has two problems for live updates:</p>
   *
   * <ol>
   *   <li><b>Pan/zoom resets</b> on every refresh — the user's
   *   carefully chosen viewport snaps back to fit-to-screen each time
   *   anyone, anywhere, edits an employee.</li>
   *   <li><b>Visual flash</b> — every node disappears for a frame and
   *   then reappears, even if it didn't change.</li>
   * </ol>
   *
   * <p>D3's idiomatic answer is the <b>data join</b>: bind data to a
   * selection with a key function (here, the employee id), and let
   * D3's {@code .join(enter, update, exit)} handle the diff. Existing
   * nodes are re-used (their DOM stays put), new nodes get created,
   * removed nodes get deleted. We can transition the transforms so
   * surviving nodes glide to their new positions instead of teleporting.</p>
   *
   * <p>The persistent structure (the {@code <g.zoom-target>} wrapper
   * plus its two child groups for links and nodes) is built once on
   * the first call and reused thereafter.</p>
   */
  private render(): void {
    if (!this.svgRef || !this.employees.length) return;

    const svgEl = this.svgRef.nativeElement;
    const { width, height } = svgEl.getBoundingClientRect();
    if (width === 0 || height === 0) return; // not visible yet

    // ---- 1. Build the hierarchy ---------------------------------------
    //
    // d3.stratify throws on two common data conditions: "multiple roots"
    // (more than one row has a null parent id) and "missing parent" (a
    // parent id doesn't appear in the data). buildChartNodes pre-cleans
    // the input so neither happens — see its docstring.
    const chartNodes = this.buildChartNodes(this.employees);
    let root: d3.HierarchyNode<ChartNode>;
    try {
      root = d3.stratify<ChartNode>()
        .id(d => d.id)
        .parentId(d => d.parentId)(chartNodes);
    } catch (e) {
      console.error('Could not build org hierarchy:', e);
      this.error.set(
        'Could not build organization tree — see browser console for details.',
      );
      return;
    }

    // ---- 2. Run the tree layout (Reingold-Tilford) --------------------
    //
    // nodeSize gives each node a fixed [width, height] coordinate
    // footprint; the tree extent grows to fit. After this call, every
    // node has .x and .y populated.
    d3.tree<ChartNode>().nodeSize([NODE_W + X_GAP, NODE_H + Y_GAP])(root);
    const nodes = root.descendants() as LaidOutNode[];
    const links = root.links();

    // ---- 3. Persistent SVG structure (built once) ---------------------
    //
    // First call only: append the zoom-target <g>, its children, and
    // install d3.zoom on the SVG. On every subsequent call we just
    // select what's already there. This is the whole reason pan/zoom
    // state survives live updates — the zoom behavior is attached to
    // the SAME SVG node and its transform is never reset.
    const svg = d3.select<SVGSVGElement, unknown>(svgEl);
    let zoomTarget = svg.select<SVGGElement>('g.zoom-target');
    if (zoomTarget.empty()) {
      zoomTarget = svg.append('g').attr('class', 'zoom-target');
      zoomTarget.append('g')
        .attr('class', 'links')
        .attr('fill', 'none')
        .attr('stroke', 'rgba(0,0,0,0.3)')
        .attr('stroke-width', 1.2);
      zoomTarget.append('g').attr('class', 'nodes');

      this.zoomBehavior = d3.zoom<SVGSVGElement, unknown>()
        .scaleExtent([0.3, 3])
        .on('zoom', event => {
          zoomTarget.attr('transform', event.transform.toString());
        });
      svg.call(this.zoomBehavior);
    }

    // ---- 4. Update the links via data join ----------------------------
    //
    // The key function `${source}->${target}` makes a link's identity
    // depend on its endpoints. If a child gets reassigned to a
    // different manager, the old link disappears (exit) and a new one
    // appears (enter) instead of the same path being mutated.
    const linkGenerator = d3.linkVertical<
      d3.HierarchyLink<ChartNode>,
      LaidOutNode
    >()
      .x(d => d.x)
      .y(d => d.y);

    zoomTarget.select<SVGGElement>('g.links')
      .selectAll<SVGPathElement, d3.HierarchyLink<ChartNode>>('path')
      .data(links, d => `${(d.source as LaidOutNode).data.id}->${(d.target as LaidOutNode).data.id}`)
      .join(
        enter => enter.append('path').attr('d', linkGenerator),
        update => update,                       // existing path stays put
        exit => exit.remove(),
      )
      // Whether the path was just created or already existed, transition
      // its `d` attribute. SVG path interpolation doesn't always look
      // right when the geometry changes drastically, but for tree links
      // (smooth cubic beziers between known endpoints) it works well.
      .transition().duration(400)
      .attr('d', linkGenerator);

    // ---- 5. Update the node cards via data join -----------------------
    //
    // Key function: the employee id (or SYNTHETIC_ROOT_ID for the
    // synthetic node). This is what tells D3 "node 1612 is the SAME
    // node across renders, just maybe at a different position now."
    // Without a key function, D3 would key by array index, which would
    // mean *every* node reusing whatever index it landed at — total
    // chaos when nodes are added or removed.
    const nodeSel = zoomTarget.select<SVGGElement>('g.nodes')
      .selectAll<SVGGElement, LaidOutNode>('g.node')
      .data(nodes, d => d.data.id);

    // ENTER — brand-new nodes. Build their full sub-structure here.
    const depthColors = ['#1a237e', '#1565c0', '#2e7d32', '#6a1b9a', '#ad1457'];
    const nodeEnter = nodeSel.enter().append('g')
      .attr('class', 'node')
      .attr('transform', n => this.nodeTransform(n))
      .style('cursor', n => (n.data.isSynthetic ? 'default' : 'pointer'))
      .style('opacity', 0)                 // fade in
      .on('click', (_event, n) => {
        if (n.data.isSynthetic) return;
        const id = n.data.employee?.employeeNumber;
        if (id != null) this.router.navigate(['/employees', id]);
      });

    nodeEnter.append('rect')
      .attr('width', NODE_W)
      .attr('height', NODE_H)
      .attr('rx', 6)
      .attr('ry', 6);

    nodeEnter.append('text')
      .attr('class', 'name-text')
      .attr('x', NODE_W / 2)
      .attr('y', 26)
      .attr('text-anchor', 'middle')
      .attr('font-size', '13')
      .attr('font-weight', '500')
      .attr('fill', '#212121');

    nodeEnter.append('text')
      .attr('class', 'title-text')
      .attr('x', NODE_W / 2)
      .attr('y', 46)
      .attr('text-anchor', 'middle')
      .attr('font-size', '11')
      .attr('fill', 'rgba(0,0,0,0.6)');

    // EXIT — nodes that are gone. Fade them out before removing.
    nodeSel.exit()
      .transition().duration(300)
      .style('opacity', 0)
      .remove();

    // UPDATE (merged enter + existing) — apply current data to every
    // visible node. .merge() takes the freshly-entered selection and
    // unions it with the existing one, so the same chained calls touch
    // both groups.
    const nodeAll = nodeEnter.merge(nodeSel);

    // Transform: existing nodes glide to new positions; entering ones
    // appear in place (their pre-set transform is the same as their
    // target). The fade-in on opacity makes the difference visible.
    nodeAll.transition().duration(400)
      .attr('transform', n => this.nodeTransform(n))
      .style('opacity', 1);

    // Re-apply the visual attrs and label text on every render. This
    // is what propagates name/title edits without recreating the DOM.
    nodeAll.select<SVGRectElement>('rect')
      .attr('fill', n => (n.data.isSynthetic ? '#f5f5f5' : '#ffffff'))
      .attr('stroke', n => n.data.isSynthetic
        ? 'rgba(0,0,0,0.45)'
        : depthColors[Math.min(n.depth, depthColors.length - 1)])
      .attr('stroke-width', 1.5)
      .attr('stroke-dasharray', n => (n.data.isSynthetic ? '4 3' : null));

    nodeAll.select<SVGTextElement>('text.name-text').text(n => n.data.label);
    nodeAll.select<SVGTextElement>('text.title-text').text(n => this.truncate(n.data.sublabel, 28));

    // ---- 6. Center on first render only -------------------------------
    //
    // The whole point of this rewrite is to NOT reset the user's
    // pan/zoom on live updates. So fit-to-screen runs only once, on
    // the first successful render. The Fit-to-screen button calls
    // {@link resetZoom} explicitly when the user wants to recenter.
    if (this.firstRender) {
      this.firstRender = false;
      this.centerView(svg, nodes, width, height, /* animate = */ false);
    }
  }

  /**
   * Compute the SVG-space transform string for a node card.
   *
   * <p>{@code d3.tree} reports node centers, but our cards anchor at
   * the top-left, so we shift by half-width and half-height. Splitting
   * this into a method keeps the math in one place — both ENTER (set
   * once) and UPDATE (animate to) call it.</p>
   */
  private nodeTransform(n: LaidOutNode): string {
    return `translate(${n.x - NODE_W / 2}, ${n.y - NODE_H / 2})`;
  }

  /**
   * Compute the fit-to-screen transform and apply it via the zoom
   * behavior. Called once on first render and from the Fit to screen
   * button. {@code animate=true} smooths the transition; first render
   * uses {@code false} so the chart appears in place rather than
   * sliding into view from {@code zoomIdentity}.
   */
  private centerView(
    svg: d3.Selection<SVGSVGElement, unknown, null, undefined>,
    nodes: LaidOutNode[],
    width: number,
    height: number,
    animate: boolean,
  ): void {
    if (!this.zoomBehavior) return;

    const xExtent = d3.extent(nodes, n => n.x) as [number, number];
    const yExtent = d3.extent(nodes, n => n.y) as [number, number];
    const treeWidth  = xExtent[1] - xExtent[0] + NODE_W;
    const treeHeight = yExtent[1] - yExtent[0] + NODE_H;

    const margin = 20;
    const scale = Math.min(
      (width - margin * 2) / treeWidth,
      (height - margin * 2) / treeHeight,
      1,
    );
    const translateX = width / 2 - ((xExtent[0] + xExtent[1]) / 2) * scale;
    const translateY = margin - yExtent[0] * scale;

    const target = d3.zoomIdentity.translate(translateX, translateY).scale(scale);

    if (animate) {
      svg.transition().duration(500).call(this.zoomBehavior.transform, target);
    } else {
      svg.call(this.zoomBehavior.transform, target);
    }
  }

  /**
   * "Fit to screen" — recompute and apply the centering transform.
   *
   * <p>Note that this no longer wipes-and-redraws; it just animates the
   * existing pan/zoom to the fit-to-screen position. The chart
   * structure stays put.</p>
   */
  resetZoom(): void {
    if (!this.svgRef || !this.employees.length) return;
    const svgEl = this.svgRef.nativeElement;
    const { width, height } = svgEl.getBoundingClientRect();
    if (width === 0 || height === 0) return;

    // Re-derive layout so we have current bounds. Cheap (~25 nodes).
    const chartNodes = this.buildChartNodes(this.employees);
    const root = d3.stratify<ChartNode>()
      .id(d => d.id)
      .parentId(d => d.parentId)(chartNodes);
    d3.tree<ChartNode>().nodeSize([NODE_W + X_GAP, NODE_H + Y_GAP])(root);

    this.centerView(
      d3.select<SVGSVGElement, unknown>(svgEl),
      root.descendants() as LaidOutNode[],
      width,
      height,
      /* animate = */ true,
    );
  }

  /**
   * Turn the flat Employee list into ChartNodes, handling two real-
   * world data conditions that would otherwise make d3.stratify throw:
   *
   * <ol>
   *   <li><b>Orphans</b> — a row whose {@code reportsTo} points at an
   *   id that's not in the data (manager was deleted / soft-deleted).
   *   Treated as null, i.e. as if they had no manager.</li>
   *   <li><b>Multiple roots</b> — more than one employee genuinely has
   *   no manager (the President and a few VPs in the seed data; could
   *   also happen as a side-effect of NULLIFY deletes higher up the
   *   tree). Solved by injecting a synthetic "Classic Models" root and
   *   re-parenting the orphans under it, so the chart renders one
   *   connected tree instead of refusing to draw anything.</li>
   * </ol>
   */
  private buildChartNodes(employees: Employee[]): ChartNode[] {
    const knownIds = new Set(employees.map(e => String(e.employeeNumber)));

    const decorated: ChartNode[] = employees.map(e => {
      // Resolve the parent. Three cases collapse to "no parent":
      //   - reportsTo is null (a real root)
      //   - reportsTo is undefined (rare, but possible from old payloads)
      //   - reportsTo is a stale id that's been deleted
      const parentId =
        e.reportsTo != null && knownIds.has(String(e.reportsTo))
          ? String(e.reportsTo)
          : null;
      return {
        id: String(e.employeeNumber),
        parentId,
        employee: e,
        label: `${e.firstName} ${e.lastName}`,
        sublabel: e.jobTitle ?? '',
        isSynthetic: false,
      };
    });

    const rootCount = decorated.filter(n => n.parentId === null).length;
    if (rootCount <= 1) return decorated;

    // Multiple natural roots — wrap them under a synthetic root.
    const synthetic: ChartNode = {
      id: SYNTHETIC_ROOT_ID,
      parentId: null,
      employee: null,
      label: 'Classic Models',
      sublabel: 'Organization',
      isSynthetic: true,
    };
    return [
      synthetic,
      ...decorated.map(n =>
        n.parentId === null ? { ...n, parentId: SYNTHETIC_ROOT_ID } : n,
      ),
    ];
  }

  private truncate(s: string, max: number): string {
    if (!s) return '';
    return s.length > max ? s.slice(0, max - 1) + '…' : s;
  }
}
