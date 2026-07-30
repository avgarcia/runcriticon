import { HttpErrorResponse } from '@angular/common/http';
import { DIALOG_DATA } from '@angular/cdk/dialog';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { of, throwError } from 'rxjs';
import { LabelDialogComponent, LabelDialogData } from './label-dialog.component';
import { ERROR_MESSAGES } from '../../../core/api/error-codes';

describe('LabelDialogComponent', () => {
  let fixture: ComponentFixture<LabelDialogComponent>;
  let component: LabelDialogComponent;
  const dialogRefMock = { close: jest.fn() };
  const submit = jest.fn();

  const datos = (overrides: Partial<LabelDialogData> = {}): LabelDialogData => ({
    title: 'Renombrar tag',
    label: 'Nombre',
    confirmLabel: 'Guardar',
    initialValue: 'nivel',
    maxLength: 40,
    field: 'nombre',
    submit,
    ...overrides,
  });

  async function crear(data: LabelDialogData = datos()): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [LabelDialogComponent],
      providers: [
        { provide: BrnDialogRef, useValue: dialogRefMock },
        { provide: DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(LabelDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    jest.clearAllMocks();
    submit.mockReturnValue(of(undefined));
  });

  it('arranca con el valor actual para poder editarlo, no en blanco', async () => {
    await crear();

    expect(component.form.controls.label.value).toBe('nivel');
  });

  it('limita el campo al máximo del contrato', async () => {
    await crear(datos({ maxLength: 60 }));

    const input: HTMLInputElement = fixture.nativeElement.querySelector('#label');
    expect(input.getAttribute('maxlength')).toBe('60');
  });

  it('no deja confirmar con el campo vacío', async () => {
    await crear();

    component.form.controls.label.setValue('');

    expect(component.form.invalid).toBe(true);
  });

  it('no deja confirmar un texto de solo espacios', async () => {
    await crear();

    component.form.controls.label.setValue('   ');

    expect(component.form.invalid).toBe(true);
  });

  it('al confirmar llama a la operación recibida y cierra con el texto', async () => {
    await crear();

    component.form.controls.label.setValue('Nivel');
    await component.submit();

    expect(submit).toHaveBeenCalledWith('Nivel');
    expect(dialogRefMock.close).toHaveBeenCalledWith('Nivel');
  });

  it('un nombre duplicado se pinta en el campo y el diálogo sigue abierto', async () => {
    await crear();
    submit.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { code: 'DUPLICATE_LABEL', field: 'nombre', message: 'duplicate' },
          }),
      ),
    );

    component.form.controls.label.setValue('terreno');
    await component.submit();

    expect(component.form.controls.label.getError('backend')).toBe(ERROR_MESSAGES['DUPLICATE_LABEL']);
    expect(dialogRefMock.close).not.toHaveBeenCalled();
  });

  it('no muestra el message del backend, sino el del catálogo', async () => {
    await crear();
    submit.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { code: 'LABEL_TOO_LONG', field: 'nombre', message: 'too_long' },
          }),
      ),
    );

    component.form.controls.label.setValue('x');
    await component.submit();

    expect(component.form.controls.label.getError('backend')).not.toContain('too_long');
  });

  it('un error de otro campo va al mensaje general, no al del formulario', async () => {
    await crear(datos({ field: 'valor' }));
    submit.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { code: 'TAG_KEY_ARCHIVED', message: 'archived' },
          }),
      ),
    );

    component.form.controls.label.setValue('x');
    await component.submit();

    expect(component.errorMessage()).toBe(ERROR_MESSAGES['TAG_KEY_ARCHIVED']);
    expect(component.form.controls.label.hasError('backend')).toBe(false);
  });

  it('un 403 no pinta mensaje propio: ya lo avisa el interceptor', async () => {
    await crear();
    submit.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

    component.form.controls.label.setValue('x');
    await component.submit();

    expect(component.errorMessage()).toBeNull();
    expect(component.form.controls.label.hasError('backend')).toBe(false);
  });
});
