import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogFooter, HlmDialogHeader, HlmDialogTitle } from '@spartan-ng/helm/dialog';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { firstValueFrom } from 'rxjs';
import { GroupMembershipResponse } from '../../../api/generated/models/group-membership-response';
import { messageForError } from '../../../core/api/error-codes';
import { GroupService } from '../../../core/group.service';
import { PlanDetail, PlanService } from '../../../core/plan.service';
import { SessionType, sessionTypeLabel } from '../session-types';

/** Datos que necesita el diálogo: el plan completo (LAL-25) — de él salen `grupoId` y las sesiones a resumir. */
export interface PublishPlanDialogData {
  readonly plan: PlanDetail;
}

/**
 * Confirmación de publicación (LAL-25), molde de `session-editor-dialog`. Solo el resumen de sesiones + el
 * listado de alumnos que recibirían el plan **ahora mismo** + el aviso de congelación del wireframe
 * (`docs/diseno/publicacion-plan.html`) — sin el bloque de personalizaciones (LAL-26) ni el switch de email
 * (sin AC que lo pida ni ruta barata para enviarlo, ver ticket de seguimiento en el README del módulo).
 *
 * El listado de alumnos es el estado **actual** de `club_taxonomia`/`miembro_grupo`, no el snapshot que
 * congelará el backend al publicar — pueden diferir en segundos, que es justo lo que dice el aviso.
 */
@Component({
  selector: 'rc-publish-plan-dialog',
  standalone: true,
  imports: [HlmButton, HlmDialogHeader, HlmDialogTitle, HlmDialogFooter, HlmSpinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div hlmDialogHeader>
      <h2 hlmDialogTitle i18n>Publicar plan al grupo</h2>
      <p class="text-sm text-muted-foreground">
        <span i18n>Semana del</span> {{ data.plan.semana }}
      </p>
    </div>

    <div class="flex min-w-96 max-w-lg flex-col gap-4 pt-2">
      <section>
        <p class="mb-2 text-sm font-medium" i18n>Lo que se publicará</p>
        <ul class="flex flex-col gap-1 text-sm">
          @for (session of data.plan.sesiones; track session.id) {
            <li class="flex justify-between rounded-lg border border-border px-3 py-1.5">
              <span>{{ session.dia }}</span>
              <span>{{ typeLabel(session.tipo) }}</span>
            </li>
          }
        </ul>
      </section>

      <section>
        <p class="mb-2 text-sm font-medium">
          @if (members(); as loaded) {
            <span i18n>{{ loaded.length }} alumnos en el grupo ahora mismo</span>
          } @else {
            <span i18n>Cargando alumnos del grupo…</span>
          }
        </p>
        <p class="text-xs text-muted-foreground" i18n>
          Este listado se congela al publicar. Cambios posteriores en tags no afectarán a este plan.
        </p>
        @if (members(); as loaded) {
          <div class="mt-2 flex flex-wrap gap-1.5">
            @for (member of loaded; track member.id) {
              <span class="rounded-full border border-border px-2.5 py-1 text-xs">{{ member.nombre }}</span>
            }
          </div>
        }
      </section>

      @if (errorMessage()) {
        <p class="text-sm text-danger" role="alert">{{ errorMessage() }}</p>
      }
    </div>

    <div hlmDialogFooter class="flex items-center justify-end gap-2">
      <button hlmBtn variant="outline" type="button" (click)="close()" i18n>Cancelar</button>
      <button hlmBtn type="button" [disabled]="publishing()" (click)="publish()">
        @if (publishing()) {
          <hlm-spinner aria-label="Publicando" i18n-aria-label />
        }
        <span i18n>Publicar plan</span>
      </button>
    </div>
  `,
})
export class PublishPlanDialogComponent implements OnInit {
  private readonly planService = inject(PlanService);
  private readonly groupService = inject(GroupService);
  private readonly dialogRef = inject(BrnDialogRef<boolean>);

  readonly data = injectBrnDialogContext<PublishPlanDialogData>();

  readonly members = signal<GroupMembershipResponse[] | undefined>(undefined);
  readonly publishing = signal(false);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.groupService.getDetail(this.data.plan.grupoId).subscribe({
      next: (detail) => this.members.set(detail.miembros),
      // Sin mensaje propio: el resumen de sesiones sigue siendo útil aunque no cargue el listado de alumnos.
      error: () => this.members.set([]),
    });
  }

  typeLabel(type: SessionType): string {
    return sessionTypeLabel(type);
  }

  async publish(): Promise<void> {
    this.publishing.set(true);
    this.errorMessage.set(null);
    try {
      await firstValueFrom(this.planService.publish(this.data.plan.id));
      this.dialogRef.close(true);
    } catch (err) {
      this.publishing.set(false);
      if (err instanceof HttpErrorResponse && err.status === 403) return;
      this.errorMessage.set(messageForError(err));
    }
  }

  close(): void {
    this.dialogRef.close(false);
  }
}
