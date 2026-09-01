import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { Observable, of, throwError } from 'rxjs';
import { MyMarks, MyMarksService } from '../../../core/my-marks.service';
import { MyMarksComponent } from './my-marks.component';

describe('MyMarksComponent', () => {
  const myMarksServiceMock = { getMarks: jest.fn() };
  const dialogServiceMock = { open: jest.fn() };

  let fixture: ComponentFixture<MyMarksComponent>;
  let component: MyMarksComponent;

  const emptyMarks: MyMarks = {
    marcas: [{ distancia: '5K' }, { distancia: '10K' }, { distancia: '21K' }, { distancia: '42K' }],
  };

  async function crear(getMarksReturn: Observable<MyMarks> = of(emptyMarks)) {
    jest.clearAllMocks();
    myMarksServiceMock.getMarks.mockReturnValue(getMarksReturn);
    dialogServiceMock.open.mockReturnValue({ closed$: of(false) });

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [MyMarksComponent],
      providers: [
        { provide: MyMarksService, useValue: myMarksServiceMock },
        { provide: HlmDialogService, useValue: dialogServiceMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(MyMarksComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('carga las marcas al entrar', async () => {
    await crear();

    expect(myMarksServiceMock.getMarks).toHaveBeenCalledWith();
  });

  it('pinta el banner de privacidad', async () => {
    await crear();

    expect(fixture.nativeElement.textContent).toContain('Tus marcas son privadas.');
  });

  it('sin ninguna marca, pinta las 4 distancias con "Sin marca" y el boton Añadir', async () => {
    await crear();

    const texto: string = fixture.nativeElement.textContent;
    expect(texto).toContain('5K');
    expect(texto).toContain('10K');
    expect(texto).toContain('21K');
    expect(texto).toContain('42K');
    expect((texto.match(/Sin marca/g) ?? []).length).toBe(4);
    expect((texto.match(/\+ Añadir/g) ?? []).length).toBe(4);
  });

  it('una distancia con marca pinta el tiempo formateado y el boton Editar', async () => {
    await crear(
      of({
        marcas: [
          { distancia: '5K', tiempoSegundos: 1365, modificadoEn: '2026-08-01T10:00:00Z' },
          { distancia: '10K' },
          { distancia: '21K' },
          { distancia: '42K' },
        ],
      }),
    );

    const texto: string = fixture.nativeElement.textContent;
    expect(texto).toContain('22:45');
    expect(texto).toContain('✎ Editar');
  });

  it('un fallo de carga muestra el estado de error con reintentar', async () => {
    await crear(throwError(() => new Error('boom')));

    expect(component.loadFailed()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('No se han podido cargar tus marcas.');
  });

  it('abrir una marca vacia pasa existingSeconds null al dialogo', async () => {
    await crear();

    component.openMark({ distance: '21K', short: '21K', full: 'media maratón' });

    expect(dialogServiceMock.open).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({
        context: { distance: '21K', label: '21K · media maratón', existingSeconds: null },
      }),
    );
  });

  it('abrir una marca existente pasa su tiempo actual al dialogo', async () => {
    const marks: MyMarks = {
      marcas: [
        { distancia: '5K' },
        { distancia: '10K', tiempoSegundos: 2850, modificadoEn: '2026-08-01T10:00:00Z' },
        { distancia: '21K' },
        { distancia: '42K' },
      ],
    };
    await crear(of(marks));

    component.openMark({ distance: '10K', short: '10K', full: null, mark: marks.marcas[1] });

    expect(dialogServiceMock.open).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({
        context: { distance: '10K', label: '10K', existingSeconds: 2850 },
      }),
    );
  });

  it('cerrar el dialogo con cambios recarga las marcas', async () => {
    await crear();
    jest.clearAllMocks();
    myMarksServiceMock.getMarks.mockReturnValue(of(emptyMarks));
    dialogServiceMock.open.mockReturnValue({ closed$: of(true) });

    component.openMark({ distance: '5K', short: '5K', full: null });

    expect(myMarksServiceMock.getMarks).toHaveBeenCalledTimes(1);
  });
});
