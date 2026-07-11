import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { MagicLinkConsumeComponent } from './magic-link-consume.component';
import { SessionService } from '../../../core/session.service';

describe('MagicLinkConsumeComponent', () => {
  let fixture: ComponentFixture<MagicLinkConsumeComponent>;
  let component: MagicLinkConsumeComponent;
  let navigate: jest.SpyInstance;
  const sessionMock = { consumeMagicLink: jest.fn() };

  async function createComponent(token: string | null): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [MagicLinkConsumeComponent],
      providers: [
        provideRouter([]),
        { provide: SessionService, useValue: sessionMock },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: { get: () => token } } },
        },
      ],
    }).compileComponents();
    navigate = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(MagicLinkConsumeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => jest.clearAllMocks());

  it('con un token válido consume y entra', async () => {
    sessionMock.consumeMagicLink.mockReturnValue(of({ userId: 'u', clubId: 'c', role: 'ALUMNO' }));
    await createComponent('token-xyz');

    expect(sessionMock.consumeMagicLink).toHaveBeenCalledWith('token-xyz');
    expect(navigate).toHaveBeenCalledWith(['/']);
    expect(component.failed()).toBe(false);
  });

  it('sin token marca el enlace como caducado y no consume', async () => {
    await createComponent(null);

    expect(sessionMock.consumeMagicLink).not.toHaveBeenCalled();
    expect(component.failed()).toBe(true);
  });

  it('si el backend rechaza el token (caducado o usado) muestra el estado caducado', async () => {
    sessionMock.consumeMagicLink.mockReturnValue(throwError(() => new Error('410')));
    await createComponent('token-caducado');

    expect(component.failed()).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });
});
