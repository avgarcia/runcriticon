import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  let httpMock: HttpTestingController;
  let http: HttpClient;
  const snackBarMock = { open: jest.fn() };

  beforeEach(() => {
    jest.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: snackBarMock },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    http = TestBed.inject(HttpClient);
  });

  afterEach(() => httpMock.verify());

  it('ante 403 muestra el toast de permiso y re-lanza el error', (done) => {
    http.get('/api/entrenadores').subscribe({
      error: (err: unknown) => {
        expect(err).toBeDefined();
        expect(snackBarMock.open).toHaveBeenCalledWith(
          'No tienes permiso para esta acción.',
          'Cerrar',
          { duration: 4000 },
        );
        done();
      },
    });
    httpMock.expectOne('/api/entrenadores').flush(null, { status: 403, statusText: 'Forbidden' });
  });

  it('ante 429 muestra el toast de rate-limit', (done) => {
    http.get('/api/entrenadores').subscribe({
      error: () => {
        expect(snackBarMock.open).toHaveBeenCalledWith(
          'Demasiados intentos. Espera unos segundos.',
          'Cerrar',
          { duration: 4000 },
        );
        done();
      },
    });
    httpMock.expectOne('/api/entrenadores').flush(null, { status: 429, statusText: 'Too Many Requests' });
  });

  it('ante 5xx muestra el toast genérico', (done) => {
    http.get('/api/entrenadores').subscribe({
      error: () => {
        expect(snackBarMock.open).toHaveBeenCalledWith(
          'Algo ha ido mal. Vuelve a intentarlo.',
          'Cerrar',
          { duration: 4000 },
        );
        done();
      },
    });
    httpMock.expectOne('/api/entrenadores').flush(null, { status: 503, statusText: 'Service Unavailable' });
  });

  it('ante un error de red (status 0) muestra el toast de sin conexión', (done) => {
    http.get('/api/entrenadores').subscribe({
      error: () => {
        expect(snackBarMock.open).toHaveBeenCalledWith('Sin conexión.', 'Cerrar', { duration: 4000 });
        done();
      },
    });
    httpMock.expectOne('/api/entrenadores').error(new ProgressEvent('error'), { status: 0 });
  });

  it('ante 400 no muestra ningún toast y re-lanza el error tal cual', (done) => {
    http.get('/api/entrenadores').subscribe({
      error: (err: unknown) => {
        expect(err).toBeDefined();
        expect(snackBarMock.open).not.toHaveBeenCalled();
        done();
      },
    });
    httpMock.expectOne('/api/entrenadores').flush(null, { status: 400, statusText: 'Bad Request' });
  });

  it('ante 409 no muestra ningún toast (lo maneja el caller, D19)', (done) => {
    http.get('/api/entrenadores').subscribe({
      error: () => {
        expect(snackBarMock.open).not.toHaveBeenCalled();
        done();
      },
    });
    httpMock.expectOne('/api/entrenadores').flush(null, { status: 409, statusText: 'Conflict' });
  });
});
