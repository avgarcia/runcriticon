import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { HomeComponent } from './home.component';
import { Session, SessionService } from '../../../core/session.service';

// El cierre de sesión ya no vive aquí: subió al shell con la cabecera. Su caso está en
// `shared/layout/app-shell.component.spec.ts`.
describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;
  const sessionMock = {
    session: signal<Session | null>({ userId: 'u-1', clubId: 'c-1', role: 'ADMIN' }),
  };

  beforeEach(async () => {
    jest.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [{ provide: SessionService, useValue: sessionMock }],
    }).compileComponents();
    fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
  });

  it('se crea', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('muestra el rol de la sesión cargada', () => {
    expect(fixture.nativeElement.textContent).toContain('ADMIN');
  });
});
