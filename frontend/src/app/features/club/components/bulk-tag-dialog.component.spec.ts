import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DIALOG_DATA } from '@angular/cdk/dialog';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { of, throwError } from 'rxjs';
import { StudentService } from '../../../core/student.service';
import { TagKey } from '../../../core/taxonomy.service';
import { BulkTagDialogComponent, BulkTagDialogData } from './bulk-tag-dialog.component';

describe('BulkTagDialogComponent', () => {
  const axes: readonly TagKey[] = [
    {
      id: 'tag-nivel',
      nombre: 'nivel',
      archivadoEn: null,
      valores: [
        { id: 'val-medio', valor: 'medio', archivadoEn: null },
        { id: 'val-alto', valor: 'alto', archivadoEn: null },
      ],
    },
    {
      id: 'tag-terreno',
      nombre: 'terreno',
      archivadoEn: null,
      valores: [{ id: 'val-trail', valor: 'trail', archivadoEn: '2026-07-01T10:00:00Z' }],
    },
  ] as unknown as readonly TagKey[];

  const studentServiceMock = { assignTagInBulk: jest.fn(), unassignTagInBulk: jest.fn() };
  const dialogRefMock = { close: jest.fn() };

  let fixture: ComponentFixture<BulkTagDialogComponent>;
  let component: BulkTagDialogComponent;

  function crear(data: BulkTagDialogData): void {
    jest.clearAllMocks();
    studentServiceMock.assignTagInBulk.mockReturnValue(of(2));
    studentServiceMock.unassignTagInBulk.mockReturnValue(of(2));

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [BulkTagDialogComponent],
      providers: [
        { provide: StudentService, useValue: studentServiceMock },
        { provide: BrnDialogRef, useValue: dialogRefMock },
        { provide: DIALOG_DATA, useValue: data },
      ],
    });
    fixture = TestBed.createComponent(BulkTagDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('elegir eje y valor y confirmar asigna el valor a los alumnos seleccionados', () => {
    crear({ mode: 'assign', studentIds: ['a1', 'a2'], axes });
    component.selectAxis('tag-nivel');
    component.selectValue('val-alto');

    component.confirm();

    expect(studentServiceMock.assignTagInBulk).toHaveBeenCalledWith(['a1', 'a2'], 'val-alto');
    expect(dialogRefMock.close).toHaveBeenCalledWith(2);
  });

  it('en modo unassign llama a unassignTagInBulk', () => {
    crear({ mode: 'unassign', studentIds: ['a1'], axes });
    component.selectAxis('tag-nivel');
    component.selectValue('val-medio');

    component.confirm();

    expect(studentServiceMock.unassignTagInBulk).toHaveBeenCalledWith(['a1'], 'val-medio');
    expect(studentServiceMock.assignTagInBulk).not.toHaveBeenCalled();
  });

  it('cambiar de eje invalida el valor elegido previamente', () => {
    crear({ mode: 'assign', studentIds: ['a1'], axes });
    component.selectAxis('tag-nivel');
    component.selectValue('val-medio');

    component.selectAxis('tag-terreno');

    expect(component.selectedValueId()).toBeUndefined();
  });

  it('un error 409 pinta el mensaje traducido y no cierra el dialogo', () => {
    crear({ mode: 'assign', studentIds: ['a1'], axes });
    component.selectAxis('tag-nivel');
    component.selectValue('val-medio');
    studentServiceMock.assignTagInBulk.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { code: 'TAG_VALUE_NOT_ASSIGNABLE', message: 'Ese valor ya no se puede asignar' },
          }),
      ),
    );

    component.confirm();

    expect(component.errorMessage()).not.toBeNull();
    expect(dialogRefMock.close).not.toHaveBeenCalled();
  });

  it('un error 403 no pinta mensaje (lo avisa el interceptor) y no cierra', () => {
    crear({ mode: 'assign', studentIds: ['a1'], axes });
    component.selectAxis('tag-nivel');
    component.selectValue('val-medio');
    studentServiceMock.assignTagInBulk.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403 })),
    );

    component.confirm();

    expect(component.errorMessage()).toBeNull();
    expect(dialogRefMock.close).not.toHaveBeenCalled();
  });

  it('el titulo usa singular con un alumno y plural con varios', () => {
    crear({ mode: 'assign', studentIds: ['a1'], axes });
    expect(component.title()).toBe('Asignar tag a 1 alumno');

    crear({ mode: 'assign', studentIds: ['a1', 'a2'], axes });
    expect(component.title()).toBe('Asignar tag a 2 alumnos');
  });

  it('sin valor elegido no confirma nada', () => {
    crear({ mode: 'assign', studentIds: ['a1'], axes });

    component.confirm();

    expect(studentServiceMock.assignTagInBulk).not.toHaveBeenCalled();
  });
});
