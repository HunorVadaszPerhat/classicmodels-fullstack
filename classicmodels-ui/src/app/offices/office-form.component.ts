import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { OfficeService, Office } from './office.service';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, RouterLink],
  templateUrl: './office-form.component.html'
})
export class OfficeFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(OfficeService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  form = this.fb.group({
    officeCode: ['', Validators.required],
    city: ['', Validators.required],
    phone: ['', Validators.required],
    addressLine1: ['', Validators.required],
    addressLine2: [''],
    state: [''],
    country: ['', Validators.required],
    postalCode: ['', Validators.required],
    territory: ['', Validators.required],
  });

  isEdit = false;
  id?: string;

  ngOnInit() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.isEdit = true;
      this.id = idParam;
      this.service.get(this.id).subscribe(data => this.form.patchValue(data));
      this.form.controls.officeCode.disable();
    }
  }

  save() {
    if (this.form.invalid) return;
    const value = this.form.getRawValue() as Office;
    const obs = this.isEdit ? this.service.update(this.id!, value) : this.service.create(value);
    obs.subscribe(() => this.router.navigate(['../'], { relativeTo: this.route }));
  }
}