import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { ClubHealthService, GroupActivity } from '../../../core/club-health.service';
import { GroupService, GroupSummary } from '../../../core/group.service';
import { ClubHealthComponent } from './club-health.component';

describe('ClubHealthComponent', () => {
  const groups = signal<GroupSummary[] | undefined>(undefined);
  const groupMock = { groups, load: jest.fn() };
  const clubHealthMock = { getGroupActivity: jest.fn() };

  let fixture: ComponentFixture<ClubHealthComponent>;
  let component: ClubHealthComponent;

  beforeEach(() => {
    jest.clearAllMocks();
    groups.set(undefined);
    groupMock.load.mockReturnValue(of([]));
    clubHealthMock.getGroupActivity.mockReturnValue(of([]));
  });

  async function crear(
    gruposIniciales: GroupSummary[] = [],
    actividadReturn: Observable<GroupActivity[]> = of([]),
  ): Promise<void> {
    groupMock.load.mockImplementation(() => {
      groups.set(gruposIniciales);
      return of(gruposIniciales);
    });
    clubHealthMock.getGroupActivity.mockReturnValue(actividadReturn);
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ClubHealthComponent],
      providers: [
        { provide: GroupService, useValue: groupMock },
        { provide: ClubHealthService, useValue: clubHealthMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ClubHealthComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function grupo(overrides: Partial<GroupSummary> = {}): GroupSummary {
    return {
      id: 'g1',
      nombre: 'Avanzados',
      valores: [],
      totalAlumnos: 4,
      tieneEntrenador: true,
      ...overrides,
    };
  }

  it('une grupo y actividad por grupoId', async () => {
    await crear(
      [grupo({ id: 'g1' })],
      of([{ grupoId: 'g1', ultimaActividadEn: '2026-09-10T09:00:00Z' }]),
    );

    expect(component.rows()).toEqual([
      {
        id: 'g1',
        nombre: 'Avanzados',
        totalAlumnos: 4,
        tieneEntrenador: true,
        ultimaActividadEn: '2026-09-10T09:00:00Z',
      },
    ]);
  });

  it('un grupo sin entrada en la actividad se pinta sin actividad', async () => {
    await crear([grupo({ id: 'g1' })], of([]));

    expect(component.rows()?.[0].ultimaActividadEn).toBeUndefined();
    expect(fixture.nativeElement.textContent).toContain('Sin actividad');
  });

  it('un grupo sin entrenador muestra el badge de aviso', async () => {
    await crear([grupo({ id: 'g1', tieneEntrenador: false })]);

    expect(fixture.nativeElement.textContent).toContain('Sin entrenador');
  });

  it('un grupo con entrenador no muestra el aviso', async () => {
    await crear([grupo({ id: 'g1', tieneEntrenador: true })]);

    expect(fixture.nativeElement.textContent).not.toContain('Sin entrenador');
  });

  it('mientras cualquiera de las dos llamadas no resuelve, muestra los esqueletos', async () => {
    groupMock.load.mockReturnValue(new Observable<GroupSummary[]>());
    clubHealthMock.getGroupActivity.mockReturnValue(of([]));
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ClubHealthComponent],
      providers: [
        { provide: GroupService, useValue: groupMock },
        { provide: ClubHealthService, useValue: clubHealthMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ClubHealthComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.rows()).toBeUndefined();
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('sin grupos invita con el estado vacío', async () => {
    await crear([]);

    expect(fixture.nativeElement.textContent).toContain('Aún no hay grupos');
  });

  it('si la carga de la actividad falla ofrece reintentar', async () => {
    await crear([grupo()], throwError(() => new Error('boom')) as Observable<never>);

    expect(component.loadFailed()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('No pudimos cargar la salud del club');
  });

  it('reintentar vuelve a lanzar ambas llamadas', async () => {
    await crear([grupo()], throwError(() => new Error('boom')) as Observable<never>);
    groupMock.load.mockClear();
    clubHealthMock.getGroupActivity.mockClear();
    clubHealthMock.getGroupActivity.mockReturnValue(of([]));

    component.reload();

    expect(groupMock.load).toHaveBeenCalled();
    expect(clubHealthMock.getGroupActivity).toHaveBeenCalled();
    expect(component.loadFailed()).toBe(false);
  });
});
