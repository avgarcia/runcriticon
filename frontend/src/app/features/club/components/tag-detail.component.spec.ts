import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { of, throwError } from 'rxjs';
import { TagDetailComponent } from './tag-detail.component';
import {
  ArchiveImpactDialogComponent,
  ArchiveImpactDialogData,
} from './archive-impact-dialog.component';
import { LabelDialogComponent, LabelDialogData } from './label-dialog.component';
import { RaceValueDialogComponent, RaceValueDialogData } from './race-value-dialog.component';
import { TagArchiveImpact, TagKey, TaxonomyService } from '../../../core/taxonomy.service';
import { ToastService } from '../../../core/toast.service';
import { ERROR_MESSAGES } from '../../../core/api/error-codes';

describe('TagDetailComponent', () => {
  let fixture: ComponentFixture<TagDetailComponent>;
  let component: TagDetailComponent;

  const sinImpacto: TagArchiveImpact = { alumnosAfectados: 0, gruposQueLoRequieren: [] };

  const taxonomyMock = {
    renameTag: jest.fn(),
    archiveTag: jest.fn(),
    getTagArchiveImpact: jest.fn(),
    reactivateTag: jest.fn(),
    changeTagType: jest.fn(),
    createValue: jest.fn(),
    renameValue: jest.fn(),
    archiveValue: jest.fn(),
    getValueArchiveImpact: jest.fn(),
    reactivateValue: jest.fn(),
    setValueMetadata: jest.fn(),
  };
  const toastMock = { success: jest.fn(), error: jest.fn() };
  const dialogMock = { open: jest.fn() };

  /** Devuelve el contexto con el que se abrió el diálogo de texto, para inspeccionarlo. */
  const contextoDelUltimoLabelDialog = (): LabelDialogData =>
    dialogMock.open.mock.calls.find((call) => call[0] === LabelDialogComponent)?.[1].context;

  /** Devuelve el contexto con el que se abrió el diálogo de impacto de archivado. */
  const contextoDelUltimoArchiveDialog = (): ArchiveImpactDialogData =>
    dialogMock.open.mock.calls.find((call) => call[0] === ArchiveImpactDialogComponent)?.[1]
      .context;

  /** Devuelve el contexto con el que se abrió el diálogo de carrera. */
  const contextoDelUltimoRaceDialog = (): RaceValueDialogData =>
    dialogMock.open.mock.calls.find((call) => call[0] === RaceValueDialogComponent)?.[1].context;

  const nivel: TagKey = {
    id: 'tag-nivel',
    nombre: 'nivel',
    tipo: 'SIMPLE',
    valores: [
      { id: 'val-inic', valor: 'iniciación', metadata: { tipo: 'EMPTY' } },
      { id: 'val-medio', valor: 'medio', metadata: { tipo: 'EMPTY' } },
    ],
  };

  const objetivo: TagKey = {
    id: 'tag-objetivo',
    nombre: 'objetivo',
    tipo: 'RACE',
    valores: [{ id: 'val-sin-carrera', valor: 'sin carrera', metadata: { tipo: 'EMPTY' } }],
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
    // El diálogo de texto devuelve el literal confirmado; el de archivado y el de carrera, `true`.
    dialogMock.open.mockImplementation((componente: unknown) =>
      componente === ArchiveImpactDialogComponent || componente === RaceValueDialogComponent
        ? { closed$: of(true) }
        : { closed$: of('Nivel') },
    );
    Object.values(taxonomyMock).forEach((fn) => fn.mockReturnValue(of(undefined)));
    taxonomyMock.getTagArchiveImpact.mockReturnValue(of(sinImpacto));
    taxonomyMock.getValueArchiveImpact.mockReturnValue(of(sinImpacto));
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
    const anadir = [...fixture.nativeElement.querySelectorAll('button')].find(
      (b: HTMLButtonElement) => b.textContent?.includes('Añadir valor'),
    );
    expect(anadir.disabled).toBe(false);
  });

  it('un tag archivado avisa, bloquea añadir valores y ofrece reactivar', async () => {
    await crear({ ...nivel, archivadoEn: '2026-07-01T00:00:00Z' });

    expect(fixture.nativeElement.textContent).toContain('Este tag está archivado');
    expect(fixture.nativeElement.textContent).toContain('Reactivar tag');
    const anadir = [...fixture.nativeElement.querySelectorAll('button')].find(
      (b: HTMLButtonElement) => b.textContent?.includes('Añadir valor'),
    );
    expect(anadir.disabled).toBe(true);
  });

  it('un valor archivado se marca como tal', async () => {
    await crear({
      ...nivel,
      valores: [
        {
          id: 'val-inic',
          valor: 'iniciación',
          metadata: { tipo: 'EMPTY' },
          archivadoEn: '2026-07-01T00:00:00Z',
        },
      ],
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

  it('archivar el tag consulta el impacto y pide confirmación antes de llamar al backend', async () => {
    await crear();

    component.archiveTag();

    expect(taxonomyMock.getTagArchiveImpact).toHaveBeenCalledWith('tag-nivel');
    expect(dialogMock.open).toHaveBeenCalledWith(ArchiveImpactDialogComponent, expect.anything());
    expect(taxonomyMock.archiveTag).toHaveBeenCalledWith('tag-nivel');
    expect(toastMock.success).toHaveBeenCalled();
  });

  it('el diálogo de impacto recibe el número de alumnos afectados', async () => {
    taxonomyMock.getTagArchiveImpact.mockReturnValue(
      of({ alumnosAfectados: 3, gruposQueLoRequieren: [] }),
    );
    await crear();

    component.archiveTag();

    expect(contextoDelUltimoArchiveDialog().impact.alumnosAfectados).toBe(3);
  });

  it('sin confirmar, archivar no llama al backend', async () => {
    dialogMock.open.mockReturnValue({ closed$: of(undefined) });
    await crear();

    component.archiveTag();

    expect(taxonomyMock.archiveTag).not.toHaveBeenCalled();
  });

  it('si un grupo vivo lo requiere, el diálogo lo recibe y no se archiva sin cerrar antes', async () => {
    taxonomyMock.getTagArchiveImpact.mockReturnValue(
      of({
        alumnosAfectados: 0,
        gruposQueLoRequieren: [
          { id: 'g1', nombre: 'Iniciación', perderiaTodosLosTagsRequeridos: true },
        ],
      }),
    );
    // Un diálogo bloqueado solo ofrece cerrar: no emite `true`.
    dialogMock.open.mockReturnValue({ closed$: of(undefined) });
    await crear();

    component.archiveTag();

    expect(contextoDelUltimoArchiveDialog().impact.gruposQueLoRequieren).toHaveLength(1);
    expect(taxonomyMock.archiveTag).not.toHaveBeenCalled();
  });

  it('si falla la consulta de impacto avisa por toast y no abre el diálogo', async () => {
    await crear();
    taxonomyMock.getTagArchiveImpact.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            error: { code: 'TAG_KEY_NOT_FOUND', message: 'texto interno del backend' },
          }),
      ),
    );

    component.archiveTag();

    expect(dialogMock.open).not.toHaveBeenCalled();
    expect(toastMock.error).toHaveBeenCalledWith(ERROR_MESSAGES['TAG_KEY_NOT_FOUND']);
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
    taxonomyMock.archiveTag.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403 })),
    );

    component.archiveTag();

    expect(toastMock.error).not.toHaveBeenCalled();
  });

  it('archivar un valor consulta su propio impacto, no el del tag', async () => {
    await crear();
    const valor = nivel.valores[0];

    component.archiveValue(valor);

    expect(taxonomyMock.getValueArchiveImpact).toHaveBeenCalledWith('val-inic');
    expect(taxonomyMock.archiveValue).toHaveBeenCalledWith('val-inic');
    expect(toastMock.success).toHaveBeenCalled();
  });

  // --- tipo del eje y metadata de carrera --------------------------------------------------

  it('muestra el tipo del eje y ofrece convertirlo', async () => {
    await crear();

    expect(fixture.nativeElement.textContent).toContain('Enum simple');
    expect(fixture.nativeElement.textContent).toContain('Convertir en carrera');
  });

  it('un eje de tipo carrera muestra su pill y ofrece convertir a simple', async () => {
    await crear(objetivo);

    expect(fixture.nativeElement.textContent).toContain('Enum con metadata');
    expect(fixture.nativeElement.textContent).toContain('Convertir en simple');
  });

  it('toggleType cambia el tipo del eje sin pedir confirmación', async () => {
    await crear();

    component.toggleType();

    expect(dialogMock.open).not.toHaveBeenCalled();
    expect(taxonomyMock.changeTagType).toHaveBeenCalledWith('tag-nivel', 'RACE');
    expect(toastMock.success).toHaveBeenCalled();
  });

  it('degradar un eje con carreras vivas avisa con el mensaje del catálogo', async () => {
    await crear(objetivo);
    taxonomyMock.changeTagType.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { code: 'TAG_KEY_HAS_RACE_VALUES', message: 'texto interno del backend' },
          }),
      ),
    );

    component.toggleType();

    expect(taxonomyMock.changeTagType).toHaveBeenCalledWith('tag-objetivo', 'SIMPLE');
    expect(toastMock.error).toHaveBeenCalledWith(ERROR_MESSAGES['TAG_KEY_HAS_RACE_VALUES']);
  });

  it('añadir valor en un eje simple abre el diálogo de texto, no el de carrera', async () => {
    await crear();

    component.addValue();

    expect(dialogMock.open).toHaveBeenCalledWith(LabelDialogComponent, expect.anything());
  });

  it('añadir valor en un eje de carrera abre el diálogo de carrera pidiendo también el literal', async () => {
    await crear(objetivo);

    component.addValue();

    const contexto = contextoDelUltimoRaceDialog();
    expect(contexto.valueField).toEqual({ initialValue: '', maxLength: 60 });
    await contexto.submit('Maratón', { tipo: 'RACE', fecha: '2026-12-06', distancia: '42K' });
    expect(taxonomyMock.createValue).toHaveBeenCalledWith('tag-objetivo', 'Maratón', {
      tipo: 'RACE',
      fecha: '2026-12-06',
      distancia: '42K',
    });
  });

  it('editar carrera abre el diálogo de carrera sin pedir el literal y con la metadata actual', async () => {
    const conCarrera: TagKey = {
      ...objetivo,
      valores: [
        {
          id: 'val-maraton',
          valor: 'Maratón',
          metadata: { tipo: 'RACE', fecha: '2026-12-06', distancia: '42K' },
        },
      ],
    };
    await crear(conCarrera);

    component.editRaceMetadata(conCarrera.valores[0]);

    const contexto = contextoDelUltimoRaceDialog();
    expect(contexto.valueField).toBeNull();
    expect(contexto.initialDate).toBe('2026-12-06');
    expect(contexto.initialDistance).toBe('42K');
    await contexto.submit(undefined, { tipo: 'EMPTY' });
    expect(taxonomyMock.setValueMetadata).toHaveBeenCalledWith('val-maraton', { tipo: 'EMPTY' });
  });
});
