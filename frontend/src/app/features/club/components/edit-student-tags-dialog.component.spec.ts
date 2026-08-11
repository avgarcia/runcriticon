import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DIALOG_DATA } from '@angular/cdk/dialog';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { of, throwError } from 'rxjs';
import { StudentService } from '../../../core/student.service';
import { TagKey } from '../../../core/taxonomy.service';
import { EditStudentTagsDialogComponent, EditStudentTagsData } from './edit-student-tags-dialog.component';

describe('EditStudentTagsDialogComponent', () => {
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

  const studentServiceMock = { replaceTags: jest.fn() };
  const dialogRefMock = { close: jest.fn() };

  let fixture: ComponentFixture<EditStudentTagsDialogComponent>;
  let component: EditStudentTagsDialogComponent;

  function crear(data: EditStudentTagsData): void {
    jest.clearAllMocks();
    studentServiceMock.replaceTags.mockReturnValue(of(undefined));

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [EditStudentTagsDialogComponent],
      providers: [
        { provide: StudentService, useValue: studentServiceMock },
        { provide: BrnDialogRef, useValue: dialogRefMock },
        { provide: DIALOG_DATA, useValue: data },
      ],
    });
    fixture = TestBed.createComponent(EditStudentTagsDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('preselecciona el valor actual del alumno en cada eje', () => {
    crear({
      studentId: 'a1',
      studentName: 'Pedro Cordero',
      currentValueIds: ['val-medio'],
      axes,
    });

    expect(component.selected().get('tag-nivel')).toBe('val-medio');
    expect(component.selected().has('tag-terreno')).toBe(false);
  });

  it('elegir un valor de un eje sustituye al anterior', () => {
    crear({ studentId: 'a1', studentName: 'Pedro Cordero', currentValueIds: ['val-medio'], axes });

    component.selectAxisValue('tag-nivel', 'val-alto');

    expect(component.selected().get('tag-nivel')).toBe('val-alto');
  });

  it('quitar un eje lo saca de la seleccion', () => {
    crear({ studentId: 'a1', studentName: 'Pedro Cordero', currentValueIds: ['val-medio'], axes });

    component.clearAxis('tag-nivel');

    expect(component.selected().has('tag-nivel')).toBe(false);
  });

  it('guardar manda los valores seleccionados y cierra con true', () => {
    crear({ studentId: 'a1', studentName: 'Pedro Cordero', currentValueIds: ['val-medio'], axes });
    component.selectAxisValue('tag-terreno', 'val-trail');

    component.save();

    expect(studentServiceMock.replaceTags).toHaveBeenCalledWith('a1', ['val-medio', 'val-trail']);
    expect(dialogRefMock.close).toHaveBeenCalledWith(true);
  });

  it('un valor que el alumno ya tenia fuera de los ejes visibles se preserva al guardar', () => {
    crear({
      studentId: 'a1',
      studentName: 'Pedro Cordero',
      currentValueIds: ['val-medio', 'val-huerfano'],
      axes,
    });

    component.save();

    expect(studentServiceMock.replaceTags).toHaveBeenCalledWith('a1', ['val-medio', 'val-huerfano']);
  });

  it('un error al guardar lo pinta y no cierra el dialogo', () => {
    crear({ studentId: 'a1', studentName: 'Pedro Cordero', currentValueIds: [], axes });
    studentServiceMock.replaceTags.mockReturnValue(throwError(() => new Error('boom')));

    component.save();

    expect(component.errorMessage()).not.toBeNull();
    expect(dialogRefMock.close).not.toHaveBeenCalled();
  });
});
