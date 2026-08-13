import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DIALOG_DATA } from '@angular/cdk/dialog';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { of, throwError } from 'rxjs';
import { CoachService, CoachWorkload } from '../../../core/coach.service';
import { GroupCoaches, GroupService } from '../../../core/group.service';
import {
  GroupCoachesDialogComponent,
  GroupCoachesDialogData,
} from './group-coaches-dialog.component';

describe('GroupCoachesDialogComponent', () => {
  const asignados = (): GroupCoaches => ({
    entrenadores: [{ id: 'c1', nombre: 'Carlos Ruiz', email: 'carlos@club.test', estado: 'ACTIVO' }],
  });

  const todosLosEntrenadores: CoachWorkload[] = [
    { id: 'c1', nombre: 'Carlos Ruiz', email: 'carlos@club.test', estado: 'ACTIVO', grupos: [], totalAlumnos: 0 },
    { id: 'c2', nombre: 'Marta López', email: 'marta@club.test', estado: 'ACTIVO', grupos: [], totalAlumnos: 0 },
    { id: 'c3', nombre: 'Ana Ruiz', email: 'ana@club.test', estado: 'INVITADO', grupos: [], totalAlumnos: 0 },
  ];

  const groupServiceMock = {
    getCoaches: jest.fn(),
    assignCoach: jest.fn(),
    unassignCoach: jest.fn(),
  };
  const coachServiceMock = { load: jest.fn() };
  const dialogRefMock = { close: jest.fn() };

  let fixture: ComponentFixture<GroupCoachesDialogComponent>;
  let component: GroupCoachesDialogComponent;

  function crear(data: GroupCoachesDialogData = { grupoId: 'g-1', nombre: 'Maratón nivel medio' }): void {
    jest.clearAllMocks();
    groupServiceMock.getCoaches.mockReturnValue(of(asignados()));
    coachServiceMock.load.mockReturnValue(of(todosLosEntrenadores));

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [GroupCoachesDialogComponent],
      providers: [
        { provide: GroupService, useValue: groupServiceMock },
        { provide: CoachService, useValue: coachServiceMock },
        { provide: BrnDialogRef, useValue: dialogRefMock },
        { provide: DIALOG_DATA, useValue: data },
      ],
    });
    fixture = TestBed.createComponent(GroupCoachesDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('carga los entrenadores asignados y el listado del club al abrirse', () => {
    crear();

    expect(groupServiceMock.getCoaches).toHaveBeenCalledWith('g-1');
    expect(coachServiceMock.load).toHaveBeenCalled();
    expect(component.coaches()?.map((c) => c.id)).toEqual(['c1']);
  });

  it('la búsqueda de candidatos excluye a los ya asignados', () => {
    crear();

    component.search.set('a');

    expect(component.candidates().map((c) => c.id)).toEqual(['c2', 'c3']);
  });

  it('sin texto en la búsqueda no hay candidatos', () => {
    crear();

    expect(component.candidates()).toEqual([]);
  });

  it('asignar manda el id del entrenador y pinta la lista ya recalculada', () => {
    crear();
    component.search.set('Marta');
    const recalculado: GroupCoaches = {
      entrenadores: [...asignados().entrenadores, { id: 'c2', nombre: 'Marta López', email: 'marta@club.test', estado: 'ACTIVO' }],
    };
    groupServiceMock.assignCoach.mockReturnValue(of(recalculado));

    component.asignar('c2');

    expect(groupServiceMock.assignCoach).toHaveBeenCalledWith('g-1', 'c2');
    expect(component.coaches()?.map((c) => c.id)).toEqual(['c1', 'c2']);
    expect(component.search()).toBe('');
  });

  it('quitar llama al DELETE y refresca la lista con una segunda consulta', () => {
    crear();
    groupServiceMock.unassignCoach.mockReturnValue(of(undefined));
    groupServiceMock.getCoaches.mockReturnValue(of({ entrenadores: [] }));

    component.quitar('c1');

    expect(groupServiceMock.unassignCoach).toHaveBeenCalledWith('g-1', 'c1');
    expect(component.coaches()).toEqual([]);
  });

  it('un error al asignar lo pinta y no cierra el dialogo', () => {
    crear();
    groupServiceMock.assignCoach.mockReturnValue(throwError(() => new Error('boom')));

    component.asignar('c2');

    expect(component.errorMessage()).not.toBeNull();
    expect(dialogRefMock.close).not.toHaveBeenCalled();
  });

  it('cerrar sin haber tocado nada cierra con false', () => {
    crear();

    component.close();

    expect(dialogRefMock.close).toHaveBeenCalledWith(false);
  });

  it('cerrar despues de asignar cierra con true', () => {
    crear();
    groupServiceMock.assignCoach.mockReturnValue(of(asignados()));
    component.asignar('c1');

    component.close();

    expect(dialogRefMock.close).toHaveBeenCalledWith(true);
  });

  it('si falla la carga inicial lo marca en vez de dejar el spinner puesto', () => {
    jest.clearAllMocks();
    groupServiceMock.getCoaches.mockReturnValue(throwError(() => new Error('boom')));
    coachServiceMock.load.mockReturnValue(of(todosLosEntrenadores));

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [GroupCoachesDialogComponent],
      providers: [
        { provide: GroupService, useValue: groupServiceMock },
        { provide: CoachService, useValue: coachServiceMock },
        { provide: BrnDialogRef, useValue: dialogRefMock },
        { provide: DIALOG_DATA, useValue: { grupoId: 'g-1', nombre: 'X' } },
      ],
    });
    fixture = TestBed.createComponent(GroupCoachesDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.loadFailed()).toBe(true);
    expect(component.loading()).toBe(false);
  });
});
