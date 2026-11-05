import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { IdentidadService } from '../api/generated/services/identidad.service';
import { CONSENT_TEXT_VERSION, ConsentService } from './consent.service';

describe('ConsentService', () => {
  const vigente = { estado: 'VIGENTE' as const };
  const apiMock = {
    consultarMiConsentimiento: jest.fn(),
    concederMiConsentimiento: jest.fn(),
    revocarMiConsentimiento: jest.fn(),
  };
  let service: ConsentService;

  beforeEach(() => {
    jest.clearAllMocks();
    apiMock.consultarMiConsentimiento.mockResolvedValue(vigente);
    apiMock.concederMiConsentimiento.mockResolvedValue(vigente);
    apiMock.revocarMiConsentimiento.mockResolvedValue({ estado: 'REVOCADO' });

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [ConsentService, { provide: IdentidadService, useValue: apiMock }],
    });
    service = TestBed.inject(ConsentService);
  });

  it('al conceder envía la versión vigente del texto de consentimiento', async () => {
    await firstValueFrom(service.grant());

    expect(apiMock.concederMiConsentimiento).toHaveBeenCalledWith({
      body: { versionConsentimiento: CONSENT_TEXT_VERSION },
    });
  });

  it('la versión del texto es la que el backend publica en docs/legal', () => {
    expect(CONSENT_TEXT_VERSION).toBe('v2026-08-25');
  });

  it('consulta el propio estado sin enviar cuerpo', async () => {
    const estado = await firstValueFrom(service.getMyConsent());

    expect(estado).toEqual(vigente);
    expect(apiMock.consultarMiConsentimiento).toHaveBeenCalledWith();
  });

  it('al revocar devuelve el estado revocado', async () => {
    const estado = await firstValueFrom(service.revoke());

    expect(estado).toEqual({ estado: 'REVOCADO' });
    expect(apiMock.revocarMiConsentimiento).toHaveBeenCalledWith();
  });
});
