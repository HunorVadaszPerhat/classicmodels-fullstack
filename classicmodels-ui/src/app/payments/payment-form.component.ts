import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { PaymentService, Payment } from './payment.service';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, RouterLink],
  templateUrl: './payment-form.component.html'
})
export class PaymentFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(PaymentService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  form = this.fb.group({
    customerNumber: [null as number | null, Validators.required],
    checkNumber: ['', Validators.required],
    paymentDate: ['', Validators.required],
    amount: [null as number | null, Validators.required],
  });

  isEdit = false;
  customerNumber?: number;
  checkNumber?: string;

  ngOnInit() {
    const customerParam = this.route.snapshot.paramMap.get('customerNumber');
    const checkParam = this.route.snapshot.paramMap.get('checkNumber');
    if (customerParam && checkParam) {
      this.isEdit = true;
      this.customerNumber = Number(customerParam);
      this.checkNumber = checkParam;
      this.service.get(this.customerNumber, this.checkNumber).subscribe(data => {
        this.form.patchValue(data);
        this.form.controls.customerNumber.disable();
        this.form.controls.checkNumber.disable();
      });
    }
  }

  save() {
    if (this.form.invalid) return;
    const value = this.form.getRawValue() as Payment;
    const obs = this.isEdit
      ? this.service.update(this.customerNumber!, this.checkNumber!, value)
      : this.service.create(value);
    obs.subscribe(() => this.router.navigate(['../'], { relativeTo: this.route }));
  }
}