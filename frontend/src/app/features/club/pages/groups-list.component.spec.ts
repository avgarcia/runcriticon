import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { Observable, of, throwError } from 'rxjs';
import { GroupService, GroupSummary } from '../../../core/group.service';
import { PermissionsService } from '../../../core/permissions.service';
import { Taxonomy, TaxonomyService } from '../../../core/taxonomy.service';
import { GroupsListComponent } from './groups-list.component';

describe('GroupsListComponent', () => {
  const taxonomia = {
    tags: [
      {
        id: 'objetivo',
        nombre: 'objetivo',
        archivadoEn: null,
        valores: [{ id: 'maraton', valor: 'Maratón Valencia', archivadoEn: null }],
      },
      {
        id: 'nivel',
        nombre: 'nivel',
        archivadoEn: null,
        valores: [
          { id: 'medio', valor: 'medio', archivadoEn: null },
          { id: 'retirado', valor: 'retirado', archivadoEn: '2026-07-01T10:00:00Z' },
        ],
      },
    ],
  } as unknown as Taxonomy;

  const groups = signal<GroupSummary[] | undefined>(undefined);
  const taxonomy = signal<Taxonomy | undefined>(undefined);
  const groupMock = { groups, load: jest.fn() };
  const taxonomyMock = { taxonomy, load: jest.fn() };
  const permissionsMock = { can: jest.fn().mockReturnValue(true) };
  const dialogMock = { open: jest.fn() };

  let fixture: ComponentFixture<GroupsListComponent>;
  let component: GroupsListComponent;

  beforeEach(() => {
    jest.clearAllMocks();
    groups.set([]);
    taxonomy.set(taxonomia);
    groupMock.load.mockReturnValue(of([]));
    taxonomyMock.load.mockReturnValue(of(taxonomia));
    permissionsMock.can.mockReturnValue(true);
    dialogMock.open.mockReturnValue({ closed$: of(undefined) });
  });

  async function crear(): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [GroupsListComponent],
      providers: [
        provideRouter([]),
        { provide: GroupService, useValue: groupMock },
        { provide: TaxonomyService, useValue: taxonomyMock },
        { provide: PermissionsService, useValue: permissionsMock },
        { provide: HlmDialogService, useValue: dialogMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(GroupsListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('carga grupos y taxonomía al entrar', async () => {
    await crear();

    expect(groupMock.load).toHaveBeenCalled();
    expect(taxonomyMock.load).toHaveBeenCalled();
  });

  it('traduce el filtro a palabras y ordena las condiciones por eje', async () => {
    groups.set([{ id: 'g1', nombre: 'Avanzados', valores: ['medio', 'maraton'], totalAlumnos: 4 }]);
    await crear();

    expect(component.cards()?.[0].filtro).toBe('nivel = medio y objetivo = Maratón Valencia');
  });

  it('marca el valor archivado en el que se apoya un grupo', async () => {
    groups.set([{ id: 'g1', nombre: 'Retirados', valores: ['retirado'], totalAlumnos: 0 }]);
    await crear();

    expect(component.cards()?.[0].filtro).toContain('archivado');
  });

  it('un grupo sin filtro lo dice en vez de dejar el hueco en blanco', async () => {
    groups.set([{ id: 'g1', nombre: 'A mano', valores: [], totalAlumnos: 2 }]);
    await crear();

    expect(component.cards()?.[0].filtro).toContain('solo entra quien se añada a mano');
  });

  it('avisa del grupo en el que no cae nadie', async () => {
    groups.set([{ id: 'g1', nombre: 'Vacío', valores: ['medio'], totalAlumnos: 0 }]);
    await crear();

    expect(fixture.nativeElement.textContent).toContain('Ningún alumno cumple este filtro');
    expect(component.membersLabel(0)).toBe('Sin alumnos');
  });

  it('usa el singular con un solo alumno', async () => {
    await crear();

    expect(component.membersLabel(1)).toBe('1 alumno');
  });

  it('sin grupos invita a crear el primero', async () => {
    await crear();

    expect(fixture.nativeElement.textContent).toContain('Aún no tienes grupos');
  });

  it('sin permiso para crear no ofrece el botón de nuevo grupo', async () => {
    permissionsMock.can.mockReturnValue(false);
    await crear();

    expect(fixture.nativeElement.textContent).not.toContain('Nuevo grupo');
  });

  it('abre el diálogo de ajuste manual con el grupo de la tarjeta', async () => {
    groups.set([{ id: 'g1', nombre: 'Avanzados', valores: ['medio'], totalAlumnos: 2 }]);
    await crear();

    component.openMembershipDialog(component.cards()![0].summary);

    expect(dialogMock.open).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ context: { grupoId: 'g1', nombre: 'Avanzados' } }),
    );
  });

  it('cerrar el diálogo tras un cambio recarga los grupos', async () => {
    groups.set([{ id: 'g1', nombre: 'Avanzados', valores: ['medio'], totalAlumnos: 2 }]);
    await crear();
    dialogMock.open.mockReturnValue({ closed$: of(true) });
    groupMock.load.mockClear();

    component.openMembershipDialog(component.cards()![0].summary);

    expect(groupMock.load).toHaveBeenCalled();
  });

  it('cerrar el diálogo sin cambios no recarga', async () => {
    groups.set([{ id: 'g1', nombre: 'Avanzados', valores: ['medio'], totalAlumnos: 2 }]);
    await crear();
    dialogMock.open.mockReturnValue({ closed$: of(false) });
    groupMock.load.mockClear();

    component.openMembershipDialog(component.cards()![0].summary);

    expect(groupMock.load).not.toHaveBeenCalled();
  });

  it('sin permiso para ajustar la pertenencia no ofrece el botón de gestionar miembros', async () => {
    groups.set([{ id: 'g1', nombre: 'Avanzados', valores: ['medio'], totalAlumnos: 2 }]);
    permissionsMock.can.mockReturnValue(false);
    await crear();

    expect(fixture.nativeElement.textContent).not.toContain('Gestionar miembros');
  });

  it('abre el diálogo de asignar entrenadores con el grupo de la tarjeta', async () => {
    groups.set([{ id: 'g1', nombre: 'Avanzados', valores: ['medio'], totalAlumnos: 2 }]);
    await crear();

    component.openCoachesDialog(component.cards()![0].summary);

    expect(dialogMock.open).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ context: { grupoId: 'g1', nombre: 'Avanzados' } }),
    );
  });

  it('sin GROUP:ASSIGN_COACH no ofrece el botón de asignar entrenadores', async () => {
    groups.set([{ id: 'g1', nombre: 'Avanzados', valores: ['medio'], totalAlumnos: 2 }]);
    permissionsMock.can.mockImplementation(
      (resource: string, action: string) => !(resource === 'GROUP' && action === 'ASSIGN_COACH'),
    );
    await crear();

    expect(fixture.nativeElement.textContent).not.toContain('Asignar entrenadores');
  });

  it('si la carga falla ofrece reintentar en vez de dejar los esqueletos puestos', async () => {
    groups.set(undefined);
    groupMock.load.mockReturnValue(throwError(() => new Error('boom')) as Observable<never>);
    await crear();

    expect(component.loadFailed()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Reintentar');
  });
});
