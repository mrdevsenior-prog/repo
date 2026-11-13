import { MatPaginatorIntl } from '@angular/material/paginator';

export function spanishPaginator(): MatPaginatorIntl {
  const labels = new MatPaginatorIntl();
  labels.itemsPerPageLabel = 'Eventos por página:';
  labels.nextPageLabel = 'Página siguiente';
  labels.previousPageLabel = 'Página anterior';
  labels.firstPageLabel = 'Primera página';
  labels.lastPageLabel = 'Última página';
  labels.getRangeLabel = (page, size, total) => total === 0 ? '0 de 0' : `${page * size + 1}–${Math.min((page + 1) * size, total)} de ${total}`;
  return labels;
}
