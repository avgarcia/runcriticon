import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CoachesService } from './coaches.service';

describe('CoachesService', () => {
  let service: CoachesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CoachesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('invite hace POST a /api/entrenadores con nombre y email', () => {
    const response = { id: 'abc-123' };
    service.invite('Ana García', 'ana@club.local').subscribe((res) => {
      expect(res).toEqual(response);
    });
    const req = http.expectOne('/api/entrenadores');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ nombre: 'Ana García', email: 'ana@club.local' });
    req.flush(response);
  });

  it('ante 409 propaga el HttpErrorResponse', () => {
    let error: unknown;
    service.invite('Ana García', 'ana@club.local').subscribe({ error: (e) => (error = e) });
    http
      .expectOne('/api/entrenadores')
      .flush(
        { code: 'CONFLICT', field: null, message: 'Email ya registrado' },
        { status: 409, statusText: 'Conflict' },
      );
    expect((error as { status: number }).status).toBe(409);
  });
});
