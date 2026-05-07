import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrderService, Order } from './order.service';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

@Component({
  standalone: true,
  imports: [CommonModule, MatTableModule, MatPaginatorModule, MatSortModule, RouterLink, MatButtonModule],
  templateUrl: './order-list.component.html'
})
export class OrderListComponent implements OnInit {
  private readonly service = inject(OrderService);
  orders = signal<Order[]>([]);
  total = signal(0);
  loading = signal(false);
  error = signal<string | undefined>(undefined);
  displayedColumns = ['orderNumber','orderDate','status','customerNumber','actions'];

  ngOnInit() {
    this.load();
  }

  load(event?: PageEvent | Sort) {
    this.loading.set(true);
    this.service.list().subscribe({
      next: data => { this.orders.set(data); this.total.set(data.length); this.loading.set(false); },
      error: err => { this.error.set(err.message); this.loading.set(false); }
    });
  }

  remove(id: number) {
    this.service.delete(id).subscribe(() => this.load());
  }
}