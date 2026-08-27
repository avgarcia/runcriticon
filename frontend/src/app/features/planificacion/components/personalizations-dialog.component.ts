import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogFooter, HlmDialogHeader, HlmDialogTitle } from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { forkJoin } from 'rxjs';
import { messageForError } from '../../../core/api/error-codes';
import { GroupDetail, GroupService } from '../../../core/group.service';
import { Personalization, PersonalizationData, PlanDetail, PlanService } from '../../../core/plan.service';
import { formatPace, parsePace } from '../pace-format';
import { SESSION_TYPES, SessionType } from '../session-types';

type VolumeType = 'DISTANCIA' | 'TIEMPO';
type FormMode = 'closed' | 'add' | 'edit';

/** Datos que necesita el diálogo: el plan y la sesión sobre la que se gestionan las personalizaciones. */
export interface PersonalizationsDialogData {
  readonly planId: string;
  readonly grupoId: string;
  readonly sesionId: string;
  readonly sesionLabel: string;
}

/**
 * Gestión de personalizaciones de una sesión por alumno (LAL-26): lista de overrides vigentes, con
 * alta/edición/retirada. Molde funcional: `GroupMembershipDialogComponent` (lista + buscador para
 * añadir + quitar); molde del formulario de override: `SessionEditorDialogComponent` (mismos campos
 * tipo/volumen/ritmo/notas, más el mensaje al alumno).
 *
 * **Candidatos del picker**: alumnos del grupo actual (`GroupService.getDetail`), no el snapshot
 * congelado del plan `PUBLICADO` — no existe endpoint que liste el snapshot, solo su tamaño
 * (`PublicacionResponse.alumnosEnSnapshot`). El backend es quien de verdad exige pertenencia al
 * snapshot en ese caso (`STUDENT_NOT_IN_PLAN`, 409); esta lista es una aproximación honesta, no la
 * fuente de verdad.
 *
 * Se abre como diálogo hermano, nunca anidado sobre `SessionEditorDialogComponent` — ver su botón
 * «Gestionar personalizaciones →», que cierra el editor antes de que `PlanDetailComponent` abra este.
 */
