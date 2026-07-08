import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let httpMock: HttpTestingController;
  let http: HttpClient;
  let navigate: jest.SpyInstance;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    http = TestBed.inject(HttpClient);
    navigate = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => httpMock.verify());

  it('ante 401 en un endpoint protegido redirige a /login con el returnUrl de la página actual', (done) => {
    http.get('/api/entrenadores').subscribe({
      error: () => {
        expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/' } });
        done();
      },
    });
    httpMock.expectOne('/api/entrenadores').flush(null, { status: 401, statusText: 'Unauthorized' });
  });

  it('ante 401 en el login (/api/sesion) no redirige — es un error de credenciales', (done) => {
    http.post('/api/sesion', {}).subscribe({
      error: () => {
        expect(navigate).not.toHaveBeenCalled();
        done();
      },
    });
    httpMock.expectOne('/api/sesion').flush(null, { status: 401, statusText: 'Unauthorized' });
  });

  it('ante 401 en /api/sesion/actual no redirige — ya lo maneja authGuard con el returnUrl correcto', (done) => {
    http.get('/api/sesion/actual').subscribe({
      error: () => {
        expect(navigate).not.toHaveBeenCalled();
        done();
      },
    });
    httpMock.expectOne('/api/sesion/actual').flush(null, { status: 401, statusText: 'Unauthorized' });
  });

  it('ante 401 en el cambio de contraseña caducada no redirige', (done) => {
    http.post('/api/sesion/contrasena', {}).subscribe({
      error: () => {
        expect(navigate).not.toHaveBeenCalled();
        done();
      },
    });
    httpMock.expectOne('/api/sesion/contrasena').flush(null, { status: 401, statusText: 'Unauthorized' });
  });

  it('ante 401 en el consumo de magic-link no redirige', (done) => {
    http.post('/api/sesion/magic-link/consumo', {}).subscribe({
      error: () => {
        expect(navigate).not.toHaveBeenCalled();
        done();
      },
    });
    httpMock.expectOne('/api/sesion/magic-link/consumo').flush(null, { status: 401, statusText: 'Unauthorized' });
  });

  it('ante 401 en el consumo de reseteo no redirige', (done) => {
    http.post('/api/sesion/reseteo/consumo', {}).subscribe({
      error: () => {
        expect(navigate).not.toHaveBeenCalled();
        done();
      },
    });
    httpMock.expectOne('/api/sesion/reseteo/consumo').flush(null, { status: 401, statusText: 'Unauthorized' });
  });

  it('ante un status distinto de 401 no redirige', (done) => {
    http.get('/api/entrenadores').subscribe({
      error: () => {
        expect(navigate).not.toHaveBeenCalled();
        done();
      },
    });
    httpMock.expectOne('/api/entrenadores').flush(null, { status: 403, statusText: 'Forbidden' });
  });
});
