import { NgTemplateOutlet } from '@angular/common';
import { Component, input } from '@angular/core';
import { DocumentArrayTableComponent } from './document-array-table.component';

@Component({
  selector: 'app-document-tree',
  standalone: true,
  imports: [NgTemplateOutlet, DocumentArrayTableComponent],
  template: `
    <ng-template #branch let-node let-path="path">
      <ul class="document-fields">
        @for (entry of entries(node); track entry.key) {
          <li>
            @if (isArray(entry.value)) {
              <details open><summary [title]="path + entry.key"><code>{{ entry.key }}</code><span class="kind">{{ kind(entry.value) }}</span></summary><app-document-array-table [rows]="entry.value" [label]="path + entry.key" /></details>
            } @else if (isBranch(entry.value)) {
              <details open>
                <summary [title]="path + entry.key"><code>{{ entry.key }}</code><span class="kind">{{ kind(entry.value) }}</span></summary>
                <ng-container [ngTemplateOutlet]="branch" [ngTemplateOutletContext]="{ $implicit: entry.value, path: path + entry.key + '.' }" />
              </details>
            } @else {
              <div class="scalar"><code [title]="path + entry.key">{{ entry.key }}</code><span class="scalar-value" [class.null-value]="entry.value === null">{{ display(entry.value) }}</span></div>
            }
          </li>
        }
      </ul>
    </ng-template>
    <ng-container [ngTemplateOutlet]="branch" [ngTemplateOutletContext]="{ $implicit: value(), path: '' }" />
  `,
  styles: [`
    :host{display:block}.document-fields{list-style:none;padding:0;margin:0}.document-fields>li{border-bottom:1px solid #edf1f4}.document-fields>li:last-child{border-bottom:0}.document-fields .document-fields{margin:0 0 9px 13px;border-left:2px solid #e3eee8;padding-left:12px}summary{display:flex;align-items:center;gap:10px;cursor:pointer;padding:11px 8px;color:#526e64;border-radius:4px;list-style:none}summary::before{content:'▸';font-size:12px;width:9px;flex-shrink:0}details[open]>summary::before{content:'▾'}summary:hover{background:#f2f8f5}code{font-family:'Cascadia Code',Consolas,monospace;font-size:11px;overflow-wrap:anywhere}.kind{margin-left:auto;flex-shrink:0;font-size:9px;color:#879c90;font-family:'Segoe UI',sans-serif}.scalar{display:grid;grid-template-columns:minmax(130px,1fr) minmax(0,1.25fr);gap:16px;padding:10px 8px}.scalar code{color:#788b9d;font-size:10px}.scalar-value{font-size:12px;color:#40576c;overflow-wrap:anywhere;white-space:pre-wrap}.null-value{color:#9aa5b1;font-family:Consolas,monospace;font-size:11px}@media(max-width:560px){.scalar{grid-template-columns:1fr;gap:6px;padding:10px 5px}.document-fields .document-fields{margin-left:5px;padding-left:8px}.kind{font-size:8px}code{font-size:10px}}
  `],
})
export class DocumentTreeComponent {
  readonly value = input.required<unknown>();

  entries(node: unknown): { key: string; value: unknown }[] {
    if (Array.isArray(node)) return node.map((value, index) => ({ key: `[${index}]`, value }));
    return node && typeof node === 'object' ? Object.entries(node).map(([key, value]) => ({ key, value })) : [];
  }

  isBranch(value: unknown): boolean {
    return value !== null && typeof value === 'object' && Object.keys(value).length > 0;
  }

  isArray(value: unknown): value is unknown[] { return Array.isArray(value); }

  kind(value: unknown): string {
    return Array.isArray(value) ? `Arreglo · ${value.length} elementos` : `Objeto · ${Object.keys(value as object).length} campos`;
  }

  display(value: unknown): string {
    if (value === null) return 'null';
    if (value === '') return '""';
    if (typeof value === 'object') return JSON.stringify(value);
    return String(value);
  }
}
