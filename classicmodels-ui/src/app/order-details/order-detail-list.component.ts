import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrderDetailService, OrderDetail } from './order-detail.service';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

@Component({
  standalone: true,
  imports: [CommonModule, MatTableModule, MatPaginatorModule, MatSortModule, RouterLink, MatButtonModule],
  templateUrl: './order-detail-list.component.html'
})
export class OrderDetailListComponent implements OnInit {
  private readonly service = inject(OrderDetailService);
  details = signal<OrderDetail[]>([]);
  total = signal(0);
  loading = signal(false);
  error = signal<string | undefined>(undefined);
  displayedColumns = ['orderNumber','productCode','quantityOrdered','priceEach','actions'];

  ngOnInit() {
    this.load();
  }

  load(event?: PageEvent | Sort) {
    this.loading.set(true);
    this.service.list().subscribe({
      next: data => { this.details.set(data); this.total.set(data.length); this.loading.set(false); },
      error: err => { this.error.set(err.message); this.loading.set(false); }
    });
  }

  remove(orderNumber: number, productCode: string) {
    this.service.delete(orderNumber, productCode).subscribe(() => this.load());
  }
}