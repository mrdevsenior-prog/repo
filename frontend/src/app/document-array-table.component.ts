import { Component, computed, input } from '@angular/core';
import { MatTableModule } from '@angular/material/table';

interface ArrayColumn { id: string; key: string | null; label: string }

@Component({
  selector: 'app-document-array-table',
  standalone: true,
  imports: [MatTableModule],
  template: `
    @if (rows().length) {
      <div class="array-table-scroll" tabindex="0" [attr.aria-label]="'Desplazar tabla: ' + label()">
        <table mat-table [dataSource]="rows()" [attr.aria-label]="label()">
          <ng-container matColumnDef="position"><th mat-header-cell *matHeaderCellDef scope="col">#</th><td mat-cell *matCellDef="let row; let index = index" class="position">{{ index + 1 }}</td></ng-container>
          @for (column of columns(); track column.id) {
            <ng-container [matColumnDef]="column.id"><th mat-header-cell *matHeaderCellDef scope="col">{{ column.label }}</th><td mat-cell *matCellDef="let row"><span class="cell-value" [class.field-name]="column.key === 'key'">{{ cell(row, column) }}</span></td></ng-container>
          }
          <tr mat-header-row *matHeaderRowDef="columnIds(); sticky: true"></tr>
          <tr mat-row *matRowDef="let row; columns: columnIds()"></tr>
        </table>
      </div>
    } @else {
      <div class="empty-list" role="status">Esta lista no contiene elementos.</div>
    }
  `,
  styles: [`
    :host{display:block;min-width:0}.array-table-scroll{max-height:235px;overflow:auto;border:1px solid #e2e9ee;border-radius:6px;background:#fff}table{width:100%;min-width:330px}th.mat-mdc-header-cell{height:34px;font-size:10px;font-weight:600;color:#6b7c8d;background:#f4f7f9;border-bottom-color:#e1e8ed;padding:0 12px;white-space:nowrap}td.mat-mdc-cell{padding:9px 12px;font-size:12px;color:#435a6e;border-bottom-color:#edf1f4;vertical-align:top;height:35px}th.mat-mdc-header-cell:first-child,td.mat-mdc-cell:first-child{width:35px;padding-left:12px;padding-right:6px}.position{color:#9ba7b2!important;font-size:10px!important;font-variant-numeric:tabular-nums}.cell-value{display:block;white-space:pre-wrap;overflow-wrap:anywhere;min-width:95px;max-width:420px;line-height:1.5}.field-name{color:#6a8193;font-family:'Cascadia Code',Consolas,monospace;font-size:10px;min-width:135px}.empty-list{padding:18px;text-align:center;border:1px dashed #dfe7e3;border-radius:6px;color:#92a095;font-size:11px}tr.mat-mdc-row:last-child td{border-bottom:0}tr.mat-mdc-row:nth-child(even){background:#fbfcfd}
  `],
})
export class DocumentArrayTableComponent {
  readonly rows = input.required<unknown[]>();
  readonly label = input('Lista de detalles');
  readonly columns = computed<ArrayColumn[]>(() => {
    const keys = new Set<string>();
    let hasValues = false;
    this.rows().forEach(row => {
      if (this.isRecord(row) && Object.keys(row).length) Object.keys(row).forEach(key => keys.add(key));
      else hasValues = true;
    });
    const columns: ArrayColumn[] = [...keys].map((key, index) => ({
      id: `field-${index}`, key, label: key === 'key' ? 'Campo' : key === 'value' ? 'Valor' : key,
    }));
    if (hasValues) columns.push({ id: 'item-value', key: null, label: keys.has('value') ? 'Elemento' : 'Valor' });
    return columns;
  });
  readonly columnIds = computed(() => ['position', ...this.columns().map(column => column.id)]);

  cell(row: unknown, column: ArrayColumn): string {
    if (column.key === null) return this.isRecord(row) && Object.keys(row).length ? '—' : this.display(row);
    return this.isRecord(row) && Object.hasOwn(row, column.key) ? this.display(row[column.key]) : '—';
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return value !== null && typeof value === 'object' && !Array.isArray(value);
  }

  private display(value: unknown): string {
    if (value === null) return 'null';
    if (value === '') return '""';
    return typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value);
  }
}
