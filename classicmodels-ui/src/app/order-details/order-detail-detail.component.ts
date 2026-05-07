import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { OrderDetailService, OrderDetail } from './order-detail.service';
import { MatCardModule } from '@angular/material/card';

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink, MatCardModule],
  templateUrl: './order-detail-detail.component.html'
})
export class OrderDetailDetailComponent implements OnInit {
  private readonly service = inject(OrderDetailService);
  private readonly route = inject(ActivatedRoute);

  detail = signal<OrderDetail | undefined>(undefined);
  loading = signal(false);
  error = signal<string | undefined>(undefined);

  ngOnInit() {
    const orderNumber = Number(this.route.snapshot.paramMap.get('orderNumber'));
    const productCode = this.route.snapshot.paramMap.get('productCode')!;
    this.loading.set(true);
    this.service.get(orderNumber, productCode).subscribe({
      next: data => { this.detail.set(data); this.loading.set(false); },
      error: err => { this.error.set(err.message); this.loading.set(false); }
    });
  }
}