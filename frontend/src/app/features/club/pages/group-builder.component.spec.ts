import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { Observable, of, throwError } from 'rxjs';
import { GroupService } from '../../../core/group.service';
import { PermissionsService } from '../../../core/permissions.service';
import { Taxonomy, TaxonomyService } from '../../../core/taxonomy.service';
import { ToastService } from '../../../core/toast.service';
import { GroupBuilderComponent } from './group-builder.component';

describe('GroupBuilderComponent', () => {
  const taxonomiaCompleta = {
    tags: [
      {
        id: 'nivel',
        nombre: 'nivel',
        archivadoEn: null,
        valores: [{ id: 'medio', valor: 'medio', archivadoEn: null }],
      },
      {
        id: 'objetivo',
        nombre: 'objetivo',
        archivadoEn: null,
        valores: [{ id: 'maraton', valor: 'Maratón Valencia', archivadoEn: null }],
      },
      // Un eje archivado y otro sin valores vivos: ninguno sirve para construir un filtro.
      {
        id: 'terreno',
        nombre: 'terreno',
        archivadoEn: '2026-07-01T10:00:00Z',
        valores: [{ id: 'trail', valor: 'trail', archivadoEn: null }],
      },
      { id: 'vacio', nombre: 'vacío', archivadoEn: null, valores: [] },
    ],
  } as unknown as Taxonomy;

  const taxonomy = signal<Taxonomy | undefined>(undefined);
  const taxonomyMock = { taxonomy, load: jest.fn() };
  const groupMock = { previewMembers: jest.fn(), create: jest.fn() };
  const toastMock = { success: jest.fn(), error: jest.fn() };
  const dialogMock = { open: jest.fn() };
  const permissionsMock = { can: jest.fn().mockReturnValue(true) };

  let fixture: ComponentFixture<GroupBuilderComponent>;
  let component: GroupBuilderComponent;
  let navigate: jest.SpyInstance;

  beforeEach(() => {
    jest.clearAllMocks();
    taxonomy.set(taxonomiaCompleta);
    taxonomyMock.load.mockReturnValue(of(taxonomiaCompleta));
    groupMock.previewMembers.mockReturnValue(of({ total: 0, alumnos: [] }));
    groupMock.create.mockReturnValue(of({ id: 'g1', nombre: 'Trail', valores: [] }));
    permissionsMock.can.mockReturnValue(true);
  });

  // Sin `await`: fakeAsync no admite una función de test async, y con plantilla en línea no hace
  // falta compilar los componentes aparte.
  function crear(): void {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [GroupBuilderComponent],
      providers: [
        provideRouter([]),
        { provide: GroupService, useValue: groupMock },
        { provide: TaxonomyService, useValue: taxonomyMock },
        { provide: ToastService, useValue: toastMock },
        { provide: HlmDialogService, useValue: dialogMock },
        { provide: PermissionsService, useValue: permissionsMock },
      ],
    });
    // El Router va real (routerLink lo necesita para renderizar), pero la navegación se intercepta:
    // el módulo de test no declara rutas y una navegación real reventaría por no encontrarlas.
    navigate = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(GroupBuilderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('solo ofrece ejes activos con algún valor vivo', fakeAsync(() => {
    crear();
    tick(250);

    expect(component.axes()?.map((axis) => axis.id)).toEqual(['nivel', 'objetivo']);
  }));

  // Quién entra en un filtro vacío lo decide el servidor; asumir cero aquí duplicaría esa regla.
  it('al entrar consulta la previsualización con el filtro vacío', fakeAsync(() => {
    crear();
    tick(250);

    expect(groupMock.previewMembers).toHaveBeenCalledWith([]);
  }));

  it('dos ediciones seguidas producen una sola consulta, la de la última', fakeAsync(() => {
    crear();
    tick(250);
    groupMock.previewMembers.mockClear();

    component.addCondition();
    component.updateCondition(0, { tagId: 'nivel', valueId: 'medio' });
    component.updateCondition(0, { tagId: 'nivel', valueId: null });
    component.addCondition();
    component.updateCondition(1, { tagId: 'objetivo', valueId: 'maraton' });
    tick(250);

    expect(groupMock.previewMembers).toHaveBeenCalledTimes(1);
    expect(groupMock.previewMembers).toHaveBeenCalledWith(['maraton']);
  }));

  // Si el error escapa del switchMap, la suscripción se completa y la vista previa queda muerta el
  // resto de la sesión: el contador se quedaría clavado sin que nadie sepa por qué.
  it('un conflicto pinta el aviso y la siguiente edición vuelve a consultar', fakeAsync(() => {
    crear();
    tick(250);
    groupMock.previewMembers.mockReturnValueOnce(
      throwError(
        () =>
          new HttpErrorResponse({ status: 409, error: { code: 'TAG_VALUE_NOT_ASSIGNABLE' } }),
      ) as Observable<never>,
    );

    component.addCondition();
    component.updateCondition(0, { tagId: 'nivel', valueId: 'medio' });
    tick(250);

    expect(component.previewError()).not.toBeNull();

    groupMock.previewMembers.mockReturnValue(of({ total: 4, alumnos: [] }));
    component.addCondition();
    component.updateCondition(1, { tagId: 'objetivo', valueId: 'maraton' });
    tick(250);

    expect(component.members()?.total).toBe(4);
    expect(component.previewError()).toBeNull();
  }));

  it('sin alumnos pide confirmación antes de crear el grupo', fakeAsync(() => {
    crear();
    tick(250);
    dialogMock.open.mockReturnValue({ closed$: of(true) });
    component.name.set('Grupo vacío');

    component.save();
    tick();

    expect(dialogMock.open).toHaveBeenCalled();
    expect(groupMock.create).toHaveBeenCalledWith('Grupo vacío', []);
  }));

  it('si se cancela la confirmación no se crea nada', fakeAsync(() => {
    crear();
    tick(250);
    dialogMock.open.mockReturnValue({ closed$: of(undefined) });
    component.name.set('Grupo vacío');

    component.save();
    tick();

    expect(groupMock.create).not.toHaveBeenCalled();
  }));

  it('con alumnos guarda directamente y vuelve al listado', fakeAsync(() => {
    crear();
    groupMock.previewMembers.mockReturnValue(
      of({ total: 3, alumnos: [{ id: 'a1', nombre: 'Ana' }] }),
    );
    component.addCondition();
    component.updateCondition(0, { tagId: 'nivel', valueId: 'medio' });
    tick(250);
    component.name.set('Nivel medio');

    component.save();
    tick();

    expect(dialogMock.open).not.toHaveBeenCalled();
    expect(groupMock.create).toHaveBeenCalledWith('Nivel medio', ['medio']);
    expect(toastMock.success).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/club/grupos']);
  }));

  it('un nombre en blanco no guarda', fakeAsync(() => {
    crear();
    tick(250);
    component.name.set('   ');

    expect(component.canSave()).toBe(false);
    component.save();
    tick();

    expect(groupMock.create).not.toHaveBeenCalled();
  }));

  it('sin ejes utilizables no consulta la previsualización', fakeAsync(() => {
    taxonomy.set({ tags: [] } as unknown as Taxonomy);
    taxonomyMock.load.mockReturnValue(of({ tags: [] }));
    crear();
    tick(250);

    expect(fixture.nativeElement.textContent).toContain('todavía no se puede construir un filtro');
  }));

  it('sin permiso sobre la taxonomía no ofrece el atajo para crear tags', fakeAsync(() => {
    taxonomy.set({ tags: [] } as unknown as Taxonomy);
    taxonomyMock.load.mockReturnValue(of({ tags: [] }));
    permissionsMock.can.mockReturnValue(false);
    crear();
    tick(250);

    expect(fixture.nativeElement.textContent).not.toContain('Ir a la taxonomía');
    expect(fixture.nativeElement.textContent).toContain('Pídele al administrador');
  }));

  it('si la taxonomía no carga ofrece reintentar', fakeAsync(() => {
    taxonomy.set(undefined);
    taxonomyMock.load.mockReturnValue(throwError(() => new Error('boom')) as Observable<never>);
    crear();
    tick(250);

    expect(component.loadFailed()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Reintentar');
  }));
});
