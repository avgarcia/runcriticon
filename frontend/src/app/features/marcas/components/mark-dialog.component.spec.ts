import { DIALOG_DATA } from '@angular/cdk/dialog';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { of, throwError } from 'rxjs';
import { MyMark, MyMarksService } from '../../../core/my-marks.service';
import { MarkDialogComponent, MarkDialogData } from './mark-dialog.component';

describe('MarkDialogComponent', () => {
  const myMarksServiceMock = { recordMark: jest.fn(), withdrawMark: jest.fn() };
  const dialogRefMock = { close: jest.fn() };

  let fixture: ComponentFixture<MarkDialogComponent>;
  let component: MarkDialogComponent;

  function mark(overrides: Partial<MyMark> = {}): MyMark {
    return { distancia: '10K', tiempoSegundos: 2850, modificadoEn: '2026-08-01T10:00:00Z', ...overrides };
  }

  async function crear(data: MarkDialogData) {
    jest.clearAllMocks();
    myMarksServiceMock.recordMark.mockReturnValue(of(mark()));
    myMarksServiceMock.withdrawMark.mockReturnValue(of(undefined));

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [MarkDialogComponent],
      providers: [
        { provide: MyMarksService, useValue: myMarksServiceMock },
        { provide: BrnDialogRef, useValue: dialogRefMock },
        { provide: DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(MarkDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('sin marca previa, arranca en 0:00:00 y no se puede guardar', async () => {
    await crear({ distance: '21K', label: '21K · media maratón', existingSeconds: null });

    expect(component.hours).toBe(0);
    expect(component.minutes).toBe(0);
    expect(component.seconds).toBe(0);
    expect(component.canSubmit()).toBe(false);
  });

  it('con marca previa de menos de una hora, precarga minutos y segundos, sin horas', async () => {
    await crear({ distance: '10K', label: '10K', existingSeconds: 2850 });

    expect(component.hours).toBe(0);
    expect(component.minutes).toBe(47);
    expect(component.seconds).toBe(30);
    expect(component.canSubmit()).toBe(true);
  });

  it('con marca previa de mas de una hora, precarga tambien las horas', async () => {
    await crear({ distance: '42K', label: '42K · maratón', existingSeconds: 12645 });

    expect(component.hours).toBe(3);
    expect(component.minutes).toBe(30);
    expect(component.seconds).toBe(45);
  });

  it('guardar llama al servicio con el total en segundos, y cierra con true', async () => {
    await crear({ distance: '5K', label: '5K', existingSeconds: null });
    component.minutes = 22;
    component.seconds = 45;

    await component.submit();

    expect(myMarksServiceMock.recordMark).toHaveBeenCalledWith('5K', 1365);
    expect(dialogRefMock.close).toHaveBeenCalledWith(true);
  });

  it('borrar marca llama al servicio de retirada, y cierra con true', async () => {
    await crear({ distance: '10K', label: '10K', existingSeconds: 2850 });

    await component.withdraw();

    expect(myMarksServiceMock.withdrawMark).toHaveBeenCalledWith('10K');
    expect(dialogRefMock.close).toHaveBeenCalledWith(true);
  });

  it('un error de servidor al guardar muestra el mensaje y no cierra el dialogo', async () => {
    await crear({ distance: '5K', label: '5K', existingSeconds: null });
    myMarksServiceMock.recordMark.mockReturnValue(throwError(() => new Error('boom')));
    component.minutes = 22;
    component.seconds = 45;

    await component.submit();

    expect(component.errorMessage()).not.toBeNull();
    expect(dialogRefMock.close).not.toHaveBeenCalled();
    expect(component.saving()).toBe(false);
  });

  it('con tiempo 0:00:00 no se puede guardar', async () => {
    await crear({ distance: '5K', label: '5K', existingSeconds: null });

    await component.submit();

    expect(myMarksServiceMock.recordMark).not.toHaveBeenCalled();
  });

  it('cancelar cierra el dialogo con false', async () => {
    await crear({ distance: '5K', label: '5K', existingSeconds: null });

    component.close();

    expect(dialogRefMock.close).toHaveBeenCalledWith(false);
  });
});
