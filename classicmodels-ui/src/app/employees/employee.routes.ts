import { Routes } from '@angular/router';
import { EmployeeListComponent } from './employee-list.component';
import { EmployeeDetailComponent } from './employee-detail.component';
import { EmployeeFormComponent } from './employee-form.component';
import { OrgChartComponent } from './org-chart.component';

export default [
  { path: '', component: EmployeeListComponent },
  { path: 'new', component: EmployeeFormComponent },
  // Static segments must come BEFORE the :id route — Angular matches
  // routes top-to-bottom, and 'org-chart' would otherwise be captured
  // as an employee id and then 404 from the backend.
  { path: 'org-chart', component: OrgChartComponent },
  { path: ':id', component: EmployeeDetailComponent },
  { path: ':id/edit', component: EmployeeFormComponent }
] as Routes;