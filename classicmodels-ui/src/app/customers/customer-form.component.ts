import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { CustomerService } from './customer.service';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';

@Component({
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    RouterLink
  ],
  templateUrl: './customer-form.component.html'
})
export class CustomerFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CustomerService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  form = this.fb.group(
    {
      customerNumber: [null as number | null, Validators.required],
      customerName: ['', [Validators.required]],
      contactLastName: ['', [Validators.required]],
      contactFirstName: ['', [Validators.required]],
      phone: ['', Validators.required],
      addressLine1: ['', [Validators.required]],
      addressLine2: [''],
      city: ['', [Validators.required]],
      state: [''],
      postalCode: [''],
      country: ['', [Validators.required]],
      salesRepEmployeeNumber: [null as number | null],
      creditLimit: [null as number | null]
    }
  );

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
    const rawValue = this.form.getRawValue();
    const value = {
      ...rawValue,
      addressLine2: rawValue.addressLine2 || undefined,
      state: rawValue.state || undefined,
      postalCode: rawValue.postalCode || undefined,
      salesRepEmployeeNumber: rawValue.salesRepEmployeeNumber ?? undefined,
      creditLimit: rawValue.creditLimit ?? undefined
    } as any;

    /*
    * if edit mode then update existing item otherwise create a new item and return observable (stream representing async operation)
    * */
    const obs = this.isEdit ? this.service.update(this.id!, value) : this.service.create(value);
    /*
    * observables are lazy so subscribe() kicks off async operation and runs callback when it completes
    * */
    obs?.subscribe(() => this.router.navigate(['../'], { relativeTo: this.route }));
  }
}
