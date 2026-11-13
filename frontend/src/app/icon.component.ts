import { Component, input } from '@angular/core';

const paths: Record<string, string> = {
  shield: 'M12 3 4 6v6c0 5 8 9 8 9s8-4 8-9V6l-8-3ZM8 12l3 3 5-6',
  search: 'M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
  list: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
  grid: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z',
  chart: 'M3 3v18h18M7 17v-4M12 17V8M17 17V5',
  activity: 'M2 12h4l3-9 6 18 3-9h4',
  database: 'M20 6c0 2-3.6 3-8 3S4 8 4 6s3.6-3 8-3 8 1 8 3ZM4 6v12c0 2 3.6 3 8 3s8-1 8-3V6M4 12c0 2 3.6 3 8 3s8-1 8-3',
  refresh: 'M20 7v5h-5M4 17v-5h5M6.1 6.1A8 8 0 0 1 19 8l1 4M4 12l1 4a8 8 0 0 0 12.9 1.9',
  download: 'M12 3v12m-5-5 5 5 5-5M5 17v4h14v-4',
  arrow: 'M5 12h14m-5-5 5 5-5 5',
  chevron: 'm9 5 7 7-7 7',
  calendar: 'M4 5h16v16H4zM8 3v4M16 3v4M4 10h16M8 14h2M14 14h2M8 17h2',
  filter: 'M4 7h16M7 12h10M10 17h4',
  close: 'm6 6 12 12M6 18 18 6',
  clock: 'M12 8v5l3 2M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',
  alert: 'M12 8v5M12 17h.01M10 3 1 19h22L14 3z',
  copy: 'M9 9h12v12H9zM15 5V3H3v12h2',
  code: 'm8 7-5 5 5 5m8-10 5 5-5 5m-3-14-2 18',
  check: 'm5 12 4 4L19 6',
  file: 'M14 2H4v20h16V8l-6-6ZM14 2v6h6M8 13h8M8 17h6',
};

@Component({
  selector: 'app-icon',
  standalone: true,
  template: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="paths[name()] || paths[\'file\']" /></svg>',
  styles: [':host{display:inline-flex;width:20px;height:20px;flex-shrink:0;vertical-align:middle}svg{width:100%;height:100%}'],
})
export class IconComponent {
  readonly name = input('file');
  readonly paths = paths;
}
