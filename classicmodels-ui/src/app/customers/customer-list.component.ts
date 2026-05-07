import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CustomerService } from './customer.service';
import { Customer } from './customer.model';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule } from '@angular/material/sort';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

@Component({
  standalone: true,
  imports: [CommonModule, MatTableModule, MatPaginatorModule, MatSortModule, RouterLink, MatButtonModule],
  templateUrl: './customer-list.component.html'
})
export class CustomerListComponent implements OnInit {
  private readonly service = inject(CustomerService);

  private allCustomers = signal<Customer[]>([]);
  customers = signal<Customer[]>([]);
  total = signal(0);
  pageIndex = signal(0);
  pageSize = signal(10);

  loading = signal(false);
  error = signal<string | undefined>(undefined);
  displayedColumns = ['customerName', 'phone', 'city', 'actions'];

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.error.set(undefined);

    this.service.list().subscribe({
      next: data => {
        this.allCustomers.set(data);
        this.total.set(data.length);
        this.pageIndex.set(0);
        this.updatePagedCustomers();
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.message);
        this.loading.set(false);
      }
    });
  }

  onPageChange(event: PageEvent) {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.updatePagedCustomers();
  }

  remove(id: number) {
    this.service.delete(id).subscribe(() => this.load());
  }

  private updatePagedCustomers() {
    const start = this.pageIndex() * this.pageSize();
    const end = start + this.pageSize();
    this.customers.set(this.allCustomers().slice(start, end));
  }
}
