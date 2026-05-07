import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { OrderDetailService, OrderDetail } from './order-detail.service';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, RouterLink],
  templateUrl: './order-detail-form.component.html'
})
export class OrderDetailFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(OrderDetailService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  form = this.fb.group({
    orderNumber: [null as number | null, Validators.required],
    productCode: ['', Validators.required],
    quantityOrdered: [null as number | null, Validators.required],
    priceEach: [null as number | null, Validators.required],
    orderLineNumber: [null as number | null, Validators.required],
  });

  isEdit = false;
  orderNumber?: number;
  productCode?: string;

  ngOnInit() {
    const orderParam = this.route.snapshot.paramMap.get('orderNumber');
    const productParam = this.route.snapshot.paramMap.get('productCode');
    if (orderParam && productParam) {
      this.isEdit = true;
      this.orderNumber = Number(orderParam);
      this.productCode = productParam;
      this.service.get(this.orderNumber, this.productCode).subscribe(data => {
        this.form.patchValue(data);
        this.form.controls.orderNumber.disable();
        this.form.controls.productCode.disable();
      });
    }
  }

  save() {
    if (this.form.invalid) return;
    const value = this.form.getRawValue() as OrderDetail;
    const obs = this.isEdit
      ? this.service.update(this.orderNumber!, this.productCode!, value)
      : this.service.create(value);
    obs.subscribe(() => this.router.navigate(['../'], { relativeTo: this.route }));
  }
}