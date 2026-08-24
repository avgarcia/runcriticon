import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { MyPlanService, MyWeek } from '../../../core/my-plan.service';
import { todayIsoDate } from '../date-format-es';
import { MyWeekComponent } from './my-week.component';

describe('MyWeekComponent', () => {
  const myPlanServiceMock = { getWeek: jest.fn() };

  let fixture: ComponentFixture<MyWeekComponent>;
  let component: MyWeekComponent;

  async function crear(getWeekReturn: Observable<MyWeek> = of({ semana: '2026-08-17', sesiones: [] })) {
    jest.clearAllMocks();
    myPlanServiceMock.getWeek.mockReturnValue(getWeekReturn);

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [MyWeekComponent],
      providers: [{ provide: MyPlanService, useValue: myPlanServiceMock }],
    }).compileComponents();
    fixture = TestBed.createComponent(MyWeekComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('carga la semana en curso al entrar, sin parámetro', async () => {
    await crear();

    expect(myPlanServiceMock.getWeek).toHaveBeenCalledWith();
  });

  it('sin sesión hoy muestra el empty state único', async () => {
    await crear(of({ semana: '2026-08-17', sesiones: [] }));

    expect(fixture.nativeElement.textContent).toContain('Hoy no hay sesión programada');
  });

  it('pinta la sesión de hoy con su tipo', async () => {
    const hoy = todayIsoDate();
    await crear(of({ semana: hoy, sesiones: [{ dia: hoy, tipo: 'RODAJE' }] }));

    expect(fixture.nativeElement.textContent).toContain('Rodaje');
  });

  it('un ritmo relativo sin marca muestra el aviso de falta de marca, sin ritmo numérico', async () => {
    const hoy = todayIsoDate();
    await crear(
      of({
        semana: hoy,
        sesiones: [{ dia: hoy, tipo: 'TEMPO', ritmo: { faltaMarca: '10K' } }],
      }),
    );

    expect(fixture.nativeElement.textContent).toContain('Añade tu marca de 10K');
  });

  it('un ritmo absoluto muestra el ritmo formateado', async () => {
    const hoy = todayIsoDate();
    await crear(
      of({
        semana: hoy,
        sesiones: [{ dia: hoy, tipo: 'TEMPO', ritmo: { segundosPorKm: 240 } }],
      }),
    );

    expect(fixture.nativeElement.textContent).toContain('4:00 /km');
  });

  it('el mensaje del entrenador solo aparece cuando la sesión lo trae', async () => {
    const hoy = todayIsoDate();
    await crear(
      of({
        semana: hoy,
        sesiones: [{ dia: hoy, tipo: 'RODAJE', mensajeDelEntrenador: 'Tómatelo con calma hoy' }],
      }),
    );

    expect(fixture.nativeElement.textContent).toContain('Tómatelo con calma hoy');
  });

  it('sin esPersonalizada en el modelo, no hay ningún indicador de personalización en la UI', async () => {
    const hoy = todayIsoDate();
    await crear(of({ semana: hoy, sesiones: [{ dia: hoy, tipo: 'RODAJE' }] }));

    expect(fixture.nativeElement.textContent).not.toContain('Personalizada');
  });

  it('seleccionar otro día de la tira cambia la sesión mostrada', async () => {
    await crear(
      of({
        semana: '2026-08-17',
        sesiones: [{ dia: '2026-08-19', tipo: 'SERIES' }],
      }),
    );
    // Día explícito sin sesión (independiente de qué día sea "hoy" al ejecutar el test).
    component.selectedDay.set('2026-08-17');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Hoy no hay sesión programada');

    component.selectedDay.set('2026-08-19');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Series');
  });

  it('si la carga falla ofrece reintentar', async () => {
    await crear(throwError(() => new Error('boom')) as Observable<never>);

    expect(component.loadFailed()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Reintentar');
  });

  it('reintentar vuelve a pedir la semana', async () => {
    await crear(throwError(() => new Error('boom')) as Observable<never>);
    myPlanServiceMock.getWeek.mockReturnValue(of({ semana: '2026-08-17', sesiones: [] }));

    component.reload();
    fixture.detectChanges();

    expect(component.loadFailed()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Hoy no hay sesión programada');
  });
});
