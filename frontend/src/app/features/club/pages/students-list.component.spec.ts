import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { signal } from '@angular/core';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { Observable, of, throwError } from 'rxjs';
import { PermissionsService } from '../../../core/permissions.service';
import { StudentService, StudentSummary } from '../../../core/student.service';
import { Taxonomy, TaxonomyService } from '../../../core/taxonomy.service';
import { ToastService } from '../../../core/toast.service';
import { StudentsListComponent } from './students-list.component';

describe('StudentsListComponent', () => {
  const taxonomia = {
    tags: [
      {
        id: 'nivel',
        nombre: 'nivel',
        archivadoEn: null,
        valores: [
          { id: 'medio', valor: 'medio', archivadoEn: null },
          { id: 'retirado', valor: 'retirado', archivadoEn: '2026-07-01T10:00:00Z' },
        ],
      },
      { id: 'sin-valores', nombre: 'vacío', archivadoEn: null, valores: [] },
    ],
  } as unknown as Taxonomy;

  const pedro: StudentSummary = {
    id: 'a1',
    nombre: 'Pedro Cordero',
    email: 'pedro@club.test',
    estado: 'ACTIVO',
    valores: ['medio'],
  };

  const ana: StudentSummary = {
    id: 'a2',
    nombre: 'Ana Ruiz',
    email: 'ana@club.test',
    estado: 'ACTIVO',
    valores: [],
  };

  const zoe: StudentSummary = {
    id: 'a3',
    nombre: 'Zoe Martín',
    email: 'zoe@club.test',
    estado: 'ACTIVO',
    valores: [],
  };

  const students = signal<StudentSummary[] | undefined>(undefined);
  const taxonomy = signal<Taxonomy | undefined>(undefined);
  const studentMock = { students, load: jest.fn() };
  const taxonomyMock = { taxonomy, load: jest.fn() };
  const permissionsMock = { can: jest.fn().mockReturnValue(true) };
  const toastMock = { success: jest.fn(), error: jest.fn() };
  const dialogMock = { open: jest.fn() };

  let fixture: ComponentFixture<StudentsListComponent>;
  let component: StudentsListComponent;

  beforeEach(() => {
    jest.clearAllMocks();
    students.set([]);
    taxonomy.set(taxonomia);
    studentMock.load.mockReturnValue(of([]));
    taxonomyMock.load.mockReturnValue(of(taxonomia));
    permissionsMock.can.mockReturnValue(true);
  });

  function crear(): void {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [StudentsListComponent],
      providers: [
        { provide: StudentService, useValue: studentMock },
        { provide: TaxonomyService, useValue: taxonomyMock },
        { provide: PermissionsService, useValue: permissionsMock },
        { provide: ToastService, useValue: toastMock },
        { provide: HlmDialogService, useValue: dialogMock },
      ],
    });
    fixture = TestBed.createComponent(StudentsListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('carga alumnos y taxonomía al entrar, sin ningún filtro', fakeAsync(() => {
    crear();
    tick();

    expect(studentMock.load).toHaveBeenCalledWith([]);
    expect(taxonomyMock.load).toHaveBeenCalled();
  }));

  it('traduce los tags del alumno cruzando con la taxonomía', fakeAsync(() => {
    students.set([pedro]);
    crear();
    tick();

    expect(component.rows()?.[0].tags).toEqual([
      { id: 'medio', label: 'nivel: medio', archivado: false },
    ]);
  }));

  it('un valor archivado se sigue mostrando en la fila', fakeAsync(() => {
    students.set([{ ...pedro, valores: ['retirado'] }]);
    crear();
    tick();

    expect(component.rows()?.[0].tags).toEqual([
      { id: 'retirado', label: 'nivel: retirado', archivado: true },
    ]);
  }));

  it('elegir un valor de un eje recalcula el filtro y vuelve a pedir la lista', fakeAsync(() => {
    crear();
    tick();
    studentMock.load.mockClear();

    component.selectAxisValue('nivel', 'medio');
    tick(250);

    expect(studentMock.load).toHaveBeenCalledWith(['medio']);
    expect(component.activeChips()).toEqual([{ tagId: 'nivel', label: 'nivel: medio' }]);
  }));

  it('quitar un chip elimina ese eje del filtro', fakeAsync(() => {
    crear();
    tick();
    component.selectAxisValue('nivel', 'medio');
    tick(250);
    studentMock.load.mockClear();

    component.clearAxis('nivel');
    tick(250);

    expect(studentMock.load).toHaveBeenCalledWith([]);
    expect(component.activeChips()).toEqual([]);
  }));

  it('sin alumnos y sin filtro invita a dar de alta al primero', fakeAsync(() => {
    crear();
    tick();

    expect(fixture.nativeElement.textContent).toContain('Aún no tienes alumnos');
  }));

  it('sin resultados con un filtro activo lo distingue de la lista realmente vacía', fakeAsync(() => {
    crear();
    tick();
    component.selectAxisValue('nivel', 'medio');
    tick(250);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ningún alumno cumple estos filtros');
    expect(fixture.nativeElement.textContent).not.toContain('Aún no tienes alumnos');
  }));

  it('si la carga inicial falla ofrece reintentar', fakeAsync(() => {
    students.set(undefined);
    studentMock.load.mockReturnValue(throwError(() => new Error('boom')) as Observable<never>);
    crear();
    tick();

    expect(component.loadFailed()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Reintentar');
  }));

  it('dar de alta abre el diálogo y recarga la lista al cerrar con email', fakeAsync(() => {
    crear();
    tick();
    dialogMock.open.mockReturnValue({ closed$: of('marta@club.test') });
    studentMock.load.mockClear();

    component.openInviteDialog();
    tick();

    expect(toastMock.success).toHaveBeenCalledWith('Invitación enviada a marta@club.test');
    expect(studentMock.load).toHaveBeenCalled();
  }));

  it('cancelar el alta no recarga ni avisa', fakeAsync(() => {
    crear();
    tick();
    dialogMock.open.mockReturnValue({ closed$: of(undefined) });
    studentMock.load.mockClear();

    component.openInviteDialog();
    tick();

    expect(toastMock.success).not.toHaveBeenCalled();
    expect(studentMock.load).not.toHaveBeenCalled();
  }));

  it('sin permiso de alta no ofrece el botón', fakeAsync(() => {
    permissionsMock.can.mockImplementation((_resource: string, action: string) => action !== 'INVITE');
    crear();
    tick();

    expect(fixture.nativeElement.textContent).not.toContain('Dar de alta alumno');
  }));

  it('sin STUDENT:CLASSIFY no ofrece el botón de editar tags', fakeAsync(() => {
    students.set([pedro]);
    permissionsMock.can.mockImplementation((_resource: string, action: string) => action !== 'CLASSIFY');
    crear();
    tick();

    expect(fixture.nativeElement.textContent).not.toContain('Editar tags');
  }));

  it('editar tags abre el dialogo con el alumno y los ejes de la fila', fakeAsync(() => {
    students.set([pedro]);
    crear();
    tick();
    dialogMock.open.mockReturnValue({ closed$: of(undefined) });

    component.openEditTagsDialog(component.rows()![0]);

    expect(dialogMock.open).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({
        context: expect.objectContaining({
          studentId: pedro.id,
          studentName: pedro.nombre,
          currentValueIds: pedro.valores,
        }),
      }),
    );
  }));

  describe('etiquetado en masa', () => {
    it('marcar un alumno lo mete en la selección visible y muestra la bulk-bar', fakeAsync(() => {
      students.set([pedro, ana]);
      crear();
      tick();

      component.toggleStudent(pedro.id, true);
      fixture.detectChanges();

      expect(component.selectionCount()).toBe(1);
      expect(fixture.nativeElement.textContent).toContain('1 seleccionado');
    }));

    it('toggleAllVisible selecciona solo los alumnos que deja ver el filtro activo', fakeAsync(() => {
      students.set([pedro, ana, zoe]);
      crear();
      tick();
      // Filtra a solo Pedro: `rows()`/`visibleIds()` pasan a tener un único alumno aunque el club tenga tres.
      students.set([pedro]);
      fixture.detectChanges();

      component.toggleAllVisible(true);

      expect(component.selectionCount()).toBe(1);
      expect(component.selectedVisibleIds()).toEqual([pedro.id]);
    }));

    it('una selección parcial deja la cabecera en estado indeterminado', fakeAsync(() => {
      students.set([pedro, ana]);
      crear();
      tick();

      component.toggleStudent(pedro.id, true);
      fixture.detectChanges();

      expect(component.someVisibleSelected()).toBe(true);
      expect(component.allVisibleSelected()).toBe(false);
    }));

    it('seleccionar a todos los visibles no deja la cabecera indeterminada', fakeAsync(() => {
      students.set([pedro, ana]);
      crear();
      tick();

      component.toggleAllVisible(true);

      expect(component.allVisibleSelected()).toBe(true);
      expect(component.someVisibleSelected()).toBe(false);
    }));

    it('cambiar el filtro vacía la selección', fakeAsync(() => {
      students.set([pedro, ana]);
      crear();
      tick();
      component.toggleStudent(pedro.id, true);
      expect(component.selectionCount()).toBe(1);

      component.selectAxisValue('nivel', 'medio');
      tick(250);

      expect(component.selectionCount()).toBe(0);
    }));

    it('abre el diálogo en masa con el modo, la selección visible y los ejes', fakeAsync(() => {
      students.set([pedro, ana]);
      crear();
      tick();
      component.toggleStudent(pedro.id, true);
      component.toggleStudent(ana.id, true);
      dialogMock.open.mockReturnValue({ closed$: of(undefined) });

      component.openBulkTagDialog('assign');

      expect(dialogMock.open).toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({
          context: expect.objectContaining({
            mode: 'assign',
            studentIds: [pedro.id, ana.id],
          }),
        }),
      );
    }));

    it('cerrar el diálogo con un número avisa, recarga con los filtros activos y vacía la selección', fakeAsync(() => {
      students.set([pedro, ana]);
      crear();
      tick();
      component.toggleStudent(pedro.id, true);
      component.toggleStudent(ana.id, true);
      dialogMock.open.mockReturnValue({ closed$: of(2) });
      studentMock.load.mockClear();

      component.openBulkTagDialog('assign');
      tick();

      expect(toastMock.success).toHaveBeenCalledWith('2 alumnos actualizados');
      expect(studentMock.load).toHaveBeenCalledWith(component.selectedValueIds());
      expect(component.selectionCount()).toBe(0);
    }));

    it('cerrar el diálogo con 1 usa la rama singular del mensaje', fakeAsync(() => {
      students.set([pedro]);
      crear();
      tick();
      component.toggleStudent(pedro.id, true);
      dialogMock.open.mockReturnValue({ closed$: of(1) });

      component.openBulkTagDialog('unassign');
      tick();

      expect(toastMock.success).toHaveBeenCalledWith('1 alumno actualizado');
    }));

    it('cerrar el diálogo con 0 sigue avisando: la operación fue correcta, no había nada que cambiar', fakeAsync(() => {
      students.set([pedro]);
      crear();
      tick();
      component.toggleStudent(pedro.id, true);
      dialogMock.open.mockReturnValue({ closed$: of(0) });

      component.openBulkTagDialog('assign');
      tick();

      expect(toastMock.success).toHaveBeenCalledWith('0 alumnos actualizados');
    }));

    it('cancelar el diálogo (cierra sin valor) no avisa ni recarga', fakeAsync(() => {
      students.set([pedro]);
      crear();
      tick();
      component.toggleStudent(pedro.id, true);
      dialogMock.open.mockReturnValue({ closed$: of(undefined) });
      studentMock.load.mockClear();

      component.openBulkTagDialog('assign');
      tick();

      expect(toastMock.success).not.toHaveBeenCalled();
      expect(studentMock.load).not.toHaveBeenCalled();
    }));

    it('sin STUDENT:CLASSIFY no pinta casillas ni bulk-bar', fakeAsync(() => {
      students.set([pedro]);
      permissionsMock.can.mockImplementation((_resource: string, action: string) => action !== 'CLASSIFY');
      crear();
      tick();

      component.toggleStudent(pedro.id, true);
      fixture.detectChanges();

      expect(component.bulkBarVisible()).toBe(false);
      expect(fixture.nativeElement.querySelector('rc-checkbox')).toBeNull();
    }));
  });
});
