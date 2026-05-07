import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { OrderService, Order } from './order.service';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, RouterLink],
  templateUrl: './order-form.component.html'
})
export class OrderFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(OrderService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  form = this.fb.group({
    orderDate: ['', Validators.required],
    requiredDate: ['', Validators.required],
    shippedDate: [''],
    status: ['', Validators.required],
    comments: [''],
    customerNumber: [null as number | null, Validators.required],
  });

  isEdit = false;
  id?: number;

  ngOnInit() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.isEdit = true;
      this.id = Number(idParam);
      this.service.get(this.id).subscribe(data => this.form.patchValue(data));
    }
  }

  save() {
    if (this.form.invalid) return;
    const value = this.form.value as Order;
    const obs = this.isEdit ? this.service.update(this.id!, value) : this.service.create(value);
    obs.subscribe(() => this.router.navigate(['../'], { relativeTo: this.route }));
  }
}