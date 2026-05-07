import { Routes } from '@angular/router';
import { OfficeListComponent } from './office-list.component';
import { OfficeDetailComponent } from './office-detail.component';
import { OfficeFormComponent } from './office-form.component';

export default [
  { path: '', component: OfficeListComponent },
  { path: 'new', component: OfficeFormComponent },
  { path: ':id', component: OfficeDetailComponent },
  { path: ':id/edit', component: OfficeFormComponent }
] as Routes;