@Component({
  selector: 'rc-personalizations-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    HlmButton,
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogFooter,
    HlmInput,
    HlmLabel,
    HlmSpinner,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div hlmDialogHeader>
      <p class="text-xs font-medium uppercase tracking-wide text-muted-foreground" i18n>
        Gestionar personalizaciones
      </p>
      <h2 hlmDialogTitle>{{ data.sesionLabel }}</h2>
    </div>

    <div class="flex min-w-96 max-w-lg flex-col gap-4 pt-2">
      @if (loading()) {
        <hlm-spinner aria-label="Cargando" i18n-aria-label />
      } @else if (loadFailed()) {
        <p class="text-sm text-danger" role="alert" i18n>No se ha podido cargar la sesión.</p>
      } @else {
        <section>
          <h3 class="text-sm font-medium" i18n>Personalizaciones actuales ({{ personalizations().length }})</h3>
          @if (personalizations().length === 0) {
            <p class="mt-2 text-sm text-muted-foreground" i18n>Ningún alumno tiene un ajuste en esta sesión.</p>
          } @else {
            <ul class="mt-2 flex flex-col gap-1.5">
              @for (p of personalizations(); track p.alumnoId) {
                <li class="flex items-start justify-between gap-2 rounded-lg border border-border px-3 py-2">
                  <div class="min-w-0">
                    <p class="text-sm font-medium">{{ nombreDe(p.alumnoId) }} → {{ typeLabel(p.tipo) }}</p>
                    @if (volumeText(p); as vol) {
                      <p class="text-xs text-muted-foreground">{{ vol }}</p>
                    }
                    @if (p.mensajeAlAlumno) {
                      <p class="mt-1 rounded-md bg-primary/10 px-2 py-1 text-xs">
                        <strong i18n>Mensaje:</strong> {{ p.mensajeAlAlumno }}
                      </p>
                    } @else {
                      <p class="mt-1 text-xs italic text-muted-foreground" i18n>Sin mensaje al alumno.</p>
                    }
                  </div>
                  <div class="flex shrink-0 gap-1">
                    <button
                      hlmBtn
                      variant="ghost"
                      size="sm"
                      type="button"
                      [disabled]="saving()"
                      (click)="startEdit(p)"
                      i18n
                    >
                      Editar
                    </button>
                    <button
                      hlmBtn
                      variant="ghost"
                      size="sm"
                      type="button"
                      class="text-danger"
                      [disabled]="saving()"
                      (click)="remove(p.alumnoId)"
                      i18n
                    >
                      Quitar
                    </button>
                  </div>
                </li>
              }
            </ul>
          }
        </section>

        @if (formMode() === 'closed') {
          <button hlmBtn variant="outline" type="button" (click)="startAdd()" i18n>+ Añadir personalización</button>
        } @else {
          <section class="rounded-lg border border-dashed border-border p-3">
            <h3 class="mb-2 text-sm font-medium" i18n>
              {{ formMode() === 'edit' ? 'Editar personalización' : 'Añadir personalización' }}
            </h3>

            @if (formMode() === 'add' && !selectedStudentId()) {
              <input
                hlmInput
                class="w-full"
                type="search"
                placeholder="Buscar alumno"
                i18n-placeholder
                [value]="search()"
                (input)="onSearchInput($event)"
              />
              @if (search().trim() && candidates().length === 0) {
                <p class="mt-2 text-sm text-muted-foreground" i18n>Nadie coincide con la búsqueda.</p>
              }
              @if (candidates().length > 0) {
                <ul class="mt-2 flex max-h-32 flex-col gap-1 overflow-y-auto">
                  @for (alumno of candidates(); track alumno.id) {
                    <li>
                      <button
                        hlmBtn
                        variant="ghost"
                        size="sm"
                        type="button"
                        class="w-full justify-start"
                        (click)="selectedStudentId.set(alumno.id)"
                      >
                        {{ alumno.nombre }}
                      </button>
                    </li>
                  }
                </ul>
              }
            } @else {
              <p class="mb-3 text-sm">
                <span class="text-muted-foreground" i18n>Para</span>
                <strong> {{ nombreDe(selectedStudentId()!) }}</strong>
              </p>

              <form [formGroup]="form" id="personalization-form" (ngSubmit)="submit()" class="flex flex-col gap-3">
                <div>
                  <p class="mb-2 text-sm font-medium" i18n>Tipo de sesión</p>
                  <div class="flex flex-wrap gap-2">
                    @for (t of sessionTypes; track t.value) {
                      <button
                        type="button"
                        class="rounded-full border px-3 py-1.5 text-sm font-medium transition-colors"
                        [class.border-primary]="selectedType() === t.value"
                        [class.bg-primary]="selectedType() === t.value"
                        [class.text-primary-foreground]="selectedType() === t.value"
                        [class.border-border]="selectedType() !== t.value"
                        (click)="selectType(t.value)"
                      >
                        {{ t.label }}
                      </button>
                    }
                  </div>
                </div>

                @if (selectedType() && selectedType() !== 'DESCANSO') {
                  <div>
                    <p class="mb-2 text-sm font-medium" i18n>Volumen</p>
                    <div class="mb-2 flex gap-2">
                      <button
                        hlmBtn
                        type="button"
                        size="sm"
                        [variant]="volumeType() === 'DISTANCIA' ? 'default' : 'outline'"
                        (click)="volumeType.set('DISTANCIA')"
                        i18n
                      >
                        Distancia
                      </button>
                      <button
                        hlmBtn
                        type="button"
                        size="sm"
                        [variant]="volumeType() === 'TIEMPO' ? 'default' : 'outline'"
                        (click)="volumeType.set('TIEMPO')"
                        i18n
                      >
                        Tiempo
                      </button>
                    </div>
                    @if (volumeType(); as tipo) {
                      <input
                        hlmInput
                        type="number"
                        min="1"
                        class="w-full"
                        [placeholder]="tipo === 'DISTANCIA' ? textoMetros : textoMinutos"
                        formControlName="volumeValue"
                      />
                    }
                  </div>

                  <div class="flex flex-col gap-1.5">
                    <label hlmLabel for="perso-pace" i18n>Ritmo (m:ss /km)</label>
                    <input hlmInput id="perso-pace" formControlName="paceText" placeholder="3:45" autocomplete="off" />
                    @if (form.controls.paceText.hasError('pace')) {
                      <p class="text-xs text-danger" i18n>Usa el formato m:ss, por ejemplo 3:45.</p>
                    }
                  </div>
                }

                <div class="flex flex-col gap-1.5">
                  <label hlmLabel for="perso-notes" i18n>Notas</label>
                  <textarea
                    id="perso-notes"
                    formControlName="notes"
                    rows="2"
                    maxlength="1000"
                    class="dark:bg-input/30 border-input focus-visible:border-ring focus-visible:ring-ring/20 min-h-16 w-full rounded-lg border bg-card px-3 py-2 text-sm shadow-xs outline-none placeholder:text-muted-foreground"
                  ></textarea>
                </div>

                <div class="flex flex-col gap-1.5">
                  <label hlmLabel for="perso-message" i18n>Mensaje para el alumno (opcional)</label>
                  <textarea
                    id="perso-message"
                    formControlName="message"
                    rows="2"
                    maxlength="1000"
                    placeholder="Lo que verá junto a su sesión de hoy…"
                    i18n-placeholder
                    class="dark:bg-input/30 border-input focus-visible:border-ring focus-visible:ring-ring/20 min-h-16 w-full rounded-lg border bg-card px-3 py-2 text-sm shadow-xs outline-none placeholder:text-muted-foreground"
                  ></textarea>
                  <p class="text-xs text-muted-foreground" i18n>✉ El alumno verá este mensaje junto a su sesión.</p>
                </div>
              </form>

              <div class="mt-3 flex justify-end gap-2">
                <button hlmBtn variant="outline" size="sm" type="button" (click)="cancelForm()" i18n>Cancelar</button>
                <button hlmBtn size="sm" type="submit" form="personalization-form" [disabled]="!canSubmit() || saving()">
                  @if (saving()) {
                    <hlm-spinner aria-label="Guardando" i18n-aria-label />
                  }
                  <span i18n>{{ formMode() === 'edit' ? 'Guardar cambios' : 'Añadir personalización' }}</span>
                </button>
              </div>
            }
          </section>
        }

        @if (errorMessage()) {
          <p class="text-sm text-danger" role="alert">{{ errorMessage() }}</p>
        }
      }
    </div>

    <div hlmDialogFooter>
      <p class="mr-auto text-xs text-muted-foreground" i18n>Los cambios se aplican al momento.</p>
      <button hlmBtn variant="outline" type="button" (click)="close()" i18n>Cerrar</button>
    </div>
  `,
})
export class PersonalizationsDialogComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly planService = inject(PlanService);
  private readonly groupService = inject(GroupService);
  private readonly dialogRef = inject(BrnDialogRef<boolean>);

  readonly data = injectBrnDialogContext<PersonalizationsDialogData>();

  readonly sessionTypes = SESSION_TYPES;
  readonly textoMetros = $localize`Metros`;
  readonly textoMinutos = $localize`Minutos`;

  readonly loading = signal(true);
  readonly loadFailed = signal(false);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly plan = signal<PlanDetail | null>(null);
  readonly group = signal<GroupDetail | null>(null);
  readonly search = signal('');

  readonly formMode = signal<FormMode>('closed');
  readonly selectedStudentId = signal<string | null>(null);
  readonly selectedType = signal<SessionType | null>(null);
  readonly volumeType = signal<VolumeType | null>(null);

  /** Cambió algo (alta, edición o retirada) — se avisa a `PlanDetailComponent` al cerrar. */
  private changed = false;

  readonly form = this.fb.nonNullable.group({
    volumeValue: [null as number | null, [Validators.min(1)]],
    paceText: ['', [paceValidator]],
    notes: ['', [Validators.maxLength(1000)]],
    message: ['', [Validators.maxLength(1000)]],
  });

  readonly personalizations = computed<Personalization[]>(
    () => this.plan()?.personalizaciones?.filter((p) => p.sesionId === this.data.sesionId) ?? [],
  );

  /** Alumnos del grupo sin personalización ya vigente en esta sesión, que coinciden con la búsqueda. */
  readonly candidates = computed(() => {
    const texto = this.search().trim().toLowerCase();
    const grupo = this.group();
    if (!texto || !grupo) return [];
    const yaPersonalizados = new Set(this.personalizations().map((p) => p.alumnoId));
    return grupo.miembros.filter(
      (m) => !yaPersonalizados.has(m.id) && m.nombre.toLowerCase().includes(texto),
    );
  });

  canSubmit(): boolean {
    return this.selectedType() !== null && this.form.valid;
  }

  ngOnInit(): void {
    this.load();
  }

  onSearchInput(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  typeLabel(type: Personalization['tipo']): string {
    return SESSION_TYPES.find((t) => t.value === type)?.label ?? type;
  }

  volumeText(p: Personalization): string | null {
    const volumen = p.volumen;
    if (!volumen) return null;
    return volumen.tipo === 'DISTANCIA' ? `${volumen.metros} m` : `${volumen.minutos} min`;
  }

  nombreDe(alumnoId: string): string {
    return this.group()?.miembros.find((m) => m.id === alumnoId)?.nombre ?? alumnoId;
  }

  selectType(type: SessionType): void {
    this.selectedType.set(type);
    if (type === 'DESCANSO') {
      this.volumeType.set(null);
      this.form.patchValue({ volumeValue: null, paceText: '' });
    }
  }

  startAdd(): void {
    this.formMode.set('add');
    this.selectedStudentId.set(null);
    this.selectedType.set(null);
    this.volumeType.set(null);
    this.search.set('');
    this.form.reset({ volumeValue: null, paceText: '', notes: '', message: '' });
  }

  startEdit(p: Personalization): void {
    this.formMode.set('edit');
    this.selectedStudentId.set(p.alumnoId);
    this.selectedType.set(p.tipo);
    this.volumeType.set((p.volumen?.tipo as VolumeType) ?? null);
    this.form.reset({
      volumeValue: p.volumen?.metros ?? p.volumen?.minutos ?? null,
      paceText: p.ritmo?.tipo === 'ABSOLUTO' ? formatPace(p.ritmo.segundosPorKm ?? 0) : '',
      notes: p.notas ?? '',
      message: p.mensajeAlAlumno ?? '',
    });
  }

  cancelForm(): void {
    this.formMode.set('closed');
    this.selectedStudentId.set(null);
  }

  submit(): void {
    const type = this.selectedType();
    const studentId = this.selectedStudentId();
    if (!type || !studentId || !this.canSubmit()) return;
    this.saving.set(true);
    this.errorMessage.set(null);
    this.form.controls.paceText.setErrors(null);
    const body = this.buildBody(type);
    this.planService.setPersonalization(this.data.planId, this.data.sesionId, studentId, body).subscribe({
      next: (plan) => {
        this.saving.set(false);
        this.changed = true;
        this.plan.set(plan);
        this.formMode.set('closed');
        this.selectedStudentId.set(null);
      },
      error: (err: unknown) => this.handleError(err),
    });
  }

  remove(alumnoId: string): void {
    this.saving.set(true);
    this.errorMessage.set(null);
    this.planService.removePersonalization(this.data.planId, this.data.sesionId, alumnoId).subscribe({
      next: () => {
        this.changed = true;
        // El DELETE no devuelve el plan recalculado: hay que volver a pedirlo (mismo criterio que
        // `GroupMembershipDialogComponent.clearOverride`).
        this.planService.get(this.data.planId).subscribe({
          next: (plan) => {
            this.saving.set(false);
            this.plan.set(plan);
          },
          error: (err: unknown) => this.handleError(err),
        });
      },
      error: (err: unknown) => this.handleError(err),
    });
  }

  close(): void {
    this.dialogRef.close(this.changed);
  }

  private load(): void {
    this.loading.set(true);
    this.loadFailed.set(false);
    forkJoin([this.planService.get(this.data.planId), this.groupService.getDetail(this.data.grupoId)]).subscribe({
      next: ([plan, grupo]) => {
        this.plan.set(plan);
        this.group.set(grupo);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadFailed.set(true);
      },
    });
  }

  private buildBody(type: SessionType): PersonalizationData {
    const { volumeValue, paceText, notes, message } = this.form.getRawValue();
    const volumeKind = this.volumeType();
    const seconds = paceText.trim() ? parsePace(paceText) : null;
    return {
      tipo: type,
      volumen:
        volumeKind && volumeValue
          ? volumeKind === 'DISTANCIA'
            ? { tipo: 'DISTANCIA', metros: volumeValue }
            : { tipo: 'TIEMPO', minutos: volumeValue }
          : undefined,
      ritmo: seconds !== null ? { tipo: 'ABSOLUTO', segundosPorKm: seconds } : undefined,
      notas: notes.trim() ? notes.trim() : undefined,
      mensajeAlAlumno: message.trim() ? message.trim() : null,
    };
  }

  private handleError(err: unknown): void {
    this.saving.set(false);
    // El 403 ya lo avisa el interceptor global con su toast; no duplicamos el mensaje.
    if (err instanceof HttpErrorResponse && err.status === 403) return;
    this.errorMessage.set(messageForError(err));
  }
}

/** Rechaza un texto que no tenga el formato `m:ss`. Vacío se acepta: el ritmo es opcional. */
function paceValidator(control: AbstractControl): { pace: true } | null {
  const value: string = control.value ?? '';
  if (!value.trim()) return null;
  return parsePace(value) === null ? { pace: true } : null;
}
