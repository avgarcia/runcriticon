import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { Observable, of, throwError } from 'rxjs';
import { ConsentService, MyConsent } from '../../../core/consent.service';
import { ToastService } from '../../../core/toast.service';
import { MyAccountComponent } from './my-account.component';

describe('MyAccountComponent', () => {
  const consentServiceMock = { getMyConsent: jest.fn(), grant: jest.fn(), revoke: jest.fn() };
  const dialogServiceMock = { open: jest.fn() };
  const toastServiceMock = { success: jest.fn(), error: jest.fn() };

  let fixture: ComponentFixture<MyAccountComponent>;
  let component: MyAccountComponent;

  async function crear(getMyConsentReturn: Observable<MyConsent> = of({ estado: 'PENDIENTE' })) {
    jest.clearAllMocks();
    consentServiceMock.getMyConsent.mockReturnValue(getMyConsentReturn);
    dialogServiceMock.open.mockReturnValue({ closed$: of(false) });

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [MyAccountComponent],
      providers: [
        { provide: ConsentService, useValue: consentServiceMock },
        { provide: HlmDialogService, useValue: dialogServiceMock },
        { provide: ToastService, useValue: toastServiceMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(MyAccountComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('carga el estado al entrar', async () => {
    await crear();

    expect(consentServiceMock.getMyConsent).toHaveBeenCalled();
  });

  it('PENDIENTE muestra el aviso y el boton de dar consentimiento', async () => {
    await crear(of({ estado: 'PENDIENTE' }));

    expect(fixture.nativeElement.textContent).toContain('Todavía no has dado tu consentimiento');
    expect(fixture.nativeElement.textContent).toContain('Dar mi consentimiento');
  });

  it('VIGENTE muestra la fecha de concesion y el boton de revocar', async () => {
    await crear(of({ estado: 'VIGENTE', versionTexto: 'v2026-08-25', concedidoEn: '2026-08-20T10:00:00Z' }));

    expect(fixture.nativeElement.textContent).toContain('Vigente');
    expect(fixture.nativeElement.textContent).toContain('20/08/2026');
    expect(fixture.nativeElement.textContent).toContain('Revocar consentimiento');
  });

  it('REVOCADO muestra la fecha de revocacion y el boton de volver a conceder', async () => {
    await crear(of({ estado: 'REVOCADO', concedidoEn: '2026-08-01T10:00:00Z', revocadoEn: '2026-08-20T10:00:00Z' }));

    expect(fixture.nativeElement.textContent).toContain('Revocado');
    expect(fixture.nativeElement.textContent).toContain('20/08/2026');
    expect(fixture.nativeElement.textContent).toContain('Volver a dar mi consentimiento');
  });

  it('si la carga falla ofrece reintentar', async () => {
    await crear(throwError(() => new Error('boom')) as Observable<never>);

    expect(component.loadFailed()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Reintentar');
  });

  it('grant llama al servicio y actualiza el estado a VIGENTE', async () => {
    await crear(of({ estado: 'PENDIENTE' }));
    consentServiceMock.grant.mockReturnValue(of({ estado: 'VIGENTE', versionTexto: 'v2026-08-25' }));

    component.grant();

    expect(consentServiceMock.grant).toHaveBeenCalled();
    expect(component.consent()?.estado).toBe('VIGENTE');
    expect(toastServiceMock.success).toHaveBeenCalled();
  });

  it('confirmRevoke abre el dialogo y solo revoca si se confirma', async () => {
    await crear(of({ estado: 'VIGENTE' }));
    dialogServiceMock.open.mockReturnValue({ closed$: of(true) });
    consentServiceMock.revoke.mockReturnValue(of({ estado: 'REVOCADO' }));

    component.confirmRevoke();

    expect(dialogServiceMock.open).toHaveBeenCalled();
    expect(consentServiceMock.revoke).toHaveBeenCalled();
    expect(component.consent()?.estado).toBe('REVOCADO');
  });

  it('cancelar el dialogo de revocar no llama al servicio', async () => {
    await crear(of({ estado: 'VIGENTE' }));
    dialogServiceMock.open.mockReturnValue({ closed$: of(false) });

    component.confirmRevoke();

    expect(consentServiceMock.revoke).not.toHaveBeenCalled();
  });
});
