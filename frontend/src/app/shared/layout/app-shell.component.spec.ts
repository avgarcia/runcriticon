import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AppShellComponent } from './app-shell.component';
import { Club, ClubService } from '../../core/club.service';
import { PermissionsService } from '../../core/permissions.service';
import { Session, SessionService } from '../../core/session.service';
import { GroupService } from '../../core/group.service';
import { StudentService } from '../../core/student.service';
import { TaxonomyService } from '../../core/taxonomy.service';

describe('AppShellComponent', () => {
  let fixture: ComponentFixture<AppShellComponent>;

  const club = signal<Club | null | undefined>({
    id: 'club-1',
    nombre: 'Club Atletismo Pinares',
    slug: null,
  });
  const session = signal<Session | null>({ userId: 'u-1', clubId: 'club-1', role: 'ADMIN' });

  const sessionMock = { session, close: jest.fn().mockReturnValue(of(undefined)) };
  const clubMock = { club, loadOnce: jest.fn(), reset: jest.fn() };
  const taxonomyMock = { reset: jest.fn() };
  const groupMock = { reset: jest.fn() };
  const studentMock = { reset: jest.fn() };
  const permissionsMock = {
    can: jest.fn().mockReturnValue(true),
    loadOnce: jest.fn(),
    reset: jest.fn(),
  };
  let navigate: jest.SpyInstance;

  // El Router va real (no mockeado): routerLink y routerLinkActive leen su routerState, y con un
  // doble se rompe el render entero del nav.
  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [AppShellComponent],
      providers: [
        provideRouter([]),
        { provide: SessionService, useValue: sessionMock },
        { provide: ClubService, useValue: clubMock },
        { provide: TaxonomyService, useValue: taxonomyMock },
        { provide: GroupService, useValue: groupMock },
        { provide: StudentService, useValue: studentMock },
        { provide: PermissionsService, useValue: permissionsMock },
      ],
    }).compileComponents();
    navigate = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();
  }

  beforeEach(() => {
    jest.clearAllMocks();
    TestBed.resetTestingModule();
    club.set({ id: 'club-1', nombre: 'Club Atletismo Pinares', slug: null });
    session.set({ userId: 'u-1', clubId: 'club-1', role: 'ADMIN' });
    permissionsMock.can.mockReturnValue(true);
  });

  it('muestra el nombre del club en la cabecera', async () => {
    await crear();

    expect(fixture.nativeElement.textContent).toContain('Club Atletismo Pinares');
  });

  it('omite el nombre del club si la ficha no existe', async () => {
    club.set(null);
    await crear();

    expect(fixture.nativeElement.textContent).not.toContain('Club Atletismo Pinares');
  });

  it('carga la ficha del club y los permisos una sola vez al iniciarse', async () => {
    await crear();

    expect(clubMock.loadOnce).toHaveBeenCalledTimes(1);
    expect(permissionsMock.loadOnce).toHaveBeenCalledTimes(1);
  });

  it('muestra Ajustes del club a quien tiene CLUB:UPDATE', async () => {
    await crear();

    expect(fixture.nativeElement.textContent).toContain('Ajustes del club');
    expect(permissionsMock.can).toHaveBeenCalledWith('CLUB', 'UPDATE');
  });

  it('muestra Taxonomía a quien tiene TAXONOMY:MANAGE', async () => {
    await crear();

    expect(fixture.nativeElement.textContent).toContain('Taxonomía');
    // MANAGE y no LIST: el entrenador también tiene LIST y esta pantalla es el editor del admin.
    expect(permissionsMock.can).toHaveBeenCalledWith('TAXONOMY', 'MANAGE');
  });

  it('muestra Grupos a quien tiene GROUP:LIST', async () => {
    await crear();

    expect(fixture.nativeElement.textContent).toContain('Grupos');
    expect(permissionsMock.can).toHaveBeenCalledWith('GROUP', 'LIST');
  });

  it('sin GROUP:LIST no aparece la entrada de Grupos', async () => {
    permissionsMock.can.mockReturnValue(false);
    await crear();

    expect(fixture.nativeElement.textContent).not.toContain('Grupos');
  });

  it('el entrenador no ve la entrada de Ajustes del club', async () => {
    session.set({ userId: 'u-2', clubId: 'club-1', role: 'ENTRENADOR' });
    permissionsMock.can.mockReturnValue(false);
    await crear();

    expect(fixture.nativeElement.textContent).not.toContain('Ajustes del club');
  });

  it('el entrenador con STUDENT:LIST ve Alumnos, pero no Entrenadores', async () => {
    session.set({ userId: 'u-2', clubId: 'club-1', role: 'ENTRENADOR' });
    permissionsMock.can.mockImplementation(
      (resource: string, action: string) => resource === 'STUDENT' && action === 'LIST',
    );
    await crear();

    expect(fixture.nativeElement.textContent).toContain('Alumnos');
    expect(fixture.nativeElement.textContent).not.toContain('Entrenadores');
    expect(permissionsMock.can).toHaveBeenCalledWith('STUDENT', 'LIST');
  });

  it('sin STUDENT:LIST no aparece la entrada de Alumnos', async () => {
    permissionsMock.can.mockReturnValue(false);
    await crear();

    expect(fixture.nativeElement.textContent).not.toContain('Alumnos');
  });

  it('el alumno no ve ninguna entrada de gestión', async () => {
    session.set({ userId: 'u-3', clubId: 'club-1', role: 'ALUMNO' });
    permissionsMock.can.mockReturnValue(false);
    await crear();

    const texto = fixture.nativeElement.textContent;
    expect(texto).not.toContain('Entrenadores');
    expect(texto).not.toContain('Alumnos');
    expect(texto).not.toContain('Ajustes del club');
    expect(texto).not.toContain('Taxonomía');
  });

  it('al cerrar sesión llama al servicio y navega a /login', async () => {
    await crear();

    fixture.componentInstance.close();

    expect(sessionMock.close).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('al cerrar sesión vacía las cachés de club, permisos y taxonomía', async () => {
    await crear();

    fixture.componentInstance.close();

    expect(clubMock.reset).toHaveBeenCalled();
    expect(permissionsMock.reset).toHaveBeenCalled();
    expect(taxonomyMock.reset).toHaveBeenCalled();
    expect(groupMock.reset).toHaveBeenCalled();
    expect(studentMock.reset).toHaveBeenCalled();
  });
});
