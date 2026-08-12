import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { CoachService, CoachWorkload } from '../../../core/coach.service';
import { CoachesListComponent } from './coaches-list.component';

describe('CoachesListComponent', () => {
  const coaches = signal<CoachWorkload[] | undefined>(undefined);
  const coachMock = { coaches, load: jest.fn() };

  let fixture: ComponentFixture<CoachesListComponent>;
  let component: CoachesListComponent;

  beforeEach(() => {
    jest.clearAllMocks();
    coaches.set(undefined);
    coachMock.load.mockReturnValue(of([]));
  });

  async function crear(): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [CoachesListComponent],
      providers: [{ provide: CoachService, useValue: coachMock }],
    }).compileComponents();
    fixture = TestBed.createComponent(CoachesListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('carga los entrenadores al entrar', async () => {
    await crear();

    expect(coachMock.load).toHaveBeenCalled();
  });

  it('sin entrenadores lo dice en vez de dejar la lista en blanco', async () => {
    coaches.set([]);
    await crear();

    expect(fixture.nativeElement.textContent).toContain('todavía no tiene entrenadores');
  });

  it('pinta nombre, email y estado de cada entrenador', async () => {
    coaches.set([
      {
        id: 'c1',
        nombre: 'Carlos Ruiz',
        email: 'carlos@club.test',
        estado: 'ACTIVO',
        grupos: [],
        totalAlumnos: 0,
      },
    ]);
    await crear();

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Carlos Ruiz');
    expect(texto).toContain('carlos@club.test');
    expect(component.statusLabel('ACTIVO')).toBe('Activo');
    expect(component.statusLabel('INVITADO')).toBe('Invitado');
  });

  it('un entrenador sin grupos se distingue con el badge, no con silencio', async () => {
    coaches.set([
      {
        id: 'c1',
        nombre: 'Carlos Ruiz',
        email: 'carlos@club.test',
        estado: 'ACTIVO',
        grupos: [],
        totalAlumnos: 0,
      },
    ]);
    await crear();

    expect(fixture.nativeElement.textContent).toContain('Sin grupos asignados');
  });

  it('un entrenador con grupos pinta cada uno y el total de alumnos', async () => {
    coaches.set([
      {
        id: 'c1',
        nombre: 'Carlos Ruiz',
        email: 'carlos@club.test',
        estado: 'ACTIVO',
        grupos: [
          { id: 'g1', nombre: 'Maratón nivel medio', totalAlumnos: 5 },
          { id: 'g2', nombre: 'Trail avanzado', totalAlumnos: 2 },
        ],
        totalAlumnos: 7,
      },
    ]);
    await crear();

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Maratón nivel medio');
    expect(texto).toContain('Trail avanzado');
    expect(texto).toContain('7 alumnos');
    expect(texto).not.toContain('Sin grupos asignados');
  });

  it('usa el singular con un solo alumno', async () => {
    await crear();

    expect(component.totalAlumnosLabel(1)).toBe('1 alumno');
  });

  it('si la carga falla ofrece reintentar en vez de dejar los esqueletos puestos', async () => {
    coaches.set(undefined);
    coachMock.load.mockReturnValue(throwError(() => new Error('boom')) as Observable<never>);
    await crear();

    expect(component.loadFailed()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Reintentar');
  });

  it('reintentar vuelve a cargar', async () => {
    coaches.set(undefined);
    coachMock.load.mockReturnValue(throwError(() => new Error('boom')) as Observable<never>);
    await crear();
    coachMock.load.mockReturnValue(of([]));

    component.reload();

    expect(component.loadFailed()).toBe(false);
  });
});
