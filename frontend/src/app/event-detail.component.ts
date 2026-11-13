import { DatePipe, JsonPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuditApiService, downloadJson, errorMessage } from './audit-api.service';
import { IconComponent } from './icon.component';
import { DocumentTreeComponent } from './document-tree.component';
import { DocumentArrayTableComponent } from './document-array-table.component';

interface DetailList { path: string; label: string; rows: unknown[] }

@Component({
  selector: 'app-event-detail',
  standalone: true,
  imports: [DatePipe, JsonPipe, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule,
    MatProgressBarModule, MatTabsModule, MatTooltipModule, IconComponent, DocumentTreeComponent,
    DocumentArrayTableComponent],
  templateUrl: './event-detail.component.html',
  styleUrl: './event-detail.component.scss',
})
export class EventDetailComponent {
  readonly data = inject<{ uid: string }>(MAT_DIALOG_DATA);
  private readonly api = inject(AuditApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly event = signal<Record<string, unknown> | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly copied = signal(false);
  readonly copyError = signal(false);
  readonly lists = computed<DetailList[]>(() => {
    const lists: DetailList[] = [];
    const visit = (node: Record<string, unknown>, prefix: string): void => {
      Object.entries(node).forEach(([key, value]) => {
        const path = prefix ? `${prefix}.${key}` : key;
        if (Array.isArray(value)) {
          lists.push({ path, label: key === 'channel_payload_detail' ? 'Detalle del canal' : key, rows: value });
        } else if (value !== null && typeof value === 'object') {
          visit(value as Record<string, unknown>, path);
        }
      });
    };
    const event = this.event();
    if (event) visit(event, '');
    return lists;
  });

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.detail(this.data.uid).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: event => { this.event.set(event); this.loading.set(false); },
      error: error => { this.error.set(errorMessage(error)); this.loading.set(false); },
    });
  }

  value(path: string): unknown {
    return path.split('.').reduce<unknown>((value, key) => value && typeof value === 'object'
      ? (value as Record<string, unknown>)[key] : undefined, this.event());
  }

  text(path: string): string {
    const value = this.value(path);
    return value === undefined || value === null || value === '' ? '—' : String(value);
  }

  dateValue(path: string): string | null {
    const value = this.value(path);
    return typeof value === 'string' && !Number.isNaN(Date.parse(value)) ? value : null;
  }

  async copyUid(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.data.uid);
      this.copied.set(true);
      this.copyError.set(false);
    } catch { this.copyError.set(true); }
  }

  download(): void {
    if (this.event()) downloadJson(this.event(), `audit-${this.data.uid.replace(/[^a-zA-Z0-9_-]/g, '_')}.json`);
  }
}
