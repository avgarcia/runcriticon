import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DIALOG_DATA } from '@angular/cdk/dialog';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { of, throwError } from 'rxjs';
import { GroupDetail, GroupService } from '../../../core/group.service';
import { StudentService, StudentSummary } from '../../../core/student.service';
import {
  GroupMembershipDialogComponent,
  GroupMembershipDialogData,
} from './group-membership-dialog.component';

describe('GroupMembershipDialogComponent', () => {
  const detalle = (): GroupDetail => ({
    id: 'g-1',
    nombre: 'Maratón nivel medio',
    valores: ['val-medio'],
    total: 2,
    miembros: [
      { id: 'a-1', nombre: 'Ana Ruiz', origen: 'FILTRO', ajusteManual: false },
      { id: 'a-2', nombre: 'Pedro Cordero', origen: 'FILTRO', ajusteManual: true },
    ],
    excluidos: [{ id: 'a-3', nombre: 'Zoe Martín', cumpleFiltro: false }],
  });

  const alumnos: StudentSummary[] = [
    { id: 'a-1', nombre: 'Ana Ruiz', email: 'ana@club.test', estado: 'ACTIVO', valores: ['val-medio'] },
    { id: 'a-2', nombre: 'Pedro Cordero', email: 'pedro@club.test', estado: 'ACTIVO', valores: [] },
    { id: 'a-3', nombre: 'Zoe Martín', email: 'zoe@club.test', estado: 'ACTIVO', valores: [] },
    { id: 'a-4', nombre: 'Marta López', email: 'marta@club.test', estado: 'ACTIVO', valores: [] },
  ];

  const groupServiceMock = {
    getDetail: jest.fn(),
    setOverride: jest.fn(),
    clearOverride: jest.fn(),
  };
  const studentServiceMock = { load: jest.fn() };
  const dialogRefMock = { close: jest.fn() };

  let fixture: ComponentFixture<GroupMembershipDialogComponent>;
  let component: GroupMembershipDialogComponent;

  function crear(data: GroupMembershipDialogData = { grupoId: 'g-1', nombre: 'Maratón nivel medio' }): void {
    jest.clearAllMocks();
    groupServiceMock.getDetail.mockReturnValue(of(detalle()));
    studentServiceMock.load.mockReturnValue(of(alumnos));

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [GroupMembershipDialogComponent],
      providers: [
        { provide: GroupService, useValue: groupServiceMock },
        { provide: StudentService, useValue: studentServiceMock },
        { provide: BrnDialogRef, useValue: dialogRefMock },
        { provide: DIALOG_DATA, useValue: data },
      ],
    });
    fixture = TestBed.createComponent(GroupMembershipDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('carga el detalle del grupo y a los alumnos del club al abrirse', () => {
    crear();

    expect(groupServiceMock.getDetail).toHaveBeenCalledWith('g-1');
    expect(studentServiceMock.load).toHaveBeenCalled();
    expect(component.detail()?.miembros).toHaveLength(2);
  });

  it('quitar la excepción de un miembro con ajusteManual llama al DELETE y refresca el detalle', () => {
    crear();
    const refrescado = { ...detalle(), miembros: [detalle().miembros[0]] };
    groupServiceMock.clearOverride.mockReturnValue(of(undefined));
    groupServiceMock.getDetail.mockReturnValue(of(refrescado));

    component.quitarExcepcion('a-2');

    expect(groupServiceMock.clearOverride).toHaveBeenCalledWith('g-1', 'a-2');
    expect(component.detail()?.miembros).toHaveLength(1);
  });

  it('restaurar un excluido usa la misma llamada que quitar una excepción', () => {
    crear();
    groupServiceMock.clearOverride.mockReturnValue(of(undefined));
    groupServiceMock.getDetail.mockReturnValue(of({ ...detalle(), excluidos: [] }));

    component.restaurar('a-3');

    expect(groupServiceMock.clearOverride).toHaveBeenCalledWith('g-1', 'a-3');
    expect(component.detail()?.excluidos).toHaveLength(0);
  });

  it('excluir a un miembro manda incluido: false y pinta la respuesta ya recalculada', () => {
    crear();
    const recalculado = { ...detalle(), miembros: [detalle().miembros[1]] };
    groupServiceMock.setOverride.mockReturnValue(of(recalculado));

    component.excluir('a-1');

    expect(groupServiceMock.setOverride).toHaveBeenCalledWith('g-1', 'a-1', false);
    expect(component.detail()?.miembros).toHaveLength(1);
  });

  it('la búsqueda de candidatos excluye a los que ya son miembros', () => {
    crear();

    component.search.set('a');

    const nombres = component.candidates().map((alumno) => alumno.nombre);
    expect(nombres).toEqual(['Zoe Martín', 'Marta López']);
  });

  it('sin texto en la búsqueda no hay candidatos', () => {
    crear();

    expect(component.candidates()).toEqual([]);
  });

  it('incluir a un candidato manda incluido: true y limpia la búsqueda', () => {
    crear();
    component.search.set('Marta');
    const recalculado = { ...detalle(), miembros: [...detalle().miembros, { id: 'a-4', nombre: 'Marta López', origen: 'INCLUSION_MANUAL' as const, ajusteManual: true }] };
    groupServiceMock.setOverride.mockReturnValue(of(recalculado));

    component.incluir('a-4');

    expect(groupServiceMock.setOverride).toHaveBeenCalledWith('g-1', 'a-4', true);
    expect(component.search()).toBe('');
  });

  it('un error al ajustar la pertenencia lo pinta y no cierra el dialogo', () => {
    crear();
    groupServiceMock.setOverride.mockReturnValue(throwError(() => new Error('boom')));

    component.excluir('a-1');

    expect(component.errorMessage()).not.toBeNull();
    expect(dialogRefMock.close).not.toHaveBeenCalled();
  });

  it('cerrar sin haber tocado nada cierra con false', () => {
    crear();

    component.close();

    expect(dialogRefMock.close).toHaveBeenCalledWith(false);
  });

  it('cerrar despues de un cambio cierra con true', () => {
    crear();
    groupServiceMock.setOverride.mockReturnValue(of(detalle()));
    component.excluir('a-1');

    component.close();

    expect(dialogRefMock.close).toHaveBeenCalledWith(true);
  });

  it('si falla la carga inicial lo marca en vez de dejar el spinner puesto', () => {
    jest.clearAllMocks();
    groupServiceMock.getDetail.mockReturnValue(throwError(() => new Error('boom')));
    studentServiceMock.load.mockReturnValue(of(alumnos));

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [GroupMembershipDialogComponent],
      providers: [
        { provide: GroupService, useValue: groupServiceMock },
        { provide: StudentService, useValue: studentServiceMock },
        { provide: BrnDialogRef, useValue: dialogRefMock },
        { provide: DIALOG_DATA, useValue: { grupoId: 'g-1', nombre: 'X' } },
      ],
    });
    fixture = TestBed.createComponent(GroupMembershipDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.loadFailed()).toBe(true);
    expect(component.loading()).toBe(false);
  });
});
