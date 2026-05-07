import { Routes } from '@angular/router';
import { PaymentListComponent } from './payment-list.component';
import { PaymentDetailComponent } from './payment-detail.component';
import { PaymentFormComponent } from './payment-form.component';

export default [
  { path: '', component: PaymentListComponent },
  { path: 'new', component: PaymentFormComponent },
  { path: ':customerNumber/:checkNumber', component: PaymentDetailComponent },
  { path: ':customerNumber/:checkNumber/edit', component: PaymentFormComponent }
] as Routes;