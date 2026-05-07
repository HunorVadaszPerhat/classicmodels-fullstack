import { Routes } from '@angular/router';
import { OrderDetailListComponent } from './order-detail-list.component';
import { OrderDetailDetailComponent } from './order-detail-detail.component';
import { OrderDetailFormComponent } from './order-detail-form.component';

export default [
  { path: '', component: OrderDetailListComponent },
  { path: 'new', component: OrderDetailFormComponent },
  { path: ':orderNumber/:productCode', component: OrderDetailDetailComponent },
  { path: ':orderNumber/:productCode/edit', component: OrderDetailFormComponent }
] as Routes;