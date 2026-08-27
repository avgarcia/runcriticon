import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DIALOG_DATA } from '@angular/cdk/dialog';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { of, throwError } from 'rxjs';
import { GroupDetail, GroupService } from '../../../core/group.service';
import { Personalization, PlanDetail, PlanService } from '../../../core/plan.service';
import {
  PersonalizationsDialogComponent,
  PersonalizationsDialogData,
} from './personalizations-dialog.component';

describe('PersonalizationsDialogComponent', () => {
  const data: PersonalizationsDialogData = {
    planId: 'plan-1',
    grupoId: 'g-1',
    sesionId: 'sesion-1',
    sesionLabel: 'Series · 2026-08-18',
  };

  const personalizacionAna: Personalization = {
    sesionId: 'sesion-1',
    alumnoId: 'a-1',
    tipo: 'DESCANSO',
    mensajeAlAlumno: 'Vuelves de lesión, no te pases.',
  };

  const plan = (): PlanDetail => ({
    id: 'plan-1',
    grupoId: 'g-1',
    semana: '2026-08-17',
    estado: 'PUBLICADO',
    sesiones: [{ id: 'sesion-1', dia: '2026-08-18', tipo: 'SERIES' }],
    personalizaciones: [personalizacionAna],
  });

  const group = (): GroupDetail => ({
    id: 'g-1',
    nombre: 'Maratón nivel medio',
    valores: [],
    total: 2,
    miembros: [
      { id: 'a-1', nombre: 'Ana Ruiz', origen: 'FILTRO', ajusteManual: false },
      { id: 'a-2', nombre: 'Pedro Cordero', origen: 'FILTRO', ajusteManual: false },
    ],
    excluidos: [],
  });

  const planServiceMock = {
    get: jest.fn(),
    setPersonalization: jest.fn(),
    removePersonalization: jest.fn(),
  };
  const groupServiceMock = { getDetail: jest.fn() };
  const dialogRefMock = { close: jest.fn() };

  let fixture: ComponentFixture<PersonalizationsDialogComponent>;
  let component: PersonalizationsDialogComponent;

  function crear(): void {
    jest.clearAllMocks();
    planServiceMock.get.mockReturnValue(of(plan()));
    groupServiceMock.getDetail.mockReturnValue(of(group()));

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [PersonalizationsDialogComponent],
      providers: [
        { provide: PlanService, useValue: planServiceMock },
        { provide: GroupService, useValue: groupServiceMock },
        { provide: BrnDialogRef, useValue: dialogRefMock },
        { provide: DIALOG_DATA, useValue: data },
      ],
    });
    fixture = TestBed.createComponent(PersonalizationsDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('carga el plan y el grupo al abrirse, y filtra las personalizaciones de esta sesion', () => {
    crear();

    expect(planServiceMock.get).toHaveBeenCalledWith('plan-1');
    expect(groupServiceMock.getDetail).toHaveBeenCalledWith('g-1');
    expect(component.personalizations()).toEqual([personalizacionAna]);
  });

  it('la busqueda de candidatos excluye a los ya personalizados en esta sesion', () => {
    crear();
    component.startAdd();

    // "r" está en ambos nombres (Ruiz/Cordero); Ana ya tiene personalización en esta sesión, así
    // que solo Pedro debe sobrevivir al filtro — ejercita tanto la búsqueda como la exclusión.
    component.search.set('r');

    expect(component.candidates().map((c) => c.nombre)).toEqual(['Pedro Cordero']);
  });

  it('anadir una personalizacion llama a setPersonalization y pinta el plan recalculado', () => {
    crear();
    const recalculado = { ...plan(), personalizaciones: [personalizacionAna, { ...personalizacionAna, alumnoId: 'a-2' }] };
    planServiceMock.setPersonalization.mockReturnValue(of(recalculado));
    component.startAdd();
    component.selectedStudentId.set('a-2');
    component.selectType('DESCANSO');

    component.submit();

    expect(planServiceMock.setPersonalization).toHaveBeenCalledWith('plan-1', 'sesion-1', 'a-2', {
      tipo: 'DESCANSO',
      volumen: undefined,
      ritmo: undefined,
      notas: undefined,
      mensajeAlAlumno: null,
    });
    expect(component.formMode()).toBe('closed');
  });

  it('editar una personalizacion existente precarga el formulario con sus valores', () => {
    crear();

    component.startEdit(personalizacionAna);

    expect(component.formMode()).toBe('edit');
    expect(component.selectedStudentId()).toBe('a-1');
    expect(component.selectedType()).toBe('DESCANSO');
    expect(component.form.controls.message.value).toBe('Vuelves de lesión, no te pases.');
  });

  it('quitar una personalizacion llama a removePersonalization y refresca el plan', () => {
    crear();
    planServiceMock.removePersonalization.mockReturnValue(of(undefined));
    const refrescado = { ...plan(), personalizaciones: [] };
    planServiceMock.get.mockReturnValue(of(refrescado));

    component.remove('a-1');

    expect(planServiceMock.removePersonalization).toHaveBeenCalledWith('plan-1', 'sesion-1', 'a-1');
    expect(component.personalizations()).toEqual([]);
  });

  it('un error al guardar lo pinta y no cierra el dialogo', () => {
    crear();
    planServiceMock.setPersonalization.mockReturnValue(throwError(() => new Error('boom')));
    component.startAdd();
    component.selectedStudentId.set('a-2');
    component.selectType('DESCANSO');

    component.submit();

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
    planServiceMock.removePersonalization.mockReturnValue(of(undefined));
    planServiceMock.get.mockReturnValue(of(plan()));
    component.remove('a-1');

    component.close();

    expect(dialogRefMock.close).toHaveBeenCalledWith(true);
  });

  it('si falla la carga inicial lo marca en vez de dejar el spinner puesto', () => {
    jest.clearAllMocks();
    planServiceMock.get.mockReturnValue(throwError(() => new Error('boom')));
    groupServiceMock.getDetail.mockReturnValue(of(group()));

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [PersonalizationsDialogComponent],
      providers: [
        { provide: PlanService, useValue: planServiceMock },
        { provide: GroupService, useValue: groupServiceMock },
        { provide: BrnDialogRef, useValue: dialogRefMock },
        { provide: DIALOG_DATA, useValue: data },
      ],
    });
    fixture = TestBed.createComponent(PersonalizationsDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.loadFailed()).toBe(true);
    expect(component.loading()).toBe(false);
  });
});
