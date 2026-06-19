import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { HomeComponent } from './home.component';
import { Session, SessionService } from '../core/session.service';

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;
  const sessionMock = {
    session: signal<Session | null>({ userId: 'u-1', clubId: 'c-1', role: 'ADMIN' }),
    close: jest.fn().mockReturnValue(of(undefined)),
  };
  const routerMock = { navigate: jest.fn() };

  beforeEach(async () => {
    jest.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        { provide: SessionService, useValue: sessionMock },
        { provide: Router, useValue: routerMock },
      ],
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

  it('al cerrar sesión llama al servicio y navega a /login', () => {
    fixture.componentInstance.close();
    expect(sessionMock.close).toHaveBeenCalled();
    expect(routerMock.navigate).toHaveBeenCalledWith(['/login']);
  });
});
