import { DIALOG_DATA } from '@angular/cdk/dialog';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { ArchiveImpactDialogComponent, ArchiveImpactDialogData } from './archive-impact-dialog.component';

describe('ArchiveImpactDialogComponent', () => {
  let fixture: ComponentFixture<ArchiveImpactDialogComponent>;
  let component: ArchiveImpactDialogComponent;
  const dialogRefMock = { close: jest.fn() };

  const datos = (overrides: Partial<ArchiveImpactDialogData> = {}): ArchiveImpactDialogData => ({
    title: 'Archivar tag',
    message: 'nivel dejará de poder asignarse.',
    confirmLabel: 'Archivar',
    impact: { alumnosAfectados: 0, gruposQueLoRequieren: [] },
    ...overrides,
  });

  async function crear(data: ArchiveImpactDialogData = datos()): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ArchiveImpactDialogComponent],
      providers: [
        { provide: BrnDialogRef, useValue: dialogRefMock },
        { provide: DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ArchiveImpactDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('sin impacto, ofrece confirmar y cierra con true al hacerlo', async () => {
    await crear();

    expect(fixture.nativeElement.textContent).toContain('Archivar');
    component.confirm();

    expect(dialogRefMock.close).toHaveBeenCalledWith(true);
  });

  it('con alumnos afectados, avisa de cuántos son sin bloquear la confirmación', async () => {
    await crear(datos({ impact: { alumnosAfectados: 3, gruposQueLoRequieren: [] } }));

    expect(fixture.nativeElement.textContent).toContain('3');
    const confirmar = [...fixture.nativeElement.querySelectorAll('button')].find(
      (b: HTMLButtonElement) => b.textContent?.includes('Archivar'),
    );
    expect(confirmar).toBeTruthy();
  });

  it('con un grupo que lo requiere, bloquea: no hay botón de confirmar', async () => {
    await crear(
      datos({
        impact: {
          alumnosAfectados: 0,
          gruposQueLoRequieren: [
            { id: 'g1', nombre: 'Iniciación', perderiaTodosLosTagsRequeridos: false },
          ],
        },
      }),
    );

    expect(component.blocked()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Iniciación');
    const confirmar = [...fixture.nativeElement.querySelectorAll('button')].find(
      (b: HTMLButtonElement) => b.textContent?.trim() === 'Archivar',
    );
    expect(confirmar).toBeUndefined();
  });

  it('avisa explícitamente cuando el grupo se quedaría sin ningún tag requerido activo', async () => {
    await crear(
      datos({
        impact: {
          alumnosAfectados: 0,
          gruposQueLoRequieren: [
            { id: 'g1', nombre: 'Solo principiantes', perderiaTodosLosTagsRequeridos: true },
          ],
        },
      }),
    );

    expect(fixture.nativeElement.textContent).toContain('sin ningún tag requerido activo');
  });
});
