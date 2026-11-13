import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subject, catchError, of, switchMap, tap } from 'rxjs';
import { AuditApiService, AuditAnalytics, AuditCatalogs, AuditEvent, AuditPage, AuditSummary, SearchQuery, downloadJson, errorMessage } from './audit-api.service';
import { EventDetailComponent } from './event-detail.component';
import { IconComponent } from './icon.component';
import { AuditDashboardComponent } from './audit-dashboard.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule, MatButtonModule, MatFormFieldModule,
    MatInputModule, MatPaginatorModule, MatProgressBarModule, MatSelectModule, MatSortModule,
    MatTableModule, MatTooltipModule, IconComponent, AuditDashboardComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  private readonly api = inject(AuditApiService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  private readonly requests = new Subject<SearchQuery>();
  readonly loading = signal(true);
  readonly page = signal<AuditPage | null>(null);
  readonly summary = signal<AuditSummary | null>(null);
  readonly analytics = signal<AuditAnalytics | null>(null);
  readonly view = signal<'dashboard' | 'events'>('dashboard');
  readonly error = signal('');
  readonly status = signal<'checking' | 'up' | 'down'>('checking');
  readonly catalogs = signal<AuditCatalogs>({ applications: [], priorities: [] });
  readonly lastUpdated = signal<Date | null>(null);
  readonly showDates = signal(false);
  readonly showDashboardFilters = signal(false);
  readonly period = signal('all');
  readonly activeCount = signal(0);
  readonly columns = ['timestamp', 'event', 'account', 'application', 'priority', 'detail'];
  readonly form = new FormGroup({
    q: new FormControl('', { nonNullable: true }),
    application: new FormControl('', { nonNullable: true }),
    priority: new FormControl('', { nonNullable: true }),
    from: new FormControl('', { nonNullable: true }),
    to: new FormControl('', { nonNullable: true }),
  });
  pageIndex = 0;
  pageSize = 10;
  sort = 'timestamp';
  direction: 'asc' | 'desc' = 'desc';

  constructor() {
    this.requests.pipe(
      tap(() => { this.loading.set(true); this.error.set(''); }),
      switchMap(query => this.api.search(query, this.view() === 'dashboard').pipe(
        tap(result => {
          this.page.set(result.page);
          this.summary.set(result.summary);
          this.analytics.set(result.analytics);
          this.status.set('up');
          this.lastUpdated.set(new Date());
        }),
        catchError(error => {
          this.error.set(errorMessage(error));
          this.page.set(null);
          this.summary.set(null);
          this.analytics.set(null);
          if (error instanceof HttpErrorResponse && (error.status === 503 || error.status === 0)) this.status.set('down');
          return of(null);
        }),
      )),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => this.loading.set(false));
    this.restoreFilters();
    this.checkConnection();
    this.load();
  }

  submit(): void { this.pageIndex = 0; this.load(); }

  navigate(view: 'dashboard' | 'events'): void {
    this.view.set(view);
    this.pageIndex = 0;
    if (view === 'dashboard') { this.sort = 'timestamp'; this.direction = 'desc'; }
    this.load();
  }

  filterApplication(application: string): void { this.form.controls.application.setValue(application); this.submit(); }
  filterPriority(priority: string): void { this.form.controls.priority.setValue(priority); this.submit(); }

  filterDay(day: string): void {
    const next = new Date(day + 'T00:00:00Z');
    next.setUTCDate(next.getUTCDate() + 1);
    this.form.patchValue({ from: day + 'T00:00', to: next.toISOString().slice(0, 16) });
    this.period.set('custom');
    this.showDates.set(true);
    this.submit();
  }

  refresh(): void { this.checkConnection(); this.load(); }

  clear(): void {
    this.form.reset();
    this.showDates.set(false);
    this.period.set('all');
    this.pageIndex = 0;
    this.load();
  }

  setPeriod(value: string): void {
    this.showDates.set(false);
    this.periodValues(value);
    this.submit();
  }

  private periodValues(value: string): void {
    this.period.set(value);
    if (value === 'all') {
      this.form.patchValue({ from: '', to: '' });
    } else {
      const limaDay = new Date(Date.now() - 5 * 3_600_000).toISOString().slice(0, 10);
      const end = new Date(`${limaDay}T00:00:00Z`);
      end.setUTCDate(end.getUTCDate() + 1);
      const start = new Date(`${limaDay}T00:00:00Z`);
      start.setUTCDate(start.getUTCDate() - (value === 'today' ? 0 : Number(value) - 1));
      this.form.patchValue({ from: start.toISOString().slice(0, 16), to: end.toISOString().slice(0, 16) });
    }
  }

  customDate(): void { this.period.set('custom'); }

  changePage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.load();
  }

  changeSort(event: Sort): void {
    this.sort = event.active;
    this.direction = event.direction || 'desc';
    this.submit();
  }

  open(event: AuditEvent): void {
    if (!event.uid) return;
    this.dialog.open(EventDetailComponent, {
      data: { uid: event.uid }, width: '720px', maxWidth: 'calc(100vw - 24px)',
      maxHeight: '92vh', autoFocus: 'first-tabbable',
    });
  }

  onRowKeydown(key: KeyboardEvent, event: AuditEvent): void {
    if (key.target === key.currentTarget && (key.key === 'Enter' || key.key === ' ')) {
      key.preventDefault();
      this.open(event);
    }
  }

  exportPage(): void {
    const page = this.page();
    if (page?.items.length) downloadJson(page, `audit-eventos-pagina-${page.page + 1}.json`);
  }

  priorityLabel(value: string): string {
    return ({ HIGH: 'Alta', CRITICAL: 'Crítica', MEDIUM: 'Media', NORMAL: 'Normal', LOW: 'Baja', INFO: 'Informativa' } as Record<string, string>)[value] || value || 'Sin prioridad';
  }

  private load(): void {
    const values = this.form.getRawValue();
    const from = values.from ? new Date(`${values.from}-05:00`) : null;
    const to = values.to ? new Date(`${values.to}-05:00`) : null;
    if ((from && Number.isNaN(from.getTime())) || (to && Number.isNaN(to.getTime())) || (from && to && from >= to)) {
      this.error.set('Revisa las fechas: desde debe ser anterior a hasta.');
      this.loading.set(false);
      return;
    }
    const params = new URLSearchParams({ view: this.view() });
    Object.entries(values).forEach(([key, value]) => { if (value.trim()) params.set(key, value.trim()); });
    history.replaceState(null, '', `${location.pathname}${params.size ? '?' + params.toString() : ''}`);
    this.activeCount.set(Object.values(values).filter(value => value.trim()).length);
    this.requests.next({ ...values, q: values.q.trim(), from: from?.toISOString() || '',
      to: to?.toISOString() || '', page: this.pageIndex, size: this.pageSize,
      sort: this.sort, direction: this.direction });
  }

  private restoreFilters(): void {
    const params = new URLSearchParams(location.search);
    this.view.set(params.get('view') === 'events' ? 'events' : 'dashboard');
    this.form.patchValue({ q: params.get('q') || '', application: params.get('application') || '',
      priority: params.get('priority') || '', from: params.get('from') || '', to: params.get('to') || '' });
    if (params.has('from') || params.has('to')) { this.period.set('custom'); this.showDates.set(true); }
    else if (this.view() === 'dashboard' && !['q', 'application', 'priority'].some(key => params.has(key))) this.periodValues('7');
  }

  private checkConnection(): void {
    this.api.health().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.status.set('up'), error: () => this.status.set('down'),
    });
    this.api.catalogs().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: catalogs => this.catalogs.set(catalogs), error: () => this.catalogs.set({ applications: [], priorities: [] }),
    });
  }
}
