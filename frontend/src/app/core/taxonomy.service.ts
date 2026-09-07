import { Injectable, inject, signal } from '@angular/core';
import { Observable, from, tap } from 'rxjs';
// OJO: el servicio generado del contrato se llama `TaxonomiaService`. Se importa aliasado para que el
// servicio de estado de la app conserve el nombre en inglés, igual que hace `club.service.ts`.
import { TaxonomiaService as TaxonomyApi } from '../api/generated/services/taxonomia.service';
import { ImpactoArchivadoResponse } from '../api/generated/models/impacto-archivado-response';
import { TagKeyResponse } from '../api/generated/models/tag-key-response';
import { TagValueResponse } from '../api/generated/models/tag-value-response';
import { TaxonomyResponse } from '../api/generated/models/taxonomy-response';

/** Taxonomía del club tal y como la devuelve el backend (alias de los modelos generados). */
export type Taxonomy = TaxonomyResponse;
export type TagKey = TagKeyResponse;
export type TagValue = TagValueResponse;
export type TagArchiveImpact = ImpactoArchivadoResponse;

/**
 * Estado de la taxonomía del club en la SPA. Delega el HTTP en el cliente generado desde el contrato
 * OpenAPI.
 *
 * El estado es de dos valores, no de tres como el del club: `undefined` mientras no se ha cargado y
 * la taxonomía en cuanto llega. El GET nunca responde 404 — un club sin ejes devuelve `{tags: []}`—
 * así que no hace falta distinguir «no existe» de «vacía».
 *
 * Cada mutación guarda en el signal **lo que devuelve el servidor**, no el valor que se tecleó: el
 * backend normaliza y es él quien decide. Se parchea el eje o el valor tocado en vez de recargar la
 * taxonomía entera porque cada endpoint ya devuelve el estado autoritativo del elemento que cambió.
 */
@Injectable({ providedIn: 'root' })
export class TaxonomyService {
  private readonly api = inject(TaxonomyApi);

  private readonly currentTaxonomy = signal<Taxonomy | undefined>(undefined);

  /** Taxonomía del club (solo lectura). `undefined` mientras no se haya cargado. */
  readonly taxonomy = this.currentTaxonomy.asReadonly();

  /**
   * Carga la taxonomía completa. Trae también los ejes y valores archivados: el editor los muestra
   * atenuados con opción de reactivarlos, así que no se filtran aquí.
   */
  load(): Observable<Taxonomy> {
    return from(this.api.consultarTaxonomia()).pipe(tap((taxonomy) => this.currentTaxonomy.set(taxonomy)));
  }

  /** Vacía la caché (al cerrar sesión): otro usuario puede pertenecer a otro club. */
  reset(): void {
    this.currentTaxonomy.set(undefined);
  }

  // --- ejes ---------------------------------------------------------------------------------------

  createTag(nombre: string): Observable<TagKey> {
    return from(this.api.crearTag({ body: { nombre } })).pipe(tap((tag) => this.appendTag(tag)));
  }

  renameTag(tagId: string, nombre: string): Observable<TagKey> {
    return from(this.api.renombrarTag({ tagId, body: { nombre } })).pipe(tap((tag) => this.replaceTag(tag)));
  }

  archiveTag(tagId: string): Observable<TagKey> {
    return from(this.api.archivarTag({ tagId })).pipe(tap((tag) => this.replaceTag(tag)));
  }

  /**
   * Impacto de archivar el eje (LAL-83), a consultar antes de intentarlo: alumnos que tienen alguno
   * de sus valores asignados (informativo) y grupos vivos que exigen alguno en su filtro (bloqueante,
   * ADR-0002 D10).
   */
  getTagArchiveImpact(tagId: string): Observable<TagArchiveImpact> {
    return from(this.api.impactoArchivadoTag({ tagId }));
  }

  reactivateTag(tagId: string): Observable<TagKey> {
    return from(this.api.reactivarTag({ tagId })).pipe(tap((tag) => this.replaceTag(tag)));
  }

  // --- valores ------------------------------------------------------------------------------------

  createValue(tagId: string, valor: string): Observable<TagValue> {
    return from(this.api.crearValorTag({ tagId, body: { valor } })).pipe(
      tap((value) => this.appendValue(tagId, value)),
    );
  }

  renameValue(valorId: string, valor: string): Observable<TagValue> {
    return from(this.api.renombrarValor({ valorId, body: { valor } })).pipe(
      tap((value) => this.replaceValue(value)),
    );
  }

  archiveValue(valorId: string): Observable<TagValue> {
    return from(this.api.archivarValor({ valorId })).pipe(tap((value) => this.replaceValue(value)));
  }

  /** Impacto de archivar el valor (LAL-83), mismo criterio que {@link getTagArchiveImpact}. */
  getValueArchiveImpact(valorId: string): Observable<TagArchiveImpact> {
    return from(this.api.impactoArchivadoValor({ valorId }));
  }

  reactivateValue(valorId: string): Observable<TagValue> {
    return from(this.api.reactivarValor({ valorId })).pipe(tap((value) => this.replaceValue(value)));
  }

  // --- parcheo del estado -------------------------------------------------------------------------

  private appendTag(tag: TagKey): void {
    // Al final y no ordenando: el backend devuelve los ejes en orden de alta y el recién creado es
    // justo el último.
    this.currentTaxonomy.update((taxonomy) =>
      taxonomy ? { ...taxonomy, tags: [...taxonomy.tags, tag] } : taxonomy,
    );
  }

  /**
   * Reemplaza el eje entero. Es seguro porque renombrar, archivar y reactivar devuelven el eje con
   * sus valores incluidos, no una versión reducida.
   */
  private replaceTag(tag: TagKey): void {
    this.currentTaxonomy.update((taxonomy) =>
      taxonomy
        ? { ...taxonomy, tags: taxonomy.tags.map((existing) => (existing.id === tag.id ? tag : existing)) }
        : taxonomy,
    );
  }

  private appendValue(tagId: string, value: TagValue): void {
    this.currentTaxonomy.update((taxonomy) =>
      taxonomy
        ? {
            ...taxonomy,
            tags: taxonomy.tags.map((tag) =>
              tag.id === tagId ? { ...tag, valores: [...tag.valores, value] } : tag,
            ),
          }
        : taxonomy,
    );
  }

  /**
   * Localiza el valor recorriendo los ejes: la respuesta de renombrar/archivar/reactivar un valor no
   * trae el id de su eje, y los ids de valor son únicos en todo el club, no solo dentro del eje.
   */
  private replaceValue(value: TagValue): void {
    this.currentTaxonomy.update((taxonomy) =>
      taxonomy
        ? {
            ...taxonomy,
            tags: taxonomy.tags.map((tag) =>
              tag.valores.some((existing) => existing.id === value.id)
                ? {
                    ...tag,
                    valores: tag.valores.map((existing) => (existing.id === value.id ? value : existing)),
                  }
                : tag,
            ),
          }
        : taxonomy,
    );
  }
}
