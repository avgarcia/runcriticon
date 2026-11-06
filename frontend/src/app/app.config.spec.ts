import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { appConfig } from './app.config';

/**
 * El backend exige CSRF activado (ADR-0003): Spring emite la cookie `XSRF-TOKEN` y espera su valor en la
 * cabecera `X-XSRF-TOKEN` de cada petición que muta estado. Angular lo hace con `withXsrfConfiguration`; este
 * test fija esa configuración para que quitarla o renombrar la cookie rompa aquí y no en producción.
 */
describe('CSRF (appConfig)', () => {
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-prueba; path=/';
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideRouter([]), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    controller.verify();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  it('una petición que muta estado lleva el token de la cookie en X-XSRF-TOKEN', () => {
    http.post('/api/planes', {}).subscribe();
    const req = controller.expectOne('/api/planes');
    expect(req.request.headers.get('X-XSRF-TOKEN')).toBe('token-de-prueba');
    req.flush({});
  });

  it('una lectura no lleva la cabecera', () => {
    http.get('/api/planes').subscribe();
    const req = controller.expectOne('/api/planes');
    expect(req.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    req.flush([]);
  });
});
