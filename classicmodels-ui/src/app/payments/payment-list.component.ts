import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PaymentService, Payment } from './payment.service';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

@Component({
  standalone: true,
  imports: [CommonModule, MatTableModule, MatPaginatorModule, MatSortModule, RouterLink, MatButtonModule],
  templateUrl: './payment-list.component.html'
})
export class PaymentListComponent implements OnInit {
  private readonly service = inject(PaymentService);
  payments = signal<Payment[]>([]);
  total = signal(0);
  loading = signal(false);
  error = signal<string | undefined>(undefined);
  displayedColumns = ['customerNumber','checkNumber','paymentDate','amount','actions'];

  ngOnInit() {
    this.load();
  }

  load(event?: PageEvent | Sort) {
    this.loading.set(true);
    this.service.list().subscribe({
      next: data => { this.payments.set(data); this.total.set(data.length); this.loading.set(false); },
      error: err => { this.error.set(err.message); this.loading.set(false); }
    });
  }

  remove(customerNumber: number, checkNumber: string) {
    this.service.delete(customerNumber, checkNumber).subscribe(() => this.load());
  }
}