import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { ClubSettingsComponent } from './club-settings.component';
import { Club, ClubService } from '../../../core/club.service';
import { PermissionsService } from '../../../core/permissions.service';
import { ToastService } from '../../../core/toast.service';
import { ERROR_MESSAGES } from '../../../core/api/error-codes';

describe('ClubSettingsComponent', () => {
  let fixture: ComponentFixture<ClubSettingsComponent>;

  const club = signal<Club | null | undefined>({
    id: 'club-1',
    nombre: 'Club Atletismo Pinares',
    slug: null,
  });
  const clubMock = { club, rename: jest.fn().mockReturnValue(of(undefined)) };
  const permissionsMock = { can: jest.fn().mockReturnValue(true) };
  const toastMock = { success: jest.fn(), error: jest.fn() };

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ClubSettingsComponent],
      providers: [
        { provide: ClubService, useValue: clubMock },
        { provide: PermissionsService, useValue: permissionsMock },
        { provide: ToastService, useValue: toastMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ClubSettingsComponent);
    fixture.detectChanges();
  }

  beforeEach(() => {
    jest.clearAllMocks();
    TestBed.resetTestingModule();
    club.set({ id: 'club-1', nombre: 'Club Atletismo Pinares', slug: null });
    clubMock.rename.mockReturnValue(of({ id: 'club-1', nombre: 'Nuevo', slug: null }));
    permissionsMock.can.mockReturnValue(true);
  });

  it('rellena el formulario con el nombre actual del club', async () => {
    await crear();

    expect(fixture.componentInstance.form.controls.nombre.value).toBe('Club Atletismo Pinares');
  });

  it('muestra el identificador como solo lectura', async () => {
    await crear();

    const slug: HTMLInputElement = fixture.nativeElement.querySelector('#slug');
    expect(slug.readOnly).toBe(true);
  });

  it('muestra «Sin asignar» cuando el club no tiene identificador', async () => {
    await crear();

    const slug: HTMLInputElement = fixture.nativeElement.querySelector('#slug');
    expect(slug.value).toBe('Sin asignar');
  });

  it('no permite guardar con el nombre vacío', async () => {
    await crear();

    fixture.componentInstance.form.controls.nombre.setValue('');

    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('no permite guardar un nombre de solo espacios', async () => {
    await crear();

    fixture.componentInstance.form.controls.nombre.setValue('   ');

    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('no permite guardar con más de 200 caracteres', async () => {
    await crear();

    fixture.componentInstance.form.controls.nombre.setValue('x'.repeat(201));

    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('al guardar delega en el servicio, que actualiza el estado compartido', async () => {
    await crear();

    fixture.componentInstance.form.controls.nombre.setValue('Club Atletismo Central');
    await fixture.componentInstance.submit();

    expect(clubMock.rename).toHaveBeenCalledWith('Club Atletismo Central');
    expect(toastMock.success).toHaveBeenCalled();
  });

  it('un 400 sobre el nombre pinta el mensaje traducido, no el del backend', async () => {
    await crear();
    clubMock.rename.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { code: 'INVALID_INPUT', field: 'nombre', message: 'too_long' },
          }),
      ),
    );

    fixture.componentInstance.form.controls.nombre.setValue('Nombre inválido');
    await fixture.componentInstance.submit();

    const error = fixture.componentInstance.form.controls.nombre.getError('backend');
    expect(error).toBe(ERROR_MESSAGES['INVALID_INPUT']);
    expect(error).not.toContain('too_long');
  });

  it('un 403 no pinta mensaje propio: ya lo avisa el interceptor', async () => {
    await crear();
    clubMock.rename.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

    await fixture.componentInstance.submit();

    expect(fixture.componentInstance.errorMessage()).toBeNull();
    expect(fixture.componentInstance.form.controls.nombre.hasError('backend')).toBe(false);
  });

  it('oculta el botón de guardar sin permiso CLUB:UPDATE', async () => {
    permissionsMock.can.mockReturnValue(false);
    await crear();

    expect(fixture.nativeElement.querySelector('button[type="submit"]')).toBeNull();
  });

  it('muestra un aviso si la ficha del club no existe', async () => {
    club.set(null);
    await crear();

    expect(fixture.nativeElement.textContent).toContain('No se ha encontrado la ficha del club');
    expect(fixture.nativeElement.querySelector('#nombre')).toBeNull();
  });

  it('muestra el esqueleto de carga mientras la ficha no ha llegado', async () => {
    club.set(undefined);
    await crear();

    expect(fixture.nativeElement.querySelector('hlm-skeleton')).not.toBeNull();
  });
});
