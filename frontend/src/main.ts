import { registerLocaleData } from '@angular/common';
import localeEsPe from '@angular/common/locales/es-PE';
import { provideHttpClient } from '@angular/common/http';
import { LOCALE_ID } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { AppComponent } from './app/app.component';
import { spanishPaginator } from './app/paginator-es';

registerLocaleData(localeEsPe);
bootstrapApplication(AppComponent, {
  providers: [provideHttpClient(), { provide: LOCALE_ID, useValue: 'es-PE' },
    { provide: MatPaginatorIntl, useFactory: spanishPaginator }],
}).catch(error => console.error('No se pudo iniciar la aplicación.', error));
