import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HlmAlert, HlmAlertDescription, HlmAlertTitle } from '@spartan-ng/helm/alert';
import { HlmBadge } from '@spartan-ng/helm/badge';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { expectNoA11yViolations } from '../../testing/axe';

@Component({
  standalone: true,
  imports: [HlmAlert, HlmAlertTitle, HlmAlertDescription, HlmBadge, HlmButton, HlmInput, HlmLabel],
  template: `
    <main>
      <h1>Componentes reutilizables</h1>
      <button hlmBtn type="button">Guardar</button>
      <a hlmBtn href="/inicio">Volver</a>
      <label hlmLabel for="email">Email</label>
      <input hlmInput id="email" type="email" />
      <span hlmBadge>Activo</span>
      <div hlmAlert>
        <h2 hlmAlertTitle>Aviso</h2>
        <p hlmAlertDescription>Se guardó el cambio.</p>
      </div>
    </main>
  `,
})
class UiHostComponent {}

describe('componentes helm reutilizables (axe-core)', () => {
  it('el botón, el campo con etiqueta, la insignia y la alerta no tienen violaciones de accesibilidad', async () => {
    TestBed.configureTestingModule({ imports: [UiHostComponent] });
    const fixture = TestBed.createComponent(UiHostComponent);
    fixture.detectChanges();

    await expectNoA11yViolations(fixture.nativeElement);
  });

  it('el helper detecta una violación real: un campo sin nombre accesible', async () => {
    const root = document.createElement('main');
    root.innerHTML = '<h1>Título</h1><input type="text" />';

    await expect(expectNoA11yViolations(root)).rejects.toThrow(/label/);
  });
});
