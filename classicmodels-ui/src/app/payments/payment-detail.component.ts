import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { PaymentService, Payment } from './payment.service';
import { MatCardModule } from '@angular/material/card';

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink, MatCardModule],
  templateUrl: './payment-detail.component.html'
})
export class PaymentDetailComponent implements OnInit {
  private readonly service = inject(PaymentService);
  private readonly route = inject(ActivatedRoute);

  payment = signal<Payment | undefined>(undefined);
  loading = signal(false);
  error = signal<string | undefined>(undefined);

  ngOnInit() {
    const customerNumber = Number(this.route.snapshot.paramMap.get('customerNumber'));
    const checkNumber = this.route.snapshot.paramMap.get('checkNumber')!;
    this.loading.set(true);
    this.service.get(customerNumber, checkNumber).subscribe({
      next: data => { this.payment.set(data); this.loading.set(false); },
      error: err => { this.error.set(err.message); this.loading.set(false); }
    });
  }
}