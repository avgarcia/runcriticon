import { HttpErrorResponse } from '@angular/common/http';
import { DIALOG_DATA } from '@angular/cdk/dialog';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { of, throwError } from 'rxjs';
import { RaceValueDialogComponent, RaceValueDialogData } from './race-value-dialog.component';
import { ERROR_MESSAGES } from '../../../core/api/error-codes';

describe('RaceValueDialogComponent', () => {
  let fixture: ComponentFixture<RaceValueDialogComponent>;
  let component: RaceValueDialogComponent;
  const dialogRefMock = { close: jest.fn() };
  const submit = jest.fn();

  const datos = (overrides: Partial<RaceValueDialogData> = {}): RaceValueDialogData => ({
    title: 'Añadir valor',
    confirmLabel: 'Añadir',
    valueField: { initialValue: '', maxLength: 60 },
    initialDate: null,
    initialDistance: null,
    submit,
    ...overrides,
  });

  async function crear(data: RaceValueDialogData = datos()): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [RaceValueDialogComponent],
      providers: [
        { provide: BrnDialogRef, useValue: dialogRefMock },
        { provide: DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(RaceValueDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    jest.clearAllMocks();
    submit.mockReturnValue(of(undefined));
  });

  it('con valueField pinta el campo de valor', async () => {
    await crear();

    const input: HTMLInputElement = fixture.nativeElement.querySelector('#valor');
    expect(input).not.toBeNull();
  });

  it('sin valueField (editar carrera) no pinta el campo de valor', async () => {
    await crear(datos({ valueField: null }));

    const input: HTMLInputElement = fixture.nativeElement.querySelector('#valor');
    expect(input).toBeNull();
  });

  it('arranca con la fecha y distancia actuales al editar una carrera', async () => {
    await crear(datos({ valueField: null, initialDate: '2026-12-06', initialDistance: '42K' }));

    expect(component.form.controls.fecha.value).toBe('2026-12-06');
    expect(component.form.controls.distancia.value).toBe('42K');
  });

  it('no deja confirmar con el valor vacío cuando se pide', async () => {
    await crear();

    component.form.controls.valor.setValue('');

    expect(component.form.invalid).toBe(true);
  });

  it('rellenar solo la fecha sin distancia es inválido', async () => {
    await crear();
    component.form.controls.valor.setValue('Maratón');

    component.form.controls.fecha.setValue('2026-12-06');

    expect(component.form.hasError('metadataIncompleta')).toBe(true);
  });

  it('rellenar solo la distancia sin fecha es inválido', async () => {
    await crear();
    component.form.controls.valor.setValue('Maratón');

    component.form.controls.distancia.setValue('42K');

    expect(component.form.hasError('metadataIncompleta')).toBe(true);
  });

  it('dejar fecha y distancia vacías es válido: guarda metadata vacía', async () => {
    await crear();
    component.form.controls.valor.setValue('sin carrera');

    await component.submit();

    expect(submit).toHaveBeenCalledWith('sin carrera', { tipo: 'EMPTY' });
    expect(dialogRefMock.close).toHaveBeenCalledWith(true);
  });

  it('con fecha y distancia envía metadata de carrera y el valor tecleado', async () => {
    await crear();
    component.form.controls.valor.setValue('Maratón de Valencia');
    component.form.controls.fecha.setValue('2026-12-06');
    component.form.controls.distancia.setValue('42K');

    await component.submit();

    expect(submit).toHaveBeenCalledWith('Maratón de Valencia', {
      tipo: 'RACE',
      fecha: '2026-12-06',
      distancia: '42K',
    });
  });

  it('al editar solo la carrera, el valor enviado es undefined', async () => {
    await crear(datos({ valueField: null, initialDate: '2026-12-06', initialDistance: '42K' }));

    await component.submit();

    expect(submit).toHaveBeenCalledWith(undefined, {
      tipo: 'RACE',
      fecha: '2026-12-06',
      distancia: '42K',
    });
  });

  it('un valor duplicado se pinta en el campo, no en el mensaje general', async () => {
    await crear();
    submit.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { code: 'DUPLICATE_LABEL', field: 'valor', message: 'duplicate' },
          }),
      ),
    );
    component.form.controls.valor.setValue('Maratón');

    await component.submit();

    expect(component.form.controls.valor.getError('backend')).toBe(
      ERROR_MESSAGES['DUPLICATE_LABEL'],
    );
    expect(dialogRefMock.close).not.toHaveBeenCalled();
  });

  it('un error de fecha o distancia va al mensaje general, no al del campo valor', async () => {
    await crear();
    submit.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { code: 'INVALID_INPUT', field: 'distancia', message: 'required' },
          }),
      ),
    );
    component.form.controls.valor.setValue('Maratón');
    component.form.controls.fecha.setValue('2026-12-06');
    component.form.controls.distancia.setValue('42K');

    await component.submit();

    expect(component.errorMessage()).toBe(ERROR_MESSAGES['INVALID_INPUT']);
    expect(component.form.controls.valor.hasError('backend')).toBe(false);
  });

  it('un 403 no pinta mensaje propio: ya lo avisa el interceptor', async () => {
    await crear();
    submit.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    component.form.controls.valor.setValue('Maratón');

    await component.submit();

    expect(component.errorMessage()).toBeNull();
  });
});
