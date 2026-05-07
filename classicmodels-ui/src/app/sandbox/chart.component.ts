import { Component, ElementRef, viewChild, effect, signal, OnDestroy, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import * as d3 from 'd3';
import { CustomerService } from '../customers/customer.service';
import { Customer } from '../customers/customer.model';

export interface ChartData {
  label: string;
  value: number;
}

@Component({
  standalone: true,
  selector: 'app-chart',
  imports: [CommonModule],
  template: `
    <div class="chart-header">
      <h2>Customers by Country (D3.js Bar Chart)</h2>
      <button class="btn" (click)="loadCustomerData()">Load Data</button>
      <button class="btn" (click)="randomizeData()">Randomize Colors</button>
    </div>
    <!-- The #chart container where D3 will render the SVG -->
    <div class="chart-container" #chart></div>
  `,
  styles: [`
    .chart-header {
      display: flex;
      gap: 10px;
      margin-bottom: 16px;
      align-items: center;
    }
    .btn {
      padding: 8px 16px;
      background-color: #3f51b5;
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
    }
    .btn:hover {
      background-color: #303f9f;
    }
    .chart-container {
      width: 100%;
      height: 500px;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      background-color: #fafafa;
    }
    /* SVG styles for transitions */
    ::ng-deep .bar:hover {
      opacity: 0.8;
      transition: opacity 0.2s;
    }
  `]
})
export class ChartComponent implements OnInit, OnDestroy {
  private customerService = inject(CustomerService);

  // 1. BEST PRACTICE: Use a viewChild signal to get a reference to the DOM element
  // D3 should render directly into this container instead of the whole body
  chartContainer = viewChild.required<ElementRef<HTMLElement>>('chart');

  // 2. BEST PRACTICE: Store data in signals for reactivity
  data = signal<ChartData[]>([]);

  private resizeObserver?: ResizeObserver;
  private colorScale = d3.scaleOrdinal(d3.schemeCategory10);

  constructor() {
    // 3. BEST PRACTICE: Use an effect to automatically re-render the chart
    // whenever the container becomes available or the data changes
    effect(() => {
      const container = this.chartContainer();
      const currentData = this.data();

      if (container && currentData.length > 0) {
        this.renderChart(container.nativeElement, currentData);

        // Setup ResizeObserver for responsiveness
        if (!this.resizeObserver) {
          this.resizeObserver = new ResizeObserver(() => {
            // Trigger a re-render when the container resizes
            this.renderChart(container.nativeElement, this.data());
          });
          this.resizeObserver.observe(container.nativeElement);
        }
      }
    });
  }

  ngOnInit() {
    this.loadCustomerData();
  }

  ngOnDestroy() {
    // Clean up observer to prevent memory leaks
    if (this.resizeObserver) {
      this.resizeObserver.disconnect();
    }
  }

  loadCustomerData() {
    this.customerService.list().subscribe(customers => {
      // Data Transformation: Group customers by country and count them
      const rollupData = d3.rollups(
        customers,
        (group) => group.length,       // Count the number of customers
        (d) => d.country               // Group by country
      );

      // Format it for your ChartData interface and sort highest to lowest
      const chartData: ChartData[] = rollupData.map(([country, count]) => ({
        label: country,
        value: count
      })).sort((a, b) => b.value - a.value);

      this.data.set(chartData);
    });
  }

  randomizeData() {
    // A trick to trigger a re-render to randomize colors
    this.colorScale = d3.scaleOrdinal(
      d3.shuffle([...d3.schemeCategory10, ...d3.schemeSet3])
    );
    this.data.set([...this.data()]);
  }

  /**
   * Main function to render/update the D3 chart
   */
  private renderChart(element: HTMLElement, data: ChartData[]) {
    // 4. BEST PRACTICE: Define margins to make room for axes, especially long labels
    const margin = { top: 30, right: 30, bottom: 80, left: 60 };

    // Calculate available width and height from the container's real DOM size
    const containerRect = element.getBoundingClientRect();
    const width = (containerRect.width || 800) - margin.left - margin.right;
    const height = (containerRect.height || 500) - margin.top - margin.bottom;

    // Abort if container is too small (e.g., hidden)
    if (width <= 0 || height <= 0) return;

    // 5. BEST PRACTICE: Use the D3 Update Pattern
    let svg = d3.select(element).select<SVGSVGElement>('svg');
    let chartGroup: d3.Selection<SVGGElement, unknown, null, undefined>;

    if (svg.empty()) {
      // Enter (Initialization) phase
      svg = d3.select(element)
        .append('svg')
        .attr('width', width + margin.left + margin.right)
        .attr('height', height + margin.top + margin.bottom);

      chartGroup = svg.append('g')
        .attr('class', 'chart-group')
        .attr('transform', `translate(${margin.left},${margin.top})`);

      // Add empty axis groups
      chartGroup.append('g').attr('class', 'x-axis');
      chartGroup.append('g').attr('class', 'y-axis');
    } else {
      // Update phase for the SVG container (if window was resized)
      svg.attr('width', width + margin.left + margin.right)
         .attr('height', height + margin.top + margin.bottom);
      chartGroup = svg.select('.chart-group');
    }

    // 6. BEST PRACTICE: Create dynamic scales
    const x = d3.scaleBand()
      .domain(data.map(d => d.label))
      .range([0, width])
      .padding(0.2);

    const y = d3.scaleLinear()
      .domain([0, d3.max(data, d => d.value) || 0]).nice()
      .range([height, 0]);

    // Setup a shared transition for animations
    const t = d3.transition().duration(750) as any;

    // 7. BEST PRACTICE: Update axes dynamically and rotate long labels
    chartGroup.select<SVGGElement>('.x-axis')
      .attr('transform', `translate(0,${height})`)
      .transition(t)
      .call(d3.axisBottom(x))
      .selectAll("text")
        .style("text-anchor", "end")
        .attr("dx", "-.8em")
        .attr("dy", ".15em")
        .attr("transform", "rotate(-45)");

    chartGroup.select<SVGGElement>('.y-axis')
      .transition(t)
      .call(d3.axisLeft(y).ticks(5).tickFormat(d3.format("d"))); // Format to integers

    // 8. BEST PRACTICE: Use D3's `.join()` method to handle Enter, Update, and Exit states for data
    chartGroup.selectAll<SVGRectElement, ChartData>('.bar')
      .data(data, d => d.label) // Key function ensures data is mapped to the correct element
      .join(
        // The "enter" function is called for new data points
        enter => enter.append('rect')
          .attr('class', 'bar')
          .attr('fill', d => this.colorScale(d.label))
          .attr('rx', 4) // Rounded corners
          .attr('x', d => x(d.label)!)
          .attr('width', x.bandwidth())
          .attr('y', height) // Start at the bottom for animation
          .attr('height', 0)
          .call(enter => enter.transition(t) // Animate height up
            .attr('y', d => y(d.value))
            .attr('height', d => height - y(d.value))
          ),

        // The "update" function is called for existing data points that are changed/moved
        update => update
          .call(update => update.transition(t)
            .attr('x', d => x(d.label)!)
            .attr('width', x.bandwidth())
            .attr('y', d => y(d.value))
            .attr('height', d => height - y(d.value))
            .attr('fill', d => this.colorScale(d.label))
          ),

        // The "exit" function is called for elements that no longer have matching data
        exit => exit
          .call(exit => exit.transition(t) // Animate height down before removing
            .attr('y', height)
            .attr('height', 0)
            .remove()
          )
      );

    // Add text labels to the bars
    chartGroup.selectAll('.label')
      .data(data, (d: any) => d.label)
      .join(
        enter => enter.append('text')
          .attr('class', 'label')
          .attr('x', d => x(d.label)! + x.bandwidth() / 2)
          .attr('y', height)
          .attr('text-anchor', 'middle')
          .attr('fill', 'black')
          .text(d => d.value)
          .style('opacity', 0)
          .call(enter => enter.transition(t)
            .attr('y', d => y(d.value) - 5)
            .style('opacity', 1)
          ),
        update => update
          .text(d => d.value)
          .call(update => update.transition(t)
            .attr('x', d => x(d.label)! + x.bandwidth() / 2)
            .attr('y', d => y(d.value) - 5)
            .style('opacity', 1)
          ),
        exit => exit
          .call(exit => exit.transition(t)
            .attr('y', height)
            .style('opacity', 0)
            .remove()
          )
      );
  }
}
