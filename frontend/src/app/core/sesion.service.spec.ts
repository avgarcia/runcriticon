import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SesionService } from './sesion.service';

describe('SesionService', () => {
  let service: SesionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SesionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('iniciar hace POST a /api/sesion y guarda la sesión', () => {
    const sesion = { userId: 'u-1', clubId: 'c-1', rol: 'ADMIN' };
    service.iniciar('a@b.com', 'secreta').subscribe();
    const req = http.expectOne('/api/sesion');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'a@b.com', password: 'secreta' });
    req.flush(sesion);
    expect(service.sesion()).toEqual(sesion);
  });

  it('cargarActual hace GET a /api/sesion/actual', () => {
    service.cargarActual().subscribe();
    const req = http.expectOne('/api/sesion/actual');
    expect(req.request.method).toBe('GET');
    req.flush({ userId: 'u-1', clubId: 'c-1', rol: 'ALUMNO' });
    expect(service.sesion()?.rol).toBe('ALUMNO');
  });

  it('cerrar hace POST a /api/sesion/cierre y limpia la sesión', () => {
    service.cerrar().subscribe();
    const req = http.expectOne('/api/sesion/cierre');
    expect(req.request.method).toBe('POST');
    req.flush(null);
    expect(service.sesion()).toBeNull();
  });
});
