import { Routes } from '@angular/router';
import { CustomerListComponent } from './customer-list.component';
import { CustomerDetailComponent } from './customer-detail.component';
import { CustomerFormComponent } from './customer-form.component';
import { CustomerLifetimeValueComponent } from './customer-lifetime-value.component';
import { CustomerMapComponent } from './customer-map.component';
import { CustomerActivityComponent } from './customer-activity.component';
import { CustomerMergeComponent } from './customer-merge.component';
import { CustomerCreditAlertsComponent } from './customer-credit-alerts.component';

export default [
  { path: '', component: CustomerListComponent },
  { path: 'new', component: CustomerFormComponent },
  // Literal segment, must come BEFORE the bare ':id' route — otherwise
  // Angular's router would match 'map' as an id and fail to load the
  // detail page. Same lesson as the C6 backend route-ordering bug.
  { path: 'map', component: CustomerMapComponent },
  { path: 'credit-alerts', component: CustomerCreditAlertsComponent },
  // Two-segment ':id/<verb>' routes are matched precisely by Angular's
  // router; they don't collide with the bare ':id' detail route below.
  { path: ':id/lifetime-value', component: CustomerLifetimeValueComponent },
  { path: ':id/activity', component: CustomerActivityComponent },
  { path: ':id/merge', component: CustomerMergeComponent },
  { path: ':id/edit', component: CustomerFormComponent },
  { path: ':id', component: CustomerDetailComponent }
] as Routes;
