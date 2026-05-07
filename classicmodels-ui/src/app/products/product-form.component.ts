import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { ProductService, Product } from './product.service';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, RouterLink],
  templateUrl: './product-form.component.html'
})
export class ProductFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ProductService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  form = this.fb.group({
    productCode: ['', Validators.required],
    productName: ['', Validators.required],
    productLine: ['', Validators.required],
    productScale: ['', Validators.required],
    productVendor: ['', Validators.required],
    productDescription: ['', Validators.required],
    quantityInStock: [null as number | null, Validators.required],
    buyPrice: [null as number | null, Validators.required],
    MSRP: [null as number | null, Validators.required],
  });

  isEdit = false;
  id?: string;

  ngOnInit() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.isEdit = true;
      this.id = idParam;
      this.service.get(this.id).subscribe(data => this.form.patchValue(data));
      this.form.controls.productCode.disable();
    }
  }

  save() {
    if (this.form.invalid) return;
    const value = this.form.getRawValue() as Product;
    const obs = this.isEdit ? this.service.update(this.id!, value) : this.service.create(value);
    obs.subscribe(() => this.router.navigate(['../'], { relativeTo: this.route }));
  }
}