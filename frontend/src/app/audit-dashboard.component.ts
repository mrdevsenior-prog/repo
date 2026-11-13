import { DecimalPipe } from '@angular/common';
import { Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuditAnalytics, CountBucket, downloadJson } from './audit-api.service';
import { IconComponent } from './icon.component';

@Component({
  selector: 'app-audit-dashboard',
  standalone: true,
  imports: [DecimalPipe, MatButtonModule, MatTooltipModule, IconComponent],
  templateUrl: './audit-dashboard.component.html',
  styleUrl: './audit-dashboard.component.scss',
})
export class AuditDashboardComponent {
  readonly data = input<AuditAnalytics | null>(null);
  readonly loading = input(false);
  readonly error = input('');
  readonly applicationSelected = output<string>();
  readonly prioritySelected = output<string>();
  readonly daySelected = output<string>();
  readonly retry = output<void>();
  readonly palette = ['#399c80', '#6a9dbb', '#d2a464', '#8c9ebc', '#8ab8a2', '#a7b7c5'];
  readonly daily = computed<CountBucket[]>(() => {
    const data = this.data();
    if (!data) return [];
    const start = Date.parse(data.rangeStart + 'T12:00:00Z');
    const end = Date.parse(data.rangeEnd + 'T12:00:00Z');
    const days = Math.round((end - start) / 86_400_000) + 1;
    if (days < 1 || days > 90) return data.daily;
    const counts = new Map(data.daily.map(bucket => [bucket.key, bucket.count]));
    return Array.from({ length: days }, (_, index) => {
      const key = new Date(start + index * 86_400_000).toISOString().slice(0, 10);
      return { key, count: counts.get(key) || 0 };
    });
  });
  readonly axisMax = computed(() => this.niceMax(this.daily()));
  readonly ticks = computed(() => Array.from({ length: 5 }, (_, index) => ({ value: this.axisMax() * (4 - index) / 4, position: index * 25 })));
  readonly hours = computed(() => {
    const counts = new Map((this.data()?.hours || []).map(bucket => [bucket.key, bucket.count]));
    return Array.from({ length: 24 }, (_, hour) => ({ key: String(hour).padStart(2, '0'), count: counts.get(String(hour).padStart(2, '0')) || 0 }));
  });
  readonly peak = computed(() => this.hours().reduce((peak, hour) => hour.count > peak.count ? hour : peak, { key: '00', count: 0 }));
  readonly segments = computed(() => {
    const total = this.data()?.summary.total || 0;
    let offset = 0;
    return (this.data()?.priorities || []).map((bucket, index) => {
      const percentage = total ? bucket.count / total * 100 : 0;
      const result = { ...bucket, percentage, offset, color: this.priorityColor(bucket.key, index), length: percentage / 100 * 339.292 };
      offset += percentage / 100 * 339.292;
      return result;
    });
  });

  maximum(buckets: CountBucket[]): number { return Math.max(1, ...buckets.map(bucket => bucket.count)); }
  share(count: number): number { return this.data()?.summary.total ? count / this.data()!.summary.total * 100 : 0; }
  dateLabel(day: string): string { return new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: 'short', timeZone: 'UTC' }).format(new Date(day + 'T12:00:00Z')); }
  priorityLabel(key: string): string { return ({ HIGH: 'Alta', CRITICAL: 'Crítica', NORMAL: 'Normal', MEDIUM: 'Media', LOW: 'Baja', INFO: 'Informativa' } as Record<string, string>)[key] || key; }
  actionLabel(key: string): string { return ({ EXECUTE: 'Ejecución', READ: 'Consulta', CREATE: 'Creación', UPDATE: 'Actualización', DELETE: 'Eliminación', LOGIN: 'Inicio de sesión' } as Record<string, string>)[key] || key; }
  export(): void { if (this.data()) downloadJson(this.data(), 'audit-dashboard-estadisticas.json'); }

  private priorityColor(key: string, index: number): string {
    return ({ HIGH: '#d4a265', CRITICAL: '#c57370', NORMAL: '#399c80', MEDIUM: '#9f9cc2', LOW: '#8bafc7', INFO: '#6d9fb4' } as Record<string, string>)[key] || this.palette[index % this.palette.length];
  }

  private niceMax(buckets: CountBucket[]): number {
    const raw = this.maximum(buckets) / 4;
    const magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
    const ratio = raw / magnitude;
    const step = (ratio <= 1 ? 1 : ratio <= 2 ? 2 : ratio <= 5 ? 5 : 10) * magnitude;
    return Math.max(4, step * 4);
  }
}
