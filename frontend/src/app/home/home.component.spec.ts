import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { HomeComponent } from './home.component';
import { Sesion, SesionService } from '../core/sesion.service';

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;
  const sesionMock = {
    sesion: signal<Sesion | null>({ userId: 'u-1', clubId: 'c-1', rol: 'ADMIN' }),
    cerrar: jest.fn().mockReturnValue(of(undefined)),
  };
  const routerMock = { navigate: jest.fn() };

  beforeEach(async () => {
    jest.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        { provide: SesionService, useValue: sesionMock },
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
    fixture.componentInstance.cerrar();
    expect(sesionMock.cerrar).toHaveBeenCalled();
    expect(routerMock.navigate).toHaveBeenCalledWith(['/login']);
  });
});
