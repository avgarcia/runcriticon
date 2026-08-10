import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GroupMembers } from '../../../core/group.service';
import { GroupPreviewComponent } from './group-preview.component';

describe('GroupPreviewComponent', () => {
  let fixture: ComponentFixture<GroupPreviewComponent>;
  let component: GroupPreviewComponent;

  function miembros(cuantos: number): GroupMembers {
    return {
      total: cuantos,
      alumnos: Array.from({ length: cuantos }, (_, i) => ({ id: `a${i}`, nombre: `Alumno ${i}` })),
    };
  }

  async function crear(inputs: Record<string, unknown>): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({ imports: [GroupPreviewComponent] }).compileComponents();
    fixture = TestBed.createComponent(GroupPreviewComponent);
    component = fixture.componentInstance;
    Object.entries(inputs).forEach(([name, value]) => fixture.componentRef.setInput(name, value));
    fixture.detectChanges();
  }

  it('el contador vive en una región que se anuncia sola al cambiar', async () => {
    await crear({ members: miembros(3) });

    expect(fixture.nativeElement.querySelector('[aria-live="polite"]')).not.toBeNull();
  });

  it('usa el singular con un solo alumno', async () => {
    await crear({ members: miembros(1) });

    expect(fixture.nativeElement.textContent).toContain('alumno cumple este filtro');
  });

  it('enseña diez alumnos y ofrece ver el resto', async () => {
    await crear({ members: miembros(12) });

    expect(component.visibleMembers()).toHaveLength(10);
    expect(component.hiddenCount()).toBe(2);

    component.expand();
    fixture.detectChanges();

    expect(component.visibleMembers()).toHaveLength(12);
    expect(component.hiddenCount()).toBe(0);
  });

  it('sin condiciones invita a añadir una en vez de decir que nadie encaja', async () => {
    await crear({ members: miembros(0), filtered: false });

    expect(component.emptyLabel()).toContain('Añade una condición');
  });

  it('con filtro y sin resultados avisa de que el filtro es demasiado estricto', async () => {
    await crear({ members: miembros(0), filtered: true });

    expect(component.emptyLabel()).toContain('Ningún alumno cumple este filtro');
  });

  // Recalcular no vacía el contador: parpadearía en cada edición del filtro.
  it('mientras recalcula conserva el número anterior y lo marca como ocupado', async () => {
    await crear({ members: miembros(5), loading: true });

    expect(fixture.nativeElement.textContent).toContain('5');
    expect(fixture.nativeElement.querySelector('[aria-busy="true"]')).not.toBeNull();
  });

  it('un error del servidor se ve en el panel con opción de recargar', async () => {
    await crear({ members: miembros(2), error: 'Ese valor está archivado' });

    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'Ese valor está archivado',
    );
    expect(fixture.nativeElement.textContent).toContain('Recargar taxonomía');
  });

  it('compone las iniciales con las dos primeras palabras del nombre', async () => {
    await crear({ members: miembros(1) });

    expect(component.initials('Pedro Cordero Ruiz')).toBe('PC');
  });
});
