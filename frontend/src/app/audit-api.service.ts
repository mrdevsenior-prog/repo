import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map } from 'rxjs';

export interface AuditEvent {
  uid: string;
  accountName: string;
  applicationCode: string;
  operationNumber: string;
  action: string;
  category: string;
  priority: string;
  eventType: string;
  eventTimestamp: string | null;
  description: string;
}

export interface AuditPage { items: AuditEvent[]; total: number; page: number; size: number }
export interface AuditSummary { total: number; applications: number; highPriority: number; today: number }
export interface AuditCatalogs { applications: string[]; priorities: string[] }
export interface CountBucket { key: string; count: number }
export interface AuditAnalytics {
  summary: AuditSummary;
  daily: CountBucket[];
  applications: CountBucket[];
  priorities: CountBucket[];
  actions: CountBucket[];
  hours: CountBucket[];
  undated: number;
  rangeStart: string;
  rangeEnd: string;
  timezone: string;
  generatedAt: string;
}
export interface SearchQuery {
  q: string;
  application: string;
  priority: string;
  from: string;
  to: string;
  page: number;
  size: number;
  sort: string;
  direction: 'asc' | 'desc';
}

@Injectable({ providedIn: 'root' })
export class AuditApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/audit';

  search(query: SearchQuery, withAnalytics = false): Observable<{ page: AuditPage; summary: AuditSummary; analytics: AuditAnalytics | null }> {
    let params = new HttpParams();
    Object.entries(query).forEach(([key, value]) => {
      if (value !== '') params = params.set(key, String(value));
    });
    const summaryParams = params.delete('page').delete('size').delete('sort').delete('direction');
    const page = this.http.get<AuditPage>(`${this.base}/events`, { params });
    if (withAnalytics) {
      return forkJoin({ page, analytics: this.http.get<AuditAnalytics>(`${this.base}/analytics`, { params: summaryParams }) })
        .pipe(map(result => ({ ...result, summary: result.analytics.summary })));
    }
    return forkJoin({ page, summary: this.http.get<AuditSummary>(`${this.base}/summary`, { params: summaryParams }) })
      .pipe(map(result => ({ ...result, analytics: null })));
  }

  catalogs() { return this.http.get<AuditCatalogs>(`${this.base}/catalogs`); }
  health() { return this.http.get<{ status: string }>(`${this.base}/health`); }
  detail(uid: string) { return this.http.get<Record<string, unknown>>(`${this.base}/events/${encodeURIComponent(uid)}`); }
}

export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (typeof error.error?.message === 'string') return error.error.message;
    if (error.status === 0) return 'No se pudo conectar con la aplicación. Comprueba que el servidor esté iniciado.';
    if (error.status === 404) return 'No se encontró el evento solicitado.';
    if (error.status === 503) return 'MongoDB no está disponible. Verifica la conexión e inténtalo nuevamente.';
  }
  return 'No se pudo completar la consulta. Inténtalo nuevamente.';
}

export function downloadJson(value: unknown, filename: string): void {
  const url = URL.createObjectURL(new Blob([JSON.stringify(value, null, 2)], { type: 'application/json;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
