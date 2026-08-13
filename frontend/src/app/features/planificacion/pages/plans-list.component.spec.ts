import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { Plan, PlanService } from '../../../core/plan.service';
import { PlansListComponent } from './plans-list.component';

describe('PlansListComponent', () => {
  const planServiceMock = { listDrafts: jest.fn(), create: jest.fn() };

  let fixture: ComponentFixture<PlansListComponent>;
  let component: PlansListComponent;

  async function crear(grupoId = 'g-1', listDraftsReturn: Observable<Plan[]> = of([])): Promise<void> {
    jest.clearAllMocks();
    planServiceMock.listDrafts.mockReturnValue(listDraftsReturn);

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [PlansListComponent],
      providers: [
        { provide: PlanService, useValue: planServiceMock },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ grupoId }) } },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PlansListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('carga los planes en borrador del grupo de la ruta al entrar', async () => {
    await crear('g-42');

    expect(planServiceMock.listDrafts).toHaveBeenCalledWith('g-42');
  });

  it('sin planes invita a crear el primero', async () => {
    await crear();

    expect(fixture.nativeElement.textContent).toContain('Todavía no hay planes en borrador');
  });

  it('pinta los planes con su semana y su estado', async () => {
    const planes: Plan[] = [{ id: 'p1', grupoId: 'g-1', semana: '2026-08-17', estado: 'BORRADOR' }];

    await crear('g-1', of(planes));

    expect(fixture.nativeElement.textContent).toContain('2026-08-17');
    expect(fixture.nativeElement.textContent).toContain('BORRADOR');
  });

  it('si la carga falla ofrece reintentar', async () => {
    await crear('g-1', throwError(() => new Error('boom')) as Observable<never>);

    expect(component.loadFailed()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Reintentar');
  });

  it('crear un plan recarga el listado', async () => {
    await crear();
    planServiceMock.create.mockReturnValue(of({ id: 'p1', grupoId: 'g-1', semana: '2026-08-17', estado: 'BORRADOR' }));
    planServiceMock.listDrafts.mockClear();

    component.createDraft();

    expect(planServiceMock.create).toHaveBeenCalledWith('g-1', expect.any(String));
    expect(planServiceMock.listDrafts).toHaveBeenCalled();
    expect(component.creating()).toBe(false);
  });

  it('un fallo al crear no rompe la pantalla y lo avisa', async () => {
    await crear();
    planServiceMock.create.mockReturnValue(throwError(() => new Error('boom')));

    component.createDraft();

    expect(component.createFailed()).toBe(true);
    expect(component.creating()).toBe(false);
  });
});
