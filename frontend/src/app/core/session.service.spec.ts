import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SessionService } from './session.service';

describe('SessionService', () => {
  let service: SessionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SessionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('start hace POST a /api/sesion y guarda la sesión', () => {
    const session = { userId: 'u-1', clubId: 'c-1', role: 'ADMIN' };
    service.start('a@b.com', 'secreta').subscribe();
    const req = http.expectOne('/api/sesion');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'a@b.com', password: 'secreta' });
    req.flush(session);
    expect(service.session()).toEqual(session);
  });

  it('loadCurrent hace GET a /api/sesion/actual', () => {
    service.loadCurrent().subscribe();
    const req = http.expectOne('/api/sesion/actual');
    expect(req.request.method).toBe('GET');
    req.flush({ userId: 'u-1', clubId: 'c-1', role: 'ALUMNO' });
    expect(service.session()?.role).toBe('ALUMNO');
  });

  it('close hace POST a /api/sesion/cierre y limpia la sesión', () => {
    service.close().subscribe();
    const req = http.expectOne('/api/sesion/cierre');
    expect(req.request.method).toBe('POST');
    req.flush(null);
    expect(service.session()).toBeNull();
  });
});
