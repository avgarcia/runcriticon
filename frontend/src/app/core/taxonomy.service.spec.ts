import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { TaxonomyService } from './taxonomy.service';
import { TaxonomiaService as TaxonomyApi } from '../api/generated/services/taxonomia.service';
import { TagValueResponse } from '../api/generated/models/tag-value-response';

describe('TaxonomyService', () => {
  let service: TaxonomyService;
  const apiMock = {
    consultarTaxonomia: jest.fn(),
    crearTag: jest.fn(),
    renombrarTag: jest.fn(),
    archivarTag: jest.fn(),
    impactoArchivadoTag: jest.fn(),
    reactivarTag: jest.fn(),
    crearValorTag: jest.fn(),
    renombrarValor: jest.fn(),
    archivarValor: jest.fn(),
    impactoArchivadoValor: jest.fn(),
    reactivarValor: jest.fn(),
  };

  const valor = (id: string, texto: string): TagValueResponse => ({
    id,
    valor: texto,
    metadata: { tipo: 'EMPTY' },
  });

  // Fábricas y no constantes compartidas: cada test arranca con su propia copia y no puede
  // contaminar al siguiente por aliasing.
  const nivel = () => ({
    id: 'tag-nivel',
    nombre: 'nivel',
    valores: [valor('val-inic', 'iniciación'), valor('val-medio', 'medio')],
  });
  const terreno = () => ({
    id: 'tag-terreno',
    nombre: 'terreno',
    valores: [valor('val-asfalto', 'asfalto')],
  });
  const taxonomia = () => ({ tags: [nivel(), terreno()] });

  beforeEach(async () => {
    jest.clearAllMocks();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [{ provide: TaxonomyApi, useValue: apiMock }],
    });
    service = TestBed.inject(TaxonomyService);
    apiMock.consultarTaxonomia.mockResolvedValue(taxonomia());
  });

  const cargar = () => firstValueFrom(service.load());

  it('arranca sin cargar', () => {
    expect(service.taxonomy()).toBeUndefined();
  });

  it('load guarda la taxonomía completa, archivados incluidos', async () => {
    const archivado = { id: 'tag-viejo', nombre: 'grupo-antiguo', valores: [], archivadoEn: '2026-01-01T00:00:00Z' };
    apiMock.consultarTaxonomia.mockResolvedValue({ tags: [nivel(), archivado] });

    await cargar();

    expect(service.taxonomy()?.tags.map((t) => t.id)).toEqual(['tag-nivel', 'tag-viejo']);
  });

  it('createTag añade el eje al final, que es donde lo pone el backend', async () => {
    await cargar();
    const creado = { id: 'tag-nuevo', nombre: 'objetivo', valores: [] };
    apiMock.crearTag.mockResolvedValue(creado);

    await firstValueFrom(service.createTag('objetivo'));

    expect(apiMock.crearTag).toHaveBeenCalledWith({ body: { nombre: 'objetivo' } });
    expect(service.taxonomy()?.tags.map((t) => t.id)).toEqual(['tag-nivel', 'tag-terreno', 'tag-nuevo']);
  });

  it('renameTag guarda el nombre normalizado por el servidor, no el tecleado', async () => {
    await cargar();
    apiMock.renombrarTag.mockResolvedValue({ ...nivel(), nombre: 'Nivel' });

    await firstValueFrom(service.renameTag('tag-nivel', '  Nivel  '));

    expect(apiMock.renombrarTag).toHaveBeenCalledWith({ tagId: 'tag-nivel', body: { nombre: '  Nivel  ' } });
    expect(service.taxonomy()?.tags[0].nombre).toBe('Nivel');
  });

  it('renombrar un eje conserva sus valores', async () => {
    await cargar();
    apiMock.renombrarTag.mockResolvedValue({ ...nivel(), nombre: 'Nivel' });

    await firstValueFrom(service.renameTag('tag-nivel', 'Nivel'));

    expect(service.taxonomy()?.tags[0].valores).toHaveLength(2);
  });

  it('archiveTag marca el eje como archivado sin sacarlo de la lista', async () => {
    await cargar();
    apiMock.archivarTag.mockResolvedValue({ ...nivel(), archivadoEn: '2026-07-30T10:00:00Z' });

    await firstValueFrom(service.archiveTag('tag-nivel'));

    expect(service.taxonomy()?.tags).toHaveLength(2);
    expect(service.taxonomy()?.tags[0].archivadoEn).toBe('2026-07-30T10:00:00Z');
  });

  it('getTagArchiveImpact delega en el endpoint de impacto del eje', async () => {
    const impacto = {
      alumnosAfectados: 2,
      gruposQueLoRequieren: [{ id: 'g1', nombre: 'Iniciación', perderiaTodosLosTagsRequeridos: false }],
    };
    apiMock.impactoArchivadoTag.mockResolvedValue(impacto);

    const resultado = await firstValueFrom(service.getTagArchiveImpact('tag-nivel'));

    expect(apiMock.impactoArchivadoTag).toHaveBeenCalledWith({ tagId: 'tag-nivel' });
    expect(resultado).toEqual(impacto);
  });

  it('reactivateTag limpia la marca de archivado', async () => {
    await cargar();
    apiMock.reactivarTag.mockResolvedValue({ ...nivel(), archivadoEn: null });

    await firstValueFrom(service.reactivateTag('tag-nivel'));

    expect(service.taxonomy()?.tags[0].archivadoEn).toBeNull();
  });

  it('createValue añade el valor a su eje y deja los demás intactos', async () => {
    await cargar();
    apiMock.crearValorTag.mockResolvedValue(valor('val-alto', 'alto'));

    await firstValueFrom(service.createValue('tag-nivel', 'alto'));

    expect(apiMock.crearValorTag).toHaveBeenCalledWith({ tagId: 'tag-nivel', body: { valor: 'alto' } });
    expect(service.taxonomy()?.tags[0].valores.map((v) => v.id)).toEqual(['val-inic', 'val-medio', 'val-alto']);
    expect(service.taxonomy()?.tags[1].valores).toHaveLength(1);
  });

  it('renameValue localiza el valor por su id aunque la respuesta no diga de qué eje es', async () => {
    await cargar();
    apiMock.renombrarValor.mockResolvedValue(valor('val-asfalto', 'Asfalto'));

    await firstValueFrom(service.renameValue('val-asfalto', 'Asfalto'));

    expect(service.taxonomy()?.tags[1].valores[0].valor).toBe('Asfalto');
    expect(service.taxonomy()?.tags[0].valores.map((v) => v.valor)).toEqual(['iniciación', 'medio']);
  });

  it('archiveValue marca el valor sin sacarlo de su eje', async () => {
    await cargar();
    apiMock.archivarValor.mockResolvedValue({ ...valor('val-medio', 'medio'), archivadoEn: '2026-07-30T10:00:00Z' });

    await firstValueFrom(service.archiveValue('val-medio'));

    expect(service.taxonomy()?.tags[0].valores).toHaveLength(2);
    expect(service.taxonomy()?.tags[0].valores[1].archivadoEn).toBe('2026-07-30T10:00:00Z');
  });

  it('getValueArchiveImpact delega en el endpoint de impacto del valor', async () => {
    const impacto = { alumnosAfectados: 0, gruposQueLoRequieren: [] };
    apiMock.impactoArchivadoValor.mockResolvedValue(impacto);

    const resultado = await firstValueFrom(service.getValueArchiveImpact('val-medio'));

    expect(apiMock.impactoArchivadoValor).toHaveBeenCalledWith({ valorId: 'val-medio' });
    expect(resultado).toEqual(impacto);
  });

  it('reactivateValue limpia la marca de archivado', async () => {
    await cargar();
    apiMock.reactivarValor.mockResolvedValue({ ...valor('val-medio', 'medio'), archivadoEn: null });

    await firstValueFrom(service.reactivateValue('val-medio'));

    expect(service.taxonomy()?.tags[0].valores[1].archivadoEn).toBeNull();
  });

  it('una mutación que falla no toca el estado', async () => {
    await cargar();
    apiMock.renombrarTag.mockRejectedValue(new Error('409'));

    await expect(firstValueFrom(service.renameTag('tag-nivel', 'terreno'))).rejects.toBeDefined();

    expect(service.taxonomy()?.tags[0].nombre).toBe('nivel');
  });

  it('una mutación antes de cargar no inventa un estado a medias', async () => {
    apiMock.crearTag.mockResolvedValue({ id: 'tag-nuevo', nombre: 'objetivo', valores: [] });

    await firstValueFrom(service.createTag('objetivo'));

    expect(service.taxonomy()).toBeUndefined();
  });

  it('reset devuelve el estado a sin cargar', async () => {
    await cargar();

    service.reset();

    expect(service.taxonomy()).toBeUndefined();
  });
});
