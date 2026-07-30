import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { of, throwError } from 'rxjs';
import { TaxonomyEditorComponent } from './taxonomy-editor.component';
import { LabelDialogData } from '../components/label-dialog.component';
import { Taxonomy, TaxonomyService } from '../../../core/taxonomy.service';
import { ToastService } from '../../../core/toast.service';

describe('TaxonomyEditorComponent', () => {
  let fixture: ComponentFixture<TaxonomyEditorComponent>;
  let component: TaxonomyEditorComponent;

  const taxonomy = signal<Taxonomy | undefined>(undefined);
  const taxonomyMock = {
    taxonomy,
    load: jest.fn(),
    createTag: jest.fn(),
  };
  const toastMock = { success: jest.fn(), error: jest.fn() };
  const dialogMock = { open: jest.fn() };

  const cargada: Taxonomy = {
    tags: [
      {
        id: 'tag-nivel',
        nombre: 'nivel',
        valores: [
          { id: 'val-inic', valor: 'iniciación', metadata: { tipo: 'EMPTY' } },
          { id: 'val-medio', valor: 'medio', metadata: { tipo: 'EMPTY' } },
        ],
      },
      { id: 'tag-viejo', nombre: 'grupo-antiguo', valores: [], archivadoEn: '2026-01-01T00:00:00Z' },
    ],
  };

  async function crear(): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [TaxonomyEditorComponent],
      providers: [
        { provide: TaxonomyService, useValue: taxonomyMock },
        { provide: ToastService, useValue: toastMock },
        { provide: HlmDialogService, useValue: dialogMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TaxonomyEditorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    jest.clearAllMocks();
    taxonomy.set(undefined);
    taxonomyMock.load.mockReturnValue(of(cargada));
    taxonomyMock.createTag.mockReturnValue(of({ id: 'tag-nuevo', nombre: 'objetivo', valores: [] }));
    dialogMock.open.mockReturnValue({ closed$: of('objetivo') });
  });

  it('carga la taxonomía al entrar en la pantalla', async () => {
    await crear();

    expect(taxonomyMock.load).toHaveBeenCalled();
  });

  it('muestra esqueletos mientras la taxonomía no ha llegado', async () => {
    await crear();

    expect(fixture.nativeElement.querySelector('hlm-skeleton')).not.toBeNull();
  });

  it('si la carga falla ofrece reintentar en vez de dejar los esqueletos puestos', async () => {
    taxonomyMock.load.mockReturnValue(throwError(() => new Error('500')));
    await crear();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No se ha podido cargar la taxonomía');
    expect(fixture.nativeElement.querySelector('hlm-skeleton')).toBeNull();
  });

  it('reintentar vuelve a pedir la taxonomía', async () => {
    taxonomyMock.load.mockReturnValue(throwError(() => new Error('500')));
    await crear();

    taxonomyMock.load.mockReturnValue(of(cargada));
    component.reload();

    expect(taxonomyMock.load).toHaveBeenCalledTimes(2);
    expect(component.loadFailed()).toBe(false);
  });

  it('sin tags ofrece crear el primero en vez de una lista vacía', async () => {
    taxonomy.set({ tags: [] });
    await crear();

    expect(fixture.nativeElement.textContent).toContain('Aún no tienes tags');
    expect(fixture.nativeElement.querySelector('rc-tag-detail')).toBeNull();
  });

  it('lista los tags del club, archivados incluidos y señalados', async () => {
    taxonomy.set(cargada);
    await crear();

    const nav = fixture.nativeElement.querySelector('nav');
    expect(nav.textContent).toContain('nivel');
    expect(nav.textContent).toContain('grupo-antiguo');
    expect(nav.textContent).toContain('archivado');
  });

  it('abre el primer tag sin necesidad de hacer clic', async () => {
    taxonomy.set(cargada);
    await crear();

    expect(component.selectedTag()?.id).toBe('tag-nivel');
    const seleccionado = fixture.nativeElement.querySelector('[aria-current="true"]');
    expect(seleccionado.textContent).toContain('nivel');
  });

  it('al elegir otro tag cambia el detalle', async () => {
    taxonomy.set(cargada);
    await crear();

    component.select('tag-viejo');
    fixture.detectChanges();

    expect(component.selectedTag()?.id).toBe('tag-viejo');
  });

  it('si el tag seleccionado desaparece, cae al primero en vez de dejar el detalle vacío', async () => {
    taxonomy.set(cargada);
    await crear();
    component.select('tag-viejo');

    taxonomy.set({ tags: [cargada.tags[0]] });

    expect(component.selectedTag()?.id).toBe('tag-nivel');
  });

  it('cuenta los valores de cada tag, en singular cuando hay uno', async () => {
    await crear();

    expect(component.valuesLabel(1)).toBe('1 valor');
    expect(component.valuesLabel(2)).toBe('2 valores');
  });

  it('crear un tag delega en el servicio y abre el recién creado', async () => {
    taxonomy.set(cargada);
    await crear();

    component.createTag();
    const contexto: LabelDialogData = dialogMock.open.mock.calls[0][1].context;
    await contexto.submit('objetivo');

    expect(taxonomyMock.createTag).toHaveBeenCalledWith('objetivo');
    expect(toastMock.success).toHaveBeenCalled();
  });

  it('cancelar la creación no avisa de nada', async () => {
    taxonomy.set(cargada);
    dialogMock.open.mockReturnValue({ closed$: of(undefined) });
    await crear();

    component.createTag();

    expect(toastMock.success).not.toHaveBeenCalled();
  });
});
