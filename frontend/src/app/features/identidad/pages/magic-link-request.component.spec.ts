import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { MagicLinkRequestComponent } from './magic-link-request.component';
import { SessionService } from '../../../core/session.service';

describe('MagicLinkRequestComponent', () => {
  let fixture: ComponentFixture<MagicLinkRequestComponent>;
  let component: MagicLinkRequestComponent;
  const sessionMock = { requestMagicLink: jest.fn() };

  beforeEach(async () => {
    jest.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [MagicLinkRequestComponent],
      providers: [
        provideRouter([]),
        { provide: SessionService, useValue: sessionMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(MagicLinkRequestComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('no llama al backend si el email es inválido', () => {
    component.form.setValue({ email: 'no-es-email' });
    component.submit();
    expect(sessionMock.requestMagicLink).not.toHaveBeenCalled();
  });

  it('con un email válido pide el enlace y muestra "revisa tu email"', () => {
    sessionMock.requestMagicLink.mockReturnValue(of(undefined));
    component.form.setValue({ email: 'ana@club.local' });
    component.submit();
    expect(sessionMock.requestMagicLink).toHaveBeenCalledWith('ana@club.local');
    expect(component.sent()).toBe(true);
  });

  it('ante un error muestra un mensaje y no marca enviado', () => {
    sessionMock.requestMagicLink.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );
    component.form.setValue({ email: 'ana@club.local' });
    component.submit();
    expect(component.sent()).toBe(false);
    expect(component.errorMessage()).not.toBeNull();
  });
});
