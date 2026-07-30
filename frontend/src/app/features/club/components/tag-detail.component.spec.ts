import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { of, throwError } from 'rxjs';
import { TagDetailComponent } from './tag-detail.component';
import { LabelDialogComponent, LabelDialogData } from './label-dialog.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { TagKey, TaxonomyService } from '../../../core/taxonomy.service';
import { ToastService } from '../../../core/toast.service';
import { ERROR_MESSAGES } from '../../../core/api/error-codes';

describe('TagDetailComponent', () => {
  let fixture: ComponentFixture<TagDetailComponent>;
  let component: TagDetailComponent;

  const taxonomyMock = {
    renameTag: jest.fn(),
    archiveTag: jest.fn(),
    reactivateTag: jest.fn(),
    createValue: jest.fn(),
    renameValue: jest.fn(),
    archiveValue: jest.fn(),
    reactivateValue: jest.fn(),
  };
  const toastMock = { success: jest.fn(), error: jest.fn() };
  const dialogMock = { open: jest.fn() };

  /** Devuelve el contexto con el que se abrió el diálogo de texto, para inspeccionarlo. */
  const contextoDelUltimoLabelDialog = (): LabelDialogData =>
    dialogMock.open.mock.calls.find((call) => call[0] === LabelDialogComponent)?.[1].context;

  const nivel: TagKey = {
    id: 'tag-nivel',
    nombre: 'nivel',
    valores: [
      { id: 'val-inic', valor: 'iniciación', metadata: { tipo: 'EMPTY' } },
      { id: 'val-medio', valor: 'medio', metadata: { tipo: 'EMPTY' } },
    ],
  };

  async function crear(tag: TagKey = nivel): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [TagDetailComponent],
      providers: [
        { provide: TaxonomyService, useValue: taxonomyMock },
        { provide: ToastService, useValue: toastMock },
        { provide: HlmDialogService, useValue: dialogMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TagDetailComponent);
    fixture.componentRef.setInput('tag', tag);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    jest.clearAllMocks();
    // El diálogo de texto devuelve el literal confirmado; el de confirmación, `true`.
    dialogMock.open.mockImplementation((componente: unknown) =>
      componente === ConfirmDialogComponent ? { closed$: of(true) } : { closed$: of('Nivel') },
    );
    Object.values(taxonomyMock).forEach((fn) => fn.mockReturnValue(of(undefined)));
  });

  it('pinta el nombre del tag y sus valores', async () => {
    await crear();

    expect(fixture.nativeElement.textContent).toContain('nivel');
    expect(fixture.nativeElement.querySelectorAll('li')).toHaveLength(2);
    expect(fixture.nativeElement.textContent).toContain('Valores (2)');
  });

  it('un tag activo ofrece archivar y deja añadir valores', async () => {
    await crear();

    expect(fixture.nativeElement.textContent).toContain('Archivar tag');
    const anadir = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) =>
      b.textContent?.includes('Añadir valor'),
    );
    expect(anadir.disabled).toBe(false);
  });

  it('un tag archivado avisa, bloquea añadir valores y ofrece reactivar', async () => {
    await crear({ ...nivel, archivadoEn: '2026-07-01T00:00:00Z' });

    expect(fixture.nativeElement.textContent).toContain('Este tag está archivado');
    expect(fixture.nativeElement.textContent).toContain('Reactivar tag');
    const anadir = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) =>
      b.textContent?.includes('Añadir valor'),
    );
    expect(anadir.disabled).toBe(true);
  });

  it('un valor archivado se marca como tal', async () => {
    await crear({
      ...nivel,
      valores: [{ id: 'val-inic', valor: 'iniciación', metadata: { tipo: 'EMPTY' }, archivadoEn: '2026-07-01T00:00:00Z' }],
    });

    expect(fixture.nativeElement.textContent).toContain('archivado');
  });

  it('un valor con metadata de carrera muestra fecha y distancia', async () => {
    await crear({
      ...nivel,
      valores: [
        {
          id: 'val-maraton',
          valor: 'Maratón Valencia',
          metadata: { tipo: 'RACE', fecha: '2099-12-06', distancia: '42K' },
        },
      ],
    });

    expect(fixture.nativeElement.textContent).toContain('42K');
    expect(fixture.nativeElement.textContent).toContain('2099');
    expect(fixture.nativeElement.textContent).not.toContain('pasada');
  });

  it('una carrera cuya fecha ya pasó se distingue', async () => {
    await crear({
      ...nivel,
      valores: [
        {
          id: 'val-vieja',
          valor: 'San Silvestre',
          metadata: { tipo: 'RACE', fecha: '2020-12-31', distancia: '10K' },
        },
      ],
    });

    expect(fixture.nativeElement.textContent).toContain('pasada');
  });

  it('renombrar el tag abre el diálogo con el nombre actual y el límite del contrato', async () => {
    await crear();

    component.renameTag();

    const contexto = contextoDelUltimoLabelDialog();
    expect(contexto.initialValue).toBe('nivel');
    expect(contexto.maxLength).toBe(40);
    expect(contexto.field).toBe('nombre');
    expect(toastMock.success).toHaveBeenCalled();
  });

  it('añadir valor abre el diálogo en blanco con el límite de los valores', async () => {
    await crear();

    component.addValue();

    const contexto = contextoDelUltimoLabelDialog();
    expect(contexto.initialValue).toBe('');
    expect(contexto.maxLength).toBe(60);
    expect(contexto.field).toBe('valor');
  });

  it('el diálogo llama a la operación del servicio que le corresponde', async () => {
    await crear();

    component.addValue();
    await contextoDelUltimoLabelDialog().submit('alto');

    expect(taxonomyMock.createValue).toHaveBeenCalledWith('tag-nivel', 'alto');
  });

  it('archivar el tag pide confirmación antes de llamar al backend', async () => {
    await crear();

    component.archiveTag();

    expect(dialogMock.open).toHaveBeenCalledWith(ConfirmDialogComponent, expect.anything());
    expect(taxonomyMock.archiveTag).toHaveBeenCalledWith('tag-nivel');
    expect(toastMock.success).toHaveBeenCalled();
  });

  it('sin confirmar, archivar no llama al backend', async () => {
    dialogMock.open.mockReturnValue({ closed$: of(undefined) });
    await crear();

    component.archiveTag();

    expect(taxonomyMock.archiveTag).not.toHaveBeenCalled();
  });

  it('reactivar no pide confirmación: no destruye nada', async () => {
    await crear({ ...nivel, archivadoEn: '2026-07-01T00:00:00Z' });

    component.reactivateTag();

    expect(dialogMock.open).not.toHaveBeenCalled();
    expect(taxonomyMock.reactivateTag).toHaveBeenCalledWith('tag-nivel');
  });

  it('si archivar falla avisa con el mensaje del catálogo, no con el del backend', async () => {
    await crear();
    taxonomyMock.archiveTag.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            error: { code: 'TAG_KEY_NOT_FOUND', message: 'texto interno del backend' },
          }),
      ),
    );

    component.archiveTag();

    expect(toastMock.error).toHaveBeenCalledWith(ERROR_MESSAGES['TAG_KEY_NOT_FOUND']);
    expect(toastMock.error).not.toHaveBeenCalledWith('texto interno del backend');
  });

  it('un 403 no duplica el aviso del interceptor', async () => {
    await crear();
    taxonomyMock.archiveTag.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

    component.archiveTag();

    expect(toastMock.error).not.toHaveBeenCalled();
  });
});
