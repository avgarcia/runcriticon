import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { ErrorBannerComponent } from './error-banner.component';

@Component({
  standalone: true,
  imports: [ErrorBannerComponent],
  template: `<rc-error-banner>Mensaje de prueba</rc-error-banner>`,
})
class HostComponent {}

describe('ErrorBannerComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('proyecta el contenido dentro de un <p role="alert">', () => {
    const p = fixture.nativeElement.querySelector('p.error-banner');
    expect(p).toBeTruthy();
    expect(p.getAttribute('role')).toBe('alert');
    expect(p.textContent).toContain('Mensaje de prueba');
  });
});
